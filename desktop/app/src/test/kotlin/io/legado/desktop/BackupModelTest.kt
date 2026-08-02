package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreChapter
import io.legado.core.library.InMemoryCoreLibrary
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupModelTest {

    private lateinit var tempDirectory: Path

    @Before
    fun setUp() {
        tempDirectory = Files.createTempDirectory("legado-backup-model-test")
    }

    @After
    fun tearDown() {
        Files.walk(tempDirectory)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::deleteIfExists)
    }

    @Test
    fun exportWritesBackupToRequestedPathAndImportRestoresContent() {
        val book = CoreBook("book-1", name = "星河", author = "作者")
        val chapter = CoreChapter(book.bookUrl, "chapter-1", "第一章", 0)
        val source = InMemoryCoreLibrary().apply {
            saveBook(book)
            saveChapter(chapter)
            saveContent(chapter, "正文内容")
        }
        val archive = tempDirectory.resolve("library.zip")

        val exported = BackupModel(source).export(archive)
        val target = InMemoryCoreLibrary()
        val imported = BackupModel(target).import(archive)

        assertTrue(exported.isSuccess)
        assertTrue(Files.isRegularFile(archive))
        assertEquals(1, exported.summary?.books)
        assertTrue(imported.isSuccess)
        assertEquals(listOf(book), target.books())
        assertEquals("正文内容", target.content(chapter))
    }

    @Test
    fun invalidBackupReturnsErrorAndLeavesExistingLibraryUntouched() {
        val archive = tempDirectory.resolve("invalid.zip")
        io.legado.core.library.CoreBackupService.writeTestArchive(
            archive,
            mapOf("manifest.json" to "{\"format\":\"invalid\",\"version\":1}")
        )
        val existing = CoreBook("existing", name = "已有书籍")
        val library = InMemoryCoreLibrary().apply { saveBook(existing) }

        val result = BackupModel(library).import(archive)

        assertFalse(result.isSuccess)
        assertNotNull(result.error)
        assertEquals(listOf(existing), library.books())
    }
}
