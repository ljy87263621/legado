package io.legado.desktop

import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreLibrary

class ReaderModel(
    private val library: CoreLibrary,
    bookUrl: String
) {

    private val book = requireNotNull(library.book(bookUrl)) { "Book does not exist: $bookUrl" }
    private val chapters = library.chapters(bookUrl).ifEmpty {
        error("Book has no chapters: $bookUrl")
    }
    private var currentIndex = book.durChapterIndex.coerceIn(chapters.indices)
    private var position = book.durChapterPos.coerceAtLeast(0)

    val currentChapter: CoreChapter
        get() = chapters[currentIndex]

    val currentContent: String
        get() = library.content(currentChapter).orEmpty()

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
    }
}
