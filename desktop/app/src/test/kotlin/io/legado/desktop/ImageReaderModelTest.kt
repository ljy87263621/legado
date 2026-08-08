package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.InMemoryCoreLibrary
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageReaderModelTest {

    @Test
    fun imageReaderNavigatesPagesAcrossChaptersAndPersistsTheCurrentPage() {
        val directory = Files.createTempDirectory("legado-image-reader-test")
        val archive = directory.resolve("comic.cbz")
        try {
            writeZip(
                archive,
                listOf(
                    "chapter 1/page1.png" to byteArrayOf(1),
                    "chapter 1/page2.png" to byteArrayOf(2),
                    "chapter 2/page1.png" to byteArrayOf(3)
                )
            )
            val library = InMemoryCoreLibrary()
            val book = CoreBook(
                bookUrl = archive.toString(),
                name = "comic",
                type = DesktopBookType.IMAGE,
                origin = "loc_book"
            )
            library.saveBook(book)

            val reader = ImageReaderModel(library, book.bookUrl, startPageIndex = 1)

            assertEquals(1, reader.currentPageIndex)
            assertEquals("chapter 1", reader.currentChapterTitle)
            assertTrue(reader.hasPrevious)
            assertTrue(reader.hasNext)
            assertTrue(reader.nextPage())
            assertEquals(2, reader.currentPageIndex)
            assertEquals("chapter 2", reader.currentChapterTitle)
            assertFalse(reader.nextPage())
            assertTrue(reader.previousPage())
            reader.savePosition()

            assertEquals(0, library.book(book.bookUrl)?.durChapterIndex)
            assertEquals(1, library.book(book.bookUrl)?.durChapterPos)
        } finally {
            deleteTree(directory)
        }
    }

    @Test
    fun zoomIsBoundedAndCanBeReset() {
        val directory = Files.createTempDirectory("legado-image-reader-zoom-test")
        val image = directory.resolve("page.png")
        try {
            Files.write(image, byteArrayOf(1, 2, 3))
            val library = InMemoryCoreLibrary()
            library.saveBook(
                CoreBook(
                    bookUrl = image.toString(),
                    name = "page",
                    type = DesktopBookType.IMAGE,
                    origin = "loc_book"
                )
            )
            val reader = ImageReaderModel(library, image.toString())

            reader.setZoom(100f)
            assertEquals(ImageReaderModel.MAX_ZOOM, reader.zoom)
            reader.setZoom(0f)
            assertEquals(ImageReaderModel.MIN_ZOOM, reader.zoom)
            reader.resetZoom()
            assertEquals(1f, reader.zoom)
        } finally {
            deleteTree(directory)
        }
    }

    private fun writeZip(path: Path, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun deleteTree(path: Path) {
        Files.walk(path).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }
}
