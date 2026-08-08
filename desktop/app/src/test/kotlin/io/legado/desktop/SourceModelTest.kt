package io.legado.desktop

import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreBookSourceType
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.URI

class SourceModelTest {

    @Test
    fun sourceModelOpensValidatedBrowserVerificationUrl() {
        val opened = mutableListOf<BrowserVerificationRequest>()
        val model = SourceModel(
            InMemoryCoreLibrary(),
            browserLauncher = DesktopBrowserLauncher { request -> opened += request }
        )

        val result = model.openBrowserVerification(
            url = "https://verify.example/login?source=legado",
            sourceUrl = "https://source.example",
            title = "示例源验证"
        )

        assertEquals("https://verify.example/login?source=legado", result.url)
        assertEquals("示例源验证", result.title)
        assertEquals("https://source.example", result.sourceUrl)
        assertEquals(
            listOf(URI("https://verify.example/login?source=legado")),
            opened.map { it.uri }
        )
    }

    @Test
    fun sourceModelOpensEmbeddedBrowserVerificationForTheCurrentSource() {
        val opened = mutableListOf<EmbeddedBrowserVerificationRequest>()
        val source = CoreBookSource(bookSourceUrl = "https://source.example", bookSourceName = "示例源")
        val model = SourceModel(
            InMemoryCoreLibrary().apply { saveSource(source) },
            embeddedBrowserLauncher = DesktopEmbeddedBrowserLauncher { request -> opened += request }
        )

        val request = model.openEmbeddedBrowserVerification(
            url = "https://verify.example/challenge",
            sourceUrl = source.bookSourceUrl,
            title = source.bookSourceName
        )

        assertEquals(request, opened.single())
        assertEquals(source.bookSourceUrl, request.sourceUrl)
    }

    @Test
    fun sourceModelRejectsBrowserVerificationUrlOutsideHttpSchemes() {
        val opened = mutableListOf<BrowserVerificationRequest>()
        val model = SourceModel(
            InMemoryCoreLibrary(),
            browserLauncher = DesktopBrowserLauncher { request -> opened += request }
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            model.openBrowserVerification("file:///verification.html")
        }

        assertTrue(error.message!!.contains("HTTP 或 HTTPS"))
        assertTrue(opened.isEmpty())
    }

    @Test
    fun sourceModelRejectsOverlongBrowserVerificationUrlBeforeLaunching() {
        val opened = mutableListOf<BrowserVerificationRequest>()
        val model = SourceModel(
            InMemoryCoreLibrary(),
            browserLauncher = DesktopBrowserLauncher { request -> opened += request }
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            model.openBrowserVerification("https://verify.example/${"x".repeat(64 * 1024)}")
        }

        assertTrue(error.message!!.contains("过长"))
        assertTrue(opened.isEmpty())
    }

    @Test
    fun sourceModelImportsExportsAndManagesSources() {
        val library = InMemoryCoreLibrary()
        val model = SourceModel(library)
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            bookSourceName = "示例源"
        )

        assertEquals(1, model.importJson("[{\"bookSourceUrl\":\"https://source.example\",\"bookSourceName\":\"示例源\"}]"))
        assertEquals(source, model.sources.single())

        model.setEnabled(source.bookSourceUrl, false)
        assertFalse(model.sources.single().enabled)
        model.delete(source.bookSourceUrl)
        assertTrue(model.sources.isEmpty())
    }

    @Test
    fun sourceModelExportsCurrentSourcesAsJson() {
        val library = InMemoryCoreLibrary()
        val model = SourceModel(library)
        model.save(CoreBookSource(bookSourceUrl = "source-1", bookSourceName = "源一"))

        val imported = SourceModel(InMemoryCoreLibrary())
        assertEquals(1, imported.importJson(model.exportJson()))
        assertEquals("源一", imported.sources.single().bookSourceName)
    }

    @Test
    fun sourceModelSavesCompleteSourceJson() {
        val model = SourceModel(InMemoryCoreLibrary())

        val saved = model.saveJson(
            """
            {
              "bookSourceUrl": "https://source.example",
              "bookSourceName": "完整书源",
              "searchUrl": "https://source.example/search?key={{key}}",
              "ruleSearch": {"bookList":".book","name":".name@text"},
              "jsLib": "function normalize(value) { return value.trim(); }"
            }
            """.trimIndent()
        )

        assertEquals("完整书源", saved.bookSourceName)
        assertEquals("https://source.example", model.sources.single().bookSourceUrl)
        assertTrue(model.sources.single().ruleSearch!!.contains("bookList"))
    }

    @Test
    fun sourceModelRejectsSourceWithoutUrl() {
        val model = SourceModel(InMemoryCoreLibrary())

        assertThrows(IllegalArgumentException::class.java) {
            model.saveJson("{\"bookSourceName\":\"无地址\"}")
        }
    }

    @Test
    fun sourceModelRemovesOldSourceWhenEditingItsUrl() {
        val library = InMemoryCoreLibrary()
        val model = SourceModel(library)
        model.save(CoreBookSource(bookSourceUrl = "https://old.example", bookSourceName = "旧地址"))

        model.saveJson(
            """
            {
              "bookSourceUrl": "https://new.example",
              "bookSourceName": "新地址"
            }
            """.trimIndent(),
            originalBookSourceUrl = "https://old.example"
        )

        assertEquals(listOf("https://new.example"), model.sources.map(CoreBookSource::bookSourceUrl))
        assertEquals(null, library.source("https://old.example"))
    }

    @Test
    fun sourceImportUrlResolvesYueduOnlineImportLink() {
        assertEquals(
            "http://source.example/sources.json",
            SourceImportUrl.resolve(
                "yuedu://rsssource/importonline?src=http%3A%2F%2Fsource.example%2Fsources.json"
            )
        )
    }

    @Test
    fun sourceImportUrlResolvesBooksourceOnlineImportLink() {
        assertEquals(
            "https://source.example/sources.json",
            SourceImportUrl.resolve(
                "yuedu://booksource/importonline?src=https%3A%2F%2Fsource.example%2Fsources.json"
            )
        )
    }

    @Test
    fun sourceImportUrlClassifiesGenericLegadoImportAsNonBookSource() {
        assertEquals(
            SourceImportKind.GENERIC,
            SourceImportUrl.classify("legado://import/auto?src=https%3A%2F%2Fsource.example%2Frules.json")
        )
    }

    @Test
    fun sourceImportUrlResolvesTheUnencodedLinkFormat() {
        assertEquals(
            "http://yuedu.example/sources.json",
            SourceImportUrl.resolve(
                "yuedu://rsssource/importonline?src=http://yuedu.example/sources.json"
            )
        )
    }

    @Test
    fun sourceImportUrlResolvesDirectHttpLink() {
        assertEquals(
            "https://source.example/sources.json",
            SourceImportUrl.resolve("https://source.example/sources.json")
        )
    }

    @Test
    fun sourceImportUrlRejectsUnsupportedLink() {
        assertThrows(IllegalArgumentException::class.java) {
            SourceImportUrl.resolve("file:///sources.json")
        }
    }

    @Test
    fun sourceModelImportsOnlineLegacyRssJson() {
        val library = InMemoryCoreLibrary()
        val requestedUrls = mutableListOf<String>()
        val httpClient = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
                requestedUrls += url
                return CoreHttpResponse(
                    url = url,
                    body = """
                        [{
                          "sourceUrl": "https://rss.example",
                          "sourceName": "在线订阅",
                          "ruleArticles": "item",
                          "ruleTitle": "title@text"
                        }]
                    """.trimIndent()
                )
            }
        }
        val model = SourceModel(library, httpClient = httpClient)

        assertEquals(
            1,
            model.importOnline(
                "yuedu://rsssource/importonline?src=https%3A%2F%2Fsource.example%2Fsources.json"
            )
        )

        assertEquals(listOf("https://source.example/sources.json"), requestedUrls)
        assertEquals(CoreBookSourceType.RSS, model.sources.single().bookSourceType)
        assertEquals("在线订阅", model.sources.single().bookSourceName)
    }

    @Test
    fun sourceModelRejectsFailedOnlineResponse() {
        val httpClient = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                CoreHttpResponse(url = url, body = "not found", statusCode = 404)
        }
        val model = SourceModel(InMemoryCoreLibrary(), httpClient = httpClient)

        val error = assertThrows(IllegalArgumentException::class.java) {
            model.importOnline("https://source.example/missing.json")
        }

        assertTrue(error.message!!.contains("HTTP 404"))
    }
}
