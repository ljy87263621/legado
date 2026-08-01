package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.InMemoryCoreLibrary
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalBookImporterTest {

    @Test
    fun importingAFilePersistsBookChaptersAndContent() {
        val directory = Files.createTempDirectory("legado-import-test")
        val file = directory.resolve("imported.txt")
        Files.writeString(file, "第一章\n正文")
        val library = InMemoryCoreLibrary()

        try {
            val book = LocalBookImporter(library).importFile(file)

            val chapter = library.chapters(book.bookUrl).single()
            assertEquals(book, library.book(book.bookUrl))
            assertEquals("正文", library.content(chapter))
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }
}
