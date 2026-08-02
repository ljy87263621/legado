package io.legado.core.library

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CoreBackupServiceTest {

    private lateinit var tempDirectory: Path

    @Before
    fun setUp() {
        tempDirectory = Files.createTempDirectory("legado-backup-test")
    }

    @After
    fun tearDown() {
        Files.walk(tempDirectory)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::deleteIfExists)
    }

    @Test
    fun exportAndImportRoundTripPreservesReaderLibraryData() {
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            bookSourceName = "示例书源",
            ruleContent = "{\"content\":\".content\"}"
        )
        val book = CoreBook(
            bookUrl = "https://source.example/book/1",
            name = "星河",
            author = "甲作者",
            origin = source.bookSourceUrl,
            originName = source.bookSourceName,
            group = 7L,
            durChapterIndex = 1,
            durChapterPos = 12,
            durChapterTitle = "第二章",
            readConfigJson = "{\"useReplaceRule\":true}"
        )
        val first = CoreChapter(book.bookUrl, "chapter-1", "第一章", 0)
        val second = CoreChapter(book.bookUrl, "chapter-2", "第二章", 1)
        val group = CoreBookGroup(groupId = 7L, groupName = "重点", order = 2)
        val bookmark = CoreBookmark(
            time = 100L,
            bookName = book.name,
            bookAuthor = book.author,
            chapterIndex = 1,
            chapterPos = 12,
            chapterName = second.title,
            bookText = "第二章正文",
            content = "重点段落"
        )
        val record = CoreReadRecord(book.name, 20260802, 10L, 40L)
        val settings = CoreReaderSettings(
            textSize = 24,
            lineSpacingExtra = 16,
            theme = CoreReaderTheme.NIGHT,
            pageMode = CoreReaderPageMode.PAGED,
            autoRead = true
        )
        val original = InMemoryCoreLibrary().apply {
            saveSource(source)
            saveBook(book)
            saveChapter(first)
            saveChapter(second)
            saveContent(first, "第一章正文")
            saveContent(second, "第二章正文")
            saveGroup(group)
            saveBookmark(bookmark)
            saveReadRecord(record)
            saveReaderSettings(settings)
        }
        val archive = tempDirectory.resolve("legado-backup.zip")

        val exported = CoreBackupService().export(original, archive)
        val restored = InMemoryCoreLibrary()
        val imported = CoreBackupService().import(restored, archive)

        assertEquals(1, exported.books)
        assertEquals(2, exported.chapters)
        assertEquals(2, exported.chapterContents)
        assertEquals(exported, imported)
        assertEquals(listOf(book), restored.books())
        assertEquals(listOf(source), restored.sources())
        assertEquals(listOf(first, second), restored.chapters(book.bookUrl))
        assertEquals("第二章正文", restored.content(second))
        assertEquals(listOf(group), restored.groups().filter { it.groupId == group.groupId })
        assertEquals(listOf(bookmark), restored.bookmarks(book.name, book.author))
        assertEquals(listOf(record), restored.readRecords())
        assertEquals(settings, restored.readerSettings())
    }

    @Test
    fun invalidManifestIsRejectedBeforeTargetLibraryChanges() {
        val archive = tempDirectory.resolve("invalid.zip")
        CoreBackupService.writeTestArchive(
            archive,
            mapOf(
                "manifest.json" to "{\"format\":\"other\",\"version\":1}",
                "bookshelf.json" to "[]"
            )
        )
        val library = InMemoryCoreLibrary().apply {
            saveBook(CoreBook("existing", name = "原有书籍"))
        }

        assertThrows(IllegalArgumentException::class.java) {
            CoreBackupService().import(library, archive)
        }
        assertTrue(library.books().any { it.bookUrl == "existing" })
        assertEquals(1, library.books().size)
    }

    @Test
    fun exportAndImportPreservesBookmarksForBooksOutsideTheBookshelf() {
        val bookmark = CoreBookmark(
            time = 200L,
            bookName = "孤立书籍",
            bookAuthor = "作者",
            chapterName = "第一章",
            bookText = "章节正文",
            content = "摘录"
        )
        val source = InMemoryCoreLibrary().apply { saveBookmark(bookmark) }
        val archive = tempDirectory.resolve("detached-bookmark.zip")
        val target = InMemoryCoreLibrary()

        val exported = CoreBackupService().export(source, archive)
        val imported = CoreBackupService().import(target, archive)

        assertEquals(1, exported.bookmarks)
        assertEquals(exported, imported)
        assertEquals(listOf(bookmark), target.bookmarks(bookmark.bookName, bookmark.bookAuthor))
    }
}
