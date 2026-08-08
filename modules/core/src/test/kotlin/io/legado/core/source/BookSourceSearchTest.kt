package io.legado.core.source

import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreBookSourceType
import io.legado.core.library.CoreBookType
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
    fun legacyRssSourceJsonIsConvertedToBookSourceFormat() {
        val source = BookSourceJsonCodec.decode(
            """
                [{
                  "sourceUrl": "https://feed.example/rss",
                  "sourceName": "旧订阅",
                  "sourceGroup": "资讯",
                  "sortUrl": "https://feed.example/rss?page={{page}}",
                  "ruleArticles": ".article",
                  "ruleTitle": "h2",
                  "rulePubDate": ".date",
                  "ruleDescription": ".summary",
                  "ruleImage": "img@src",
                  "ruleLink": "a@href",
                  "ruleContent": ".content"
                }]
            """.trimIndent()
        ).single()

        assertEquals("https://feed.example/rss", source.bookSourceUrl)
        assertEquals("旧订阅", source.bookSourceName)
        assertEquals("资讯", source.bookSourceGroup)
        assertEquals(5, source.bookSourceType)
        assertEquals("https://feed.example/rss?page={{page}}", source.exploreUrl)
        assertEquals(
            """{"bookList":".article","name":"h2","author":".date","intro":".summary","coverUrl":"img@src","bookUrl":"a@href"}""",
            source.ruleExplore
        )
        assertEquals("{\"content\":\".content\"}", source.ruleContent)
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
    fun dynamicHeaderCanUseSharedJsLibrary() {
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            searchUrl = "https://source.example/search?q={{key}}",
            header = "@js:JSON.stringify({ 'X-Token': makeToken() })",
            jsLib = "function makeToken() { return 'token-from-js'; }",
            ruleSearch = "{\"bookList\":\".book\",\"name\":\"h2\"}"
        )
        val client = FakeHttpClient { _, headers ->
            assertEquals("token-from-js", headers["X-Token"])
            CoreHttpResponse("https://source.example", "<div class='book'><h2>星河</h2></div>")
        }
        val service = BookSourceSearchService(InMemoryCoreLibrary().also { it.saveSource(source) }, client)

        assertEquals("星河", service.search("星河").single().book.name)
    }

    @Test
    fun dynamicSearchUrlCanUseKeyAndSharedJsLibrary() {
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            searchUrl = "@js:'https://source.example/search?q=' + encodeURIComponent(key)",
            jsLib = "function encodeURIComponent(value) { return value.replace(' ', '%20'); }",
            ruleSearch = "{\"bookList\":\".book\",\"name\":\"h2\"}"
        )
        val client = FakeHttpClient { url, _ ->
            assertEquals("https://source.example/search?q=%E6%98%9F%E6%B2%B3", url)
            CoreHttpResponse(url, "<div class='book'><h2>星河</h2></div>")
        }
        val service = BookSourceSearchService(InMemoryCoreLibrary().also { it.saveSource(source) }, client)

        assertEquals("星河", service.search("星河").single().book.name)
    }

    @Test
    fun dynamicSearchUrlCanReadPersistentSourceVariable() {
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            searchUrl = "@js:'https://source.example/search?token=' + source.getVariable()",
            ruleSearch = "{\"bookList\":\".book\",\"name\":\"h2\"}"
        )
        val library = InMemoryCoreLibrary().also {
            it.saveSource(source)
            it.saveSourceVariable(source.bookSourceUrl, "stored-token")
        }
        val client = FakeHttpClient { url, _ ->
            assertEquals("https://source.example/search?token=stored-token", url)
            CoreHttpResponse(url, "<div class='book'><h2>星河</h2></div>")
        }
        val service = BookSourceSearchService(library, CoreSourceHttpClient(library, client))

        assertEquals("星河", service.search("星河").single().book.name)
    }

    @Test
    fun searchTemplateQueryUsesRfc3986EncodingOnce() {
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            searchUrl = "https://source.example/search?q={{key}}",
            ruleSearch = "{\"bookList\":\".book\",\"name\":\"h2\"}"
        )
        val library = InMemoryCoreLibrary().also { it.saveSource(source) }
        val client = FakeHttpClient { url, _ ->
            assertEquals("https://source.example/search?q=%E6%98%9F%E6%B2%B3%20%E4%BC%A0", url)
            CoreHttpResponse(url, "<div class='book'><h2>星河传</h2></div>")
        }

        assertEquals("星河传", BookSourceSearchService(library, client).search("星河 传").single().book.name)
    }

    @Test
    fun searchUrlOptionsAreSentAsAnHttpRequest() {
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            header = "@js:JSON.stringify({ 'X-Token': 'source-token', 'X-Source': 'yes' })",
            searchUrl = "https://source.example/search?unused={{key}}," +
                "{\"method\":\"POST\",\"headers\":{\"X-Token\":\"url-token\"}," +
                "\"body\":\"query={{key}}&page={{page}}\"}",
            ruleSearch = "{\"bookList\":\".book\",\"name\":\"h2\"}"
        )
        val library = InMemoryCoreLibrary().also { it.saveSource(source) }
        val client = RequestRecordingHttpClient { request ->
            assertEquals("POST", request.method)
            assertEquals("https://source.example/search?unused=%E6%98%9F%E6%B2%B3", request.url)
            assertEquals("query=%E6%98%9F%E6%B2%B3&page=2", request.body)
            assertEquals("url-token", request.headers["X-Token"])
            assertEquals("yes", request.headers["X-Source"])
            CoreHttpResponse(request.url, "<div class='book'><h2>星河</h2></div>")
        }

        val result = BookSourceSearchService(library, client).search("星河", page = 2).single()

        assertEquals("星河", result.book.name)
        assertEquals(1, client.requests.size)
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
    fun xpathSearchParsesRecordsAndAttributeFields() {
        val source = CoreBookSource(
            bookSourceUrl = "https://xpath-search.example",
            bookSourceName = "XPath搜索源",
            searchUrl = "https://xpath-search.example/search?q={{key}}",
            ruleSearch = """
                {
                  "bookList": "@XPath://section[@class='book']",
                  "name": "./h2",
                  "author": "./span[@class='author']",
                  "bookUrl": "./a/@href",
                  "coverUrl": "./img/@src",
                  "intro": "./p[@class='intro']"
                }
            """.trimIndent()
        )
        val html = """
            <section class="book">
              <h2>星河</h2>
              <span class="author">甲作者</span>
              <a href="/book/1">详情</a>
              <img src="/cover/1.jpg">
              <p class="intro">简介</p>
            </section>
        """.trimIndent()
        val client = FakeHttpClient { url, _ -> CoreHttpResponse(url, html) }
        val service = BookSourceSearchService(
            InMemoryCoreLibrary().also { it.saveSource(source) },
            client
        )

        val result = service.search("星河").single().book

        assertEquals("星河", result.name)
        assertEquals("甲作者", result.author)
        assertEquals("https://xpath-search.example/book/1", result.bookUrl)
        assertEquals("https://xpath-search.example/cover/1.jpg", result.coverUrl)
        assertEquals("简介", result.intro)
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

    @Test
    fun exploreParsesSubscriptionArticlesAndResolvesRelativeLinks() {
        val source = CoreBookSource(
            bookSourceUrl = "https://feed.example",
            bookSourceName = "示例订阅",
            bookSourceType = 5,
            exploreUrl = "https://feed.example/articles?page={{page}}",
            ruleExplore = """
                {
                  "bookList": ".article",
                  "name": "h2",
                  "author": ".date",
                  "intro": ".summary",
                  "coverUrl": "img@src",
                  "bookUrl": "a@href"
                }
            """.trimIndent()
        )
        val library = InMemoryCoreLibrary().also { it.saveSource(source) }
        val client = FakeHttpClient { url, _ ->
            assertEquals("https://feed.example/articles?page=1", url)
            CoreHttpResponse(
                url,
                """
                    <article class="article">
                      <h2>今日要闻</h2>
                      <span class="date">2026-08-02</span>
                      <p class="summary">摘要内容</p>
                      <img src="/cover.jpg">
                      <a href="/article/1">阅读</a>
                    </article>
                """.trimIndent()
            )
        }
        val service = BookSourceSearchService(library, client)

        val result = service.explore(source, page = 1).single().book

        assertEquals("今日要闻", result.name)
        assertEquals("2026-08-02", result.author)
        assertEquals("摘要内容", result.intro)
        assertEquals("https://feed.example/cover.jpg", result.coverUrl)
        assertEquals("https://feed.example/article/1", result.bookUrl)
        assertEquals(CoreBookType.RSS, result.type)
    }

    @Test
    fun exploreExpandsSelectedCategoryOptionsAlongsidePage() {
        val source = CoreBookSource(
            bookSourceUrl = "https://books.example",
            bookSourceName = "分类源",
            exploreUrl = "https://books.example/explore/<分类(玄幻:fantasy,都市:city)>?page={{page}}",
            ruleExplore = "{\"bookList\":\".book\",\"name\":\"h2\",\"bookUrl\":\"a@href\"}"
        )
        val library = InMemoryCoreLibrary().also { it.saveSource(source) }
        val client = FakeHttpClient { url, _ ->
            assertEquals("https://books.example/explore/city?page=2", url)
            CoreHttpResponse(url, "<div class='book'><h2>都市书</h2><a href='/book/2'>详情</a></div>")
        }

        val result = BookSourceSearchService(library, client).explore(
            source,
            page = 2,
            selectedOptions = mapOf("分类" to "city")
        ).single().book

        assertEquals("都市书", result.name)
    }

    @Test
    fun searchMapsImageSourceCategoryToThePersistedImageBookFlag() {
        val source = CoreBookSource(
            bookSourceUrl = "https://manga.example",
            bookSourceType = CoreBookSourceType.IMAGE,
            searchUrl = "https://manga.example/search?q={{key}}",
            ruleSearch = "{\"bookList\":\".comic\",\"name\":\"h2\",\"bookUrl\":\"a@href\"}"
        )
        val library = InMemoryCoreLibrary().also { it.saveSource(source) }
        val client = FakeHttpClient { _, _ ->
            CoreHttpResponse(
                "https://manga.example/search?q=test",
                "<article class='comic'><h2>漫画</h2><a href='/comic/1'>阅读</a></article>"
            )
        }

        val result = BookSourceSearchService(library, client).search("test").single().book

        assertEquals(CoreBookType.IMAGE, result.type)
    }

    @Test
    fun sourceCategoriesUseAndroidCompatibleBookFlags() {
        assertEquals(CoreBookType.TEXT, CoreBookType.fromSourceType(0))
        assertEquals(CoreBookType.AUDIO, CoreBookType.fromSourceType(CoreBookSourceType.AUDIO))
        assertEquals(CoreBookType.IMAGE, CoreBookType.fromSourceType(CoreBookSourceType.IMAGE))
        assertEquals(CoreBookType.TEXT or CoreBookType.WEB_FILE, CoreBookType.fromSourceType(CoreBookSourceType.FILE))
        assertEquals(CoreBookType.VIDEO, CoreBookType.fromSourceType(CoreBookSourceType.VIDEO))
        assertEquals(CoreBookType.RSS, CoreBookType.fromSourceType(CoreBookSourceType.RSS))
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

    private class RequestRecordingHttpClient(
        private val handler: (CoreHttpRequest) -> CoreHttpResponse
    ) : CoreHttpClient {
        val requests = mutableListOf<CoreHttpRequest>()

        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
            error("search URL options must use CoreHttpRequest")

        override fun request(request: CoreHttpRequest): CoreHttpResponse {
            requests += request
            return handler(request)
        }
    }
}
