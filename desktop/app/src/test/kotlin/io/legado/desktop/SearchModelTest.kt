package io.legado.desktop

import io.legado.core.library.CoreBookSource
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import io.legado.core.source.BookSourceSearchService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchModelTest {

    @Test
    fun searchModelStoresResultsAndAddsSelectedBookToBookshelf() {
        val library = InMemoryCoreLibrary()
        library.saveSource(
            CoreBookSource(
                bookSourceUrl = "https://source.example",
                bookSourceName = "示例源",
                searchUrl = "https://source.example/search?q={{key}}",
                ruleSearch = "{\"bookList\":\".book\",\"name\":\"h2\",\"author\":\".author\",\"bookUrl\":\"a@href\"}"
            )
        )
        val service = BookSourceSearchService(library, FakeHttpClient())
        val model = SearchModel(library, service)

        model.setQuery("星河")
        model.search()

        assertEquals(1, model.results.size)
        assertEquals("星河", model.results.single().book.name)
        model.addToBookshelf(model.results.single())
        assertTrue(library.book("https://source.example/book/1") != null)
    }

    @Test
    fun blankSearchDoesNotIssueARequest() {
        val library = InMemoryCoreLibrary()
        val client = FakeHttpClient()
        val model = SearchModel(library, BookSourceSearchService(library, client))

        model.setQuery("   ")
        model.search()

        assertTrue(model.results.isEmpty())
        assertEquals(0, client.requestCount)
    }

    @Test
    fun searchModelExposesFailureWhenAllEnabledSourcesFail() {
        val library = InMemoryCoreLibrary()
        library.saveSource(
            CoreBookSource(
                bookSourceUrl = "https://source.example",
                bookSourceName = "示例源",
                searchUrl = "https://source.example/search?q={{key}}",
                ruleSearch = "{\"bookList\":\".book\",\"name\":\"h2\",\"bookUrl\":\"a@href\"}"
            )
        )
        val client = FailingHttpClient()
        val model = SearchModel(library, BookSourceSearchService(library, client))

        model.setQuery("星河")
        model.search()

        assertEquals("连接失败", model.error)
        assertTrue(model.results.isEmpty())
    }

    private class FakeHttpClient : CoreHttpClient {
        var requestCount = 0

        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
            requestCount++
            return CoreHttpResponse(
                url = "https://source.example/search",
                body = "<div class='book'><h2>星河</h2><span class='author'>甲作者</span><a href='/book/1'>详情</a></div>"
            )
        }
    }

    private class FailingHttpClient : CoreHttpClient {
        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
            error("连接失败")
        }
    }
}
