package io.legado.desktop.persistence

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookGroup
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreCookie
import io.legado.core.library.CoreBookmark
import io.legado.core.library.CoreBackupService
import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreChapterDownloadItem
import io.legado.core.library.CoreChapterDownloadTask
import io.legado.core.library.CoreDictRule
import io.legado.core.library.CoreReadRecord
import io.legado.core.library.CoreReaderPageMode
import io.legado.core.library.CoreReaderSettings
import io.legado.core.library.CoreReaderTheme
import io.legado.core.library.CoreReplaceRule
import io.legado.core.library.CoreSubscriptionPage
import io.legado.core.library.CoreSourceFilterRule
import io.legado.core.library.CoreTxtTocRule
import io.legado.desktop.persistence.DesktopWebDavConfig
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SqliteCoreLibraryTest {

    @Test
    fun sourceVariablesAndPersistentCookiesSurviveClosingAndReopeningTheDatabase() {
        val databasePath = tempDirectory.resolve("source-runtime-data.db")
        val sourceUrl = "https://books.example"
        val cookie = CoreCookie(
            domain = "books.example",
            path = "/",
            name = "sid",
            value = "persistent",
            persistent = true,
            expiresAt = 1_900_000_000_000L
        )

        SqliteCoreLibrary(databasePath).use { library ->
            library.saveSourceVariable(sourceUrl, "{\"token\":\"abc\"}")
            library.saveCookie(cookie)
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals("{\"token\":\"abc\"}", library.sourceVariable(sourceUrl))
            assertEquals(listOf(cookie), library.cookies())
        }
    }

    private lateinit var tempDirectory: Path

    @Before
    fun setUp() {
        tempDirectory = Files.createTempDirectory("legado-persistence-test")
    }

    @After
    fun tearDown() {
        Files.walk(tempDirectory)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::deleteIfExists)
    }

    @Test
    fun dataSurvivesClosingAndReopeningTheDatabase() {
        val databasePath = tempDirectory.resolve("legado.db")
        val book = CoreBook(
            bookUrl = "book-1",
            name = "星河",
            author = "甲作者",
            kind = "玄幻",
            customTag = "重点",
            customCoverUrl = "custom-cover",
            customIntro = "自定义简介",
            charset = "UTF-8",
            latestChapterTitle = "最新章",
            latestChapterTime = 11,
            lastCheckTime = 12,
            lastCheckCount = 3,
            totalChapterNum = 1,
            durChapterIndex = 0,
            durChapterPos = 9,
            durChapterTitle = "第一章",
            durChapterTime = 13,
            wordCount = "10万",
            canUpdate = false,
            order = 4,
            originOrder = 5,
            variable = "{\"key\":\"value\"}",
            readConfigJson = "{\"reverseToc\":true}",
            syncTime = 14
        )
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            bookSourceName = "示例书源",
            bookSourceGroup = "测试",
            bookSourceType = 2,
            bookUrlPattern = "book/.*",
            customOrder = 3,
            enabled = false,
            enabledExplore = false,
            enabledReview = true,
            enabledCookieJar = null,
            enableDangerousApi = true,
            concurrentRate = "2",
            header = "{\"User-Agent\":\"Legado\"}",
            loginUrl = "https://source.example/login",
            loginUi = "login-ui",
            searchUrl = "https://source.example/search",
            ruleSearch = "search-rule",
            ruleBookInfo = "info-rule",
            ruleToc = "toc-rule",
            ruleContent = "content-rule",
            ruleExplore = "explore-rule",
            ruleReview = "review-rule",
            jsLib = "js-lib",
            loginCheckJs = "check()",
            coverDecodeJs = "decode()",
            bookSourceComment = "comment",
            variableComment = "variables",
            lastUpdateTime = 15,
            respondTime = 16,
            weight = 17,
            exploreUrl = "https://source.example/explore",
            exploreScreen = "玄幻",
            exploreStyle = 18
        )
        val chapter = CoreChapter(
            bookUrl = book.bookUrl,
            url = "chapter-1",
            title = "第一章",
            index = 1,
            isVolume = true,
            isVip = true,
            isPay = true,
            resourceUrl = "audio-1",
            tag = "2026-08-02",
            wordCount = "3000",
            variable = "{\"chapter\":\"value\"}",
            start = 19,
            end = 20,
            startFragmentId = "fragment-start",
            endFragmentId = "fragment-end"
        )

        SqliteCoreLibrary(databasePath).use { library ->
            library.saveBook(book)
            library.saveSource(source)
            library.saveChapter(chapter)
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(book, library.book(book.bookUrl))
            assertEquals(source, library.source(source.bookSourceUrl))
            assertEquals(listOf(chapter), library.chapters(book.bookUrl))
        }
    }

    @Test
    fun firstRunSetupCompletionSurvivesClosingAndReopeningTheDatabase() {
        val databasePath = tempDirectory.resolve("desktop-setup.db")

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(false, library.isSetupComplete())
            library.markSetupComplete()
            assertEquals(true, library.isSetupComplete())
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(true, library.isSetupComplete())
        }
    }

    @Test
    fun webDavConfigSurvivesClosingAndReopeningTheDatabase() {
        val databasePath = tempDirectory.resolve("webdav-config.db")
        val config = DesktopWebDavConfig(
            url = "https://dav.example/legado/",
            username = "reader",
            password = "secret"
        )

        SqliteCoreLibrary(databasePath).use { library ->
            library.saveWebDavConfig(config)
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(config, library.webDavConfig())
        }
    }

    @Test
    fun existingBooksMakeAnUnmarkedDatabaseAvailableWithoutWelcome() {
        val databasePath = tempDirectory.resolve("legacy-setup.db")

        SqliteCoreLibrary(databasePath).use { library ->
            library.saveBook(CoreBook(bookUrl = "legacy-book", name = "已有书籍"))
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(true, library.isSetupComplete())
        }
    }

    @Test
    fun subscriptionPagesSurviveClosingAndReopeningTheDatabase() {
        val databasePath = tempDirectory.resolve("subscription-pages.db")
        val page = CoreSubscriptionPage(
            url = "http://yuedu.miaogongzi.net/gx.html",
            title = "喵公子阅读书源",
            iconUrl = "https://yuedu.miaogongzi.net/favicon.ico",
            category = "订阅页面",
            lastUpdatedAt = 123L
        )

        SqliteCoreLibrary(databasePath).use { library -> library.saveSubscriptionPage(page) }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(listOf(page), library.subscriptionPages())
        }
    }

    @Test
    fun deletingBookCascadesToItsChapters() {
        val databasePath = tempDirectory.resolve("legado.db")
        SqliteCoreLibrary(databasePath).use { library ->
            library.saveBook(CoreBook(bookUrl = "book-1", name = "星河"))
            val chapter = CoreChapter(bookUrl = "book-1", url = "chapter-1")
            library.saveChapter(chapter)
            library.saveContent(chapter, "章节正文")

            library.deleteBook("book-1")

            assertEquals(null, library.book("book-1"))
            assertTrue(library.chapters("book-1").isEmpty())
            assertEquals(null, library.content(chapter))
        }
    }

    @Test
    fun deletingChaptersRemovesOnlyTheSelectedBookAndItsContent() {
        val databasePath = tempDirectory.resolve("delete-chapters.db")
        val selected = CoreChapter(bookUrl = "book-1", url = "chapter-1")
        val other = CoreChapter(bookUrl = "book-2", url = "chapter-2")

        SqliteCoreLibrary(databasePath).use { library ->
            library.saveBook(CoreBook(bookUrl = selected.bookUrl, name = "书一"))
            library.saveBook(CoreBook(bookUrl = other.bookUrl, name = "书二"))
            library.saveChapter(selected)
            library.saveContent(selected, "旧正文")
            library.saveChapter(other)
            library.saveContent(other, "保留正文")

            library.deleteChapters(selected.bookUrl)

            assertTrue(library.chapters(selected.bookUrl).isEmpty())
            assertEquals(null, library.content(selected))
            assertEquals(listOf(other), library.chapters(other.bookUrl))
            assertEquals("保留正文", library.content(other))
        }
    }

    @Test
    fun chapterContentSurvivesClosingAndReopeningTheDatabase() {
        val databasePath = tempDirectory.resolve("legado.db")
        val chapter = CoreChapter(
            bookUrl = "book-content",
            url = "chapter-1",
            title = "第一章"
        )

        SqliteCoreLibrary(databasePath).use { library ->
            library.saveBook(CoreBook(bookUrl = chapter.bookUrl, name = "正文书"))
            library.saveChapter(chapter)
            library.saveContent(chapter, "持久化正文")
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals("持久化正文", library.content(chapter))
        }
    }

    @Test
    fun rssArticleBookChapterAndContentSurviveClosingAndReopeningTheDatabase() {
        val databasePath = tempDirectory.resolve("rss-article.db")
        val source = CoreBookSource(
            bookSourceUrl = "https://feed.example",
            bookSourceName = "示例订阅",
            bookSourceType = 5,
            ruleContent = "{\"content\":\".content\"}"
        )
        val book = CoreBook(
            bookUrl = "https://feed.example/article/1",
            name = "订阅文章",
            intro = "摘要",
            origin = source.bookSourceUrl,
            originName = source.bookSourceName,
            tocUrl = "https://feed.example/article/1",
            type = 5
        )
        val chapter = CoreChapter(
            bookUrl = book.bookUrl,
            url = book.bookUrl,
            title = book.name,
            index = 0
        )

        SqliteCoreLibrary(databasePath).use { library ->
            library.saveSource(source)
            library.saveBook(book)
            library.saveChapter(chapter)
            library.saveContent(chapter, "完整正文")
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(source, library.source(source.bookSourceUrl))
            assertEquals(book, library.book(book.bookUrl))
            assertEquals(listOf(chapter), library.chapters(book.bookUrl))
            assertEquals("完整正文", library.content(chapter))
        }
    }

    @Test
    fun readerDataSurvivesClosingAndReopeningTheDatabase() {
        val databasePath = tempDirectory.resolve("reader-data.db")
        val group = CoreBookGroup(
            groupId = 8L,
            groupName = "玄幻",
            cover = "group-cover",
            order = 2,
            enableRefresh = false,
            show = true,
            bookSort = 3
        )
        val bookmark = CoreBookmark(
            time = 100L,
            bookName = "星河",
            bookAuthor = "甲作者",
            chapterIndex = 3,
            chapterPos = 20,
            chapterName = "第三章",
            bookText = "章节原文",
            content = "值得回看的段落"
        )
        val record = CoreReadRecord(
            bookName = "星河",
            day = 20260802,
            startSec = 10L,
            endSec = 40L
        )
        val settings = io.legado.core.library.CoreReaderSettings(
            textSize = 24,
            lineSpacingExtra = 16,
            theme = CoreReaderTheme.NIGHT,
            pageMode = CoreReaderPageMode.PAGED,
            autoRead = true,
            autoReadSpeedSeconds = 7
        )

        SqliteCoreLibrary(databasePath).use { library ->
            library.saveGroup(group)
            library.saveBookmark(bookmark)
            library.saveReadRecord(record)
            library.saveReaderSettings(settings)
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(group, library.groups().single { it.groupId == group.groupId })
            assertEquals(listOf(bookmark), library.bookmarks("星河", "甲作者"))
            assertEquals(listOf(record), library.readRecords())
            assertEquals(settings, library.readerSettings())
        }
    }

    @Test
    fun webReadConfigJsonSurvivesClosingAndReopeningTheDatabase() {
        val databasePath = tempDirectory.resolve("web-read-config.db")
        val config = """{"theme":5,"fontSize":26,"customFontName":"等线","spacing":{"line":1.1}}"""

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(null, library.webReadConfigJson())
            library.saveWebReadConfigJson(config)
            assertEquals(config, library.webReadConfigJson())
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(config, library.webReadConfigJson())
        }
    }

    @Test
    fun replacementRulesSurviveClosingAndReopeningTheDatabase() {
        val databasePath = tempDirectory.resolve("replace-rules.db")
        val first = CoreReplaceRule(
            id = 100L,
            name = "正文清理",
            group = "通用",
            pattern = "广告",
            replacement = "",
            scope = "星河",
            scopeTitle = false,
            scopeContent = true,
            excludeScope = "source-excluded",
            enabled = true,
            isRegex = false,
            timeoutMillisecond = 2500L,
            order = 2
        )
        val second = CoreReplaceRule(
            id = 101L,
            name = "标题清理",
            pattern = "第(\\d+)章",
            replacement = "章节$1",
            scopeTitle = true,
            scopeContent = false,
            order = 1
        )

        SqliteCoreLibrary(databasePath).use { library ->
            library.saveReplaceRule(first)
            library.saveReplaceRule(second)
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(listOf(second, first), library.replaceRules())
            library.deleteReplaceRule(first.id)
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(listOf(second), library.replaceRules())
        }
    }

    @Test
    fun backupRoundTripWorksWithSqliteLibraries() {
        val sourceDatabase = tempDirectory.resolve("backup-source.db")
        val targetDatabase = tempDirectory.resolve("backup-target.db")
        val archive = tempDirectory.resolve("library-backup.zip")
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
            group = 12L,
            durChapterIndex = 0,
            durChapterPos = 8
        )
        val chapter = CoreChapter(book.bookUrl, "chapter-1", "第一章", 0)
        val group = CoreBookGroup(groupId = 12L, groupName = "备份分组", order = 3)
        val bookmark = CoreBookmark(
            time = 300L,
            bookName = book.name,
            bookAuthor = book.author,
            chapterIndex = 0,
            chapterPos = 8,
            chapterName = chapter.title,
            bookText = "章节正文",
            content = "标注"
        )
        val record = CoreReadRecord(book.name, 20260802, 20L, 60L)
        val settings = CoreReaderSettings(
            textSize = 26,
            lineSpacingExtra = 14,
            theme = CoreReaderTheme.GREEN,
            pageMode = CoreReaderPageMode.PAGED,
            autoRead = true,
            autoReadSpeedSeconds = 8
        )

        SqliteCoreLibrary(sourceDatabase).use { library ->
            library.saveSource(source)
            library.saveGroup(group)
            library.saveBook(book)
            library.saveChapter(chapter)
            library.saveContent(chapter, "章节正文")
            library.saveBookmark(bookmark)
            library.saveReadRecord(record)
            library.saveReaderSettings(settings)
            CoreBackupService().export(library, archive)
        }

        SqliteCoreLibrary(targetDatabase).use { library ->
            CoreBackupService().import(library, archive)
        }

        SqliteCoreLibrary(targetDatabase).use { library ->
            assertEquals(book, library.book(book.bookUrl))
            assertEquals(source, library.source(source.bookSourceUrl))
            assertEquals(listOf(chapter), library.chapters(book.bookUrl))
            assertEquals("章节正文", library.content(chapter))
            assertEquals(group, library.groups().single { it.groupId == group.groupId })
            assertEquals(listOf(bookmark), library.bookmarks(book.name, book.author))
            assertEquals(listOf(record), library.readRecords())
            assertEquals(settings, library.readerSettings())
        }
    }

    @Test
    fun backupRoundTripPreservesChapterDownloadTasksWithSqliteLibraries() {
        val sourceDatabase = tempDirectory.resolve("download-backup-source.db")
        val targetDatabase = tempDirectory.resolve("download-backup-target.db")
        val archive = tempDirectory.resolve("download-task-backup.zip")
        val book = CoreBook(
            bookUrl = "https://source.example/book/download",
            name = "下载任务书"
        )
        val first = CoreChapter(book.bookUrl, "chapter-1", "第一章", 0)
        val second = CoreChapter(book.bookUrl, "chapter-2", "第二章", 1)
        val task = CoreChapterDownloadTask(
            taskId = "sqlite-download-task",
            bookUrl = book.bookUrl,
            status = "PAUSED",
            total = 2,
            completed = 1,
            skipped = 0,
            downloaded = 0,
            failed = 1,
            items = listOf(
                CoreChapterDownloadItem(first, "FAILED", "源站超时"),
                CoreChapterDownloadItem(second, "PENDING")
            ),
            error = "部分章节失败",
            createdAt = 100L,
            updatedAt = 200L
        )

        SqliteCoreLibrary(sourceDatabase).use { library ->
            library.saveBook(book)
            library.saveChapter(first)
            library.saveChapter(second)
            library.saveChapterDownloadTask(task)
            CoreBackupService().export(library, archive)
        }

        SqliteCoreLibrary(targetDatabase).use { library ->
            CoreBackupService().import(library, archive)
        }

        SqliteCoreLibrary(targetDatabase).use { library ->
            assertEquals(listOf(task), library.chapterDownloadTasks())
        }
    }

    @Test
    fun newDatabasesContainAndroidStandardBookGroups() {
        val databasePath = tempDirectory.resolve("standard-groups.db")

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(
                listOf(-1L, -2L, -4L, -11L),
                library.groups().map(CoreBookGroup::groupId)
            )
        }
    }

    @Test
    fun sourceFilterRulesSurviveClosingReopeningAndAreSorted() {
        val databasePath = tempDirectory.resolve("source-filter-rules.db")
        val later = CoreSourceFilterRule(
            id = "later",
            name = "后置",
            pattern = "广告",
            fields = "NAME",
            order = 2
        )
        val earlier = later.copy(id = "earlier", name = "前置", order = 1)

        SqliteCoreLibrary(databasePath).use { library ->
            library.saveSourceFilterRule(later)
            library.saveSourceFilterRule(earlier)
        }
        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(listOf("前置", "后置"), library.sourceFilterRules().map(CoreSourceFilterRule::name))
            library.deleteSourceFilterRule("earlier")
        }
        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(listOf("later"), library.sourceFilterRules().map(CoreSourceFilterRule::id))
        }
    }

    @Test
    fun readsExistingAndroidStyleReaderTables() {
        val databasePath = tempDirectory.resolve("android-reader-data.db")
        DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(ANDROID_GROUPS_SCHEMA)
                statement.execute(ANDROID_BOOKMARKS_SCHEMA)
                statement.execute(ANDROID_READ_RECORD_SCHEMA)
            }
            connection.prepareStatement(
                "INSERT INTO book_groups(groupId, groupName, cover, `order`, enableRefresh, show, bookSort) VALUES (?, ?, ?, ?, ?, ?, ?)"
            ).use { statement ->
                statement.setLong(1, 21L)
                statement.setString(2, "兼容分组")
                statement.setString(3, "cover")
                statement.setInt(4, 4)
                statement.setInt(5, 0)
                statement.setInt(6, 1)
                statement.setInt(7, 2)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                "INSERT INTO bookmarks(time, bookName, bookAuthor, chapterIndex, chapterPos, chapterName, bookText, content) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            ).use { statement ->
                statement.setLong(1, 200L)
                statement.setString(2, "兼容书")
                statement.setString(3, "兼容作者")
                statement.setInt(4, 5)
                statement.setInt(5, 6)
                statement.setString(6, "第五章")
                statement.setString(7, "原文")
                statement.setString(8, "摘录")
                statement.executeUpdate()
            }
            connection.prepareStatement(
                "INSERT INTO readRecord(bookName, day, startSec, endSec) VALUES (?, ?, ?, ?)"
            ).use { statement ->
                statement.setString(1, "兼容书")
                statement.setInt(2, 20260802)
                statement.setLong(3, 100L)
                statement.setLong(4, 120L)
                statement.executeUpdate()
            }
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals("兼容分组", library.groups().single { it.groupId == 21L }.groupName)
            assertEquals("摘录", library.bookmarks("兼容书", "兼容作者").single().content)
            assertEquals(120L, library.readRecords().single().endSec)
        }
    }

    @Test
    fun readsExistingAndroidStyleReplacementRulesTable() {
        val databasePath = tempDirectory.resolve("android-replace-rules.db")
        DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(ANDROID_REPLACE_RULES_SCHEMA)
            }
            connection.prepareStatement(
                """
                    INSERT INTO replace_rules(
                        id, name, `group`, pattern, replacement, scope, scopeTitle,
                        scopeContent, excludeScope, isEnabled, isRegex,
                        timeoutMillisecond, sortOrder
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """.trimIndent()
            ).use { statement ->
                statement.setLong(1, 31L)
                statement.setString(2, "Android 规则")
                statement.setString(3, "兼容")
                statement.setString(4, "广告")
                statement.setString(5, "")
                statement.setString(6, "兼容书")
                statement.setInt(7, 0)
                statement.setInt(8, 1)
                statement.setString(9, null)
                statement.setInt(10, 1)
                statement.setInt(11, 0)
                statement.setLong(12, 3000L)
                statement.setInt(13, 4)
                statement.executeUpdate()
            }
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(
                CoreReplaceRule(
                    id = 31L,
                    name = "Android 规则",
                    group = "兼容",
                    pattern = "广告",
                    replacement = "",
                    scope = "兼容书",
                    scopeTitle = false,
                    scopeContent = true,
                    excludeScope = null,
                    enabled = true,
                    isRegex = false,
                    timeoutMillisecond = 3000L,
                    order = 4
                ),
                library.replaceRules().single()
            )
        }
    }

    @Test
    fun dictRulesSurviveClosingAndReopeningTheDatabase() {
        val databasePath = tempDirectory.resolve("dict-rules.db")
        val rule = CoreDictRule(
            name = "释义词典",
            urlRule = "https://dict.example/?q={{key}}",
            showRule = "@CSS:.meaning@text",
            enabled = true,
            sortNumber = 3
        )

        SqliteCoreLibrary(databasePath).use { library ->
            library.saveDictRule(rule)
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(listOf(rule), library.dictRules())
            assertEquals(rule, library.dictRule(rule.name))
            assertEquals(listOf(rule), library.enabledDictRules())
        }
    }

    @Test
    fun txtTocRulesSurviveClosingAndReopeningTheDatabase() {
        val databasePath = tempDirectory.resolve("txt-toc-rules.db")
        val rule = CoreTxtTocRule(
            id = 101L,
            name = "自定义目录",
            rule = "^第\\d+章.*$",
            example = "第一章",
            serialNumber = 4,
            enable = true
        )

        SqliteCoreLibrary(databasePath).use { library -> library.saveTxtTocRule(rule) }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(listOf(rule), library.txtTocRules())
            assertEquals(rule, library.txtTocRule(rule.id))
            assertEquals(listOf(rule), library.enabledTxtTocRules())
        }
    }

    @Test
    fun readsExistingAndroidStyleDictRulesTable() {
        val databasePath = tempDirectory.resolve("android-dict-rules.db")
        DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(ANDROID_DICT_RULES_SCHEMA)
            }
            connection.prepareStatement(
                "INSERT INTO dictRules(name, urlRule, showRule, enabled, sortNumber) VALUES (?, ?, ?, ?, ?)"
            ).use { statement ->
                statement.setString(1, "旧字典")
                statement.setString(2, "https://dict.example/?q={{key}}")
                statement.setString(3, "@Json:$.meaning")
                statement.setInt(4, 1)
                statement.setInt(5, 7)
                statement.executeUpdate()
            }
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(
                CoreDictRule(
                    name = "旧字典",
                    urlRule = "https://dict.example/?q={{key}}",
                    showRule = "@Json:$.meaning",
                    enabled = true,
                    sortNumber = 7
                ),
                library.dictRules().single()
            )
        }
    }

    @Test
    fun migratesReaderSettingsTableCreatedBeforeAutoReadSpeedWasAdded() {
        val databasePath = tempDirectory.resolve("legacy-reader-settings.db")
        DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE desktop_settings (
                        id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
                        textSize INTEGER NOT NULL,
                        lineSpacingExtra INTEGER NOT NULL,
                        theme TEXT NOT NULL,
                        pageMode TEXT NOT NULL,
                        autoRead INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
            connection.prepareStatement(
                "INSERT INTO desktop_settings(id, textSize, lineSpacingExtra, theme, pageMode, autoRead) VALUES (1, 22, 14, 'NIGHT', 'SCROLL', 1)"
            ).use { statement -> statement.executeUpdate() }
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(22, library.readerSettings().textSize)
            assertEquals(true, library.readerSettings().autoRead)
            assertEquals(10, library.readerSettings().autoReadSpeedSeconds)

            library.saveReaderSettings(library.readerSettings().copy(autoReadSpeedSeconds = 5))
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(5, library.readerSettings().autoReadSpeedSeconds)
        }
    }

    @Test
    fun updateScheduleSurvivesClosingAndReopeningTheDatabase() {
        val databasePath = tempDirectory.resolve("update-schedule.db")
        val schedule = io.legado.core.library.CoreUpdateSchedule(
            enabled = true,
            intervalMinutes = 60,
            nextRunAt = 123_000L,
            lastRunAt = 60_000L,
            lastSummary = "自动更新完成"
        )

        SqliteCoreLibrary(databasePath).use { library ->
            library.saveUpdateSchedule(schedule)
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(schedule, library.updateSchedule())
        }
    }

    @Test
    fun readsAnExistingAndroidStyleCoreSchema() {
        val databasePath = tempDirectory.resolve("legado.db")
        DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                statement.execute(ANDROID_BOOKS_SCHEMA)
                statement.execute(ANDROID_SOURCES_SCHEMA)
                statement.execute(ANDROID_CHAPTERS_SCHEMA)
            }
            insertAndroidRows(connection)
        }

        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals("兼容书籍", library.book("android-book")?.name)
            assertEquals("兼容书源", library.source("android-source")?.bookSourceName)
            assertEquals("兼容章节", library.chapters("android-book").single().title)
        }
    }

    @Test
    fun migratesDatabaseToAnEmptyDirectoryAndLeavesAPreMigrationSnapshot() {
        val sourceDirectory = tempDirectory.resolve("source")
        val targetDirectory = tempDirectory.resolve("target")
        val databasePath = sourceDirectory.resolve("legado.db")
        val book = CoreBook("migration-book", name = "迁移测试")
        val chapter = CoreChapter(book.bookUrl, "chapter-1", "第一章", 0)

        SqliteCoreLibrary(databasePath).use { library ->
            library.saveBook(book)
            library.saveChapter(chapter)
            library.saveContent(chapter, "迁移正文")

            val result = DesktopDataDirectoryMigration().migrate(library, targetDirectory)

            assertEquals(sourceDirectory.toAbsolutePath().normalize(), result.sourceDirectory)
            assertEquals(targetDirectory.toAbsolutePath().normalize(), result.targetDirectory)
            assertTrue(Files.isRegularFile(result.sourceBackup))
            assertTrue(Files.isRegularFile(result.targetDatabase))
        }

        SqliteCoreLibrary(targetDirectory.resolve("legado.db")).use { library ->
            assertEquals(book, library.book(book.bookUrl))
            assertEquals("迁移正文", library.content(chapter))
        }
        SqliteCoreLibrary(databasePath).use { library ->
            assertEquals(book, library.book(book.bookUrl))
        }
    }

    @Test
    fun refusesToOverwriteAnExistingTargetDatabase() {
        val sourceDatabase = tempDirectory.resolve("source").resolve("legado.db")
        val targetDirectory = tempDirectory.resolve("target")
        val targetDatabase = targetDirectory.resolve("legado.db")
        val sourceBook = CoreBook("source-book", name = "源数据")
        val targetBook = CoreBook("target-book", name = "目标数据")

        SqliteCoreLibrary(sourceDatabase).use { library ->
            library.saveBook(sourceBook)
        }
        SqliteCoreLibrary(targetDatabase).use { library ->
            library.saveBook(targetBook)
        }

        SqliteCoreLibrary(sourceDatabase).use { library ->
            val error = runCatching {
                DesktopDataDirectoryMigration().migrate(library, targetDirectory)
            }.exceptionOrNull()

            assertNotNull(error)
            assertTrue(error!!.message.orEmpty().contains("已存在数据库"))
        }

        SqliteCoreLibrary(targetDatabase).use { library ->
            assertEquals(listOf(targetBook), library.books())
        }
    }

    @Test
    fun rejectsTheSourceDirectoryAndDirectoriesInsideItAsMigrationTargets() {
        val sourceDirectory = tempDirectory.resolve("source")
        val sourceDatabase = sourceDirectory.resolve("legado.db")
        SqliteCoreLibrary(sourceDatabase).use { library ->
            val sameDirectoryError = runCatching {
                DesktopDataDirectoryMigration().migrate(library, sourceDirectory)
            }.exceptionOrNull()
            val nestedDirectoryError = runCatching {
                DesktopDataDirectoryMigration().migrate(library, sourceDirectory.resolve("nested"))
            }.exceptionOrNull()

            assertTrue(sameDirectoryError is IllegalArgumentException)
            assertTrue(nestedDirectoryError is IllegalArgumentException)
        }
    }

    private fun insertAndroidRows(connection: Connection) {
        connection.prepareStatement(
            """
            INSERT INTO books (
                bookUrl, tocUrl, origin, originName, name, author, kind, customTag,
                coverUrl, customCoverUrl, intro, customIntro, charset, type, `group`,
                latestChapterTitle, latestChapterTime, lastCheckTime, lastCheckCount,
                totalChapterNum, durChapterTitle, durChapterIndex, durChapterPos,
                durChapterTime, wordCount, canUpdate, `order`, originOrder, variable,
                readConfig, syncTime
            ) VALUES (?, '', 'loc_book', '', '兼容书籍', '兼容作者', '兼容分类', NULL,
                NULL, NULL, NULL, NULL, NULL, 0, 0, '兼容最新章', 1, 2, 3, 1,
                '兼容章节', 0, 0, 0, NULL, 1, 0, 0, NULL, NULL, 4)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, "android-book")
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO book_sources (bookSourceUrl, bookSourceName, bookSourceType, customOrder, enabled, enabledExplore, enabledReview, lastUpdateTime, respondTime, weight) VALUES (?, ?, 0, 0, 1, 1, 1, 5, 6, 7)"
        ).use { statement ->
            statement.setString(1, "android-source")
            statement.setString(2, "兼容书源")
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO chapters (url, title, isVolume, bookUrl, `index`, isVip, isPay, start, end) VALUES (?, ?, 0, ?, 0, 0, 0, NULL, NULL)"
        ).use { statement ->
            statement.setString(1, "android-chapter")
            statement.setString(2, "兼容章节")
            statement.setString(3, "android-book")
            statement.executeUpdate()
        }
    }

    private companion object {
        const val ANDROID_BOOKS_SCHEMA = """
            CREATE TABLE books (
                bookUrl TEXT NOT NULL DEFAULT '', tocUrl TEXT NOT NULL DEFAULT '',
                origin TEXT NOT NULL DEFAULT 'loc_book', originName TEXT NOT NULL DEFAULT '',
                name TEXT NOT NULL DEFAULT '', author TEXT NOT NULL DEFAULT '', kind TEXT,
                customTag TEXT, coverUrl TEXT, customCoverUrl TEXT, intro TEXT, customIntro TEXT,
                charset TEXT, type INTEGER NOT NULL DEFAULT 0, `group` INTEGER NOT NULL DEFAULT 0,
                latestChapterTitle TEXT, latestChapterTime INTEGER NOT NULL DEFAULT 0,
                lastCheckTime INTEGER NOT NULL DEFAULT 0, lastCheckCount INTEGER NOT NULL DEFAULT 0,
                totalChapterNum INTEGER NOT NULL DEFAULT 0, durChapterTitle TEXT,
                durChapterIndex INTEGER NOT NULL DEFAULT 0, durChapterPos INTEGER NOT NULL DEFAULT 0,
                durChapterTime INTEGER NOT NULL DEFAULT 0, wordCount TEXT,
                canUpdate INTEGER NOT NULL DEFAULT 1, `order` INTEGER NOT NULL DEFAULT 0,
                originOrder INTEGER NOT NULL DEFAULT 0, variable TEXT, readConfig TEXT,
                syncTime INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (bookUrl)
            )
        """
        const val ANDROID_SOURCES_SCHEMA = """
            CREATE TABLE book_sources (
                bookSourceUrl TEXT NOT NULL, bookSourceName TEXT NOT NULL, bookSourceGroup TEXT,
                bookSourceType INTEGER NOT NULL, bookUrlPattern TEXT, customOrder INTEGER NOT NULL DEFAULT 0,
                enabled INTEGER NOT NULL DEFAULT 1, enabledExplore INTEGER NOT NULL DEFAULT 1,
                enabledReview INTEGER NOT NULL DEFAULT 1, jsLib TEXT, enabledCookieJar INTEGER,
                enableDangerousApi INTEGER, concurrentRate TEXT, header TEXT, loginUrl TEXT,
                loginUi TEXT, loginCheckJs TEXT, coverDecodeJs TEXT, bookSourceComment TEXT,
                variableComment TEXT, lastUpdateTime INTEGER NOT NULL, respondTime INTEGER NOT NULL,
                weight INTEGER NOT NULL, exploreUrl TEXT, exploreScreen TEXT,
                exploreStyle INTEGER NOT NULL DEFAULT 0, ruleExplore TEXT, searchUrl TEXT,
                ruleSearch TEXT, ruleBookInfo TEXT, ruleToc TEXT, ruleContent TEXT, ruleReview TEXT,
                PRIMARY KEY (bookSourceUrl)
            )
        """
        const val ANDROID_CHAPTERS_SCHEMA = """
            CREATE TABLE chapters (
                url TEXT NOT NULL, title TEXT NOT NULL, isVolume INTEGER NOT NULL,
                bookUrl TEXT NOT NULL, `index` INTEGER NOT NULL, isVip INTEGER NOT NULL,
                isPay INTEGER NOT NULL, resourceUrl TEXT, tag TEXT, wordCount TEXT,
                start INTEGER, end INTEGER, startFragmentId TEXT, endFragmentId TEXT,
                variable TEXT, PRIMARY KEY (bookUrl, url),
                FOREIGN KEY (bookUrl) REFERENCES books(bookUrl) ON DELETE CASCADE
            )
        """
        const val ANDROID_GROUPS_SCHEMA = """
            CREATE TABLE book_groups (
                groupId INTEGER NOT NULL PRIMARY KEY,
                groupName TEXT NOT NULL,
                cover TEXT,
                `order` INTEGER NOT NULL DEFAULT 0,
                enableRefresh INTEGER NOT NULL DEFAULT 1,
                show INTEGER NOT NULL DEFAULT 1,
                bookSort INTEGER NOT NULL DEFAULT -1
            )
        """
        const val ANDROID_BOOKMARKS_SCHEMA = """
            CREATE TABLE bookmarks (
                time INTEGER NOT NULL PRIMARY KEY,
                bookName TEXT NOT NULL,
                bookAuthor TEXT NOT NULL,
                chapterIndex INTEGER NOT NULL DEFAULT 0,
                chapterPos INTEGER NOT NULL DEFAULT 0,
                chapterName TEXT NOT NULL DEFAULT '',
                bookText TEXT NOT NULL DEFAULT '',
                content TEXT NOT NULL DEFAULT ''
            )
        """
        const val ANDROID_READ_RECORD_SCHEMA = """
            CREATE TABLE readRecord (
                bookName TEXT NOT NULL,
                day INTEGER NOT NULL,
                startSec INTEGER NOT NULL,
                endSec INTEGER NOT NULL,
                PRIMARY KEY(bookName, day, startSec)
            )
        """
        const val ANDROID_REPLACE_RULES_SCHEMA = """
            CREATE TABLE replace_rules (
                id INTEGER NOT NULL PRIMARY KEY,
                name TEXT NOT NULL DEFAULT '',
                `group` TEXT,
                pattern TEXT NOT NULL DEFAULT '',
                replacement TEXT NOT NULL DEFAULT '',
                scope TEXT,
                scopeTitle INTEGER NOT NULL DEFAULT 0,
                scopeContent INTEGER NOT NULL DEFAULT 1,
                excludeScope TEXT,
                isEnabled INTEGER NOT NULL DEFAULT 1,
                isRegex INTEGER NOT NULL DEFAULT 1,
                timeoutMillisecond INTEGER NOT NULL DEFAULT 3000,
                sortOrder INTEGER NOT NULL DEFAULT 0
            )
        """
        const val ANDROID_DICT_RULES_SCHEMA = """
            CREATE TABLE dictRules (
                name TEXT NOT NULL PRIMARY KEY,
                urlRule TEXT NOT NULL DEFAULT '',
                showRule TEXT NOT NULL DEFAULT '',
                enabled INTEGER NOT NULL DEFAULT 1,
                sortNumber INTEGER NOT NULL DEFAULT 0
            )
        """
    }
}
