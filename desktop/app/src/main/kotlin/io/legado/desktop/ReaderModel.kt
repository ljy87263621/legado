package io.legado.desktop

import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreBookmark
import io.legado.core.library.CoreLibrary
import io.legado.core.library.CoreReadRecord
import io.legado.core.source.OnlineBookService

class ReaderModel(
    private val library: CoreLibrary,
    bookUrl: String,
    private val onlineService: OnlineBookService? = null,
    private val clockSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
    startChapterIndex: Int? = null
) {

    private val book = requireNotNull(library.book(bookUrl)) { "Book does not exist: $bookUrl" }
    private val chapters = library.chapters(bookUrl).ifEmpty {
        error("Book has no chapters: $bookUrl")
    }
    private var currentIndex = (startChapterIndex ?: book.durChapterIndex).let { requestedIndex ->
        chapters.indexOfFirst { it.index == requestedIndex }
            .takeIf { it >= 0 }
            ?: requestedIndex.coerceIn(chapters.indices)
    }
    private var position = if (startChapterIndex == null) {
        book.durChapterPos.coerceAtLeast(0)
    } else {
        0
    }
    private val sessionStartSec = clockSeconds()

    var error: String? = null
        private set

    var isLoading: Boolean = false
        private set

    val currentChapter: CoreChapter
        get() = chapters[currentIndex]

    val currentContent: String
        get() = library.content(currentChapter).orEmpty()

    fun loadCurrentContent(): Boolean {
        if (library.content(currentChapter) != null) {
            error = null
            return true
        }
        val service = onlineService
        if (service == null) {
            error = "正文未缓存"
            return false
        }
        isLoading = true
        error = null
        return try {
            service.loadContent(book, currentChapter)
            true
        } catch (throwable: Throwable) {
            error = throwable.message ?: "正文加载失败"
            false
        } finally {
            isLoading = false
        }
    }

    val currentPosition: Int
        get() = position

    val hasPrevious: Boolean
        get() = currentIndex > chapters.indices.first

    val hasNext: Boolean
        get() = currentIndex < chapters.indices.last

    fun previousChapter(): Boolean {
        if (!hasPrevious) return false
        currentIndex--
        position = 0
        return true
    }

    fun nextChapter(): Boolean {
        if (!hasNext) return false
        currentIndex++
        position = 0
        return true
    }

    fun savePosition(position: Int) {
        this.position = position.coerceAtLeast(0)
        val updatedBook = book.copy(
            durChapterIndex = currentIndex,
            durChapterPos = this.position,
            durChapterTitle = currentChapter.title,
            durChapterTime = System.currentTimeMillis()
        )
        library.saveBook(updatedBook)
        val endSec = clockSeconds()
        if (endSec >= sessionStartSec) {
            library.saveReadRecord(
                CoreReadRecord(
                    bookName = book.name,
                    day = CoreReadRecord.dayKey(endSec),
                    startSec = sessionStartSec,
                    endSec = endSec
                )
            )
        }
    }

    fun addBookmark(content: String = currentContent): CoreBookmark {
        val bookmark = CoreBookmark(
            time = clockSeconds() * 1000L,
            bookName = book.name,
            bookAuthor = book.author,
            chapterIndex = currentIndex,
            chapterPos = position,
            chapterName = currentChapter.title,
            bookText = currentContent,
            content = content
        )
        library.saveBookmark(bookmark)
        return bookmark
    }

    fun removeBookmark(time: Long): Boolean {
        val before = library.bookmarks(book.name, book.author).any { it.time == time }
        if (before) library.deleteBookmark(time)
        return before
    }

    fun bookmarks(): List<CoreBookmark> = library.bookmarks(book.name, book.author)

    fun openBookmark(bookmark: CoreBookmark): Boolean {
        val index = chapters.indexOfFirst { it.index == bookmark.chapterIndex }
            .takeIf { it >= 0 }
            ?: bookmark.chapterIndex.takeIf { it in chapters.indices }
            ?: return false
        currentIndex = index
        position = bookmark.chapterPos.coerceAtLeast(0)
        error = null
        return true
    }
}
