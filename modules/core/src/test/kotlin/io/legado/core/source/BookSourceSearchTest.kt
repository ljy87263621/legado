package io.legado.core.source

import io.legado.core.library.CoreBookSource
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourceSearchTest {

    @Test
    fun sourceJsonRoundTripsAsObjectAndArray() {
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            bookSourceName = "示例源",
            enabled = false,
            searchUrl = "https://source.example/search?q={{key}}&page={{page}}",
            ruleSearch = "{\"bookList\":\".book\",\"name\":\"h2\"}"
        )

        val objectJson = BookSourceJsonCodec.encode(source)
        val arrayJson = BookSourceJsonCodec.encode(listOf(source))

        assertEquals(listOf(source), BookSourceJsonCodec.decode(objectJson))
        assertEquals(listOf(source), BookSourceJsonCodec.decode(arrayJson))
    }

    @Test
    fun searchUrlReplacesKeyAndPageVariables() {
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            searchUrl = "https://source.example/search/{{key}}/{{page}}"
        )

        assertEquals(
            "https://source.example/search/星河/3",
            SourceUrlTemplate.expand(source.searchUrl!!, keyword = "星河", page = 3)
        )
    }

    @Test
    fun htmlSearchParsesBooksAndDeduplicatesByBookUrl() {
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            bookSourceName = "HTML源",
            searchUrl = "https://source.example/search?q={{key}}",
            ruleSearch = """
                {
                  "bookList": ".book",
                  "name": "h2",
                  "author": ".author",
                  "bookUrl": "a@href",
                  "coverUrl": "img@src",
                  "intro": ".intro"
                }
            """.trimIndent()
        )
        val html = """
            <div class="book"><h2>星河</h2><span class="author">甲作者</span><a href="/book/1">详情</a><img src="/cover/1.jpg"><p class="intro">简介</p></div>
            <div class="book"><h2>星河（重复）</h2><span class="author">甲作者</span><a href="/book/1">详情</a></div>
        """.trimIndent()
        val client = FakeHttpClient { _, _ -> CoreHttpResponse("https://source.example/result", html) }
        val service = BookSourceSearchService(InMemoryCoreLibrary().also { it.saveSource(source) }, client)

        val results = service.search("星河")

        assertEquals(1, results.size)
        assertEquals("星河", results.single().book.name)
        assertEquals("甲作者", results.single().book.author)
        assertEquals("https://source.example/book/1", results.single().book.bookUrl)
        assertEquals("https://source.example/cover/1.jpg", results.single().book.coverUrl)
        assertEquals("HTML源", results.single().source.bookSourceName)
        assertEquals("https://source.example/search?q=%E6%98%9F%E6%B2%B3", client.lastUrl)
    }

    @Test
    fun jsonSearchParsesJsonPathBookList() {
        val source = CoreBookSource(
            bookSourceUrl = "https://json.example",
            bookSourceName = "JSON源",
            searchUrl = "https://json.example/search?key={{key}}",
            ruleSearch = """
                {
                  "bookList": "$.books[*]",
                  "name": "$.name",
                  "author": "$.author",
                  "bookUrl": "$.url"
                }
            """.trimIndent()
        )
        val body = """{"books":[{"name":"山海","author":"乙作者","url":"/book/2"}]}"""
        val client = FakeHttpClient { _, _ -> CoreHttpResponse("https://json.example/search", body) }
        val service = BookSourceSearchService(InMemoryCoreLibrary().also { it.saveSource(source) }, client)

        val result = service.search("山海").single().book

        assertEquals("山海", result.name)
        assertEquals("乙作者", result.author)
        assertEquals("https://json.example/book/2", result.bookUrl)
    }

    @Test
    fun regexSearchUsesNamedCaptureGroups() {
        val source = CoreBookSource(
            bookSourceUrl = "https://regex.example",
            searchUrl = "https://regex.example/search/{{key}}",
            ruleSearch = """
                {
                  "bookList": "@regex:(?<name>[^|]+)\\|(?<author>[^|]+)\\|(?<url>\\S+)",
                  "name": "group:name",
                  "author": "group:author",
                  "bookUrl": "group:url"
                }
            """.trimIndent()
        )
        val client = FakeHttpClient { _, _ -> CoreHttpResponse("https://regex.example", "星河|甲作者|/book/3") }
        val service = BookSourceSearchService(InMemoryCoreLibrary().also { it.saveSource(source) }, client)

        val result = service.search("星河").single().book

        assertEquals("星河", result.name)
        assertEquals("甲作者", result.author)
        assertEquals("https://regex.example/book/3", result.bookUrl)
    }

    @Test
    fun searchResultsCanBeAddedToTheBookshelf() {
        val library = InMemoryCoreLibrary()
        library.saveSource(
            CoreBookSource(
                bookSourceUrl = "https://source.example",
                searchUrl = "https://source.example/search/{{key}}",
                ruleSearch = "{\"bookList\":\".book\",\"name\":\"h2\",\"bookUrl\":\"a@href\"}"
            )
        )
        val client = FakeHttpClient { _, _ ->
            CoreHttpResponse("https://source.example", "<div class='book'><h2>星河</h2><a href='/book/1'>详情</a></div>")
        }
        val service = BookSourceSearchService(library, client)

        service.searchAndAddToBookshelf("星河")

        assertTrue(library.book("https://source.example/book/1") != null)
        assertEquals("星河", library.books().single().name)
    }

    private class FakeHttpClient(
        private val handler: (String, Map<String, String>) -> CoreHttpResponse
    ) : CoreHttpClient {
        var lastUrl: String? = null

        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
            lastUrl = url
            return handler(url, headers)
        }
    }
}
