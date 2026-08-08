package io.legado.desktop

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import io.legado.core.library.CoreBookSource
import io.legado.core.library.InMemoryCoreLibrary
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopManagementWebSocketTest {

    @Test
    fun exposesAndroidCompatibleSearchWebSocketAndClosesAfterResults() {
        val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        upstream.createContext("/search") { exchange ->
            val body = """
                {"books":[{"name":"测试书","author":"作者","url":"https://source.test/book/1","intro":"简介"}]}
            """.trimIndent()
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        upstream.start()
        val library = InMemoryCoreLibrary().apply {
            saveSource(
                CoreBookSource(
                    bookSourceUrl = "https://source.test",
                    bookSourceName = "测试书源",
                    searchUrl = "http://127.0.0.1:${upstream.address.port}/search?key={{key}}",
                    ruleSearch = """
                        {"bookList":"$.books","name":"name","author":"author","bookUrl":"url","intro":"intro"}
                    """.trimIndent()
                )
            )
        }
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val session = open(server.webSocketEndpoint + "/searchBook")
            session.socket.sendText("{\"key\":\"测试\"}", true).join()
            val closeCode = session.closed.get(5, TimeUnit.SECONDS)

            assertEquals(1000, closeCode)
            val payload = JsonParser.parseString(
                session.messages.first { it.trimStart().startsWith("[") }
            ).asJsonArray.first().asJsonObject
            assertEquals("测试书", payload.get("name").asString)
            assertEquals("作者", payload.get("author").asString)
            assertEquals("测试书源", payload.get("originName").asString)
            assertTrue(payload.has("bookUrl"))
            assertTrue(payload.has("origin"))
            assertTrue(!payload.has("book"))
        } finally {
            server.close()
            upstream.stop(0)
        }
    }

    @Test
    fun rejectsMalformedSearchMessageAndClosesTheConnection() {
        val server = DesktopManagementServer(InMemoryCoreLibrary(), port = 0)
        server.start()
        try {
            val session = open(server.webSocketEndpoint + "/searchBook")
            session.socket.sendText("not-json", true).join()

            assertEquals(1000, session.closed.get(5, TimeUnit.SECONDS))
            assertTrue(session.messages.any { it.contains("Json") })
        } finally {
            server.close()
        }
    }

    @Test
    fun streamsSourceDebugRequestMetadataAndClosesAfterTheResponse() {
        val receivedMethod = AtomicReference<String>()
        val receivedBody = AtomicReference<String>()
        val receivedHeader = AtomicReference<String>()
        val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        upstream.createContext("/search") { exchange ->
            receivedMethod.set(exchange.requestMethod)
            receivedBody.set(exchange.requestBody.use { it.readBytes().toString(Charsets.UTF_8) })
            receivedHeader.set(exchange.requestHeaders.getFirst("X-Option"))
            val body = "debug-body"
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        upstream.start()
        val source = CoreBookSource(
            bookSourceUrl = "https://source.test",
            bookSourceName = "测试书源",
            searchUrl = "http://127.0.0.1:${upstream.address.port}/search?key={{key}}," +
                "{\"method\":\"POST\",\"body\":\"query={{key}}\",\"headers\":{\"X-Option\":\"yes\"}}"
        )
        val library = InMemoryCoreLibrary().apply { saveSource(source) }
        val server = DesktopManagementServer(library, port = 0)

        server.start()
        try {
            val session = open(server.webSocketEndpoint + "/bookSourceDebug")
            session.socket.sendText(
                Gson().toJson(mapOf("tag" to source.bookSourceUrl, "key" to "测试")),
                true
            ).join()

            assertEquals(1000, session.closed.get(5, TimeUnit.SECONDS))
            assertEquals("POST", receivedMethod.get())
            assertEquals("query=%E6%B5%8B%E8%AF%95", receivedBody.get())
            assertEquals("yes", receivedHeader.get())
            assertTrue(session.messages.any { it.contains("测试书源") })
            assertTrue(session.messages.any { it.contains("请求方法：POST") })
            assertTrue(session.contents().any { it == "请求 body：query=%E6%B5%8B%E8%AF%95" })
            assertTrue(session.contents().any { it.contains("X-Option: yes") })
            assertTrue(session.contents().any { it == "debug-body" })
        } finally {
            server.close()
            upstream.stop(0)
        }
    }

    private fun open(uri: String): Session {
        val messages = CopyOnWriteArrayList<String>()
        val closed = CompletableFuture<Int>()
        val listener = object : WebSocket.Listener {
            override fun onOpen(webSocket: WebSocket) {
                webSocket.request(1)
            }

            override fun onText(
                webSocket: WebSocket,
                data: CharSequence,
                last: Boolean
            ): CompletableFuture<*> {
                messages += data.toString()
                webSocket.request(1)
                return CompletableFuture.completedFuture(null)
            }

            override fun onClose(
                webSocket: WebSocket,
                statusCode: Int,
                reason: String
            ): CompletableFuture<*> {
                closed.complete(statusCode)
                return CompletableFuture.completedFuture(null)
            }

            override fun onError(webSocket: WebSocket, error: Throwable) {
                closed.completeExceptionally(error)
            }
        }
        val socket = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build()
            .newWebSocketBuilder()
            .buildAsync(URI.create(uri), listener)
            .join()
        return Session(socket, messages, closed)
    }

    private fun Session.contents(): List<String> = messages.mapNotNull { message ->
        runCatching {
            JsonParser.parseString(message).asJsonObject["content"]?.asString
        }.getOrNull()
    }

    private data class Session(
        val socket: WebSocket,
        val messages: List<String>,
        val closed: CompletableFuture<Int>
    )
}
