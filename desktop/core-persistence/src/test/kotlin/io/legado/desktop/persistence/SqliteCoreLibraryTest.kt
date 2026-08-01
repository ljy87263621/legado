package io.legado.desktop.persistence

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreChapter
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
    }
}
