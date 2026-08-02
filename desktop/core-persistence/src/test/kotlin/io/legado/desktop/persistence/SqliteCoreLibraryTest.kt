package io.legado.desktop.persistence

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookGroup
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreBookmark
import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreReadRecord
import io.legado.core.library.CoreReaderPageMode
import io.legado.core.library.CoreReaderTheme
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SqliteCoreLibraryTest {

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
            autoRead = true
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
    }
}
