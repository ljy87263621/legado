package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreLibrary
import io.legado.core.source.BookSourceSearchService
import io.legado.core.source.CoreSearchResult
import io.legado.core.source.OnlineBookService
import io.legado.core.source.CoreSourceSessionException
import io.legado.core.source.CoreSourceSessionStatus

data class BookSourceCandidate(
    val book: CoreBook,
    val source: io.legado.core.library.CoreBookSource
)

class BookDetailModel(
    private val library: CoreLibrary,
    private val service: OnlineBookService,
    private val searchService: BookSourceSearchService? = null
) {
    var book: CoreBook? = null
        private set

    var chapters: List<CoreChapter> = emptyList()
        private set

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

    var sourceCandidates: List<BookSourceCandidate> = emptyList()
        private set

    var sourceError: String? = null
        private set

    fun open(book: CoreBook) {
        this.book = book
        chapters = library.chapters(book.bookUrl)
        sourceCandidates = emptyList()
        sourceError = null
        error = null
        sessionStatus = null
        loginUrl = null
        loginSourceUrl = null
        isLoading = true
        try {
            val updatedBook = service.loadBookInfo(book)
            this.book = updatedBook
            chapters = service.refreshChapters(updatedBook)
            this.book = library.book(updatedBook.bookUrl) ?: updatedBook
        } catch (throwable: Throwable) {
            error = throwable.message ?: "书籍详情加载失败"
            captureSessionFailure(throwable)
        } finally {
            isLoading = false
        }
    }

    fun refreshChapters() {
        val currentBook = book ?: return
        error = null
        sessionStatus = null
        loginUrl = null
        loginSourceUrl = null
        isLoading = true
        try {
            chapters = service.refreshChapters(currentBook)
            book = library.book(currentBook.bookUrl) ?: currentBook
        } catch (throwable: Throwable) {
            error = throwable.message ?: "目录加载失败"
            captureSessionFailure(throwable)
        } finally {
            isLoading = false
        }
    }

    fun loadSourceCandidates(): List<BookSourceCandidate> {
        val currentBook = book ?: return emptyList()
        val sourceSearch = searchService
        if (sourceSearch == null) {
            sourceError = "当前桌面实例未配置书源搜索"
            sourceCandidates = emptyList()
            return emptyList()
        }
        sourceError = null
        return try {
            val searchedCandidates = sourceSearch.search(currentBook.name)
                .asSequence()
                .filter { result -> matches(currentBook, result) }
                .map { result ->
                    BookSourceCandidate(
                        book = result.book.copy(group = currentBook.group),
                        source = result.source
                    )
                }
                .toList()
            val currentCandidate = library.source(currentBook.origin)?.let { source ->
                BookSourceCandidate(book = currentBook, source = source)
            }
            val candidates = (searchedCandidates + listOfNotNull(currentCandidate))
                .distinctBy { it.source.bookSourceUrl }
                .sortedWith(
                    compareBy<BookSourceCandidate> { it.source.bookSourceUrl != currentBook.origin }
                        .thenBy { it.source.customOrder }
                        .thenBy { it.source.bookSourceName }
                )
                .toList()
            sourceCandidates = candidates
            candidates
        } catch (throwable: Throwable) {
            sourceCandidates = emptyList()
            sourceError = throwable.message ?: "书源候选加载失败"
            emptyList()
        }
    }

    fun switchSource(candidate: BookSourceCandidate): CoreBook? {
        val currentBook = book ?: return null
        if (candidate.source.bookSourceUrl == currentBook.origin && candidate.book.bookUrl == currentBook.bookUrl) {
            return currentBook
        }
        val replacement = candidate.book.copy(
            group = currentBook.group,
            customTag = currentBook.customTag,
            customCoverUrl = currentBook.customCoverUrl,
            customIntro = currentBook.customIntro
        )
        val replacementAlreadyStored = library.book(replacement.bookUrl) != null
        sourceError = null
        return try {
            val loaded = service.loadBookInfo(replacement)
            val refreshedChapters = service.refreshChapters(loaded)
            if (refreshedChapters.isEmpty()) error("切换书源后目录为空")
            if (currentBook.bookUrl != loaded.bookUrl) {
                library.deleteBook(currentBook.bookUrl)
            }
            book = library.book(loaded.bookUrl) ?: loaded
            chapters = refreshedChapters
            sourceCandidates = sourceCandidates.map { existing ->
                if (existing.source.bookSourceUrl == candidate.source.bookSourceUrl) {
                    candidate.copy(book = book!!)
                } else {
                    existing
                }
            }
            book
        } catch (throwable: Throwable) {
            if (!replacementAlreadyStored && replacement.bookUrl != currentBook.bookUrl) {
                library.deleteBook(replacement.bookUrl)
            }
            sourceError = throwable.message ?: "切换书源失败"
            captureSessionFailure(throwable)
            null
        }
    }

    private fun matches(currentBook: CoreBook, result: CoreSearchResult): Boolean {
        if (result.source.bookSourceUrl == currentBook.origin && result.book.bookUrl == currentBook.bookUrl) {
            return true
        }
        return normalize(currentBook.name) == normalize(result.book.name) &&
            normalize(currentBook.author) == normalize(result.book.author)
    }

    private fun normalize(value: String): String = value
        .trim()
        .replace(Regex("\\s+"), "")
        .lowercase()

    private fun captureSessionFailure(throwable: Throwable) {
        if (throwable is CoreSourceSessionException) {
            sessionStatus = throwable.status
            loginUrl = throwable.loginUrl
            loginSourceUrl = throwable.sourceUrl
        }
    }
}
