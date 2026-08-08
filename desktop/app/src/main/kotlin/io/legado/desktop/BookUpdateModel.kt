package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreLibrary
import io.legado.core.source.OnlineBookService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class BookUpdateStatus {
    UPDATED,
    NO_UPDATE,
    FAILED
}

data class BookUpdateResult(
    val book: CoreBook,
    val status: BookUpdateStatus,
    val newChapterCount: Int = 0,
    val error: String? = null
)

enum class BookUpdateTaskStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED
}

enum class BookUpdateItemStatus {
    PENDING,
    RUNNING,
    UPDATED,
    NO_UPDATE,
    FAILED,
    CANCELLED
}

data class BookUpdateItemState(
    val book: CoreBook,
    val status: BookUpdateItemStatus = BookUpdateItemStatus.PENDING,
    val attempt: Int = 0,
    val result: BookUpdateResult? = null
)

data class BookUpdateTaskState(
    val status: BookUpdateTaskStatus = BookUpdateTaskStatus.IDLE,
    val total: Int = 0,
    val completed: Int = 0,
    val currentBook: CoreBook? = null,
    val currentAttempt: Int = 0,
    val items: List<BookUpdateItemState> = emptyList(),
    val results: List<BookUpdateResult> = emptyList(),
    val error: String? = null
)

class BookUpdateModel(
    private val library: CoreLibrary,
    private val service: OnlineBookService
) {
    var results: List<BookUpdateResult> = emptyList()
        private set

    @Volatile
    var isChecking: Boolean = false
        private set

    fun checkAll(): List<BookUpdateResult> {
        isChecking = true
        val checked = mutableListOf<BookUpdateResult>()
        try {
            updateCandidates().forEach { book ->
                checked += checkBook(book)
            }
            results = checked
            return checked
        } finally {
            isChecking = false
        }
    }

    fun createUpdateTask(maxRetries: Int = 1): BookUpdateTask = BookUpdateTask(
        model = this,
        books = updateCandidates(),
        maxRetries = maxRetries.coerceAtLeast(0)
    )

    internal fun updateCandidates(): List<CoreBook> = library.books()
        .filter { it.canUpdate && it.origin != "local" && it.origin != "loc_book" }
        .filter { library.source(it.origin)?.enabled == true }

    internal fun checkBook(book: CoreBook): BookUpdateResult {
        val previousCount = book.totalChapterNum
        return try {
            service.refreshChapters(book)
            val refreshed = library.book(book.bookUrl) ?: book
            val newChapterCount = (refreshed.totalChapterNum - previousCount).coerceAtLeast(0)
            val updated = refreshed.copy(
                lastCheckTime = System.currentTimeMillis(),
                lastCheckCount = newChapterCount
            )
            library.saveBook(updated)
            BookUpdateResult(
                book = updated,
                status = if (newChapterCount > 0) {
                    BookUpdateStatus.UPDATED
                } else {
                    BookUpdateStatus.NO_UPDATE
                },
                newChapterCount = newChapterCount
            )
        } catch (throwable: Throwable) {
            BookUpdateResult(
                book = book,
                status = BookUpdateStatus.FAILED,
                error = throwable.message ?: "更新检查失败"
            )
        }
    }

    internal fun taskStarted() {
        isChecking = true
    }

    internal fun taskFinished(taskResults: List<BookUpdateResult>) {
        results = taskResults
        isChecking = false
    }
}

class BookUpdateTask internal constructor(
    private val model: BookUpdateModel,
    books: List<CoreBook>,
    private val maxRetries: Int
) {
    private val control = ReentrantLock()
    private val stateChanged = control.newCondition()
    private val stateFlow = MutableStateFlow(
        BookUpdateTaskState(
            total = books.size,
            items = books.map(::BookUpdateItemState)
        )
    )
    private val books = books.toList()

    @Volatile
    private var paused = false

    @Volatile
    private var cancelled = false

    private var hasRun = false

    val state: StateFlow<BookUpdateTaskState> = stateFlow.asStateFlow()

    fun pause() {
        control.withLock {
            if (stateFlow.value.status == BookUpdateTaskStatus.RUNNING) {
                paused = true
                publish { it.copy(status = BookUpdateTaskStatus.PAUSED) }
            }
        }
    }

    fun resume() {
        control.withLock {
            if (stateFlow.value.status == BookUpdateTaskStatus.PAUSED && !cancelled) {
                paused = false
                publish { it.copy(status = BookUpdateTaskStatus.RUNNING) }
                stateChanged.signalAll()
            }
        }
    }

    fun cancel() {
        control.withLock {
            if (stateFlow.value.status in setOf(
                    BookUpdateTaskStatus.IDLE,
                    BookUpdateTaskStatus.RUNNING,
                    BookUpdateTaskStatus.PAUSED
                )
            ) {
                cancelled = true
                paused = false
                publish { current ->
                    current.copy(
                        status = BookUpdateTaskStatus.CANCELLED,
                        items = current.items.map { item ->
                            if (item.status == BookUpdateItemStatus.PENDING) {
                                item.copy(status = BookUpdateItemStatus.CANCELLED)
                            } else {
                                item
                            }
                        }
                    )
                }
                stateChanged.signalAll()
            }
        }
    }

    fun run(): List<BookUpdateResult> {
        control.withLock {
            check(!hasRun) { "更新任务只能执行一次" }
            hasRun = true
            if (cancelled) return emptyList()
            publish { it.copy(status = BookUpdateTaskStatus.RUNNING) }
        }
        model.taskStarted()

        val completedResults = mutableListOf<BookUpdateResult>()
        try {
            for ((index, book) in books.withIndex()) {
                if (!awaitReady()) break

                var result: BookUpdateResult? = null
                for (attempt in 1..(maxRetries + 1)) {
                    if (!awaitReady()) break
                    publishItem(index, BookUpdateItemStatus.RUNNING, attempt)
                    val checked = model.checkBook(book)
                    if (checked.status != BookUpdateStatus.FAILED || attempt > maxRetries) {
                        result = checked
                        break
                    }
                }

                if (result == null) {
                    publishItem(index, BookUpdateItemStatus.CANCELLED, state.value.items[index].attempt)
                    break
                }

                completedResults += result
                publishItem(
                    index = index,
                    status = when (result.status) {
                        BookUpdateStatus.UPDATED -> BookUpdateItemStatus.UPDATED
                        BookUpdateStatus.NO_UPDATE -> BookUpdateItemStatus.NO_UPDATE
                        BookUpdateStatus.FAILED -> BookUpdateItemStatus.FAILED
                    },
                    attempt = state.value.items[index].attempt,
                    result = result
                )
                publish { current ->
                    current.copy(
                        completed = completedResults.size,
                        results = completedResults.toList(),
                        currentBook = current.currentBook
                    )
                }
                if (cancelled) break
            }

            publish { current ->
                current.copy(
                    status = if (cancelled) BookUpdateTaskStatus.CANCELLED else BookUpdateTaskStatus.COMPLETED,
                    currentBook = if (cancelled) current.currentBook else null,
                    currentAttempt = if (cancelled) current.currentAttempt else 0
                )
            }
            return completedResults.toList()
        } catch (throwable: Throwable) {
            publish {
                it.copy(
                    status = BookUpdateTaskStatus.FAILED,
                    error = throwable.message ?: "更新任务失败"
                )
            }
            throw throwable
        } finally {
            model.taskFinished(completedResults)
        }
    }

    private fun awaitReady(): Boolean = control.withLock {
        while (paused && !cancelled) {
            publish { it.copy(status = BookUpdateTaskStatus.PAUSED) }
            try {
                stateChanged.await()
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                cancelled = true
                paused = false
            }
        }
        !cancelled
    }

    private fun publishItem(
        index: Int,
        status: BookUpdateItemStatus,
        attempt: Int,
        result: BookUpdateResult? = null
    ) {
        publish { current ->
            current.copy(
                currentBook = if (status == BookUpdateItemStatus.RUNNING) books[index] else current.currentBook,
                currentAttempt = if (status == BookUpdateItemStatus.RUNNING) attempt else current.currentAttempt,
                items = current.items.toMutableList().also {
                    it[index] = it[index].copy(status = status, attempt = attempt, result = result)
                }
            )
        }
    }

    private fun publish(transform: (BookUpdateTaskState) -> BookUpdateTaskState) {
        control.withLock {
            stateFlow.value = transform(stateFlow.value)
        }
    }
}
