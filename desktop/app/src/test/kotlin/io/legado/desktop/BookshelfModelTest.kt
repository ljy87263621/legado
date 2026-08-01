package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfModelTest {

    @Test
    fun emptyLibraryProducesAnEmptyBookshelfState() {
        val model = BookshelfModel(InMemoryCoreLibrary())

        assertTrue(model.visibleBooks().isEmpty())
        assertTrue(model.isEmpty)
    }

    @Test
    fun visibleBooksAreSortedByMostRecentReadingTime() {
        val library = InMemoryCoreLibrary()
        library.saveBook(
            CoreBook(
                bookUrl = "book-old",
                name = "旧书",
                durChapterTime = 10
            )
        )
        library.saveBook(
            CoreBook(
                bookUrl = "book-new",
                name = "新书",
                durChapterTime = 20
            )
        )
        val model = BookshelfModel(library)

        assertEquals(listOf("新书", "旧书"), model.visibleBooks().map(CoreBook::name))
    }

    @Test
    fun searchMatchesBookNameOrAuthor() {
        val library = InMemoryCoreLibrary()
        library.saveBook(CoreBook("book-1", name = "星河", author = "甲作者"))
        library.saveBook(CoreBook("book-2", name = "山海", author = "乙作者"))
        val model = BookshelfModel(library)

        model.setQuery("乙作者")

        assertEquals(listOf("山海"), model.visibleBooks().map(CoreBook::name))
    }

    @Test
    fun blankSearchRestoresAllBooks() {
        val library = InMemoryCoreLibrary()
        library.saveBook(CoreBook("book-1", name = "星河"))
        library.saveBook(CoreBook("book-2", name = "山海"))
        val model = BookshelfModel(library)

        model.setQuery("星")
        model.setQuery("   ")

        assertEquals(2, model.visibleBooks().size)
        assertEquals("", model.query)
    }
}
