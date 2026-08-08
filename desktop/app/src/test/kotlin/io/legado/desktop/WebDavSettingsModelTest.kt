package io.legado.desktop

import io.legado.core.library.InMemoryCoreLibrary
import io.legado.desktop.persistence.DesktopWebDavConfig
import io.legado.desktop.persistence.DesktopWebDavConfigStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebDavSettingsModelTest {

    private lateinit var tempDirectory: Path

    @Before
    fun setUp() {
        tempDirectory = Files.createTempDirectory("legado-webdav-settings-test")
    }

    @After
    fun tearDown() {
        Files.walk(tempDirectory)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::deleteIfExists)
    }

    @Test
    fun configureLoadsRemoteBackupsAndSelectsOneForRestore() {
        val client = FakeWebDavClient()
        val settings = WebDavSettingsModel(
            WebDavBackupModel(
                InMemoryCoreLibrary(),
                client = client,
                store = MemoryWebDavConfigStore()
            )
        )

        settings.updateUrl("https://dav.example/legado")
        settings.updateUsername("reader")
        settings.updatePassword("secret")

        assertTrue(settings.configure().isSuccess)
        assertEquals(listOf("backup-old.zip"), settings.state.backups.map(WebDavBackupFile::name))

        settings.selectBackup("backup-old.zip")
        assertEquals("backup-old.zip", settings.state.selectedBackupName)
    }

    @Test
    fun uploadRefreshesRemoteBackupsForTheSettingsView() {
        val client = FakeWebDavClient()
        val settings = WebDavSettingsModel(
            WebDavBackupModel(
                InMemoryCoreLibrary(),
                client = client,
                store = MemoryWebDavConfigStore()
            )
        )
        settings.updateUrl("https://dav.example/legado")
        settings.updateUsername("reader")
        settings.updatePassword("secret")
        assertTrue(settings.configure().isSuccess)
        settings.updateRemoteName("backup-new.zip")
        val archive = tempDirectory.resolve("local.zip")
        Files.write(archive, byteArrayOf(1, 2, 3))

        assertTrue(settings.upload(archive).isSuccess)
        assertEquals(
            listOf("backup-old.zip", "backup-new.zip"),
            settings.state.backups.map(WebDavBackupFile::name)
        )
    }

    @Test
    fun remoteBookActionsExposeTheRemoteLibraryAndDownloadItIntoTheDesktopLibrary() {
        val client = FakeWebDavClient().apply {
            put(DesktopWebDavConfig("https://dav.example/", "reader", "secret"), "books/remote.txt", "第一章\n远端正文".toByteArray())
        }
        val library = InMemoryCoreLibrary()
        val remoteModel = WebDavRemoteBookModel(
            library = library,
            client = client,
            store = MemoryWebDavConfigStore(),
            downloadDirectory = tempDirectory.resolve("downloads")
        )
        val settings = WebDavSettingsModel(
            backupModel = WebDavBackupModel(
                library,
                client = client,
                store = MemoryWebDavConfigStore()
            ),
            remoteBookModel = remoteModel
        )
        settings.updateUrl("https://dav.example/")
        settings.updateUsername("reader")
        settings.updatePassword("secret")

        assertTrue(settings.configure().isSuccess)
        assertTrue(settings.refreshRemoteBooks().isSuccess)
        assertEquals(listOf("remote.txt"), settings.state.remoteBooks.map(WebDavRemoteBook::name))
        settings.selectRemoteBook("remote.txt")

        assertTrue(settings.downloadSelectedRemoteBook().isSuccess)
        assertEquals(1, library.books().size)
        val chapter = library.chapters(library.books().single().bookUrl).single()
        assertEquals("第一章", chapter.title)
        assertEquals("远端正文", library.content(chapter))
    }

    private class MemoryWebDavConfigStore : DesktopWebDavConfigStore {
        private var config: DesktopWebDavConfig? = null

        override fun webDavConfig(): DesktopWebDavConfig? = config

        override fun saveWebDavConfig(config: DesktopWebDavConfig) {
            this.config = config
        }
    }

    private class FakeWebDavClient : WebDavClient {
        private val files = linkedMapOf("backup-old.zip" to byteArrayOf(1))

        override fun list(config: DesktopWebDavConfig): List<WebDavBackupFile> = files.map { (name, content) ->
            WebDavBackupFile(name, content.size.toLong())
        }

        override fun put(config: DesktopWebDavConfig, name: String, content: ByteArray) {
            files[name] = content
        }

        override fun get(config: DesktopWebDavConfig, name: String): ByteArray = files.getValue(
            if (config.url.endsWith("/books/")) "books/$name" else name
        )

        override fun listDirectory(config: DesktopWebDavConfig): List<WebDavBackupFile> = files.map { (name, content) ->
            WebDavBackupFile(name.substringAfterLast('/'), content.size.toLong())
        }
    }
}
