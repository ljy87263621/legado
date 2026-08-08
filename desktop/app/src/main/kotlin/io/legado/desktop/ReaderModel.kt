package io.legado.desktop

import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreBookmark
import io.legado.core.library.CoreLibrary
import io.legado.core.library.CoreReadRecord
import io.legado.core.library.CoreReplacementService
import io.legado.core.source.OnlineBookService
import io.legado.core.source.CoreSourceSessionException
import io.legado.core.source.CoreSourceSessionStatus
import java.nio.file.Files
import java.nio.file.Path

data class ReaderPage(
    val text: String,
    val startPosition: Int,
    val endPosition: Int
)

enum class ReaderAutoReadResult {
    PAGE_ADVANCED,
    CHAPTER_ADVANCED,
    END
}

enum class ReaderKeyboardKey {
    LEFT,
    RIGHT,
    UP,
    DOWN,
    SPACE,
    S
}

enum class ReaderKeyboardCommand {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    PREVIOUS_CHAPTER,
    NEXT_CHAPTER,
    SAVE_POSITION;

    companion object {
        fun from(key: ReaderKeyboardKey, ctrlPressed: Boolean = false): ReaderKeyboardCommand? = when {
            ctrlPressed && key == ReaderKeyboardKey.LEFT -> PREVIOUS_CHAPTER
            ctrlPressed && key == ReaderKeyboardKey.RIGHT -> NEXT_CHAPTER
            key == ReaderKeyboardKey.LEFT || key == ReaderKeyboardKey.UP -> PREVIOUS_PAGE
            key == ReaderKeyboardKey.RIGHT || key == ReaderKeyboardKey.DOWN || key == ReaderKeyboardKey.SPACE -> NEXT_PAGE
            key == ReaderKeyboardKey.S -> SAVE_POSITION
            else -> null
        }
    }
}

class ReaderModel(
    private val library: CoreLibrary,
    bookUrl: String,
    private val onlineService: OnlineBookService? = null,
    private val clockSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
    startChapterIndex: Int? = null,
    startPosition: Int? = null
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
    private var position = when {
        startPosition != null -> startPosition.coerceAtLeast(0)
        startChapterIndex == null -> book.durChapterPos.coerceAtLeast(0)
        else -> 0
    }
    private val sessionStartSec = clockSeconds()
    private val replacementService = CoreReplacementService(library)

    var error: String? = null
        private set

    var sessionStatus: CoreSourceSessionStatus? = null
        private set

    var loginUrl: String? = null
        private set

    var loginSourceUrl: String? = null
        private set

    var isLoading: Boolean = false
        private set

    val currentChapter: CoreChapter
        get() = chapters[currentIndex]

    val currentChapterTitle: String
        get() = replacementService.processTitle(book, currentChapter.title).text

    val currentContent: String
        get() = replacementService.processContent(book, library.content(currentChapter).orEmpty()).text

    fun loadCurrentContent(): Boolean = loadContent(currentChapter)

    /** Loads the supplied chapter without consulting the mutable reader cursor again. */
    fun loadContent(chapter: CoreChapter): Boolean {
        if (library.content(chapter) != null) {
            error = null
            return true
        }
        val localTxtPath = localTxtPath()
        if (localTxtPath != null) {
            isLoading = true
            error = null
            return try {
                val content = LocalBookParser.readTextChapter(
                    localTxtPath,
                    chapter,
                    book.charset
                )
                library.saveContent(chapter, content)
                true
            } catch (throwable: Throwable) {
                error = throwable.message ?: "本地正文加载失败"
                false
            } finally {
                isLoading = false
            }
        }
        val service = onlineService
        if (service == null) {
            error = "正文未缓存"
            return false
        }
        isLoading = true
        error = null
        sessionStatus = null
        loginUrl = null
        loginSourceUrl = null
        return try {
            service.loadContent(book, chapter)
            true
        } catch (throwable: Throwable) {
            error = throwable.message ?: "正文加载失败"
            if (throwable is CoreSourceSessionException) {
                sessionStatus = throwable.status
                loginUrl = throwable.loginUrl
                loginSourceUrl = throwable.sourceUrl
            }
            false
        } finally {
            isLoading = false
        }
    }

    private fun localTxtPath(): Path? {
        if (book.origin != "loc_book" || !book.bookUrl.endsWith(".txt", ignoreCase = true)) {
            return null
        }
        return runCatching { Path.of(book.bookUrl) }
            .getOrNull()
            ?.toAbsolutePath()
            ?.normalize()
            ?.takeIf(Files::isRegularFile)
    }

    val currentPosition: Int
        get() = position

    fun pages(pageSize: Int): List<ReaderPage> {
        val safePageSize = pageSize.coerceAtLeast(1)
        if (currentContent.isEmpty()) return listOf(ReaderPage("", 0, 0))
        return currentContent.chunked(safePageSize).mapIndexed { index, text ->
            val start = index * safePageSize
            ReaderPage(text, start, start + text.length)
        }
    }

    fun currentPageIndex(pageSize: Int): Int {
        val availablePages = pages(pageSize)
        return (position / pageSize.coerceAtLeast(1)).coerceIn(0, availablePages.lastIndex)
    }

    fun nextPage(pageSize: Int): Boolean {
        val availablePages = pages(pageSize)
        val pageIndex = currentPageIndex(pageSize)
        if (pageIndex >= availablePages.lastIndex) return false
        position = availablePages[pageIndex + 1].startPosition
        return true
    }

    fun previousPage(pageSize: Int): Boolean {
        val availablePages = pages(pageSize)
        val pageIndex = currentPageIndex(pageSize)
        if (pageIndex <= 0) return false
        position = availablePages[pageIndex - 1].startPosition
        return true
    }

    fun autoReadTick(pageSize: Int): ReaderAutoReadResult {
        if (nextPage(pageSize)) return ReaderAutoReadResult.PAGE_ADVANCED
        if (nextChapter()) return ReaderAutoReadResult.CHAPTER_ADVANCED
        return ReaderAutoReadResult.END
    }

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
