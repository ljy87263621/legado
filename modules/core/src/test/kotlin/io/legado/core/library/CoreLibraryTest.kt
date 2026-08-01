package io.legado.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CoreLibraryTest {

    private val library = InMemoryCoreLibrary()

    @Test
    fun booksAreIdentifiedByBookUrlAndCanBeUpdated() {
        library.saveBook(
            CoreBook(
                bookUrl = "https://example.com/book/1",
                name = "初始标题",
                author = "作者"
            )
        )
        library.saveBook(
            CoreBook(
                bookUrl = "https://example.com/book/1",
                name = "更新标题",
                author = "作者"
            )
        )

        assertEquals(1, library.books().size)
        assertEquals("更新标题", library.book("https://example.com/book/1")?.name)
    }

    @Test
    fun sourcesAreIdentifiedByBookSourceUrl() {
        library.saveSource(
            CoreBookSource(
                bookSourceUrl = "https://example.com/source",
                bookSourceName = "初始源"
            )
        )
        library.saveSource(
            CoreBookSource(
                bookSourceUrl = "https://example.com/source",
                bookSourceName = "更新源"
            )
        )

        assertEquals(1, library.sources().size)
        assertEquals("更新源", library.source("https://example.com/source")?.bookSourceName)
    }

    @Test
    fun chaptersAreUniquePerBookAndSortedByOriginalIndex() {
        library.saveChapter(
            CoreChapter(
                bookUrl = "book-1",
                url = "chapter-2",
                title = "第二章",
                index = 1
            )
        )
        library.saveChapter(
            CoreChapter(
                bookUrl = "book-1",
                url = "chapter-1",
                title = "第一章",
                index = 0
            )
        )
        library.saveChapter(
            CoreChapter(
                bookUrl = "book-1",
                url = "chapter-2",
                title = "第二章（修订）",
                index = 1
            )
        )

        val chapters = library.chapters("book-1")
        assertEquals(listOf("第一章", "第二章（修订）"), chapters.map { it.title })
        assertEquals(2, chapters.size)
    }

    @Test
    fun deletingBookAlsoRemovesItsChapters() {
        library.saveBook(CoreBook(bookUrl = "book-1", name = "书"))
        library.saveChapter(CoreChapter(bookUrl = "book-1", url = "chapter-1"))

        library.deleteBook("book-1")

        assertNull(library.book("book-1"))
        assertEquals(emptyList<CoreChapter>(), library.chapters("book-1"))
    }

    @Test
    fun missingRecordsReturnNull() {
        assertNull(library.book("missing"))
        assertNull(library.source("missing"))
        assertNotNull(library.chapters("missing"))
    }
}
