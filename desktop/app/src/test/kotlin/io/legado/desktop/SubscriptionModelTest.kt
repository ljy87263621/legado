package io.legado.desktop

import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreBookSourceType
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.core.source.BookSourceSearchService
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import io.legado.core.source.OnlineBookService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionModelTest {

    @Test
    fun refreshLoadsRssArticlesAndOpeningArticleCreatesReadableChapter() {
        val source = CoreBookSource(
            bookSourceUrl = "https://feed.example",
            bookSourceName = "示例订阅",
            bookSourceType = CoreBookSourceType.RSS,
            exploreUrl = "https://feed.example/feed",
            ruleExplore = """
                {
                  "bookList": ".article",
                  "name": "h2",
                  "author": ".date",
                  "intro": ".summary",
                  "bookUrl": "a@href"
                }
            """.trimIndent(),
            ruleContent = "{\"content\":\".content\"}"
        )
        val library = InMemoryCoreLibrary().also { it.saveSource(source) }
        val service = BookSourceSearchService(library, object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>) = CoreHttpResponse(
                url,
                """
                    <article class="article">
                      <h2>今日要闻</h2>
                      <span class="date">2026-08-02</span>
                      <p class="summary">摘要内容</p>
                      <a href="/article/1">阅读</a>
                    </article>
                """.trimIndent()
            )
        })
        val model = SubscriptionModel(library, service)

        model.refreshSources()
        model.refresh()
        val article = model.articles.single()
        val book = model.openArticle(article)

        assertEquals("今日要闻", book.name)
        assertEquals("https://feed.example/article/1", book.bookUrl)
        assertEquals(CoreBookSourceType.RSS, book.type)
        assertNotNull(library.book(book.bookUrl))
        assertEquals(listOf(book.bookUrl), library.chapters(book.bookUrl).map { it.url })
    }

    @Test
    fun openingArticleWithoutContentRuleUsesItsSummaryAsCachedContent() {
        val source = CoreBookSource(
            bookSourceUrl = "https://feed.example",
            bookSourceName = "示例订阅",
            bookSourceType = CoreBookSourceType.RSS,
            exploreUrl = "https://feed.example/feed",
            ruleExplore = "{\"bookList\":\".article\",\"name\":\"h2\",\"intro\":\".summary\",\"bookUrl\":\"a@href\"}"
        )
        val library = InMemoryCoreLibrary().also { it.saveSource(source) }
        val service = BookSourceSearchService(library, object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>) =
                CoreHttpResponse(url, "<article class='article'><h2>文章</h2><p class='summary'>摘要</p><a href='/1'>打开</a></article>")
        })
        val model = SubscriptionModel(library, service)

        model.refreshSources()
        model.refresh()
        val article = model.articles.single()
        val book = model.openArticle(article)
        val chapter = library.chapters(book.bookUrl).single()

        assertEquals("摘要", library.content(chapter))
    }

    @Test
    fun openingArticleLoadsRuleContentThroughTheReaderAndCachesIt() {
        val source = CoreBookSource(
            bookSourceUrl = "https://feed.example",
            bookSourceName = "示例订阅",
            bookSourceType = CoreBookSourceType.RSS,
            exploreUrl = "https://feed.example/feed",
            ruleExplore = "{\"bookList\":\".article\",\"name\":\"h2\",\"bookUrl\":\"a@href\"}",
            ruleContent = "{\"content\":\".content\"}"
        )
        val library = InMemoryCoreLibrary().also { it.saveSource(source) }
        val requestedUrls = mutableListOf<String>()
        val client = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
                requestedUrls += url
                return when (url) {
                    "https://feed.example/feed" -> CoreHttpResponse(
                        url,
                        "<article class='article'><h2>文章</h2><a href='/article/1'>打开</a></article>"
                    )
                    "https://feed.example/article/1" -> CoreHttpResponse(
                        url,
                        "<article><div class='content'>完整正文</div></article>"
                    )
                    else -> error("unexpected url: $url")
                }
            }
        }
        val model = SubscriptionModel(library, BookSourceSearchService(library, client))

        model.refreshSources()
        model.refresh()
        val book = model.openArticle(model.articles.single())
        val reader = ReaderModel(library, book.bookUrl, OnlineBookService(library, client))

        assertTrue(reader.loadCurrentContent())
        assertEquals("完整正文", reader.currentContent)
        assertEquals(listOf("https://feed.example/feed", "https://feed.example/article/1"), requestedUrls)

        val cachedReader = ReaderModel(library, book.bookUrl, OnlineBookService(library, client))
        assertTrue(cachedReader.loadCurrentContent())
        assertEquals("完整正文", cachedReader.currentContent)
        assertEquals(listOf("https://feed.example/feed", "https://feed.example/article/1"), requestedUrls)
    }
}
