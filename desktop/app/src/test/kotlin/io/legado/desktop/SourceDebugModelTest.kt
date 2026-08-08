package io.legado.desktop

import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreCookie
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.core.source.BookSourceJsonCodec
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import io.legado.core.source.CoreHttpRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceDebugModelTest {

    @Test
    fun sendsManualHeadersAndReportsSuccessfulResponse() {
        var receivedHeaders: Map<String, String> = emptyMap()
        val client = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
                receivedHeaders = headers
                return CoreHttpResponse(
                    url = "https://source.example/result",
                    body = "response body",
                    statusCode = 200,
                    headers = mapOf("Content-Type" to listOf("text/plain"))
                )
            }
        }

        val result = SourceDebugModel(client).execute(
            SourceDebugRequest(
                url = "https://source.example/request",
                headersText = """{"X-Test":"manual-value"}"""
            )
        )

        assertEquals("manual-value", receivedHeaders["X-Test"])
        assertEquals("https://source.example/result", result.finalUrl)
        assertEquals(200, result.statusCode)
        assertEquals("response body", result.body)
        assertEquals(listOf("text/plain"), result.responseHeaders["Content-Type"])
        assertNull(result.error)
    }

    @Test
    fun resolvesSourceHeadersBeforeSendingTheRequest() {
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            header = "@js:JSON.stringify({ 'X-Token': makeToken() })",
            jsLib = "function makeToken() { return 'token-from-js'; }"
        )
        var receivedHeaders: Map<String, String> = emptyMap()
        val client = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
                receivedHeaders = headers
                return CoreHttpResponse(url = url, body = "ok")
            }
        }

        SourceDebugModel(client).execute(
            SourceDebugRequest(
                url = "https://source.example/page",
                sourceJson = BookSourceJsonCodec.encode(source)
            )
        )

        assertEquals("token-from-js", receivedHeaders["X-Token"])
        assertEquals("Legado/Windows", receivedHeaders["User-Agent"])
    }

    @Test
    fun preservesFailedHttpResponseForInspection() {
        val client = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                CoreHttpResponse(
                    url = "https://source.example/error",
                    body = "server error",
                    statusCode = 503,
                    headers = mapOf("Retry-After" to listOf("30"))
                )
        }

        val result = SourceDebugModel(client).execute(
            SourceDebugRequest("https://source.example/request")
        )

        assertEquals(503, result.statusCode)
        assertEquals("server error", result.body)
        assertEquals(listOf("30"), result.responseHeaders["Retry-After"])
        assertEquals("HTTP 503", result.error)
    }

    @Test
    fun reportsTransportFailureAsInspectableError() {
        val client = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                error("connection refused")
        }

        val result = SourceDebugModel(client).execute(
            SourceDebugRequest("https://source.example/request")
        )

        assertEquals("connection refused", result.error)
        assertNull(result.statusCode)
        assertEquals("", result.body)
    }

    @Test
    fun sourceDebugUsesPersistentCookiesWhenLibraryIsProvided() {
        val source = CoreBookSource(bookSourceUrl = "https://source.example")
        val library = InMemoryCoreLibrary().apply {
            saveCookie(
                CoreCookie(
                    domain = "source.example",
                    path = "/",
                    name = "sid",
                    value = "stored",
                    persistent = true
                )
            )
        }
        var receivedHeaders: Map<String, String> = emptyMap()
        val client = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
                receivedHeaders = headers
                return CoreHttpResponse(url = url, body = "ok")
            }
        }

        SourceDebugModel(httpClient = client, library = library).execute(
            SourceDebugRequest(
                url = "https://source.example/page",
                sourceJson = BookSourceJsonCodec.encode(source)
            )
        )

        assertEquals("sid=stored", receivedHeaders["Cookie"])
    }

    @Test
    fun resolvesUrlOptionsAndSendsTheCompleteHttpRequest() {
        var receivedRequest: CoreHttpRequest? = null
        val client = object : CoreHttpClient {
            override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                error("legacy get must not be used")

            override fun request(request: CoreHttpRequest): CoreHttpResponse {
                receivedRequest = request
                return CoreHttpResponse(request.url, "debug response")
            }
        }
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            header = "{\"X-Token\":\"source-token\"}"
        )

        val result = SourceDebugModel(client).execute(
            SourceDebugRequest(
                url = "https://source.example/api?x=value," +
                    "{\"method\":\"POST\",\"body\":\"query=test\",\"headers\":{\"X-Token\":\"url-token\",\"X-Option\":\"yes\"}}",
                headersText = "{\"x-token\":\"manual-token\"}",
                sourceJson = BookSourceJsonCodec.encode(source)
            )
        )

        assertEquals("https://source.example/api?x=value", receivedRequest?.url)
        assertEquals("POST", receivedRequest?.method)
        assertEquals("query=test", receivedRequest?.body)
        assertEquals("manual-token", receivedRequest?.headers?.get("x-token"))
        assertEquals("yes", receivedRequest?.headers?.get("X-Option"))
        assertEquals("https://source.example/api?x=value", result.requestUrl)
        assertEquals("POST", result.requestMethod)
        assertEquals("debug response", result.body)
    }
}
