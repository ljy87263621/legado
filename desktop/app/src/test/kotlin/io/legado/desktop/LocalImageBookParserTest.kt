package io.legado.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalImageBookParserTest {

    @Test
    fun cbzFiltersImagesSortsNaturallyAndGroupsDirectoriesIntoChapters() {
        val directory = Files.createTempDirectory("legado-image-parser-test")
        val archive = directory.resolve("comic.cbz")
        try {
            writeZip(
                archive,
                listOf(
                    "page10.png" to byteArrayOf(10),
                    "page2.png" to byteArrayOf(2),
                    "chapter 2/page1.jpg" to byteArrayOf(21),
                    "chapter 2/page11.jpg" to byteArrayOf(22),
                    "chapter 2/notes.txt" to byteArrayOf(0),
                    "chapter 10/page1.png" to byteArrayOf(101)
                )
            )

            val parsed = LocalImageBookParser.parse(archive)

            assertEquals(listOf("正文", "chapter 2", "chapter 10"), parsed.chapters.map { it.title })
            assertEquals(
                listOf("page2.png", "page10.png", "chapter 2/page1.jpg", "chapter 2/page11.jpg", "chapter 10/page1.png"),
                parsed.pages.map { it.entryName }
            )
            assertEquals(listOf(0, 0, 1, 1, 2), parsed.pages.map { it.chapterIndex })
            assertEquals(listOf(0, 1, 0, 1, 0), parsed.pages.map { it.chapterPageIndex })
            assertTrue(parsed.book.type == DesktopBookType.IMAGE)
        } finally {
            deleteTree(directory)
        }
    }

    @Test
    fun aStandaloneImageBecomesOnePageAndOneChapter() {
        val directory = Files.createTempDirectory("legado-image-parser-single-test")
        val image = directory.resolve("cover.png")
        try {
            Files.write(image, byteArrayOf(1, 2, 3))

            val parsed = LocalImageBookParser.parse(image)

            assertEquals("cover", parsed.book.name)
            assertEquals(1, parsed.chapters.size)
            assertEquals(1, parsed.pages.size)
            assertEquals(null, parsed.pages.single().entryName)
            assertEquals(image.toAbsolutePath().normalize(), parsed.pages.single().source)
        } finally {
            deleteTree(directory)
        }
    }

    @Test
    fun anArchiveWithoutImagesIsRejectedWithAnActionableError() {
        val directory = Files.createTempDirectory("legado-image-parser-empty-test")
        val archive = directory.resolve("empty.cbz")
        try {
            writeZip(archive, listOf("readme.txt" to "not an image".toByteArray()))

            val error = runCatching { LocalImageBookParser.parse(archive) }.exceptionOrNull()

            assertTrue(error is IllegalArgumentException)
            assertTrue(error?.message.orEmpty().contains("图片"))
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
