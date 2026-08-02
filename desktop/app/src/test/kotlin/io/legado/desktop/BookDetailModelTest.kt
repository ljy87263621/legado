package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookSource
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import io.legado.core.source.OnlineBookService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailModelTest {

    @Test
    fun openingSearchResultLoadsBookInfoAndChapterList() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            bookSourceName = "示例源",
            ruleBookInfo = "{\"name\":\"h1\",\"author\":\".author\"}",
            ruleToc = "{\"chapterList\":\".chapter\",\"chapterName\":\"a\",\"chapterUrl\":\"a@href\"}"
        )
        library.saveSource(source)
        val service = OnlineBookService(library, FakeHttpClient())
        val model = BookDetailModel(library, service)

        model.open(
            CoreBook(
                bookUrl = "https://source.example/book/1",
                tocUrl = "https://source.example/book/1",
                origin = source.bookSourceUrl
            )
        )

        assertEquals("星河", model.book?.name)
        assertEquals("甲作者", model.book?.author)
        assertEquals("第一章", model.chapters.single().title)
        assertTrue(library.book("https://source.example/book/1") != null)
        assertTrue(model.error == null)
    }

    @Test
    fun detailModelExposesLoadFailures() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            ruleBookInfo = "{\"name\":\"h1\"}"
        )
        val book = CoreBook("https://source.example/book/1", origin = source.bookSourceUrl)
        library.saveSource(source)
        val model = BookDetailModel(
            library,
            OnlineBookService(library, object : CoreHttpClient {
                override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                    error("连接失败")
            })
        )

        model.open(book)

        assertEquals("连接失败", model.error)
        assertTrue(model.chapters.isEmpty())
    }

    private class FakeHttpClient : CoreHttpClient {
        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
            when (url) {
                "https://source.example/book/1" -> CoreHttpResponse(
                    url,
                    "<h1>星河</h1><span class='author'>甲作者</span><div class='chapter'><a href='/chapter/1'>第一章</a></div>"
                )
                else -> error("unexpected url: $url")
            }
    }
}
