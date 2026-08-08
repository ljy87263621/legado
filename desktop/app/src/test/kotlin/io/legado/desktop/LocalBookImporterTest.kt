package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.InMemoryCoreLibrary
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBookImporterTest {

    @Test
    fun importingAFilePersistsBookChaptersAndContent() {
        val directory = Files.createTempDirectory("legado-import-test")
        val file = directory.resolve("imported.txt")
        Files.writeString(file, "第一章\n章节内容")
        val library = InMemoryCoreLibrary()

        try {
            val book = LocalBookImporter(library).importFile(file)

            val chapter = library.chapters(book.bookUrl).single()
            assertEquals(book, library.book(book.bookUrl))
            assertEquals("章节内容", library.content(chapter))
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun replacingAnImportedTextBookRemovesStaleChaptersAfterParsing() {
        val directory = Files.createTempDirectory("legado-import-refresh-test")
        val file = directory.resolve("refresh.txt")
        Files.writeString(file, "第一章\n新正文")
        val library = InMemoryCoreLibrary()
        val bookUrl = file.toAbsolutePath().normalize().toString()
        val stale = io.legado.core.library.CoreChapter(bookUrl, "$bookUrl#stale", "旧章节", 8)
        library.saveBook(CoreBook(bookUrl, "refresh", origin = "loc_book", originName = "本地文件"))
        library.saveChapter(stale)
        library.saveContent(stale, "旧正文")

        try {
            LocalBookImporter(library).importFile(file, replaceExistingChapters = true)

            val chapters = library.chapters(bookUrl)
            assertEquals(listOf("第一章"), chapters.map(io.legado.core.library.CoreChapter::title))
            assertEquals("新正文", library.content(chapters.single()))
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun importingAnImageBookPersistsImageChaptersWithoutTextContent() {
        val directory = Files.createTempDirectory("legado-image-import-test")
        val file = directory.resolve("comic.cbz")
        try {
            ZipOutputStream(Files.newOutputStream(file)).use { zip ->
                zip.putNextEntry(ZipEntry("page2.png"))
                zip.write(byteArrayOf(2))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("page10.png"))
                zip.write(byteArrayOf(10))
                zip.closeEntry()
            }
            val library = InMemoryCoreLibrary()

            val book = LocalBookImporter(library).importFile(file)

            assertEquals(DesktopBookType.IMAGE, book.type)
            assertEquals(1, library.chapters(book.bookUrl).size)
            assertTrue(library.content(library.chapters(book.bookUrl).single()) == null)
        } finally {
            Files.walk(directory).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun importingAnAudioFilePersistsAPlayableAudioChapter() {
        val directory = Files.createTempDirectory("legado-audio-import-test")
        val file = directory.resolve("chapter.wav")
        try {
            Files.write(file, byteArrayOf(1, 2, 3))
            val library = InMemoryCoreLibrary()

            val book = LocalBookImporter(library).importFile(file)

            assertEquals(DesktopBookType.AUDIO, book.type)
            assertEquals(file.toAbsolutePath().normalize().toString(), library.chapters(book.bookUrl).single().resourceUrl)
            assertTrue(library.content(library.chapters(book.bookUrl).single()) == null)
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }
}
