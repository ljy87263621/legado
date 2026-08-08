package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreLibrary
import io.legado.core.source.OnlineBookService
import io.legado.desktop.persistence.DesktopChapterDownloadItemRecord
import io.legado.desktop.persistence.DesktopChapterDownloadStore
import io.legado.desktop.persistence.DesktopChapterDownloadTaskRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class ChapterDownloadTaskStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED
}

enum class ChapterDownloadItemStatus {
    PENDING,
    RUNNING,
    CACHED,
    DOWNLOADED,
    FAILED,
    CANCELLED
}

data class ChapterDownloadItemState(
    val chapter: CoreChapter,
    val status: ChapterDownloadItemStatus = ChapterDownloadItemStatus.PENDING,
    val error: String? = null
)

data class ChapterDownloadTaskState(
    val status: ChapterDownloadTaskStatus = ChapterDownloadTaskStatus.IDLE,
    val total: Int = 0,
    val completed: Int = 0,
    val skipped: Int = 0,
    val downloaded: Int = 0,
    val failed: Int = 0,
    val currentChapter: CoreChapter? = null,
    val items: List<ChapterDownloadItemState> = emptyList(),
    val error: String? = null
)

class ChapterDownloadModel(
    private val library: CoreLibrary,
    private val service: OnlineBookService,
    private val store: DesktopChapterDownloadStore? = null
) {
    private val tasks = ConcurrentHashMap<String, ChapterDownloadTask>()
    private val migrationTasks = ConcurrentHashMap.newKeySet<ChapterDownloadTask>()

    @Volatile
    private var migrationStopped = false

    init {
        store?.recoverInterruptedChapterDownloadTasks()
            ?.forEach(::restoreTask)
    }

    fun createTask(book: CoreBook, chapters: List<CoreChapter>): ChapterDownloadTask {
        check(!migrationStopped) { "数据迁移准备中，暂不能创建章节下载任务" }
        val task = ChapterDownloadTask(
            model = this,
            book = book,
            taskId = UUID.randomUUID().toString(),
            chapters = chapters,
            createdAt = System.currentTimeMillis()
        )
        tasks[task.taskId] = task
        task.persist()
        return task
    }

    fun taskForBook(book: CoreBook): ChapterDownloadTask? {
        tasks.values
            .firstOrNull { it.bookUrl == book.bookUrl && it.isUnfinished }
            ?.let { return it }
        return store
            ?.unfinishedChapterDownloadTasks()
            ?.firstOrNull { it.bookUrl == book.bookUrl }
            ?.let(::restoreTask)
    }

    fun shutdown(timeoutMillis: Long = 35_000L): Boolean {
        val tasksToStop = tasks.values.toList()
        tasksToStop.forEach(ChapterDownloadTask::pauseForShutdown)
        val deadline = System.nanoTime() + timeoutMillis.coerceAtLeast(0L) * 1_000_000L
        var stopped = true
        tasksToStop.forEach { task ->
            val remainingNanos = deadline - System.nanoTime()
            if (remainingNanos > 0L) {
                stopped = task.awaitStopped(remainingNanos) && stopped
            } else {
                stopped = false
            }
        }
        return stopped
    }

    fun stopForMigration(timeoutMillis: Long = 35_000L): Boolean = runCatching {
        migrationStopped = true
        migrationTasks.clear()
        val tasksToStop = tasks.values.filter { it.isWorkerActive }
        migrationTasks.addAll(tasksToStop)
        tasksToStop.forEach(ChapterDownloadTask::pauseForShutdown)
        val deadline = System.nanoTime() + timeoutMillis.coerceAtLeast(0L) * 1_000_000L
        for (task in tasksToStop) {
            val remainingNanos = deadline - System.nanoTime()
            if (remainingNanos <= 0L || !task.awaitStopped(remainingNanos)) {
                return@runCatching false
            }
        }
        return true
    }.getOrElse {
        false
    }

    fun resumeAfterMigration() {
        val tasksToResume = migrationTasks.toList()
        migrationTasks.clear()
        migrationStopped = false
        tasksToResume.forEach(ChapterDownloadTask::resume)
    }

    internal fun isCached(chapter: CoreChapter): Boolean = library.content(chapter) != null

    internal fun download(book: CoreBook, chapter: CoreChapter) {
        service.loadContent(book, chapter)
    }

    private fun restoreTask(record: DesktopChapterDownloadTaskRecord): ChapterDownloadTask {
        tasks[record.taskId]?.let { return it }
        val book = library.book(record.bookUrl) ?: CoreBook(bookUrl = record.bookUrl)
        val task = ChapterDownloadTask(this, book, record)
        val existing = tasks.putIfAbsent(record.taskId, task)
        return existing ?: task
    }

    internal fun save(task: ChapterDownloadTask, state: ChapterDownloadTaskState) {
        store?.saveChapterDownloadTask(
            DesktopChapterDownloadTaskRecord(
                taskId = task.taskId,
                bookUrl = task.bookUrl,
                status = state.status.name,
                total = state.total,
                completed = state.completed,
                skipped = state.skipped,
                downloaded = state.downloaded,
                failed = state.failed,
                items = state.items.map { item ->
                    DesktopChapterDownloadItemRecord(
                        chapter = item.chapter,
                        status = item.status.name,
                        error = item.error
                    )
                },
                error = state.error,
                createdAt = task.createdAt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}

class ChapterDownloadTask internal constructor(
    private val model: ChapterDownloadModel,
    private val book: CoreBook,
    val taskId: String,
    chapters: List<CoreChapter>,
    val createdAt: Long
) {
    private val control = ReentrantLock()
    private val stateChanged = control.newCondition()
    private val chapters = chapters.distinctBy { it.url }
    private val stateFlow = MutableStateFlow(
        ChapterDownloadTaskState(
            total = this.chapters.size,
            items = this.chapters.map(::ChapterDownloadItemState)
        )
    )

    @Volatile
    private var paused = false

    @Volatile
    private var cancelled = false

    private var workerActive = false
    private var stopRequested = false

    val bookUrl: String = book.bookUrl
    val isUnfinished: Boolean
        get() = stateFlow.value.status in UNFINISHED_TASK_STATUSES
    val state: StateFlow<ChapterDownloadTaskState> = stateFlow.asStateFlow()

    internal val isWorkerActive: Boolean
        get() = control.withLock { workerActive }

    internal constructor(
        model: ChapterDownloadModel,
        book: CoreBook,
        record: DesktopChapterDownloadTaskRecord
    ) : this(
        model = model,
        book = book,
        taskId = record.taskId,
        chapters = record.items.map(DesktopChapterDownloadItemRecord::chapter),
        createdAt = record.createdAt
    ) {
        val restoredItems = record.items.map { item ->
            ChapterDownloadItemState(
                chapter = item.chapter,
                status = item.status.toChapterDownloadItemStatus(),
                error = item.error
            )
        }
        control.withLock {
            stateFlow.value = ChapterDownloadTaskState(
                status = record.status.toChapterDownloadTaskStatus(),
                total = restoredItems.size,
                completed = record.completed,
                skipped = record.skipped,
                downloaded = record.downloaded,
                failed = record.failed,
                items = restoredItems,
                error = record.error
            )
            paused = stateFlow.value.status == ChapterDownloadTaskStatus.PAUSED
        }
    }

    fun pause() {
        control.withLock {
            if (stateFlow.value.status == ChapterDownloadTaskStatus.RUNNING) {
                paused = true
                publish { it.copy(status = ChapterDownloadTaskStatus.PAUSED) }
            }
        }
    }

    fun resume() {
        var shouldStartWorker = false
        control.withLock {
            if (stateFlow.value.status == ChapterDownloadTaskStatus.PAUSED && !cancelled) {
                paused = false
                stopRequested = false
                publish { it.copy(status = ChapterDownloadTaskStatus.RUNNING) }
                stateChanged.signalAll()
                shouldStartWorker = !workerActive
            }
        }
        if (shouldStartWorker) {
            startWorker()
        }
    }

    fun cancel() {
        control.withLock {
            if (stateFlow.value.status in setOf(
                    ChapterDownloadTaskStatus.IDLE,
                    ChapterDownloadTaskStatus.RUNNING,
                    ChapterDownloadTaskStatus.PAUSED
                )
            ) {
                cancelled = true
                paused = false
                stopRequested = true
                publish { current ->
                    current.copy(
                        status = ChapterDownloadTaskStatus.CANCELLED,
                        items = current.items.map { item ->
                            if (item.status in setOf(
                                    ChapterDownloadItemStatus.PENDING,
                                    ChapterDownloadItemStatus.RUNNING
                                )
                            ) {
                                item.copy(status = ChapterDownloadItemStatus.CANCELLED)
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

    fun run() {
        control.withLock {
            check(!workerActive) { "章节下载任务正在执行" }
            check(stateFlow.value.status !in TERMINAL_TASK_STATUSES) { "章节下载任务已经结束" }
            workerActive = true
            cancelled = false
            paused = false
            stopRequested = false
            publish { it.copy(status = ChapterDownloadTaskStatus.RUNNING, error = null) }
        }

        try {
            chapters.forEachIndexed { index, chapter ->
                if (!awaitReady()) return@forEachIndexed
                val item = stateFlow.value.items[index]
                if (item.status in SKIP_DOWNLOAD_ITEM_STATUSES) {
                    return@forEachIndexed
                }
                if (model.isCached(chapter)) {
                    publishItem(index, ChapterDownloadItemStatus.CACHED)
                } else {
                    publishItem(index, ChapterDownloadItemStatus.RUNNING)
                    try {
                        model.download(book, chapter)
                        publishItem(index, ChapterDownloadItemStatus.DOWNLOADED)
                    } catch (throwable: Throwable) {
                        publishItem(
                            index,
                            ChapterDownloadItemStatus.FAILED,
                            throwable.message ?: "章节下载失败"
                        )
                    }
                }
                publish { current ->
                    current.copy(
                        completed = current.items.count { it.status in COMPLETED_ITEM_STATUSES },
                        skipped = current.items.count { it.status == ChapterDownloadItemStatus.CACHED },
                        downloaded = current.items.count { it.status == ChapterDownloadItemStatus.DOWNLOADED },
                        failed = current.items.count { it.status == ChapterDownloadItemStatus.FAILED }
                    )
                }
            }
            publish { current ->
                current.copy(
                    status = when {
                        cancelled -> ChapterDownloadTaskStatus.CANCELLED
                        stopRequested -> ChapterDownloadTaskStatus.PAUSED
                        else -> ChapterDownloadTaskStatus.COMPLETED
                    },
                    currentChapter = null
                )
            }
        } catch (throwable: Throwable) {
            publish { current ->
                if (stopRequested) {
                    current.copy(
                        status = ChapterDownloadTaskStatus.PAUSED,
                        currentChapter = null
                    )
                } else {
                    current.copy(
                        status = ChapterDownloadTaskStatus.FAILED,
                        error = throwable.message ?: "章节下载任务失败",
                        currentChapter = null
                    )
                }
            }
            if (!stopRequested) throw throwable
        } finally {
            control.withLock {
                workerActive = false
                stateChanged.signalAll()
            }
        }
    }

    internal fun persist() {
        control.withLock { model.save(this, stateFlow.value) }
    }

    internal fun pauseForShutdown() {
        control.withLock {
            if (stateFlow.value.status in setOf(
                    ChapterDownloadTaskStatus.IDLE,
                    ChapterDownloadTaskStatus.RUNNING,
                    ChapterDownloadTaskStatus.PAUSED
                )
            ) {
                paused = true
                stopRequested = true
                publish { it.copy(status = ChapterDownloadTaskStatus.PAUSED) }
                stateChanged.signalAll()
            }
        }
    }

    internal fun awaitStopped(timeoutNanos: Long): Boolean = control.withLock {
        var remaining = timeoutNanos
        while (workerActive && remaining > 0L) {
            remaining = try {
                stateChanged.awaitNanos(remaining)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                return@withLock false
            }
        }
        !workerActive
    }

    private fun startWorker() {
        Thread(::run, "legado-chapter-download-$taskId").apply {
            isDaemon = true
            start()
        }
    }

    private fun awaitReady(): Boolean = control.withLock {
        while (paused && !cancelled && !stopRequested) {
            publish { it.copy(status = ChapterDownloadTaskStatus.PAUSED) }
            try {
                stateChanged.await()
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                cancelled = true
                paused = false
            }
        }
        !cancelled && !stopRequested
    }

    private fun publishItem(index: Int, status: ChapterDownloadItemStatus, error: String? = null) {
        publish { current ->
            current.copy(
                currentChapter = if (status == ChapterDownloadItemStatus.RUNNING) chapters[index] else current.currentChapter,
                items = current.items.toMutableList().also {
                    it[index] = it[index].copy(status = status, error = error)
                }
            )
        }
    }

    private fun publish(transform: (ChapterDownloadTaskState) -> ChapterDownloadTaskState) {
        control.withLock {
            val next = transform(stateFlow.value)
            stateFlow.value = next
            model.save(this, next)
        }
    }

    private companion object {
        val COMPLETED_ITEM_STATUSES = setOf(
            ChapterDownloadItemStatus.CACHED,
            ChapterDownloadItemStatus.DOWNLOADED,
            ChapterDownloadItemStatus.FAILED
        )
        val SKIP_DOWNLOAD_ITEM_STATUSES = setOf(
            ChapterDownloadItemStatus.CACHED,
            ChapterDownloadItemStatus.DOWNLOADED
        )
        val UNFINISHED_TASK_STATUSES = setOf(
            ChapterDownloadTaskStatus.IDLE,
            ChapterDownloadTaskStatus.RUNNING,
            ChapterDownloadTaskStatus.PAUSED
        )
        val TERMINAL_TASK_STATUSES = setOf(
            ChapterDownloadTaskStatus.COMPLETED,
            ChapterDownloadTaskStatus.CANCELLED
        )
    }
}

private fun String.toChapterDownloadTaskStatus(): ChapterDownloadTaskStatus =
    runCatching { ChapterDownloadTaskStatus.valueOf(this) }
        .getOrDefault(ChapterDownloadTaskStatus.PAUSED)

private fun String.toChapterDownloadItemStatus(): ChapterDownloadItemStatus =
    runCatching { ChapterDownloadItemStatus.valueOf(this) }
        .getOrDefault(ChapterDownloadItemStatus.PENDING)
