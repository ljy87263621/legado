package io.legado.core.source

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookSource
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineBookServiceTest {

    @Test
    fun bookInfoAndTocRulesUpdateBookAndResolveRelativeUrls() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            bookSourceName = "示例源",
            ruleBookInfo = """
                {
                  "name": "h1",
                  "author": ".author",
                  "intro": ".intro",
                  "coverUrl": "img@src",
                  "tocUrl": "a.toc@href"
                }
            """.trimIndent(),
            ruleToc = """
                {
                  "chapterList": ".chapter",
                  "chapterName": "a",
                  "chapterUrl": "a@href"
                }
            """.trimIndent(),
            ruleContent = "{\"content\":\".content@text\"}"
        )
        val book = CoreBook(
            bookUrl = "https://source.example/book/1",
            tocUrl = "https://source.example/book/1",
            origin = source.bookSourceUrl,
            originName = source.bookSourceName
        )
        library.saveSource(source)
        library.saveBook(book)
        val client = FakeHttpClient { url, _ ->
            when (url) {
                "https://source.example/book/1" -> CoreHttpResponse(
                    url,
                    """
                        <h1>星河</h1><span class="author">甲作者</span>
                        <p class="intro">一段简介</p><img src="/cover.jpg">
                        <a class="toc" href="/book/1/toc">目录</a>
                    """.trimIndent()
                )
                "https://source.example/book/1/toc" -> CoreHttpResponse(
                    url,
                    "<div class='chapter'><a href='/chapter/1'>第一章</a></div>"
                )
                else -> error("unexpected url: $url")
            }
        }
        val service = OnlineBookService(library, client)

        val updatedBook = service.loadBookInfo(book)
        val chapters = service.refreshChapters(updatedBook)

        assertEquals("星河", updatedBook.name)
        assertEquals("甲作者", updatedBook.author)
        assertEquals("https://source.example/cover.jpg", updatedBook.coverUrl)
        assertEquals("https://source.example/book/1/toc", updatedBook.tocUrl)
        assertEquals(1, chapters.size)
        assertEquals("第一章", chapters.single().title)
        assertEquals("https://source.example/chapter/1", chapters.single().url)
        assertEquals(1, library.book(book.bookUrl)?.totalChapterNum)
    }

    @Test
    fun jsonTocAndContentRulesSupportNextPages() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://json.example",
            bookSourceName = "JSON源",
            ruleToc = """
                {
                  "chapterList": "$.chapters[*]",
                  "chapterName": "$.name",
                  "chapterUrl": "$.url",
                  "nextTocUrl": "$.next"
                }
            """.trimIndent(),
            ruleContent = """
                {
                  "content": "$.content",
                  "nextContentUrl": "$.next"
                }
            """.trimIndent()
        )
        val book = CoreBook(
            bookUrl = "https://json.example/book/1",
            tocUrl = "https://json.example/toc/1",
            origin = source.bookSourceUrl
        )
        library.saveSource(source)
        library.saveBook(book)
        val client = FakeHttpClient { url, _ ->
            when (url) {
                "https://json.example/toc/1" -> CoreHttpResponse(
                    url,
                    """{"chapters":[{"name":"第一章","url":"/chapter/1"}],"next":"/toc/2"}"""
                )
                "https://json.example/toc/2" -> CoreHttpResponse(
                    url,
                    """{"chapters":[{"name":"第二章","url":"/chapter/2"}],"next":""}"""
                )
                "https://json.example/chapter/1" -> CoreHttpResponse(
                    url,
                    """{"content":"第一页","next":"/chapter/1/2"}"""
                )
                "https://json.example/chapter/1/2" -> CoreHttpResponse(
                    url,
                    """{"content":"第二页","next":""}"""
                )
                else -> error("unexpected url: $url")
            }
        }
        val service = OnlineBookService(library, client)

        val chapters = service.refreshChapters(book)
        val content = service.loadContent(book, chapters.first())

        assertEquals(listOf("第一章", "第二章"), chapters.map { it.title })
        assertEquals("第一页\n第二页", content)
        assertEquals(content, library.content(chapters.first()))
    }

    @Test
    fun cachedContentDoesNotFetchChapterAgain() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://cache.example",
            ruleContent = "{\"content\":\".content\"}"
        )
        val book = CoreBook(
            bookUrl = "https://cache.example/book",
            tocUrl = "https://cache.example/book",
            origin = source.bookSourceUrl
        )
        val chapter = io.legado.core.library.CoreChapter(
            bookUrl = book.bookUrl,
            url = "https://cache.example/chapter",
            title = "第一章",
            index = 0
        )
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(chapter)
        library.saveContent(chapter, "已有缓存")
        val client = FakeHttpClient { _, _ -> error("cached content must not fetch") }
        val service = OnlineBookService(library, client)

        assertEquals("已有缓存", service.loadContent(book, chapter))
        assertTrue(client.requestedUrls.isEmpty())
    }

    @Test
    fun regexBookInfoAndContentRulesAreSupported() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://regex.example",
            ruleBookInfo = """
                {
                  "name": "@regex:(?<name>[^|]+)\\|(?<author>[^|]+)",
                  "author": "group:author"
                }
            """.trimIndent(),
            ruleContent = "{\"content\":\"@regex:(?<content>正文[^<]+)\"}"
        )
        val book = CoreBook(
            bookUrl = "https://regex.example/book",
            tocUrl = "https://regex.example/book",
            origin = source.bookSourceUrl
        )
        val chapter = io.legado.core.library.CoreChapter(
            bookUrl = book.bookUrl,
            url = "https://regex.example/chapter",
            title = "第一章",
            index = 0
        )
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(chapter)
        val client = FakeHttpClient { _, _ ->
            CoreHttpResponse(
                "https://regex.example/chapter",
                "正文内容<end>"
            )
        }
        val service = OnlineBookService(library, client)

        val info = service.loadBookInfo(book, "星河|甲作者")
        val content = service.loadContent(info, chapter)

        assertEquals("星河", info.name)
        assertEquals("甲作者", info.author)
        assertEquals("正文内容", content)
    }

    private class FakeHttpClient(
        private val handler: (String, Map<String, String>) -> CoreHttpResponse
    ) : CoreHttpClient {
        val requestedUrls = mutableListOf<String>()

        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
            requestedUrls += url
            return handler(url, headers)
        }
    }
}
