package io.legado.desktop

import io.legado.core.library.CoreBookSource
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpRequest
import io.legado.core.source.CoreHttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedBrowserVerificationModelTest {

    @Test
    fun completedBrowserVerificationPersistsCookiesAndRefetchesWithTheVerifiedSession() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(bookSourceUrl = "https://source.example", bookSourceName = "验证源")
        library.saveSource(source)
        var receivedCookie: String? = null
        val client = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse = request(
                CoreHttpRequest(url = url, headers = headers)
            )

            override fun request(request: CoreHttpRequest): CoreHttpResponse {
                receivedCookie = request.headers["Cookie"]
                return CoreHttpResponse(request.url, "verified body")
            }
        }
        val model = EmbeddedBrowserVerificationModel(
            library = library,
            httpClient = io.legado.core.source.CoreSourceHttpClient(library, client),
            sessionFactory = EmbeddedBrowserVerificationSessionFactory { _, source, _ ->
                object : EmbeddedBrowserVerificationSession {
                    override fun navigate(request: EmbeddedBrowserVerificationRequest) = Unit

                    override fun complete(): EmbeddedBrowserVerificationResult = EmbeddedBrowserVerificationResult(
                        sourceUrl = source.bookSourceUrl,
                        finalUrl = "https://source.example/challenge",
                        html = "browser html",
                        cookies = EmbeddedBrowserCookieParser.parse(
                            java.net.URI("https://source.example/challenge"),
                            "sid=verified"
                        )
                    )

                    override fun close() = Unit
                }
            }
        )

        val result = model.verify(
            EmbeddedBrowserVerificationRequest(
                url = "https://source.example/challenge",
                sourceUrl = source.bookSourceUrl
            )
        )

        assertEquals("verified body", result.html)
        assertEquals("sid=verified", receivedCookie)
        assertEquals("verified", library.cookies().single().value)
    }

    @Test
    fun browserCookieParserKeepsCookieScopeAndPersistenceMetadata() {
        val cookie = EmbeddedBrowserCookieParser.parseSetCookie(
            java.net.URI("https://source.example/challenge/path"),
            "sid=token; Domain=.source.example; Path=/challenge; Max-Age=120"
        )

        requireNotNull(cookie)
        assertEquals("source.example", cookie.domain)
        assertEquals("/challenge", cookie.path)
        assertEquals("token", cookie.value)
        assertTrue(cookie.persistent)
    }
}
