package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreBookSourceType
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpRequest
import io.legado.core.source.CoreHttpResponse
import io.legado.core.source.CoreSourceSessionStatus
import io.legado.core.source.OnlineBookService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineImageReaderModelTest {

    @Test
    fun exposesLoginUrlWhenOnlineImageChapterRequiresAuthentication() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://manga.example",
            bookSourceName = "漫画源",
            bookSourceType = CoreBookSourceType.IMAGE,
            ruleContent = "{\"content\":\".pages\"}"
        )
        val book = CoreBook(
            bookUrl = "https://manga.example/book/1",
            origin = source.bookSourceUrl,
            type = DesktopBookType.ONLINE_IMAGE
        )
        val chapter = CoreChapter(book.bookUrl, "https://manga.example/chapter/1", "第一话", 0)
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(chapter)
        val client = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                CoreHttpResponse("https://manga.example/login", "", 401)
        }
        val reader = OnlineImageReaderModel(
            library = library,
            bookUrl = book.bookUrl,
            onlineService = OnlineBookService(library, client),
            httpClient = client,
            cacheDirectory = java.nio.file.Files.createTempDirectory("legado-online-image-login")
        )

        assertFalse(reader.loadCurrentChapter())
        assertEquals(CoreSourceSessionStatus.LOGIN_REQUIRED, reader.sessionStatus)
        assertEquals("https://manga.example/login", reader.loginUrl)
        assertEquals(source.bookSourceUrl, reader.loginSourceUrl)
    }

    @Test
    fun exposesLoginUrlWhenOnlineImagePageRequiresAuthentication() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://manga.example",
            bookSourceName = "漫画源",
            bookSourceType = CoreBookSourceType.IMAGE,
            ruleContent = "{\"content\":\".pages\"}"
        )
        val book = CoreBook(
            bookUrl = "https://manga.example/book/1",
            origin = source.bookSourceUrl,
            type = DesktopBookType.ONLINE_IMAGE
        )
        val chapter = CoreChapter(book.bookUrl, "https://manga.example/chapter/1", "第一话", 0)
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(chapter)
        val client = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                CoreHttpResponse(url, "<div class='pages'><img src='/images/1.png'></div>")

            override fun request(request: CoreHttpRequest): CoreHttpResponse = if (request.url == chapter.url) {
                CoreHttpResponse(request.url, "<div class='pages'><img src='/images/1.png'></div>")
            } else {
                CoreHttpResponse("https://manga.example/login", "", 401)
            }
        }
        val reader = OnlineImageReaderModel(
            library = library,
            bookUrl = book.bookUrl,
            onlineService = OnlineBookService(library, client),
            httpClient = client,
            cacheDirectory = java.nio.file.Files.createTempDirectory("legado-online-image-page-login")
        )

        assertTrue(reader.loadCurrentChapter())
        runCatching { reader.currentPageBytes() }
        assertEquals(CoreSourceSessionStatus.LOGIN_REQUIRED, reader.sessionStatus)
        assertEquals("https://manga.example/login", reader.loginUrl)
        assertEquals(source.bookSourceUrl, reader.loginSourceUrl)
    }

    @Test
    fun loadsImageUrlsFromAnOnlineChapterCachesImagesAndPersistsPageProgress() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://manga.example",
            bookSourceName = "漫画源",
            bookSourceType = CoreBookSourceType.IMAGE,
            ruleContent = "{\"content\":\".pages\"}"
        )
        val book = CoreBook(
            bookUrl = "https://manga.example/book/1",
            name = "在线漫画",
            origin = source.bookSourceUrl,
            type = DesktopBookType.ONLINE_IMAGE
        )
        val chapter = CoreChapter(book.bookUrl, "https://manga.example/chapter/1", "第一话", 0)
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(chapter)
        val requests = mutableListOf<CoreHttpRequest>()
        val client = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse = request(
                CoreHttpRequest(url = url, headers = headers)
            )

            override fun request(request: CoreHttpRequest): CoreHttpResponse {
                requests += request
                return when (request.url) {
                    chapter.url -> CoreHttpResponse(
                        url = request.url,
                        body = "<div class='pages'><img src='/images/1.png'><img src='https://cdn.manga.example/2.png'></div>"
                    )
                    "https://manga.example/images/1.png" -> CoreHttpResponse(request.url, "first-image")
                    "https://cdn.manga.example/2.png" -> CoreHttpResponse(request.url, "second-image")
                    else -> error("unexpected request: ${request.url}")
                }
            }
        }
        val reader = OnlineImageReaderModel(
            library = library,
            bookUrl = book.bookUrl,
            onlineService = OnlineBookService(library, client),
            httpClient = client,
            cacheDirectory = java.nio.file.Files.createTempDirectory("legado-online-image-cache")
        )

        assertTrue(reader.loadCurrentChapter())
        assertEquals(
            listOf("https://manga.example/images/1.png", "https://cdn.manga.example/2.png"),
            reader.currentChapterPages.map(OnlineImagePage::url)
        )
        assertEquals("first-image", reader.currentPageBytes().decodeToString())
        assertTrue(reader.nextPage())
        assertEquals("second-image", reader.currentPageBytes().decodeToString())
        reader.savePosition()

        assertEquals(0, library.book(book.bookUrl)?.durChapterIndex)
        assertEquals(1, library.book(book.bookUrl)?.durChapterPos)
        assertEquals(3, requests.size)

        assertEquals("second-image", reader.currentPageBytes().decodeToString())
        assertEquals(3, requests.size)
    }

    @Test
    fun acceptsJsonAndLineBasedImageListsWithoutKeepingDuplicateUrls() {
        val urls = OnlineImagePageParser.parse(
            content = """["/page/1.jpg", "/page/2.jpg", "/page/1.jpg"]
https://cdn.example/page/3.jpg""",
            baseUrl = "https://manga.example/chapter"
        )

        assertEquals(
            listOf(
                "https://manga.example/page/1.jpg",
                "https://manga.example/page/2.jpg",
                "https://cdn.example/page/3.jpg"
            ),
            urls
        )
        assertFalse(OnlineImagePageParser.parse("正文内容", "https://manga.example/chapter").isNotEmpty())
    }

    @Test
    fun pageParserKeepsImageAttributesAndRejectsOrdinaryLinks() {
        val urls = OnlineImagePageParser.parse(
            content = """
                <div class="pages">
                  <img data-src="/image/opaque-token">
                  <source src="https://cdn.example/render?id=1">
                  <a href="/chapter/next">下一章</a>
                </div>
            """.trimIndent(),
            baseUrl = "https://manga.example/chapter/1"
        )

        assertEquals(
            listOf(
                "https://manga.example/image/opaque-token",
                "https://cdn.example/render?id=1"
            ),
            urls
        )
    }
}
