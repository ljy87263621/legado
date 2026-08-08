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
        val replaceRule = CoreReplaceRule(
            id = 77L,
            name = "清理广告",
            pattern = "广告",
            replacement = "",
            isRegex = false,
            order = 3
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
            saveReplaceRule(replaceRule)
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
        assertEquals(listOf(replaceRule), restored.replaceRules())
        assertEquals(1, imported.replaceRules)
    }

    @Test
    fun exportAndImportPreservesSavedSubscriptionPages() {
        val page = CoreSubscriptionPage(
            url = "https://example.com/gx.html",
            title = "阅读书源",
            iconUrl = "https://example.com/favicon.ico",
            category = "订阅页面",
            lastUpdatedAt = 123L
        )
        val original = InMemoryCoreLibrary().apply { saveSubscriptionPage(page) }
        val archive = tempDirectory.resolve("subscription-page.zip")
        val restored = InMemoryCoreLibrary()

        val exported = CoreBackupService().export(original, archive)
        CoreBackupService().import(restored, archive)

        assertEquals(1, exported.subscriptionPages)
        assertEquals(listOf(page), restored.subscriptionPages())
    }

    @Test
    fun exportAndImportPreservesSourceRuntimeData() {
        val source = CoreBookSource(bookSourceUrl = "https://source.example", bookSourceName = "示例源")
        val cookie = CoreCookie(
            domain = "source.example",
            path = "/",
            name = "sid",
            value = "persistent",
            persistent = true,
            expiresAt = 1_900_000_000_000L
        )
        val original = InMemoryCoreLibrary().apply {
            saveSource(source)
            saveSourceVariable(source.bookSourceUrl, "{\"token\":\"abc\"}")
            saveCookie(cookie)
        }
        val archive = tempDirectory.resolve("source-runtime-data.zip")
        val restored = InMemoryCoreLibrary()

        val exported = CoreBackupService().export(original, archive)
        CoreBackupService().import(restored, archive)

        assertEquals(1, exported.sourceVariables)
        assertEquals(1, exported.cookies)
        assertEquals("{\"token\":\"abc\"}", restored.sourceVariable(source.bookSourceUrl))
        assertEquals(listOf(cookie), restored.cookies())
    }

    @Test
    fun exportAndImportPreservesDictRules() {
        val rule = CoreDictRule(
            name = "示例字典",
            urlRule = "https://dict.example/?q={{key}}",
            showRule = "@Json:$.meaning",
            enabled = true,
            sortNumber = 2
        )
        val original = InMemoryCoreLibrary().apply { saveDictRule(rule) }
        val archive = tempDirectory.resolve("dict-rules.zip")
        val restored = InMemoryCoreLibrary()

        val exported = CoreBackupService().export(original, archive)
        val imported = CoreBackupService().import(restored, archive)

        assertEquals(1, exported.dictRules)
        assertEquals(exported, imported)
        assertEquals(listOf(rule), restored.dictRules())
    }

    @Test
    fun exportAndImportPreservesTxtTocRules() {
        val rule = CoreTxtTocRule(
            id = 9L,
            name = "TXT目录",
            rule = "^Chapter \\d+.*$",
            example = "Chapter 1",
            serialNumber = 3,
            enable = true
        )
        val original = InMemoryCoreLibrary().apply { saveTxtTocRule(rule) }
        val archive = tempDirectory.resolve("txt-toc-rules.zip")
        val restored = InMemoryCoreLibrary()

        val exported = CoreBackupService().export(original, archive)
        val imported = CoreBackupService().import(restored, archive)

        assertEquals(1, exported.txtTocRules)
        assertEquals(exported, imported)
        assertEquals(listOf(rule), restored.txtTocRules())
    }

    @Test
    fun exportAndImportPreservesSourceFilterRules() {
        val rule = CoreSourceFilterRule(
            id = "source-filter-1",
            name = "屏蔽广告书",
            pattern = "广告",
            fields = "NAME,INTRO",
            scope = "玄幻",
            order = 3,
            createTime = 123L
        )
        val original = InMemoryCoreLibrary().apply { saveSourceFilterRule(rule) }
        val archive = tempDirectory.resolve("source-filter-rules.zip")
        val restored = InMemoryCoreLibrary()

        val exported = CoreBackupService().export(original, archive)
        val imported = CoreBackupService().import(restored, archive)

        assertEquals(1, exported.sourceFilterRules)
        assertEquals(exported, imported)
        assertEquals(listOf(rule), restored.sourceFilterRules())
    }

    @Test
    fun exportAndImportPreserveUpdateSchedule() {
        val schedule = CoreUpdateSchedule(
            enabled = true,
            intervalMinutes = 30,
            nextRunAt = 456_000L,
            lastRunAt = 123_000L,
            lastSummary = "最近完成"
        )
        val original = InMemoryCoreLibrary().apply { saveUpdateSchedule(schedule) }
        val archive = tempDirectory.resolve("schedule.zip")
        val restored = InMemoryCoreLibrary()

        CoreBackupService().export(original, archive)
        CoreBackupService().import(restored, archive)

        assertEquals(schedule, restored.updateSchedule())
    }

    @Test
    fun exportAndImportPreservesChapterDownloadTasks() {
        val book = CoreBook(
            bookUrl = "https://source.example/book/download",
            name = "待下载的书"
        )
        val first = CoreChapter(book.bookUrl, "chapter-1", "第一章", 0)
        val second = CoreChapter(book.bookUrl, "chapter-2", "第二章", 1)
        val task = CoreChapterDownloadTask(
            taskId = "download-task-1",
            bookUrl = book.bookUrl,
            status = "PAUSED",
            total = 2,
            completed = 1,
            skipped = 0,
            downloaded = 0,
            failed = 1,
            items = listOf(
                CoreChapterDownloadItem(first, "FAILED", "网络暂时不可用"),
                CoreChapterDownloadItem(second, "PENDING")
            ),
            error = null,
            createdAt = 100L,
            updatedAt = 200L
        )
        val original = InMemoryCoreLibrary().apply {
            saveBook(book)
            saveChapter(first)
            saveChapter(second)
            saveChapterDownloadTask(task)
        }
        val archive = tempDirectory.resolve("chapter-download-task.zip")
        val restored = InMemoryCoreLibrary()

        val exported = CoreBackupService().export(original, archive)
        val imported = CoreBackupService().import(restored, archive)

        assertEquals(1, exported.chapterDownloadTasks)
        assertEquals(exported, imported)
        assertEquals(listOf(task), restored.chapterDownloadTasks())
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

    @Test
    fun importsOlderReaderSettingsWithoutAutoReadSpeedUsingTheDefault() {
        val archive = tempDirectory.resolve("legacy-reader-settings.zip")
        CoreBackupService.writeTestArchive(
            archive,
            mapOf(
                "manifest.json" to """
                    {
                      "format":"legado-desktop-backup",
                      "version":1,
                      "books":0,
                      "chapters":0,
                      "chapterContents":0,
                      "groups":0,
                      "sources":0,
                      "bookmarks":0,
                      "readRecords":0,
                      "hasReaderSettings":true
                    }
                """.trimIndent(),
                "bookshelf.json" to "[]",
                "chapters.json" to "[]",
                "chapterContents.json" to "[]",
                "bookGroup.json" to "[]",
                "bookSource.json" to "[]",
                "bookmark.json" to "[]",
                "readRecord.json" to "[]",
                "readerSettings.json" to """
                    {
                      "textSize":20,
                      "lineSpacingExtra":12,
                      "theme":"DAY",
                      "pageMode":"SCROLL",
                      "autoRead":false
                    }
                """.trimIndent()
            )
        )

        val restored = InMemoryCoreLibrary()
        CoreBackupService().import(restored, archive)

        assertEquals(10, restored.readerSettings().autoReadSpeedSeconds)
    }

    @Test
    fun importsOlderBackupsWithoutReplacementRulesAsAnEmptyList() {
        val archive = tempDirectory.resolve("legacy-without-replace-rules.zip")
        CoreBackupService.writeTestArchive(
            archive,
            mapOf(
                "manifest.json" to """
                    {
                      "format":"legado-desktop-backup",
                      "version":1,
                      "books":0,
                      "chapters":0,
                      "chapterContents":0,
                      "groups":0,
                      "sources":0,
                      "bookmarks":0,
                      "readRecords":0,
                      "hasReaderSettings":true
                    }
                """.trimIndent(),
                "bookshelf.json" to "[]",
                "chapters.json" to "[]",
                "chapterContents.json" to "[]",
                "bookGroup.json" to "[]",
                "bookSource.json" to "[]",
                "bookmark.json" to "[]",
                "readRecord.json" to "[]",
                "readerSettings.json" to "{}"
            )
        )

        val restored = InMemoryCoreLibrary()

        val summary = CoreBackupService().import(restored, archive)

        assertEquals(0, summary.replaceRules)
        assertTrue(restored.replaceRules().isEmpty())
        assertEquals(CoreUpdateSchedule(), restored.updateSchedule())
    }

    @Test
    fun importsOlderBackupsWithoutDictRulesAsAnEmptyList() {
        val archive = tempDirectory.resolve("legacy-without-dict-rules.zip")
        CoreBackupService.writeTestArchive(
            archive,
            mapOf(
                "manifest.json" to """
                    {
                      "format":"legado-desktop-backup",
                      "version":1,
                      "books":0,
                      "chapters":0,
                      "chapterContents":0,
                      "groups":0,
                      "sources":0,
                      "bookmarks":0,
                      "readRecords":0,
                      "hasReaderSettings":true
                    }
                """.trimIndent(),
                "bookshelf.json" to "[]",
                "chapters.json" to "[]",
                "chapterContents.json" to "[]",
                "bookGroup.json" to "[]",
                "bookSource.json" to "[]",
                "bookmark.json" to "[]",
                "readRecord.json" to "[]",
                "readerSettings.json" to "{}"
            )
        )

        val restored = InMemoryCoreLibrary()

        val summary = CoreBackupService().import(restored, archive)

        assertEquals(0, summary.dictRules)
        assertTrue(restored.dictRules().isEmpty())
    }

    @Test
    fun rejectsDuplicateReplacementRuleIdsBeforeChangingTheTargetLibrary() {
        val archive = tempDirectory.resolve("duplicate-replace-rules.zip")
        CoreBackupService.writeTestArchive(
            archive,
            mapOf(
                "manifest.json" to """
                    {
                      "format":"legado-desktop-backup",
                      "version":1,
                      "books":0,
                      "chapters":0,
                      "chapterContents":0,
                      "groups":0,
                      "sources":0,
                      "bookmarks":0,
                      "readRecords":0,
                      "replaceRules":2,
                      "hasReaderSettings":true
                    }
                """.trimIndent(),
                "bookshelf.json" to "[]",
                "chapters.json" to "[]",
                "chapterContents.json" to "[]",
                "bookGroup.json" to "[]",
                "bookSource.json" to "[]",
                "bookmark.json" to "[]",
                "readRecord.json" to "[]",
                "readerSettings.json" to "{}",
                "replaceRule.json" to """
                    [
                      {"id":9,"pattern":"a"},
                      {"id":9,"pattern":"b"}
                    ]
                """.trimIndent()
            )
        )
        val library = InMemoryCoreLibrary().apply {
            saveBook(CoreBook("existing", name = "原有书籍"))
        }

        assertThrows(IllegalArgumentException::class.java) {
            CoreBackupService().import(library, archive)
        }
        assertEquals(emptyList<CoreReplaceRule>(), library.replaceRules())
        assertEquals(1, library.books().size)
    }
}
