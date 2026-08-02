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
    fun browserDependentWebJsIsRejectedBeforeReadingCachedContent() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://cached-web-js.example",
            ruleContent = """
                {
                  "content": ".content",
                  "webJs": "document.body.innerText = '动态正文';"
                }
            """.trimIndent()
        )
        val book = CoreBook(
            bookUrl = "https://cached-web-js.example/book",
            tocUrl = "https://cached-web-js.example/book",
            origin = source.bookSourceUrl
        )
        val chapter = io.legado.core.library.CoreChapter(
            bookUrl = book.bookUrl,
            url = "https://cached-web-js.example/chapter",
            title = "第一章",
            index = 0
        )
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(chapter)
        library.saveContent(chapter, "已有缓存")

        val error = org.junit.Assert.assertThrows(CoreScriptException::class.java) {
            OnlineBookService(library, FakeHttpClient { _, _ -> error("must not fetch") })
                .loadContent(book, chapter)
        }

        assertTrue(error.message.orEmpty().contains("webJs"))
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

    @Test
    fun formatJsCanRewriteChapterTitlesWithIndex() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://script.example",
            ruleToc = """
                {
                  "chapterList": ".chapter",
                  "chapterName": "a",
                  "chapterUrl": "a@href",
                  "formatJs": "@js:chapter.tag = 'formatted'; chapter.isVip = true; chapter.wordCount = '999'; return index + ': ' + title;"
                }
            """.trimIndent()
        )
        val book = CoreBook(
            bookUrl = "https://script.example/book",
            tocUrl = "https://script.example/toc",
            origin = source.bookSourceUrl
        )
        library.saveSource(source)
        library.saveBook(book)
        val client = FakeHttpClient { url, _ ->
            CoreHttpResponse(
                url,
                "<div class='chapter'><a href='/chapter/1'>原始标题</a></div>"
            )
        }

        val chapters = OnlineBookService(library, client).refreshChapters(book)

        assertEquals("1: 原始标题", chapters.single().title)
        assertEquals("formatted", chapters.single().tag)
        assertTrue(chapters.single().isVip)
        assertEquals("999", chapters.single().wordCount)
    }

    @Test
    fun bookInfoInitLimitsExtractionToSelectedHtmlSubtree() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://init.example",
            ruleBookInfo = """
                {
                  "init": ".detail",
                  "name": "h1",
                  "author": ".author"
                }
            """.trimIndent()
        )
        val book = CoreBook(
            bookUrl = "https://init.example/book",
            tocUrl = "https://init.example/book",
            origin = source.bookSourceUrl
        )
        library.saveSource(source)
        library.saveBook(book)

        val updated = OnlineBookService(library, FakeHttpClient { url, _ ->
            CoreHttpResponse(
                url,
                """
                    <h1>页面标题</h1>
                    <div class="detail">
                      <h1>目标书名</h1>
                      <span class="author">目标作者</span>
                    </div>
                """.trimIndent()
            )
        }).loadBookInfo(book)

        assertEquals("目标书名", updated.name)
        assertEquals("目标作者", updated.author)
    }

    @Test
    fun preUpdateJsCanReturnUpdatedTocUrlBeforeRefreshingChapters() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://pre-update.example",
            ruleToc = """
                {
                  "preUpdateJs": "@js:book.tocUrl = 'https://pre-update.example/toc-2'; book.coverUrl = 'https://pre-update.example/cover.jpg'; book.intro = '简介'; book.kind = '奇幻'; book.latestChapterTitle = '最新章'; book.totalChapterNum = 42; book.durChapterIndex = 7; book.wordCount = '999'; book.variable = 'token'; return book;",
                  "chapterList": ".chapter",
                  "chapterName": "a",
                  "chapterUrl": "a@href"
                }
            """.trimIndent()
        )
        val book = CoreBook(
            bookUrl = "https://pre-update.example/book",
            tocUrl = "https://pre-update.example/toc-1",
            origin = source.bookSourceUrl
        )
        library.saveSource(source)
        library.saveBook(book)
        val client = FakeHttpClient { url, _ ->
            assertEquals("https://pre-update.example/toc-2", url)
            CoreHttpResponse(url, "<div class='chapter'><a href='/chapter/2'>第二章</a></div>")
        }

        val chapters = OnlineBookService(library, client).refreshChapters(book)

        assertEquals("第二章", chapters.single().title)
        assertEquals("https://pre-update.example/toc-2", library.book(book.bookUrl)?.tocUrl)
        assertEquals("https://pre-update.example/cover.jpg", library.book(book.bookUrl)?.coverUrl)
        assertEquals("简介", library.book(book.bookUrl)?.intro)
        assertEquals("奇幻", library.book(book.bookUrl)?.kind)
        assertEquals("最新章", library.book(book.bookUrl)?.latestChapterTitle)
        assertEquals(42, library.book(book.bookUrl)?.totalChapterNum)
        assertEquals(7, library.book(book.bookUrl)?.durChapterIndex)
        assertEquals("999", library.book(book.bookUrl)?.wordCount)
        assertEquals("token", library.book(book.bookUrl)?.variable)
    }

    @Test
    fun preUpdateJsCanReturnANewPartialBookObject() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://returned-book.example",
            ruleToc = """
                {
                  "preUpdateJs": "return {tocUrl: 'https://returned-book.example/toc-2', coverUrl: 'https://returned-book.example/cover.jpg'};",
                  "chapterList": ".chapter",
                  "chapterName": "a",
                  "chapterUrl": "a@href"
                }
            """.trimIndent()
        )
        val book = CoreBook(
            bookUrl = "https://returned-book.example/book",
            tocUrl = "https://returned-book.example/toc-1",
            origin = source.bookSourceUrl
        )
        library.saveSource(source)
        library.saveBook(book)
        val client = FakeHttpClient { url, _ ->
            assertEquals("https://returned-book.example/toc-2", url)
            CoreHttpResponse(url, "<div class='chapter'><a href='/chapter/2'>第二章</a></div>")
        }

        OnlineBookService(library, client).refreshChapters(book)

        assertEquals("https://returned-book.example/toc-2", library.book(book.bookUrl)?.tocUrl)
        assertEquals("https://returned-book.example/cover.jpg", library.book(book.bookUrl)?.coverUrl)
    }

    @Test
    fun contentTitleJsCanUpdateChapterTitleBeforeSavingContent() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://content-script.example",
            ruleContent = """
                {
                  "title": "@js:return title + '（已校正）';",
                  "content": ".content"
                }
            """.trimIndent()
        )
        val book = CoreBook(
            bookUrl = "https://content-script.example/book",
            tocUrl = "https://content-script.example/book",
            origin = source.bookSourceUrl
        )
        val chapter = io.legado.core.library.CoreChapter(
            bookUrl = book.bookUrl,
            url = "https://content-script.example/chapter",
            title = "原始标题",
            index = 0
        )
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(chapter)
        val client = FakeHttpClient { url, _ ->
            CoreHttpResponse(url, "<div class='content'>正文</div>")
        }

        val content = OnlineBookService(library, client).loadContent(book, chapter)

        assertEquals("正文", content)
        assertEquals("原始标题（已校正）", library.chapters(book.bookUrl).single().title)
    }

    @Test
    fun browserDependentWebJsFailsWithExplicitCoreRuntimeError() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://web-js.example",
            ruleContent = """
                {
                  "content": ".content",
                  "webJs": "document.querySelector('.content').innerText = '动态正文';"
                }
            """.trimIndent()
        )
        val book = CoreBook(
            bookUrl = "https://web-js.example/book",
            tocUrl = "https://web-js.example/book",
            origin = source.bookSourceUrl
        )
        val chapter = io.legado.core.library.CoreChapter(
            bookUrl = book.bookUrl,
            url = "https://web-js.example/chapter",
            title = "第一章",
            index = 0
        )
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(chapter)

        val error = org.junit.Assert.assertThrows(CoreScriptException::class.java) {
            OnlineBookService(library, FakeHttpClient { url, _ ->
                CoreHttpResponse(url, "<div class='content'>静态正文</div>")
            }).loadContent(book, chapter)
        }

        assertTrue(error.message.orEmpty().contains("webJs"))
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
