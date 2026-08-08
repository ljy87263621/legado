package io.legado.desktop

import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopManagementServerModelTest {

    @Test
    fun rejectsInvalidPortWithoutStartingTheServer() {
        val model = DesktopManagementServerModel(InMemoryCoreLibrary(), defaultPort = 0)

        model.updatePort("70000")
        model.start()

        assertFalse(model.isRunning)
        assertEquals("端口必须在 0-65534 之间", model.message)
        assertEquals("", model.endpoint)
        assertEquals("", model.webSocketEndpoint)
    }

    @Test
    fun rejects65535BecauseWebSocketNeedsTheNextPort() {
        val model = DesktopManagementServerModel(InMemoryCoreLibrary(), defaultPort = 0)

        model.updatePort("65535")
        model.start()

        assertFalse(model.isRunning)
        assertEquals("端口必须在 0-65534 之间", model.message)
        assertEquals("", model.endpoint)
    }

    @Test
    fun startsAndStopsLoopbackManagementServer() {
        val model = DesktopManagementServerModel(InMemoryCoreLibrary(), defaultPort = 0)

        model.start()
        try {
            assertTrue(model.isRunning)
            assertTrue(model.endpoint.startsWith("http://127.0.0.1:"))
            assertTrue(model.webSocketEndpoint.startsWith("ws://127.0.0.1:"))
            val httpPort = model.endpoint.substringAfterLast(':').toInt()
            val webSocketPort = model.webSocketEndpoint.substringAfterLast(':').toInt()
            assertEquals(httpPort + 1, webSocketPort)
            assertEquals("本地管理服务已启动", model.message)
        } finally {
            model.stop()
        }

        assertFalse(model.isRunning)
        assertEquals("本地管理服务已停止", model.message)
        assertEquals("", model.endpoint)
        assertEquals("", model.webSocketEndpoint)
    }

    @Test
    fun exposesSupportedReadAndProgressRoutesForSettingsScreen() {
        val model = DesktopManagementServerModel(InMemoryCoreLibrary(), defaultPort = 0)

        assertTrue(model.supportedRoutesDescription.contains("/getBookContent"))
        assertTrue(model.supportedRoutesDescription.contains("GET /getReadConfig"))
        assertTrue(model.supportedRoutesDescription.contains("POST /saveBookProgress"))
        assertTrue(model.supportedRoutesDescription.contains("POST /saveBook"))
        assertTrue(model.supportedRoutesDescription.contains("POST /saveReadConfig"))
        assertTrue(model.supportedRoutesDescription.contains("WebSocket"))
        assertTrue(model.supportedRoutesDescription.contains("/searchBook"))
        assertTrue(model.supportedRoutesDescription.contains("/bookSourceDebug"))
    }
}
