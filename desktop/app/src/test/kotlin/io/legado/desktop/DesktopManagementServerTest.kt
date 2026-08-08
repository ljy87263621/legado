package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookGroup
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreReplaceRule
import io.legado.core.library.InMemoryCoreLibrary
import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import com.sun.net.httpserver.HttpServer as JdkHttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopManagementServerTest {

    @Test
    fun failedWebSocketBindReleasesTheHttpPortForTheNextStart() {
        var httpPort = 0
        var webSocketBlocker: ServerSocket? = null
        repeat(32) {
            val httpReservation = ServerSocket(
                0,
                50,
                InetAddress.getByName("127.0.0.1")
            )
            val candidatePort = httpReservation.localPort
            val candidateBlocker = if (candidatePort < 65534) {
                runCatching {
                    ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), candidatePort + 1))
                    }
                }.getOrNull()
            } else {
                null
            }
            httpReservation.close()
            if (candidateBlocker != null) {
                httpPort = candidatePort
                webSocketBlocker = candidateBlocker
                return@repeat
            }
        }

        val blocker = requireNotNull(webSocketBlocker) { "无法为端口生命周期测试预留相邻端口" }
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = httpPort)
        try {
            val failure = runCatching { server.start() }.exceptionOrNull()
            assertTrue(failure is java.net.BindException)
            assertFalse(server.isRunning)

            blocker.close()
            server.start()
            assertTrue(server.isRunning)
        } finally {
            blocker.close()
            server.close()
        }
    }

    @Test
    fun restartsRandomPortManagementServerRepeatedlyWithoutLeavingAStaleSocket() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        repeat(48) {
            server.start()
            try {
                assertTrue(server.isRunning)
                assertTrue(server.port > 0)
                assertEquals(server.port + 1, server.webSocketEndpoint.substringAfterLast(':').toInt())
            } finally {
                server.close()
            }
            assertFalse(server.isRunning)
        }
    }

    @Test
    fun loopbackServerExposesAndroidCompatibleReadEndpointsAndStopsCleanly() {
        val library = InMemoryCoreLibrary().apply {
            saveBook(
                CoreBook(
                    bookUrl = "https://example.test/book/1",
                    name = "测试书",
                    author = "作者"
                )
            )
            saveSource(
                CoreBookSource(
                    bookSourceUrl = "https://example.test",
                    bookSourceName = "测试书源"
                )
            )
            saveGroup(CoreBookGroup(groupId = 1L, groupName = "默认组"))
            val chapter = CoreChapter(
                bookUrl = "https://example.test/book/1",
                url = "https://example.test/book/1/chapter/1",
                title = "第一章",
                index = 0
            )
            saveChapter(chapter)
            saveContent(chapter, "第一章正文")
        }
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            assertTrue(server.isRunning)
            assertTrue(server.port > 0)
            assertTrue(server.endpoint.startsWith("http://127.0.0.1:"))

            assertTrue(server.get("/health").contains("\"status\":\"running\""))
            assertTrue(server.get("/getBookshelf").contains("测试书"))
            assertTrue(server.get("/getBookSources").contains("测试书源"))
            assertTrue(server.get("/getGroups").contains("默认组"))
            val bookUrl = URLEncoder.encode(
                "https://example.test/book/1",
                StandardCharsets.UTF_8
            )
            assertTrue(server.get("/getChapterList?bookUrl=$bookUrl").contains("第一章"))
            assertTrue(server.get("/getChapterList?url=$bookUrl").contains("第一章"))
            val contentUrl = URLEncoder.encode(
                "https://example.test/book/1",
                StandardCharsets.UTF_8
            )
            assertTrue(
                server.get("/getBookContent?url=$contentUrl&index=0").contains("第一章正文")
            )
        } finally {
            server.close()
        }

        assertFalse(server.isRunning)
        val connection = runCatching {
            (java.net.URI.create(server.endpoint).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 500
                readTimeout = 500
            }
        }.getOrNull()
        assertTrue(connection == null || runCatching { connection.responseCode }.isFailure)
    }

    @Test
    fun getBookshelfFiltersBooksByAndroidGroupIdBitmask() {
        val library = InMemoryCoreLibrary().apply {
            saveGroup(CoreBookGroup(groupId = 1L, groupName = "玄幻"))
            saveGroup(CoreBookGroup(groupId = 2L, groupName = "科幻"))
            saveBook(CoreBook("https://example.test/fantasy", name = "玄幻书", group = 1L))
            saveBook(CoreBook("https://example.test/scifi", name = "科幻书", group = 2L))
            saveBook(CoreBook("https://example.test/both", name = "双分组书", group = 3L))
            saveBook(
                CoreBook(
                    "https://example.test/temporary",
                    name = "临时阅读书",
                    group = 1L,
                    type = 1 shl 10
                )
            )
        }
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val response = server.get("/getBookshelf?groupId=1")

            assertTrue(response.contains("玄幻书"))
            assertTrue(response.contains("双分组书"))
            assertFalse(response.contains("科幻书"))
            assertFalse(response.contains("临时阅读书"))

            val allResponse = server.get("/getBookshelf?groupId=-1")
            assertFalse(allResponse.contains("临时阅读书"))
        } finally {
            server.close()
        }
    }

    @Test
    fun getBookshelfRejectsAnInvalidAndroidGroupId() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.getResponse("/getBookshelf?groupId=not-a-number")

            assertEquals(400, response.status)
            assertTrue(response.body.contains("groupId 参数无效"))
        } finally {
            server.close()
        }
    }

    @Test
    fun getBookshelfReturnsAndroidFailureEnvelopeWhenNoBooksMatch() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.getResponse("/getBookshelf")

            assertEquals(200, response.status)
            assertTrue(response.body.contains("\"isSuccess\":false"))
            assertTrue(response.body.contains("未找到"))
        } finally {
            server.close()
        }
    }

    @Test
    fun getBookSourcesReturnsAndroidFailureEnvelopeWhenNoSourcesExist() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.getResponse("/getBookSources")

            assertEquals(200, response.status)
            assertTrue(response.body.contains("\"isSuccess\":false"))
            assertTrue(response.body.contains("设备源列表为空"))
        } finally {
            server.close()
        }
    }

    @Test
    fun getChapterListRefreshesAnEmptyOnlineCatalogBeforeReturningIt() {
        val upstream = JdkHttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        upstream.createContext("/toc") { exchange ->
            val body = "<html><body><a class=\"chapter\" href=\"/chapter/1\">第一章</a></body></html>"
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        upstream.start()

        val source = CoreBookSource(
            bookSourceUrl = "https://source.test",
            bookSourceName = "测试书源",
            ruleToc = "{\"chapterList\":\".chapter\",\"chapterName\":\"a\",\"chapterUrl\":\"a@href\"}"
        )
        val bookUrl = "https://source.test/book/1"
        val library = InMemoryCoreLibrary().apply {
            saveSource(source)
            saveBook(
                CoreBook(
                    bookUrl = bookUrl,
                    name = "测试书",
                    origin = source.bookSourceUrl,
                    tocUrl = "http://127.0.0.1:${upstream.address.port}/toc"
                )
            )
        }
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val response = server.getResponse("/getChapterList?url=${encoded(bookUrl)}")

            assertEquals(200, response.status)
            assertTrue(response.body.contains("第一章"))
            assertEquals(listOf("第一章"), library.chapters(bookUrl).map(CoreChapter::title))
        } finally {
            server.close()
            upstream.stop(0)
        }
    }

    @Test
    fun getChapterListAcceptsBookUrlWhenRefreshingAnEmptyOnlineCatalog() {
        val upstream = JdkHttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        upstream.createContext("/toc") { exchange ->
            val body = "<html><body><a class=\"chapter\" href=\"/chapter/1\">第一章</a></body></html>"
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        upstream.start()
        val bookUrl = "https://example.test/book/toc-by-book-url"
        val library = InMemoryCoreLibrary().apply {
            saveBook(CoreBook(bookUrl = bookUrl, name = "目录书", author = "作者", origin = "https://source.test"))
            saveSource(
                CoreBookSource(
                    bookSourceUrl = "https://source.test",
                    bookSourceName = "测试书源",
                    ruleToc = """{"chapterList":".chapter","chapterName":"a","chapterUrl":"a@href"}""",
                    enabled = true
                )
            )
            saveBook(
                requireNotNull(book(bookUrl)).copy(
                    tocUrl = "http://127.0.0.1:${upstream.address.port}/toc"
                )
            )
        }
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val response = server.getResponse("/getChapterList?bookUrl=${encoded(bookUrl)}")

            assertEquals(200, response.status)
            assertTrue(response.body.contains("第一章"))
            assertEquals(listOf("第一章"), library.chapters(bookUrl).map(CoreChapter::title))
        } finally {
            server.close()
            upstream.stop(0)
        }
    }

    @Test
    fun getBookContentFetchesAndCachesMissingContentThroughItsBookSource() {
        val upstream = JdkHttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        upstream.createContext("/chapter") { exchange ->
            val body = "<html><body><div class=\"content\">在线章节正文</div></body></html>"
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        upstream.start()

        val chapterUrl = "http://127.0.0.1:${upstream.address.port}/chapter"
        val source = CoreBookSource(
            bookSourceUrl = "https://source.test",
            bookSourceName = "测试书源",
            ruleContent = "{\"content\":\".content\"}"
        )
        val bookUrl = "https://source.test/book/1"
        val chapter = CoreChapter(bookUrl, chapterUrl, "第一章", 0)
        val library = InMemoryCoreLibrary().apply {
            saveSource(source)
            saveBook(CoreBook(bookUrl, name = "测试书", origin = source.bookSourceUrl))
            saveChapter(chapter)
        }
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val response = server.getResponse("/getBookContent?url=${encoded(bookUrl)}&index=0")

            assertEquals(200, response.status)
            assertTrue(response.body.contains("在线章节正文"))
            assertEquals("在线章节正文", library.content(chapter))
        } finally {
            server.close()
            upstream.stop(0)
        }
    }

    @Test
    fun getBookContentReturnsAnAndroidFailureEnvelopeWhenOnlineLoadingFails() {
        val upstream = JdkHttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        upstream.createContext("/chapter") { exchange ->
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        upstream.start()
        val bookUrl = "https://example.test/book/content-failure"
        val chapter = CoreChapter(
            bookUrl = bookUrl,
            url = "http://127.0.0.1:${upstream.address.port}/chapter",
            title = "第一章",
            index = 0
        )
        val library = InMemoryCoreLibrary().apply {
            saveSource(
                CoreBookSource(
                    bookSourceUrl = "https://source.test",
                    bookSourceName = "测试书源",
                    ruleContent = """{"content":"body"}"""
                )
            )
            saveBook(CoreBook(bookUrl = bookUrl, name = "正文失败书", author = "作者", origin = "https://source.test"))
            saveChapter(chapter)
        }
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val response = server.getResponse("/getBookContent?url=${encoded(bookUrl)}&index=0")

            assertEquals(200, response.status)
            assertTrue(response.body.contains("\"isSuccess\":false"))
            assertTrue(response.body.contains("书源请求失败"))
        } finally {
            server.close()
            upstream.stop(0)
        }
    }

    @Test
    fun refreshTocReturnsAnAndroidFailureEnvelopeWhenOnlineLoadingFails() {
        val upstream = JdkHttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        upstream.createContext("/toc") { exchange ->
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        upstream.start()
        val bookUrl = "https://example.test/book/toc-failure"
        val library = InMemoryCoreLibrary().apply {
            saveSource(
                CoreBookSource(
                    bookSourceUrl = "https://source.test",
                    bookSourceName = "测试书源",
                    ruleToc = """{"chapterList":".chapter","chapterName":"a","chapterUrl":"a@href"}"""
                )
            )
            saveBook(
                CoreBook(
                    bookUrl = bookUrl,
                    name = "目录失败书",
                    author = "作者",
                    origin = "https://source.test",
                    tocUrl = "http://127.0.0.1:${upstream.address.port}/toc"
                )
            )
        }
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val response = server.getResponse("/refreshToc?url=${encoded(bookUrl)}")

            assertEquals(200, response.status)
            assertTrue(response.body.contains("\"isSuccess\":false"))
            assertTrue(response.body.contains("书源请求失败"))
        } finally {
            server.close()
            upstream.stop(0)
        }
    }

    @Test
    fun postSaveBookProgressUpdatesAnExistingBookUsingAndroidPayload() {
        val bookUrl = "https://example.test/book/progress"
        val library = InMemoryCoreLibrary().apply {
            saveBook(
                CoreBook(
                    bookUrl = bookUrl,
                    name = "进度书",
                    author = "作者"
                )
            )
        }
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val response = server.post(
                "/saveBookProgress",
                """{"name":"进度书","author":"作者","durChapterIndex":4,"durChapterPos":128,"durChapterTitle":"第五章","durChapterTime":1700000000000}"""
            )

            assertEquals(200, response.status)
            assertTrue(response.body.contains("\"isSuccess\":true"))
            val saved = requireNotNull(library.book(bookUrl))
            assertEquals(4, saved.durChapterIndex)
            assertEquals(128, saved.durChapterPos)
            assertEquals("第五章", saved.durChapterTitle)
            assertEquals(1700000000000L, saved.durChapterTime)
        } finally {
            server.close()
        }
    }

    @Test
    fun postSaveBookProgressRejectsMalformedPayload() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.post("/saveBookProgress", "not-json")

            assertEquals(400, response.status)
            assertTrue(response.body.contains("格式不对"))
        } finally {
            server.close()
        }
    }

    @Test
    fun postSaveBookProgressRejectsPayloadWithMissingFields() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.post(
                "/saveBookProgress",
                """{"name":"进度书","author":"作者"}"""
            )

            assertEquals(400, response.status)
            assertTrue(response.body.contains("缺少阅读进度字段"))
        } finally {
            server.close()
        }
    }

    @Test
    fun postSaveBookProgressRejectsUnknownBook() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.post(
                "/saveBookProgress",
                """{"name":"不存在","author":"作者","durChapterIndex":1,"durChapterPos":2,"durChapterTitle":"第二章","durChapterTime":3}"""
            )

            assertEquals(404, response.status)
            assertTrue(response.body.contains("未找到书籍"))
        } finally {
            server.close()
        }
    }

    @Test
    fun postSaveBookUpdatesAnExistingBookFromAndroidCompatibleBookJson() {
        val bookUrl = "https://example.test/book/save"
        val library = InMemoryCoreLibrary().apply {
            saveBook(
                CoreBook(
                    bookUrl = bookUrl,
                    name = "旧书名",
                    author = "旧作者"
                )
            )
        }
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val response = server.post(
                "/saveBook",
                Gson().toJson(
                    CoreBook(
                        bookUrl = bookUrl,
                        name = "更新后的书名",
                        author = "更新后的作者",
                        intro = "更新后的简介",
                        durChapterIndex = 6,
                        durChapterPos = 256,
                        durChapterTitle = "第七章",
                        durChapterTime = 1700000000000L
                    )
                )
            )

            assertEquals(200, response.status)
            assertTrue(response.body.contains("\"isSuccess\":true"))
            val saved = requireNotNull(library.book(bookUrl))
            assertEquals("更新后的书名", saved.name)
            assertEquals("更新后的作者", saved.author)
            assertEquals("更新后的简介", saved.intro)
            assertEquals(6, saved.durChapterIndex)
            assertEquals(256, saved.durChapterPos)
            assertEquals("第七章", saved.durChapterTitle)
            assertEquals(1700000000000L, saved.durChapterTime)
        } finally {
            server.close()
        }
    }

    @Test
    fun postSaveBookRejectsMalformedJson() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.post("/saveBook", "not-json")

            assertEquals(400, response.status)
            assertTrue(response.body.contains("格式不对"))
        } finally {
            server.close()
        }
    }

    @Test
    fun postSaveBookRejectsBlankBookUrl() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.post(
                "/saveBook",
                Gson().toJson(CoreBook(bookUrl = "   ", name = "书名", author = "作者"))
            )

            assertEquals(400, response.status)
            assertTrue(response.body.contains("bookUrl"))
        } finally {
            server.close()
        }
    }

    @Test
    fun postSaveBookRejectsMissingBookUrl() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.post(
                "/saveBook",
                """{"name":"书名","author":"作者"}"""
            )

            assertEquals(400, response.status)
            assertTrue(response.body.contains("bookUrl"))
        } finally {
            server.close()
        }
    }

    @Test
    fun postSaveBookSourceAcceptsWebRuleObjectsAndCanReadTheSavedSource() {
        val library = InMemoryCoreLibrary()
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val response = server.post(
                "/saveBookSource",
                """
                {
                  "bookSourceUrl":"https://example.test/source",
                  "bookSourceName":"兼容书源",
                  "bookSourceType":0,
                  "ruleSearch":{"bookList":".book","name":"h2"},
                  "enabled":true
                }
                """.trimIndent()
            )

            assertEquals(200, response.status)
            assertTrue(response.body.contains("\"isSuccess\":true"))
            val saved = requireNotNull(library.source("https://example.test/source"))
            assertEquals("兼容书源", saved.bookSourceName)
            assertEquals("{\"bookList\":\".book\",\"name\":\"h2\"}", saved.ruleSearch)

            val queried = server.getResponse(
                "/getBookSource?url=" +
                    URLEncoder.encode("https://example.test/source", StandardCharsets.UTF_8)
            )
            assertEquals(200, queried.status)
            assertTrue(queried.body.contains("兼容书源"))
        } finally {
            server.close()
        }
    }

    @Test
    fun postSaveBookSourcesStoresOnlyValidSourcesAndReturnsThem() {
        val library = InMemoryCoreLibrary()
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val response = server.post(
                "/saveBookSources",
                """
                [
                  {"bookSourceUrl":"https://example.test/one","bookSourceName":"一号源"},
                  {"bookSourceUrl":"","bookSourceName":"无地址"},
                  {"bookSourceUrl":"https://example.test/two","bookSourceName":"二号源"},
                  {"bookSourceUrl":"https://example.test/no-name","bookSourceName":""}
                ]
                """.trimIndent()
            )

            assertEquals(200, response.status)
            assertTrue(response.body.contains("一号源"))
            assertTrue(response.body.contains("二号源"))
            assertEquals(2, library.sources().size)
            assertEquals(null, library.source("https://example.test/no-name"))
        } finally {
            server.close()
        }
    }

    @Test
    fun postDeleteBookSourcesRemovesEachSourceFromAnAndroidSourceArray() {
        val firstUrl = "https://example.test/one"
        val secondUrl = "https://example.test/two"
        val library = InMemoryCoreLibrary().apply {
            saveSource(CoreBookSource(bookSourceUrl = firstUrl, bookSourceName = "一号源"))
            saveSource(CoreBookSource(bookSourceUrl = secondUrl, bookSourceName = "二号源"))
        }
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val response = server.post(
                "/deleteBookSources",
                Gson().toJson(
                    listOf(
                        CoreBookSource(bookSourceUrl = firstUrl, bookSourceName = "一号源"),
                        CoreBookSource(bookSourceUrl = secondUrl, bookSourceName = "二号源")
                    )
                )
            )

            assertEquals(200, response.status)
            assertTrue(response.body.contains("已执行"))
            assertEquals(emptyList<CoreBookSource>(), library.sources())
        } finally {
            server.close()
        }
    }

    @Test
    fun postDeleteBookRemovesTheBookAndItsChapters() {
        val bookUrl = "https://example.test/book/delete"
        val chapter = CoreChapter(bookUrl = bookUrl, url = "$bookUrl/1", title = "第一章")
        val library = InMemoryCoreLibrary().apply {
            saveBook(CoreBook(bookUrl = bookUrl, name = "待删除", author = "作者"))
            saveChapter(chapter)
            saveContent(chapter, "正文")
        }
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val response = server.post(
                "/deleteBook",
                Gson().toJson(CoreBook(bookUrl = bookUrl, name = "待删除", author = "作者"))
            )

            assertEquals(200, response.status)
            assertEquals(null, library.book(bookUrl))
            assertEquals(emptyList<CoreChapter>(), library.chapters(bookUrl))
            assertTrue(library.content(chapter) == null)
        } finally {
            server.close()
        }
    }

    @Test
    fun replaceRuleRoutesPersistSortedRulesAsAnAndroidJsonString() {
        val library = InMemoryCoreLibrary()
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val first = CoreReplaceRule(
                id = 11L,
                name = "后置规则",
                pattern = "foo",
                replacement = "bar",
                order = Int.MIN_VALUE
            )
            val second = CoreReplaceRule(
                id = 12L,
                name = "前置规则",
                pattern = "bar",
                replacement = "baz",
                order = -1
            )

            assertEquals(200, server.post("/saveReplaceRule", Gson().toJson(first)).status)
            assertEquals(200, server.post("/saveReplaceRule", Gson().toJson(second)).status)
            assertEquals(0, requireNotNull(library.replaceRules().first { it.id == 11L }).order)

            val response = server.getResponse("/getReplaceRules")
            assertEquals(200, response.status)
            assertTrue(response.body.contains("\\\"name\\\":\\\"前置规则\\\""))
            assertTrue(response.body.indexOf("前置规则") < response.body.indexOf("后置规则"))

            assertEquals(200, server.post("/deleteReplaceRule", Gson().toJson(second)).status)
            assertEquals(listOf(first.copy(order = 0)), library.replaceRules())
        } finally {
            server.close()
        }
    }

    @Test
    fun rootServesTheWebAppFromTheManagementEndpoint() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.getResponse("/")

            assertEquals(200, response.status)
            assertTrue(response.contentType?.startsWith("text/html") == true)
            assertTrue(response.body.startsWith("<!DOCTYPE html>"))
            assertTrue(response.body.contains("getBookshelf"))
        } finally {
            server.close()
        }
    }

    @Test
    fun uploadBookRouteServesTheAndroidUploadPage() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            listOf("/uploadBook/", "/uploadBook/index.html").forEach { path ->
                val response = server.getResponse(path)

                assertEquals(200, response.status)
                assertTrue(response.contentType?.startsWith("text/html") == true)
                assertTrue(response.body.contains("WiFi传输"))
                assertTrue(response.body.contains("addLocalBook"))
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun postToUnsupportedRouteRemainsRejected() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.post("/saveBook/delete", "{}")

            assertEquals(405, response.status)
            assertTrue(response.body.contains("未找到 POST 接口"))
        } finally {
            server.close()
        }
    }

    @Test
    fun getReadConfigReturnsAndroidWebConfigEnvelope() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.getResponse("/getReadConfig")

            assertEquals(200, response.status)
            assertTrue(response.body.contains("\"isSuccess\":true"))
            assertTrue(response.body.contains("\\\"fontSize\\\":18"))
            assertTrue(response.body.contains("\\\"spacing\\\""))
        } finally {
            server.close()
        }
    }

    @Test
    fun postSaveReadConfigPersistsValidatedConfigForSubsequentReads() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.post(
                "/saveReadConfig",
                """{"theme":6,"font":2,"fontSize":30,"readWidth":960,"infiniteLoading":true,"customFontName":"Microsoft YaHei","jumpDuration":1500,"spacing":{"paragraph":2,"line":1.2,"letter":0.1}}"""
            )

            assertEquals(200, response.status)
            assertTrue(response.body.contains("\"isSuccess\":true"))

            val saved = server.getResponse("/getReadConfig")
            assertEquals(200, saved.status)
            assertTrue(saved.body.contains("\\\"theme\\\":6"))
            assertTrue(saved.body.contains("\\\"fontSize\\\":30"))
            assertTrue(saved.body.contains("\\\"customFontName\\\":\\\"Microsoft YaHei\\\""))
            assertTrue(saved.body.contains("\\\"line\\\":1.2"))
        } finally {
            server.close()
        }
    }

    @Test
    fun savedReadConfigSurvivesManagementServerRestart() {
        val library = InMemoryCoreLibrary()
        val firstServer = DesktopManagementServer(library, port = 0)
        firstServer.start()
        try {
            val response = firstServer.post(
                "/saveReadConfig",
                """{"theme":5,"font":1,"fontSize":26,"readWidth":720,"infiniteLoading":true,"customFontName":"等线","jumpDuration":900,"spacing":{"paragraph":1.5,"line":1.1,"letter":0.2}}"""
            )
            assertEquals(200, response.status)
        } finally {
            firstServer.close()
        }

        val restartedServer = DesktopManagementServer(library, port = 0)
        restartedServer.start()
        try {
            val response = restartedServer.getResponse("/getReadConfig")

            assertEquals(200, response.status)
            assertTrue(response.body.contains("\\\"theme\\\":5"))
            assertTrue(response.body.contains("\\\"fontSize\\\":26"))
            assertTrue(response.body.contains("\\\"customFontName\\\":\\\"等线\\\""))
        } finally {
            restartedServer.close()
        }
    }

    @Test
    fun postSaveReadConfigRejectsMalformedOrOutOfRangeValues() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.post(
                "/saveReadConfig",
                """{"fontSize":0,"readWidth":-1,"spacing":{"line":9}}"""
            )

            assertEquals(400, response.status)
            assertTrue(response.body.contains("阅读配置"))
        } finally {
            server.close()
        }
    }

    @Test
    fun optionsReturnsAndroidCompatibleCorsHeaders() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("${server.endpoint}/health"))
                .method("OPTIONS", java.net.http.HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:5173")
                .build()
            val response = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(1))
                .build()
                .send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray())

            assertEquals(204, response.statusCode())
            assertEquals(
                "http://localhost:5173",
                response.headers().firstValue("Access-Control-Allow-Origin").orElse(null)
            )
            assertTrue(
                response.headers().firstValue("Access-Control-Allow-Methods")
                    .orElse("")
                    .contains("POST")
            )
            assertEquals(
                "content-type",
                response.headers().firstValue("Access-Control-Allow-Headers").orElse(null)
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun testReplaceRuleReturnsReplacedTextInAndroidEnvelope() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.post(
                "/testReplaceRule",
                """{"rule":{"pattern":"foo","replacement":"bar","isRegex":false},"text":"foo text"}"""
            )

            assertEquals(200, response.status)
            assertTrue(response.body.contains("\"isSuccess\":true"))
            assertTrue(response.body.contains("\"data\":\"bar text\""))
        } finally {
            server.close()
        }
    }

    @Test
    fun testReplaceRuleRejectsMissingTextOrRule() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.post("/testReplaceRule", """{"text":"foo"}""")

            assertEquals(400, response.status)
            assertTrue(response.body.contains("格式不对"))
        } finally {
            server.close()
        }
    }

    @Test
    fun refreshTocRejectsMissingAndUnknownBook() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val missing = server.getResponse("/refreshToc")
            assertEquals(400, missing.status)
            assertTrue(missing.body.contains("参数url不能为空"))

            val unknown = server.getResponse("/refreshToc?url=" + encoded("missing"))
            assertEquals(404, unknown.status)
            assertTrue(unknown.body.contains("未在数据库找到对应书籍"))
        } finally {
            server.close()
        }
    }

    @Test
    fun refreshTocReimportsLocalBookAndRemovesStaleChapters() {
        val directory = Files.createTempDirectory("legado-refresh-toc")
        val path = directory.resolve("refresh.txt")
        Files.writeString(path, "第一章\n新正文\n")
        val staleBookUrl = path.toAbsolutePath().normalize().toString()
        val library = InMemoryCoreLibrary().apply {
            saveBook(CoreBook(staleBookUrl, "refresh", origin = "loc_book", originName = "本地文件"))
            val stale = CoreChapter(staleBookUrl, "$staleBookUrl#stale", "旧章节", 8)
            saveChapter(stale)
            saveContent(stale, "旧正文")
        }
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val response = server.getResponse("/refreshToc?url=" + encoded(staleBookUrl))

            assertEquals(200, response.status)
            assertTrue(response.body.contains("第一章"))
            assertEquals(listOf("第一章"), library.chapters(staleBookUrl).map(CoreChapter::title))
            assertTrue(library.chapters(staleBookUrl).none { it.title == "旧章节" })
            assertEquals("新正文", library.content(library.chapters(staleBookUrl).single())?.trim())
        } finally {
            server.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun coverReturnsPngForLocalImageAndStableErrorForMissingPath() {
        val directory = Files.createTempDirectory("legado-cover")
        val image = directory.resolve("cover.png")
        writePng(image, 2, 3)
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.getBinaryResponse("/cover?path=" + encoded(image.toString()))
            assertEquals(200, response.status)
            assertEquals("image/png", response.contentType?.substringBefore(';'))
            assertTrue(response.body.take(8).toByteArray().contentEquals(PNG_SIGNATURE))

            val missing = server.getResponse("/cover?path=" + encoded(directory.resolve("missing.png").toString()))
            assertEquals(404, missing.status)
        } finally {
            server.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun imageRequiresBookAndPathAndCanProxyAReferencedImage() {
        val image = Files.createTempFile("legado-image", ".png")
        writePng(image, 4, 5)
        val bookUrl = "https://example.test/image-book"
        val library = InMemoryCoreLibrary().apply {
            saveBook(CoreBook(bookUrl, "图片书", "作者"))
        }
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val missing = server.getResponse("/image?path=" + encoded(image.toString()))
            assertEquals(400, missing.status)
            assertTrue(missing.body.contains("bookUrl为空"))

            val response = server.getBinaryResponse(
                "/image?path=${encoded(image.toString())}&url=${encoded(bookUrl)}&width=20"
            )
            assertEquals(200, response.status)
            assertEquals("image/png", response.contentType?.substringBefore(';'))
            assertTrue(response.body.take(8).toByteArray().contentEquals(PNG_SIGNATURE))
        } finally {
            server.close()
            Files.deleteIfExists(image)
        }
    }

    @Test
    fun addLocalBookRejectsMissingMultipartFields() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)

        server.start()
        try {
            val response = server.postMultipart(
                "/addLocalBook",
                mapOf("fileName" to "book.txt"),
                emptyMap()
            )

            assertEquals(400, response.status)
            assertTrue(response.body.contains("fileData 不能为空"))
        } finally {
            server.close()
        }
    }

    private fun DesktopManagementServer.get(path: String): String {
        return getResponse(path).body
    }

    private fun encoded(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun DesktopManagementServer.getResponse(path: String): HttpResult {
        val connection = java.net.URI.create("$endpoint$path").toURL()
            .openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 1_000
            connection.readTimeout = 1_000
            val stream = if (connection.responseCode in 200..399) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            HttpResult(
                status = connection.responseCode,
                body = stream.bufferedReader().use { it.readText() },
                contentType = connection.contentType
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun DesktopManagementServer.post(path: String, body: String): HttpResult {
        val connection = java.net.URI.create("$endpoint$path").toURL()
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.connectTimeout = 1_000
            connection.readTimeout = 1_000
            connection.outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            val stream = if (connection.responseCode in 200..399) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            HttpResult(
                status = connection.responseCode,
                body = stream.bufferedReader().use { it.readText() },
                contentType = connection.contentType
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun DesktopManagementServer.getBinaryResponse(path: String): BinaryHttpResult {
        val connection = java.net.URI.create("$endpoint$path").toURL()
            .openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 1_000
            connection.readTimeout = 1_000
            val stream = if (connection.responseCode in 200..399) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            BinaryHttpResult(
                status = connection.responseCode,
                body = stream.readBytes(),
                contentType = connection.contentType
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun DesktopManagementServer.postMultipart(
        path: String,
        fields: Map<String, String>,
        files: Map<String, Pair<String, ByteArray>>
    ): HttpResult {
        val boundary = "----LegadoTestBoundary"
        val body = java.io.ByteArrayOutputStream().apply {
            fields.forEach { (name, value) ->
                write("--$boundary\r\n".toByteArray())
                write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                write(value.toByteArray(StandardCharsets.UTF_8))
                write("\r\n".toByteArray())
            }
            files.forEach { (name, file) ->
                write("--$boundary\r\n".toByteArray())
                write("Content-Disposition: form-data; name=\"$name\"; filename=\"${file.first}\"\r\n".toByteArray())
                write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
                write(file.second)
                write("\r\n".toByteArray())
            }
            write("--$boundary--\r\n".toByteArray())
        }.toByteArray()
        val connection = java.net.URI.create("$endpoint$path").toURL()
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.connectTimeout = 1_000
            connection.readTimeout = 1_000
            connection.outputStream.use { it.write(body) }
            val stream = if (connection.responseCode in 200..399) connection.inputStream else connection.errorStream
            HttpResult(connection.responseCode, stream.bufferedReader().use { it.readText() }, connection.contentType)
        } finally {
            connection.disconnect()
        }
    }

    private fun writePng(path: Path, width: Int, height: Int) {
        val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        javax.imageio.ImageIO.write(image, "png", path.toFile())
    }

    private data class HttpResult(
        val status: Int,
        val body: String,
        val contentType: String?
    )

    private data class BinaryHttpResult(
        val status: Int,
        val body: ByteArray,
        val contentType: String?
    )

    companion object {
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
    }
}
