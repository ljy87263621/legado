package io.legado.core.source

import io.legado.core.library.CoreBookSource
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreSourceHttpClientTest {

    @Test
    fun persistentCookiesAreInjectedAndUpdatedForTheSameSourceDomain() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(bookSourceUrl = "https://books.example")
        val delegate = RecordingHttpClient(
            responses = ArrayDeque(
                listOf(
                    CoreHttpResponse(
                        url = "https://books.example/login",
                        body = "ok",
                        headers = mapOf("Set-Cookie" to listOf("sid=first; Path=/; Max-Age=3600"))
                    ),
                    CoreHttpResponse(
                        url = "https://books.example/books",
                        body = "ok",
                        headers = mapOf("Set-Cookie" to listOf("sid=second; Path=/; Max-Age=3600"))
                    )
                )
            )
        )
        val client = CoreSourceHttpClient(library, delegate)

        client.get(source, "https://books.example/login")
        client.get(source, "https://books.example/books")

        assertFalse(delegate.receivedHeaders[0].containsKey("Cookie"))
        assertEquals("sid=first", delegate.receivedHeaders[1]["Cookie"])
        assertEquals("second", library.cookies().single().value)
        assertTrue(library.cookies().single().persistent)
    }

    @Test
    fun sessionCookiesAreAvailableUntilTheClientIsRecreatedButAreNotPersisted() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(bookSourceUrl = "https://books.example")
        val firstDelegate = RecordingHttpClient(
            responses = ArrayDeque(
                listOf(
                    CoreHttpResponse(
                        url = "https://books.example/login",
                        body = "ok",
                        headers = mapOf("Set-Cookie" to listOf("sid=session; Path=/"))
                    ),
                    CoreHttpResponse(url = "https://books.example/books", body = "ok")
                )
            )
        )
        val firstClient = CoreSourceHttpClient(library, firstDelegate)

        firstClient.get(source, "https://books.example/login")
        firstClient.get(source, "https://books.example/books")

        val secondDelegate = RecordingHttpClient(
            responses = ArrayDeque(listOf(CoreHttpResponse(url = "https://books.example/books", body = "ok")))
        )
        CoreSourceHttpClient(library, secondDelegate).get(source, "https://books.example/books")

        assertEquals("sid=session", firstDelegate.receivedHeaders[1]["Cookie"])
        assertFalse(secondDelegate.receivedHeaders[0].containsKey("Cookie"))
        assertTrue(library.cookies().isEmpty())
    }

    @Test
    fun disabledCookieJarDoesNotInjectOrPersistCookies() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://books.example",
            enabledCookieJar = false
        )
        val delegate = RecordingHttpClient(
            responses = ArrayDeque(
                listOf(
                    CoreHttpResponse(
                        url = "https://books.example/login",
                        body = "ok",
                        headers = mapOf("Set-Cookie" to listOf("sid=ignored; Path=/; Max-Age=3600"))
                    ),
                    CoreHttpResponse(url = "https://books.example/books", body = "ok")
                )
            )
        )
        val client = CoreSourceHttpClient(library, delegate)

        client.get(source, "https://books.example/login")
        client.get(source, "https://books.example/books")

        assertFalse(delegate.receivedHeaders[1].containsKey("Cookie"))
        assertTrue(library.cookies().isEmpty())
    }

    @Test
    fun cookiesRespectDomainAndPathBoundaries() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(bookSourceUrl = "https://books.example")
        val delegate = RecordingHttpClient(
            responses = ArrayDeque(
                listOf(
                    CoreHttpResponse(
                        url = "https://books.example/login",
                        body = "ok",
                        headers = mapOf(
                            "Set-Cookie" to listOf(
                                "root=root; Domain=.example; Path=/",
                                "book=book; Domain=books.example; Path=/books"
                            )
                        )
                    ),
                    CoreHttpResponse(url = "https://api.example/outside", body = "ok"),
                    CoreHttpResponse(url = "https://books.example/books/1", body = "ok")
                )
            )
        )
        val client = CoreSourceHttpClient(library, delegate)

        client.get(source, "https://books.example/login")
        client.get(source, "https://api.example/outside")
        client.get(source, "https://books.example/books/1")

        assertFalse(delegate.receivedHeaders[1].containsKey("Cookie"))
        assertEquals("root=root; book=book", delegate.receivedHeaders[2]["Cookie"])
    }

    @Test
    fun sessionCookieReplacesPersistentCookieWithTheSameKey() {
        val library = InMemoryCoreLibrary()
        library.saveCookie(
            io.legado.core.library.CoreCookie(
                domain = "books.example",
                path = "/",
                name = "sid",
                value = "persistent",
                persistent = true,
                expiresAt = System.currentTimeMillis() + 60_000
            )
        )
        val source = CoreBookSource(bookSourceUrl = "https://books.example")
        val delegate = RecordingHttpClient(
            responses = ArrayDeque(
                listOf(
                    CoreHttpResponse(
                        url = "https://books.example/login",
                        body = "ok",
                        headers = mapOf("Set-Cookie" to listOf("sid=session; Path=/"))
                    ),
                    CoreHttpResponse(url = "https://books.example/books", body = "ok")
                )
            )
        )
        val client = CoreSourceHttpClient(library, delegate)

        client.get(source, "https://books.example/login")
        client.get(source, "https://books.example/books")

        assertEquals("sid=session", delegate.receivedHeaders[1]["Cookie"])
        assertTrue(library.cookies().isEmpty())
    }

    @Test
    fun cookieWithDomainUnrelatedToResponseHostIsIgnored() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(bookSourceUrl = "https://books.example")
        val delegate = FixedResponseHttpClient(
            CoreHttpResponse(
                url = "https://other.example/login",
                body = "ok",
                headers = mapOf("Set-Cookie" to listOf("sid=invalid; Domain=books.example; Path=/"))
            )
        )
        val client = CoreSourceHttpClient(library, delegate)

        client.get(source, "https://books.example/login")

        assertTrue(library.cookies().isEmpty())
    }

    @Test
    fun sourceAwareRequestPreservesMethodBodyAndCookieHandling() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(bookSourceUrl = "https://books.example")
        val delegate = RequestRecordingHttpClient(
            responses = ArrayDeque(
                listOf(
                    CoreHttpResponse(
                        url = "https://books.example/login",
                        body = "ok",
                        headers = mapOf("Set-Cookie" to listOf("sid=first; Path=/"))
                    ),
                    CoreHttpResponse(url = "https://books.example/search", body = "ok")
                )
            )
        )
        val client = CoreSourceHttpClient(library, delegate)

        client.request(source, CoreHttpRequest("https://books.example/login", method = "POST", body = "user=1"))
        client.request(source, CoreHttpRequest("https://books.example/search", method = "POST", body = "query=2"))

        assertEquals("POST", delegate.requests[0].method)
        assertEquals("user=1", delegate.requests[0].body)
        assertEquals("sid=first", delegate.requests[1].headers["Cookie"])
        assertEquals("query=2", delegate.requests[1].body)
    }

    private class RecordingHttpClient(
        private val responses: ArrayDeque<CoreHttpResponse>
    ) : CoreHttpClient {
        val receivedHeaders = mutableListOf<Map<String, String>>()

        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
            receivedHeaders += headers
            return responses.removeFirst().copy(url = url)
        }
    }

    private class FixedResponseHttpClient(
        private val response: CoreHttpResponse
    ) : CoreHttpClient {
        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse = response
    }

    private class RequestRecordingHttpClient(
        private val responses: ArrayDeque<CoreHttpResponse>
    ) : CoreHttpClient {
        val requests = mutableListOf<CoreHttpRequest>()

        override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
            error("source-aware request must preserve CoreHttpRequest")

        override fun request(request: CoreHttpRequest): CoreHttpResponse {
            requests += request
            return responses.removeFirst()
        }
    }
}
