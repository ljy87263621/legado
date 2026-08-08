package io.legado.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.legado.core.library.CoreBook
import io.legado.core.library.CoreChapter
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.desktop.persistence.DesktopWebDavConfig
import io.legado.desktop.persistence.DesktopWebDavConfigStore
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.Comparator
import java.util.concurrent.ConcurrentHashMap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebDavBackupModelTest {

    private lateinit var tempDirectory: Path
    private lateinit var server: FakeWebDavServer

    @Before
    fun setUp() {
        tempDirectory = Files.createTempDirectory("legado-webdav-test")
        server = FakeWebDavServer("dav-user", "dav-password").also(FakeWebDavServer::start)
    }

    @After
    fun tearDown() {
        server.close()
        Files.walk(tempDirectory)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::deleteIfExists)
    }

    @Test
    fun configNormalizesUrlAndSurvivesStoreRoundTrip() {
        val store = MemoryWebDavConfigStore()
        val model = WebDavBackupModel(InMemoryCoreLibrary(), store = store)

        val result = model.configure(
            url = server.url.removeSuffix("/"),
            username = "dav-user",
            password = "dav-password"
        )

        assertTrue(result.error ?: "WebDAV 配置失败", result.isSuccess)
        assertEquals(server.url, store.config?.url)
        assertEquals(store.config, model.config)
    }

    @Test
    fun uploadsListsAndRestoresBackupThroughWebDav() {
        val book = CoreBook("book-1", name = "远端书", author = "作者")
        val chapter = CoreChapter(book.bookUrl, "chapter-1", "第一章", 0)
        val source = InMemoryCoreLibrary().apply {
            saveBook(book)
            saveChapter(chapter)
            saveContent(chapter, "从 WebDAV 恢复的正文")
        }
        val archive = tempDirectory.resolve("local-backup.zip")
        assertTrue(BackupModel(source).export(archive).isSuccess)

        val target = InMemoryCoreLibrary()
        val model = WebDavBackupModel(target, store = MemoryWebDavConfigStore()).apply {
            val result = configure(server.url, "dav-user", "dav-password")
            assertTrue(result.error ?: "WebDAV 配置失败", result.isSuccess)
        }

        assertTrue(model.upload(archive, "backup-2026-08-05.zip").isSuccess)
        server.put("notes.txt", "ignore".toByteArray(StandardCharsets.UTF_8))

        val backups = model.listBackups().getOrThrow()
        assertEquals(
            listOf("backup-existing.zip", "backup-2026-08-05.zip"),
            backups.map { it.name }
        )

        val restored = model.restore("backup-2026-08-05.zip")
        assertTrue(restored.isSuccess)
        assertEquals(listOf(book), target.books())
        assertEquals("从 WebDAV 恢复的正文", target.content(chapter))
    }

    @Test
    fun rejectsUnsafeRemoteNamesAndAuthenticationFailures() {
        val model = WebDavBackupModel(InMemoryCoreLibrary(), store = MemoryWebDavConfigStore()).apply {
            assertTrue(configure(server.url, "dav-user", "dav-password").isSuccess)
        }
        val archive = tempDirectory.resolve("backup-local.zip")
        Files.write(archive, byteArrayOf(1, 2, 3))

        assertFalse(model.upload(archive, "../escape.zip").isSuccess)

        val unauthorized = WebDavBackupModel(
            InMemoryCoreLibrary(),
            store = MemoryWebDavConfigStore()
        ).apply {
            val result = configure(server.url, "wrong", "password")
            assertFalse(result.isSuccess)
        }
        assertFalse(unauthorized.listBackups().isSuccess)
    }

    @Test
    fun listsUploadsAndDownloadsSupportedRemoteBooks() {
        val store = MemoryWebDavConfigStore()
        val model = WebDavRemoteBookModel(
            library = InMemoryCoreLibrary(),
            client = JavaNetWebDavClient(),
            store = store,
            downloadDirectory = tempDirectory.resolve("downloads")
        )
        server.put("books/existing.epub", byteArrayOf(1, 2, 3))
        server.put("books/ignore.pdf", byteArrayOf(4))
        val localBook = tempDirectory.resolve("uploaded.txt")
        Files.writeString(localBook, "第一章\n远端正文")

        assertTrue(
            model.configure(
                url = server.url,
                username = "dav-user",
                password = "dav-password",
                bookDirectory = "books"
            ).isSuccess
        )
        assertEquals(listOf("existing.epub"), model.listBooks().getOrThrow().map(WebDavRemoteBook::name))

        assertTrue(model.upload(localBook).isSuccess)
        assertEquals(
            listOf("existing.epub", "uploaded.txt"),
            model.listBooks().getOrThrow().map(WebDavRemoteBook::name)
        )

        val downloaded = model.download(WebDavRemoteBook("uploaded.txt", "books/uploaded.txt", Files.size(localBook)))
            .getOrThrow()
        assertEquals("第一章\n远端正文", Files.readString(downloaded))
    }

    @Test
    fun deletesRemoteBookThroughTheWebDavDeleteMethod() {
        val model = WebDavRemoteBookModel(
            library = InMemoryCoreLibrary(),
            client = JavaNetWebDavClient(),
            store = MemoryWebDavConfigStore(),
            downloadDirectory = tempDirectory.resolve("downloads")
        )
        server.put("books/remove.txt", "第一章\n待删除".toByteArray(StandardCharsets.UTF_8))

        assertTrue(model.configure(server.url, "dav-user", "dav-password", "books").isSuccess)
        val book = model.listBooks().getOrThrow().single()

        val deletion = model.delete(book)
        assertTrue(deletion.error ?: "WebDAV 删除失败", deletion.isSuccess)
        assertTrue(model.listBooks().getOrThrow().isEmpty())
    }

    private class MemoryWebDavConfigStore : DesktopWebDavConfigStore {
        var config: DesktopWebDavConfig? = null

        override fun webDavConfig(): DesktopWebDavConfig? = config

        override fun saveWebDavConfig(config: DesktopWebDavConfig) {
            this.config = config
        }
    }

    private class FakeWebDavServer(
        private val username: String,
        private val password: String
    ) : AutoCloseable {
        private val files = ConcurrentHashMap<String, ByteArray>()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        val url: String
            get() = "http://127.0.0.1:${server.address.port}/dav/"

        init {
            server.createContext("/dav") { exchange -> handle(exchange) }
            files["backup-existing.zip"] = byteArrayOf(9)
        }

        fun start() {
            server.start()
        }

        fun put(name: String, content: ByteArray) {
            files[name] = content
        }

        override fun close() {
            server.stop(0)
        }

        private fun handle(exchange: HttpExchange) {
            exchange.use {
                val authorization = exchange.requestHeaders.getFirst("Authorization")
                val expected = "Basic " + Base64.getEncoder().encodeToString(
                    "$username:$password".toByteArray(StandardCharsets.UTF_8)
                )
                if (authorization != expected) {
                    respond(exchange, 401, ByteArray(0), "")
                    return
                }
                when (exchange.requestMethod) {
                    "OPTIONS" -> respond(exchange, 200, ByteArray(0), "")
                    "PROPFIND" -> respond(exchange, 207, propfindResponse(), "application/xml")
                    "PUT" -> {
                        val name = exchange.requestURI.path.substringAfterLast('/')
                        files[name] = exchange.requestBody.readBytes()
                        respond(exchange, 201, ByteArray(0), "")
                    }
                    "GET" -> {
                        val name = exchange.requestURI.path.substringAfterLast('/')
                        files[name]?.let { respond(exchange, 200, it, "application/zip") }
                            ?: respond(exchange, 404, ByteArray(0), "")
                    }
                    "DELETE" -> {
                        val name = exchange.requestURI.path.substringAfter("/dav/").trimStart('/')
                        if (files.remove(name) != null) {
                            respond(exchange, 204, ByteArray(0), "")
                        } else {
                            respond(exchange, 404, ByteArray(0), "")
                        }
                    }
                    else -> respond(exchange, 405, ByteArray(0), "")
                }
            }
        }

        private fun propfindResponse(): ByteArray {
            val body = buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                append("<d:multistatus xmlns:d=\"DAV:\">")
                files.keys.sorted().forEach { name ->
                    append("<d:response><d:href>/dav/$name</d:href>")
                    append("<d:propstat><d:prop><d:displayname>$name</d:displayname>")
                    append("<d:getcontentlength>${files[name]?.size ?: 0}</d:getcontentlength>")
                    append("</d:prop></d:propstat></d:response>")
                }
                append("</d:multistatus>")
            }
            return body.toByteArray(StandardCharsets.UTF_8)
        }

        private fun respond(exchange: HttpExchange, status: Int, body: ByteArray, contentType: String) {
            if (contentType.isNotBlank()) exchange.responseHeaders.set("Content-Type", contentType)
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }
}
