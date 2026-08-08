package io.legado.core.source

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreChapter
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreSourceSessionTest {
    @Test
    fun searchReportsLoginRequiredWithTheDetectedLoginUrl() {
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            enabled = true,
            searchUrl = "https://source.example/search?key={{key}}",
            ruleSearch = "{\"bookList\":\".book\",\"name\":\".name\"}"
        )
        val library = InMemoryCoreLibrary().apply { saveSource(source) }
        val client = FixedSessionResponseClient(
            CoreHttpResponse(
                url = "https://source.example/signin?next=%2Fsearch",
                body = "",
                statusCode = 302
            )
        )

        val error = runCatching { BookSourceSearchService(library, client).search("keyword") }.exceptionOrNull()

        assertTrue(error?.let { "${it::class.java.name}: ${it.message}" } ?: "no error", error is CoreSourceSessionException)
        assertEquals(CoreSourceSessionStatus.LOGIN_REQUIRED, (error as CoreSourceSessionException).status)
        assertEquals("https://source.example/signin?next=%2Fsearch", error.loginUrl)
    }

    @Test
    fun onlineBookServiceReportsLoginRequiredForContentRequests() {
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            ruleContent = "{\"content\":\".content\"}"
        )
        val book = CoreBook(bookUrl = "https://source.example/book", origin = source.bookSourceUrl, name = "Book")
        val chapter = CoreChapter(book.bookUrl, "https://source.example/chapter", "Chapter", 0)
        val library = InMemoryCoreLibrary().apply {
            saveSource(source)
            saveBook(book)
            saveChapter(chapter)
        }
        val client = FixedSessionResponseClient(
            CoreHttpResponse(
                url = "https://source.example/login",
                body = "<form><input type=\"password\"></form>",
                statusCode = 200
            )
        )

        val error = runCatching { OnlineBookService(library, client).loadContent(book, chapter) }.exceptionOrNull()

        assertTrue(error is CoreSourceSessionException)
        assertEquals(CoreSourceSessionStatus.LOGIN_REQUIRED, (error as CoreSourceSessionException).status)
        assertEquals("https://source.example/login", error.loginUrl)
    }

    private class FixedSessionResponseClient(private val response: CoreHttpResponse) : CoreHttpClient {
        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse = response
    }
}
