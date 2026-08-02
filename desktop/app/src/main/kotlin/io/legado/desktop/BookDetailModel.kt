package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreLibrary
import io.legado.core.source.OnlineBookService

class BookDetailModel(
    private val library: CoreLibrary,
    private val service: OnlineBookService
) {
    var book: CoreBook? = null
        private set

    var chapters: List<CoreChapter> = emptyList()
        private set

    var error: String? = null
        private set

    var isLoading: Boolean = false
        private set

    fun open(book: CoreBook) {
        this.book = book
        chapters = library.chapters(book.bookUrl)
        error = null
        isLoading = true
        try {
            val updatedBook = service.loadBookInfo(book)
            this.book = updatedBook
            chapters = service.refreshChapters(updatedBook)
        } catch (throwable: Throwable) {
            error = throwable.message ?: "书籍详情加载失败"
        } finally {
            isLoading = false
        }
    }

    fun refreshChapters() {
        val currentBook = book ?: return
        error = null
        isLoading = true
        try {
            chapters = service.refreshChapters(currentBook)
        } catch (throwable: Throwable) {
            error = throwable.message ?: "目录加载失败"
        } finally {
            isLoading = false
        }
    }
}
