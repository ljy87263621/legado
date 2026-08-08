package io.legado.desktop.persistence

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookGroup
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreBookmark
import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreChapterDownloadItem
import io.legado.core.library.CoreChapterDownloadTask
import io.legado.core.library.CoreCookie
import io.legado.core.library.CoreDictRule
import io.legado.core.library.CoreLibrary
import io.legado.core.library.CoreSourceVariable
import io.legado.core.library.CoreReadRecord
import io.legado.core.library.CoreReaderPageMode
import io.legado.core.library.CoreReaderSettings
import io.legado.core.library.CoreReaderTheme
import io.legado.core.library.CoreReplaceRule
import io.legado.core.library.CoreSubscriptionPage
import io.legado.core.library.CoreSourceFilterRule
import io.legado.core.library.CoreTxtTocRule
import io.legado.core.library.CoreUpdateSchedule
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.sql.DriverManager

class SqliteCoreLibrary(databasePath: Path) :
    CoreLibrary,
    DesktopChapterDownloadStore,
    DesktopSetupStore,
    DesktopWebDavConfigStore,
    AutoCloseable {

    val databasePath: Path = databasePath.toAbsolutePath().normalize()

    private val connection: Connection

    init {
        this.databasePath.parent?.let(Files::createDirectories)
        connection = DriverManager.getConnection("jdbc:sqlite:${this.databasePath}")
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            createSchema(statement)
            seedStandardGroups(statement)
        }
    }

    override fun books(): List<CoreBook> = queryList(
        "SELECT * FROM books ORDER BY rowid",
        mapper = ::readBook
    )

    override fun book(bookUrl: String): CoreBook? = queryOne(
        sql = "SELECT * FROM books WHERE bookUrl = ?",
        bind = { statement -> statement.setString(1, bookUrl) },
        mapper = ::readBook
    )

    override fun saveBook(book: CoreBook) {
        connection.prepareStatement(BOOK_UPSERT).use { statement ->
            bindBook(statement, book)
            statement.executeUpdate()
        }
    }

    override fun deleteBook(bookUrl: String) {
        connection.prepareStatement("DELETE FROM books WHERE bookUrl = ?").use { statement ->
            statement.setString(1, bookUrl)
            statement.executeUpdate()
        }
    }

    override fun sources(): List<CoreBookSource> = queryList(
        "SELECT * FROM book_sources ORDER BY rowid",
        mapper = ::readSource
    )

    override fun source(bookSourceUrl: String): CoreBookSource? = queryOne(
        sql = "SELECT * FROM book_sources WHERE bookSourceUrl = ?",
        bind = { statement -> statement.setString(1, bookSourceUrl) },
        mapper = ::readSource
    )

    override fun saveSource(source: CoreBookSource) {
        connection.prepareStatement(SOURCE_UPSERT).use { statement ->
            bindSource(statement, source)
            statement.executeUpdate()
        }
    }

    override fun deleteSource(bookSourceUrl: String) {
        connection.prepareStatement("DELETE FROM book_sources WHERE bookSourceUrl = ?").use { statement ->
            statement.setString(1, bookSourceUrl)
            statement.executeUpdate()
        }
    }

    override fun cookies(): List<CoreCookie> = queryList(
        "SELECT * FROM cookies ORDER BY domain, path, name",
        mapper = ::readCookie
    )

    override fun saveCookie(cookie: CoreCookie) {
        connection.prepareStatement(COOKIE_UPSERT).use { statement ->
            statement.setString(1, cookie.domain)
            statement.setString(2, cookie.path)
            statement.setString(3, cookie.name)
            statement.setString(4, cookie.value)
            statement.setBooleanAsInteger(5, cookie.persistent)
            statement.setNullableLong(6, cookie.expiresAt)
            statement.executeUpdate()
        }
    }

    override fun deleteCookie(domain: String, path: String, name: String) {
        connection.prepareStatement(
            "DELETE FROM cookies WHERE domain = ? AND path = ? AND name = ?"
        ).use { statement ->
            statement.setString(1, domain)
            statement.setString(2, path)
            statement.setString(3, name)
            statement.executeUpdate()
        }
    }

    override fun sourceVariable(sourceUrl: String): String? = queryOne(
        sql = "SELECT value FROM source_variables WHERE sourceUrl = ?",
        bind = { statement -> statement.setString(1, sourceUrl) },
        mapper = { resultSet -> resultSet.getString("value") }
    )

    override fun saveSourceVariable(sourceUrl: String, value: String?) {
        if (value == null) {
            connection.prepareStatement("DELETE FROM source_variables WHERE sourceUrl = ?").use { statement ->
                statement.setString(1, sourceUrl)
                statement.executeUpdate()
            }
        } else {
            connection.prepareStatement(SOURCE_VARIABLE_UPSERT).use { statement ->
                statement.setString(1, sourceUrl)
                statement.setString(2, value)
                statement.executeUpdate()
            }
        }
    }

    override fun sourceVariables(): List<CoreSourceVariable> = queryList(
        "SELECT sourceUrl, value FROM source_variables ORDER BY sourceUrl",
        mapper = { resultSet ->
            CoreSourceVariable(
                sourceUrl = resultSet.getString("sourceUrl"),
                value = resultSet.getString("value")
            )
        }
    )

    override fun chapters(bookUrl: String): List<CoreChapter> = queryList(
        "SELECT * FROM chapters WHERE bookUrl = ? ORDER BY `index`, rowid",
        bind = { statement -> statement.setString(1, bookUrl) },
        mapper = ::readChapter
    )

    override fun deleteChapters(bookUrl: String) {
        connection.prepareStatement("DELETE FROM chapters WHERE bookUrl = ?").use { statement ->
            statement.setString(1, bookUrl)
            statement.executeUpdate()
        }
    }

    override fun saveChapter(chapter: CoreChapter) {
        connection.prepareStatement(CHAPTER_UPSERT).use { statement ->
            bindChapter(statement, chapter)
            statement.executeUpdate()
        }
    }

    override fun content(chapter: CoreChapter): String? = queryOne(
        sql = "SELECT content FROM chapter_contents WHERE bookUrl = ? AND chapterUrl = ?",
        bind = { statement ->
            statement.setString(1, chapter.bookUrl)
            statement.setString(2, chapter.url)
        },
        mapper = { resultSet -> resultSet.getString("content") }
    )

    override fun saveContent(chapter: CoreChapter, content: String) {
        connection.prepareStatement(CONTENT_UPSERT).use { statement ->
            statement.setString(1, chapter.bookUrl)
            statement.setString(2, chapter.url)
            statement.setString(3, content)
            statement.executeUpdate()
        }
    }

    override fun groups(): List<CoreBookGroup> = queryList(
        "SELECT * FROM book_groups ORDER BY `order`, groupId",
        mapper = ::readGroup
    )

    override fun saveGroup(group: CoreBookGroup) {
        connection.prepareStatement(GROUP_UPSERT).use { statement ->
            bindGroup(statement, group)
            statement.executeUpdate()
        }
    }

    override fun deleteGroup(groupId: Long) {
        connection.prepareStatement("DELETE FROM book_groups WHERE groupId = ?").use { statement ->
            statement.setLong(1, groupId)
            statement.executeUpdate()
        }
    }

    override fun bookmarks(bookName: String, bookAuthor: String): List<CoreBookmark> = queryList(
        "SELECT * FROM bookmarks WHERE bookName = ? AND bookAuthor = ? ORDER BY time DESC",
        bind = { statement ->
            statement.setString(1, bookName)
            statement.setString(2, bookAuthor)
        },
        mapper = ::readBookmark
    )

    override fun allBookmarks(): List<CoreBookmark> = queryList(
        "SELECT * FROM bookmarks ORDER BY time DESC",
        mapper = ::readBookmark
    )

    override fun saveBookmark(bookmark: CoreBookmark) {
        connection.prepareStatement(BOOKMARK_UPSERT).use { statement ->
            bindBookmark(statement, bookmark)
            statement.executeUpdate()
        }
    }

    override fun deleteBookmark(time: Long) {
        connection.prepareStatement("DELETE FROM bookmarks WHERE time = ?").use { statement ->
            statement.setLong(1, time)
            statement.executeUpdate()
        }
    }

    override fun readRecords(): List<CoreReadRecord> = queryList(
        "SELECT * FROM readRecord ORDER BY day, startSec",
        mapper = ::readRecord
    )

    override fun saveReadRecord(record: CoreReadRecord) {
        connection.prepareStatement(READ_RECORD_UPSERT).use { statement ->
            bindReadRecord(statement, record)
            statement.executeUpdate()
        }
    }

    override fun deleteReadRecords(bookName: String) {
        connection.prepareStatement("DELETE FROM readRecord WHERE bookName = ?").use { statement ->
            statement.setString(1, bookName)
            statement.executeUpdate()
        }
    }

    override fun readerSettings(): CoreReaderSettings = queryOne(
        sql = "SELECT * FROM desktop_settings WHERE id = 1",
        bind = {},
        mapper = ::readReaderSettings
    ) ?: CoreReaderSettings()

    override fun isSetupComplete(): Boolean = queryOne(
        sql = "SELECT completed FROM desktop_setup WHERE id = 1",
        bind = {},
        mapper = { resultSet -> resultSet.getInt("completed") != 0 }
    ) ?: books().isNotEmpty()

    override fun markSetupComplete() {
        connection.prepareStatement(
            "INSERT INTO desktop_setup(id, completed) VALUES (1, 1) " +
                "ON CONFLICT(id) DO UPDATE SET completed = excluded.completed"
        ).use { statement ->
            statement.executeUpdate()
        }
    }

    override fun saveReaderSettings(settings: CoreReaderSettings) {
        connection.prepareStatement(READER_SETTINGS_UPSERT).use { statement ->
            statement.setInt(1, 1)
            statement.setInt(2, settings.textSize)
            statement.setInt(3, settings.lineSpacingExtra)
            statement.setString(4, settings.theme.name)
            statement.setString(5, settings.pageMode.name)
            statement.setBooleanAsInteger(6, settings.autoRead)
            statement.setInt(7, settings.autoReadSpeedSeconds)
            statement.executeUpdate()
        }
    }

    override fun webReadConfigJson(): String? = queryOne(
        sql = "SELECT configJson FROM desktop_web_read_config WHERE id = 1",
        bind = {},
        mapper = { resultSet -> resultSet.getString("configJson") }
    )

    override fun saveWebReadConfigJson(configJson: String) {
        connection.prepareStatement(WEB_READ_CONFIG_UPSERT).use { statement ->
            statement.setInt(1, 1)
            statement.setString(2, configJson)
            statement.executeUpdate()
        }
    }

    override fun webDavConfig(): DesktopWebDavConfig? = queryOne(
        sql = "SELECT url, username, password, bookDirectory FROM desktop_webdav_config WHERE id = 1",
        bind = {},
        mapper = { resultSet ->
            DesktopWebDavConfig(
                url = resultSet.getString("url"),
                username = resultSet.getString("username"),
                password = resultSet.getString("password"),
                bookDirectory = resultSet.getString("bookDirectory") ?: "books"
            )
        }
    )

    override fun saveWebDavConfig(config: DesktopWebDavConfig) {
        connection.prepareStatement(WEB_DAV_CONFIG_UPSERT).use { statement ->
            statement.setInt(1, 1)
            statement.setString(2, config.url)
            statement.setString(3, config.username)
            statement.setString(4, config.password)
            statement.setString(5, config.bookDirectory)
            statement.executeUpdate()
        }
    }

    override fun replaceRules(): List<CoreReplaceRule> = queryList(
        "SELECT * FROM replace_rules ORDER BY sortOrder, id",
        mapper = ::readReplaceRule
    )

    override fun saveReplaceRule(rule: CoreReplaceRule) {
        connection.prepareStatement(REPLACE_RULE_UPSERT).use { statement ->
            bindReplaceRule(statement, rule)
            statement.executeUpdate()
        }
    }

    override fun deleteReplaceRule(id: Long) {
        connection.prepareStatement("DELETE FROM replace_rules WHERE id = ?").use { statement ->
            statement.setLong(1, id)
            statement.executeUpdate()
        }
    }

    override fun dictRules(): List<CoreDictRule> = queryList(
        "SELECT * FROM dictRules ORDER BY sortNumber, name",
        mapper = ::readDictRule
    )

    override fun dictRule(name: String): CoreDictRule? = queryOne(
        sql = "SELECT * FROM dictRules WHERE name = ?",
        bind = { statement -> statement.setString(1, name) },
        mapper = ::readDictRule
    )

    override fun saveDictRule(rule: CoreDictRule) {
        connection.prepareStatement(DICT_RULE_UPSERT).use { statement ->
            statement.setString(1, rule.name)
            statement.setString(2, rule.urlRule)
            statement.setString(3, rule.showRule)
            statement.setBooleanAsInteger(4, rule.enabled)
            statement.setInt(5, rule.sortNumber)
            statement.executeUpdate()
        }
    }

    override fun deleteDictRule(name: String) {
        connection.prepareStatement("DELETE FROM dictRules WHERE name = ?").use { statement ->
            statement.setString(1, name)
            statement.executeUpdate()
        }
    }

    override fun txtTocRules(): List<CoreTxtTocRule> = queryList(
        "SELECT * FROM txtTocRules ORDER BY serialNumber, id",
        mapper = ::readTxtTocRule
    )

    override fun txtTocRule(id: Long): CoreTxtTocRule? = queryOne(
        sql = "SELECT * FROM txtTocRules WHERE id = ?",
        bind = { statement -> statement.setLong(1, id) },
        mapper = ::readTxtTocRule
    )

    override fun saveTxtTocRule(rule: CoreTxtTocRule) {
        connection.prepareStatement(TXT_TOC_RULE_UPSERT).use { statement ->
            statement.setLong(1, rule.id)
            statement.setString(2, rule.name)
            statement.setString(3, rule.rule)
            statement.setNullableString(4, rule.example)
            statement.setInt(5, rule.serialNumber)
            statement.setBooleanAsInteger(6, rule.enable)
            statement.executeUpdate()
        }
    }

    override fun deleteTxtTocRule(id: Long) {
        connection.prepareStatement("DELETE FROM txtTocRules WHERE id = ?").use { statement ->
            statement.setLong(1, id)
            statement.executeUpdate()
        }
    }

    override fun sourceFilterRules(): List<CoreSourceFilterRule> = queryList(
        "SELECT * FROM source_filter_rules ORDER BY sortOrder, createTime, id",
        mapper = ::readSourceFilterRule
    )

    override fun sourceFilterRule(id: String): CoreSourceFilterRule? = queryOne(
        sql = "SELECT * FROM source_filter_rules WHERE id = ?",
        bind = { statement -> statement.setString(1, id) },
        mapper = ::readSourceFilterRule
    )

    override fun saveSourceFilterRule(rule: CoreSourceFilterRule) {
        connection.prepareStatement(SOURCE_FILTER_RULE_UPSERT).use { statement ->
            statement.setString(1, rule.id)
            statement.setString(2, rule.name)
            statement.setBooleanAsInteger(3, rule.enabled)
            statement.setString(4, rule.pattern)
            statement.setString(5, rule.fields)
            statement.setString(6, rule.scope)
            statement.setInt(7, rule.order)
            statement.setLong(8, rule.createTime)
            statement.executeUpdate()
        }
    }

    override fun deleteSourceFilterRule(id: String) {
        connection.prepareStatement("DELETE FROM source_filter_rules WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeUpdate()
        }
    }

    override fun subscriptionPages(): List<CoreSubscriptionPage> = queryList(
        "SELECT * FROM subscription_pages ORDER BY lastUpdatedAt DESC, url",
        mapper = ::readSubscriptionPage
    )

    override fun saveSubscriptionPage(page: CoreSubscriptionPage) {
        connection.prepareStatement(SUBSCRIPTION_PAGE_UPSERT).use { statement ->
            statement.setString(1, page.url)
            statement.setString(2, page.title)
            statement.setNullableString(3, page.iconUrl)
            statement.setString(4, page.category)
            statement.setLong(5, page.lastUpdatedAt)
            statement.executeUpdate()
        }
    }

    override fun deleteSubscriptionPage(url: String) {
        connection.prepareStatement("DELETE FROM subscription_pages WHERE url = ?").use { statement ->
            statement.setString(1, url)
            statement.executeUpdate()
        }
    }

    override fun updateSchedule(): CoreUpdateSchedule = queryOne(
        sql = "SELECT * FROM desktop_update_schedule WHERE id = 1",
        bind = {},
        mapper = ::readUpdateSchedule
    ) ?: CoreUpdateSchedule()

    override fun saveUpdateSchedule(schedule: CoreUpdateSchedule) {
        connection.prepareStatement(UPDATE_SCHEDULE_UPSERT).use { statement ->
            statement.setInt(1, 1)
            statement.setBooleanAsInteger(2, schedule.enabled)
            statement.setInt(3, schedule.intervalMinutes.coerceIn(15, 1440))
            statement.setLong(4, schedule.nextRunAt)
            statement.setLong(5, schedule.lastRunAt)
            statement.setNullableString(6, schedule.lastSummary)
            statement.executeUpdate()
        }
    }

    override fun saveChapterDownloadTask(task: DesktopChapterDownloadTaskRecord) {
        connection.prepareStatement(CHAPTER_DOWNLOAD_TASK_UPSERT).use { statement ->
            statement.setString(1, task.taskId)
            statement.setString(2, task.bookUrl)
            statement.setString(3, task.status)
            statement.setInt(4, task.total)
            statement.setInt(5, task.completed)
            statement.setInt(6, task.skipped)
            statement.setInt(7, task.downloaded)
            statement.setInt(8, task.failed)
            statement.setString(9, chapterDownloadRecordCodec.encode(task.items))
            statement.setNullableString(10, task.error)
            statement.setLong(11, task.createdAt)
            statement.setLong(12, task.updatedAt)
            statement.executeUpdate()
        }
    }

    override fun chapterDownloadTasks(): List<CoreChapterDownloadTask> = queryList(
        sql = "SELECT * FROM desktop_chapter_download_tasks ORDER BY updatedAt, taskId",
        mapper = ::readCoreChapterDownloadTask
    )

    override fun saveChapterDownloadTask(task: CoreChapterDownloadTask) {
        saveChapterDownloadTask(
            DesktopChapterDownloadTaskRecord(
                taskId = task.taskId,
                bookUrl = task.bookUrl,
                status = task.status,
                total = task.total,
                completed = task.completed,
                skipped = task.skipped,
                downloaded = task.downloaded,
                failed = task.failed,
                items = task.items.map { item ->
                    DesktopChapterDownloadItemRecord(
                        chapter = item.chapter,
                        status = item.status,
                        error = item.error
                    )
                },
                error = task.error,
                createdAt = task.createdAt,
                updatedAt = task.updatedAt
            )
        )
    }

    override fun chapterDownloadTask(taskId: String): DesktopChapterDownloadTaskRecord? = queryOne(
        sql = "SELECT * FROM desktop_chapter_download_tasks WHERE taskId = ?",
        bind = { statement -> statement.setString(1, taskId) },
        mapper = ::readChapterDownloadTask
    )

    override fun unfinishedChapterDownloadTasks(): List<DesktopChapterDownloadTaskRecord> = queryList(
        sql = "SELECT * FROM desktop_chapter_download_tasks WHERE status IN ('IDLE', 'RUNNING', 'PAUSED') ORDER BY updatedAt, taskId",
        mapper = ::readChapterDownloadTask
    )

    override fun recoverInterruptedChapterDownloadTasks(): List<DesktopChapterDownloadTaskRecord> {
        val interrupted = queryList(
            sql = "SELECT * FROM desktop_chapter_download_tasks WHERE status IN ('IDLE', 'RUNNING') ORDER BY updatedAt, taskId",
            mapper = ::readChapterDownloadTask
        )
        interrupted.map { it.copy(status = "PAUSED", updatedAt = System.currentTimeMillis()) }
            .forEach(::saveChapterDownloadTask)
        return unfinishedChapterDownloadTasks()
    }

    override fun close() {
        connection.close()
    }

    /**
     * Creates a transactionally consistent SQLite snapshot while this library is open.
     * The destination must not exist because SQLite's VACUUM INTO does not overwrite it.
     */
    fun snapshotTo(destination: Path) {
        val normalizedDestination = destination.toAbsolutePath().normalize()
        require(normalizedDestination != databasePath) { "快照目标不能是当前数据库" }
        require(!Files.exists(normalizedDestination)) { "快照目标已存在: $normalizedDestination" }
        normalizedDestination.parent?.let(Files::createDirectories)
        val escapedPath = normalizedDestination.toString().replace("'", "''")
        connection.createStatement().use { statement ->
            statement.execute("VACUUM INTO '$escapedPath'")
        }
    }

    private fun createSchema(statement: java.sql.Statement) {
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS books (
                bookUrl TEXT NOT NULL DEFAULT '',
                tocUrl TEXT NOT NULL DEFAULT '',
                origin TEXT NOT NULL DEFAULT 'loc_book',
                originName TEXT NOT NULL DEFAULT '',
                name TEXT NOT NULL DEFAULT '',
                author TEXT NOT NULL DEFAULT '',
                kind TEXT,
                customTag TEXT,
                coverUrl TEXT,
                customCoverUrl TEXT,
                intro TEXT,
                customIntro TEXT,
                charset TEXT,
                type INTEGER NOT NULL DEFAULT 0,
                `group` INTEGER NOT NULL DEFAULT 0,
                latestChapterTitle TEXT,
                latestChapterTime INTEGER NOT NULL DEFAULT 0,
                lastCheckTime INTEGER NOT NULL DEFAULT 0,
                lastCheckCount INTEGER NOT NULL DEFAULT 0,
                totalChapterNum INTEGER NOT NULL DEFAULT 0,
                durChapterTitle TEXT,
                durChapterIndex INTEGER NOT NULL DEFAULT 0,
                durChapterPos INTEGER NOT NULL DEFAULT 0,
                durChapterTime INTEGER NOT NULL DEFAULT 0,
                wordCount TEXT,
                canUpdate INTEGER NOT NULL DEFAULT 1,
                `order` INTEGER NOT NULL DEFAULT 0,
                originOrder INTEGER NOT NULL DEFAULT 0,
                variable TEXT,
                readConfig TEXT,
                syncTime INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (bookUrl)
            )
            """.trimIndent()
        )
        statement.execute(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_books_name_author ON books(name, author)"
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS source_variables (
                sourceUrl TEXT NOT NULL PRIMARY KEY,
                value TEXT NOT NULL
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS cookies (
                domain TEXT NOT NULL,
                path TEXT NOT NULL,
                name TEXT NOT NULL,
                value TEXT NOT NULL,
                persistent INTEGER NOT NULL DEFAULT 0,
                expiresAt INTEGER,
                PRIMARY KEY (domain, path, name)
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS subscription_pages (
                url TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL DEFAULT '',
                iconUrl TEXT,
                category TEXT NOT NULL DEFAULT '订阅页面',
                lastUpdatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS replace_rules (
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
                sortOrder INTEGER NOT NULL DEFAULT -2147483648
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS dictRules (
                name TEXT NOT NULL PRIMARY KEY,
                urlRule TEXT NOT NULL DEFAULT '',
                showRule TEXT NOT NULL DEFAULT '',
                enabled INTEGER NOT NULL DEFAULT 1,
                sortNumber INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS txtTocRules (
                id INTEGER NOT NULL PRIMARY KEY,
                name TEXT NOT NULL DEFAULT '',
                rule TEXT NOT NULL DEFAULT '',
                example TEXT,
                serialNumber INTEGER NOT NULL DEFAULT -1,
                enable INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS source_filter_rules (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL DEFAULT '',
                enabled INTEGER NOT NULL DEFAULT 1,
                pattern TEXT NOT NULL DEFAULT '',
                fields TEXT NOT NULL DEFAULT '',
                scope TEXT NOT NULL DEFAULT '',
                sortOrder INTEGER NOT NULL DEFAULT 0,
                createTime INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS book_groups (
                groupId INTEGER NOT NULL PRIMARY KEY,
                groupName TEXT NOT NULL DEFAULT '',
                cover TEXT,
                `order` INTEGER NOT NULL DEFAULT 0,
                enableRefresh INTEGER NOT NULL DEFAULT 1,
                show INTEGER NOT NULL DEFAULT 1,
                bookSort INTEGER NOT NULL DEFAULT -1
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS bookmarks (
                time INTEGER NOT NULL PRIMARY KEY,
                bookName TEXT NOT NULL DEFAULT '',
                bookAuthor TEXT NOT NULL DEFAULT '',
                chapterIndex INTEGER NOT NULL DEFAULT 0,
                chapterPos INTEGER NOT NULL DEFAULT 0,
                chapterName TEXT NOT NULL DEFAULT '',
                bookText TEXT NOT NULL DEFAULT '',
                content TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS readRecord (
                bookName TEXT NOT NULL,
                day INTEGER NOT NULL,
                startSec INTEGER NOT NULL,
                endSec INTEGER NOT NULL,
                PRIMARY KEY(bookName, day, startSec)
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS desktop_settings (
                id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
                textSize INTEGER NOT NULL,
                lineSpacingExtra INTEGER NOT NULL,
                theme TEXT NOT NULL,
                pageMode TEXT NOT NULL,
                autoRead INTEGER NOT NULL,
                autoReadSpeedSeconds INTEGER NOT NULL DEFAULT 10
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS desktop_setup (
                id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
                completed INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS desktop_web_read_config (
                id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
                configJson TEXT NOT NULL
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS desktop_webdav_config (
                id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
                url TEXT NOT NULL,
                username TEXT NOT NULL,
                password TEXT NOT NULL,
                bookDirectory TEXT NOT NULL DEFAULT 'books'
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS desktop_update_schedule (
                id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
                enabled INTEGER NOT NULL DEFAULT 0,
                intervalMinutes INTEGER NOT NULL DEFAULT 360,
                nextRunAt INTEGER NOT NULL DEFAULT 0,
                lastRunAt INTEGER NOT NULL DEFAULT 0,
                lastSummary TEXT
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS desktop_chapter_download_tasks (
                taskId TEXT NOT NULL PRIMARY KEY,
                bookUrl TEXT NOT NULL,
                status TEXT NOT NULL,
                total INTEGER NOT NULL DEFAULT 0,
                completed INTEGER NOT NULL DEFAULT 0,
                skipped INTEGER NOT NULL DEFAULT 0,
                downloaded INTEGER NOT NULL DEFAULT 0,
                failed INTEGER NOT NULL DEFAULT 0,
                itemsJson TEXT NOT NULL DEFAULT '[]',
                error TEXT,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        ensureColumn(
            statement,
            table = "desktop_settings",
            column = "autoReadSpeedSeconds",
            definition = "INTEGER NOT NULL DEFAULT 10"
        )
        ensureColumn(
            statement,
            table = "desktop_webdav_config",
            column = "bookDirectory",
            definition = "TEXT NOT NULL DEFAULT 'books'"
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS book_sources (
                bookSourceUrl TEXT NOT NULL,
                bookSourceName TEXT NOT NULL,
                bookSourceGroup TEXT,
                bookSourceType INTEGER NOT NULL,
                bookUrlPattern TEXT,
                customOrder INTEGER NOT NULL DEFAULT 0,
                enabled INTEGER NOT NULL DEFAULT 1,
                enabledExplore INTEGER NOT NULL DEFAULT 1,
                enabledReview INTEGER NOT NULL DEFAULT 1,
                jsLib TEXT,
                enabledCookieJar INTEGER DEFAULT 0,
                enableDangerousApi INTEGER DEFAULT 0,
                concurrentRate TEXT,
                header TEXT,
                loginUrl TEXT,
                loginUi TEXT,
                loginCheckJs TEXT,
                coverDecodeJs TEXT,
                bookSourceComment TEXT,
                variableComment TEXT,
                lastUpdateTime INTEGER NOT NULL,
                respondTime INTEGER NOT NULL,
                weight INTEGER NOT NULL,
                exploreUrl TEXT,
                exploreScreen TEXT,
                exploreStyle INTEGER NOT NULL DEFAULT 0,
                ruleExplore TEXT,
                searchUrl TEXT,
                ruleSearch TEXT,
                ruleBookInfo TEXT,
                ruleToc TEXT,
                ruleContent TEXT,
                ruleReview TEXT,
                PRIMARY KEY (bookSourceUrl)
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS chapters (
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                isVolume INTEGER NOT NULL,
                bookUrl TEXT NOT NULL,
                `index` INTEGER NOT NULL,
                isVip INTEGER NOT NULL,
                isPay INTEGER NOT NULL,
                resourceUrl TEXT,
                tag TEXT,
                wordCount TEXT,
                start INTEGER,
                end INTEGER,
                startFragmentId TEXT,
                endFragmentId TEXT,
                variable TEXT,
                PRIMARY KEY (bookUrl, url),
                FOREIGN KEY (bookUrl) REFERENCES books(bookUrl) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS chapter_contents (
                bookUrl TEXT NOT NULL,
                chapterUrl TEXT NOT NULL,
                content TEXT NOT NULL,
                PRIMARY KEY (bookUrl, chapterUrl),
                FOREIGN KEY (bookUrl, chapterUrl) REFERENCES chapters(bookUrl, url) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    private fun seedStandardGroups(statement: java.sql.Statement) {
        val groups = listOf(
            CoreBookGroup(groupId = -1L, groupName = "全部", order = -10),
            CoreBookGroup(groupId = -2L, groupName = "本地", order = -9, enableRefresh = false),
            CoreBookGroup(groupId = -4L, groupName = "未分组", order = -7),
            CoreBookGroup(groupId = -11L, groupName = "更新失败", order = -1)
        )
        connection.prepareStatement(
            """
            INSERT OR IGNORE INTO book_groups(
                groupId, groupName, cover, `order`, enableRefresh, show, bookSort
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { insert ->
            groups.forEach { group ->
                bindGroup(insert, group)
                insert.executeUpdate()
            }
        }
    }

    private fun bindBook(statement: PreparedStatement, book: CoreBook) {
        statement.setString(1, book.bookUrl)
        statement.setString(2, book.tocUrl)
        statement.setString(3, book.origin)
        statement.setString(4, book.originName)
        statement.setString(5, book.name)
        statement.setString(6, book.author)
        statement.setNullableString(7, book.kind)
        statement.setNullableString(8, book.customTag)
        statement.setNullableString(9, book.coverUrl)
        statement.setNullableString(10, book.customCoverUrl)
        statement.setNullableString(11, book.intro)
        statement.setNullableString(12, book.customIntro)
        statement.setNullableString(13, book.charset)
        statement.setInt(14, book.type)
        statement.setLong(15, book.group)
        statement.setNullableString(16, book.latestChapterTitle)
        statement.setLong(17, book.latestChapterTime)
        statement.setLong(18, book.lastCheckTime)
        statement.setInt(19, book.lastCheckCount)
        statement.setInt(20, book.totalChapterNum)
        statement.setNullableString(21, book.durChapterTitle)
        statement.setInt(22, book.durChapterIndex)
        statement.setInt(23, book.durChapterPos)
        statement.setLong(24, book.durChapterTime)
        statement.setNullableString(25, book.wordCount)
        statement.setBooleanAsInteger(26, book.canUpdate)
        statement.setInt(27, book.order)
        statement.setInt(28, book.originOrder)
        statement.setNullableString(29, book.variable)
        statement.setNullableString(30, book.readConfigJson)
        statement.setLong(31, book.syncTime)
    }

    private fun bindSource(statement: PreparedStatement, source: CoreBookSource) {
        statement.setString(1, source.bookSourceUrl)
        statement.setString(2, source.bookSourceName)
        statement.setNullableString(3, source.bookSourceGroup)
        statement.setInt(4, source.bookSourceType)
        statement.setNullableString(5, source.bookUrlPattern)
        statement.setInt(6, source.customOrder)
        statement.setBooleanAsInteger(7, source.enabled)
        statement.setBooleanAsInteger(8, source.enabledExplore)
        statement.setBooleanAsInteger(9, source.enabledReview)
        statement.setNullableString(10, source.jsLib)
        statement.setNullableBooleanAsInteger(11, source.enabledCookieJar)
        statement.setNullableBooleanAsInteger(12, source.enableDangerousApi)
        statement.setNullableString(13, source.concurrentRate)
        statement.setNullableString(14, source.header)
        statement.setNullableString(15, source.loginUrl)
        statement.setNullableString(16, source.loginUi)
        statement.setNullableString(17, source.loginCheckJs)
        statement.setNullableString(18, source.coverDecodeJs)
        statement.setNullableString(19, source.bookSourceComment)
        statement.setNullableString(20, source.variableComment)
        statement.setLong(21, source.lastUpdateTime)
        statement.setLong(22, source.respondTime)
        statement.setInt(23, source.weight)
        statement.setNullableString(24, source.exploreUrl)
        statement.setNullableString(25, source.exploreScreen)
        statement.setInt(26, source.exploreStyle)
        statement.setNullableString(27, source.ruleExplore)
        statement.setNullableString(28, source.searchUrl)
        statement.setNullableString(29, source.ruleSearch)
        statement.setNullableString(30, source.ruleBookInfo)
        statement.setNullableString(31, source.ruleToc)
        statement.setNullableString(32, source.ruleContent)
        statement.setNullableString(33, source.ruleReview)
    }

    private fun bindChapter(statement: PreparedStatement, chapter: CoreChapter) {
        statement.setString(1, chapter.url)
        statement.setString(2, chapter.title)
        statement.setBooleanAsInteger(3, chapter.isVolume)
        statement.setString(4, chapter.bookUrl)
        statement.setInt(5, chapter.index)
        statement.setBooleanAsInteger(6, chapter.isVip)
        statement.setBooleanAsInteger(7, chapter.isPay)
        statement.setNullableString(8, chapter.resourceUrl)
        statement.setNullableString(9, chapter.tag)
        statement.setNullableString(10, chapter.wordCount)
        statement.setNullableLong(11, chapter.start)
        statement.setNullableLong(12, chapter.end)
        statement.setNullableString(13, chapter.startFragmentId)
        statement.setNullableString(14, chapter.endFragmentId)
        statement.setNullableString(15, chapter.variable)
    }

    private fun bindGroup(statement: PreparedStatement, group: CoreBookGroup) {
        statement.setLong(1, group.groupId)
        statement.setString(2, group.groupName)
        statement.setNullableString(3, group.cover)
        statement.setInt(4, group.order)
        statement.setBooleanAsInteger(5, group.enableRefresh)
        statement.setBooleanAsInteger(6, group.show)
        statement.setInt(7, group.bookSort)
    }

    private fun bindBookmark(statement: PreparedStatement, bookmark: CoreBookmark) {
        statement.setLong(1, bookmark.time)
        statement.setString(2, bookmark.bookName)
        statement.setString(3, bookmark.bookAuthor)
        statement.setInt(4, bookmark.chapterIndex)
        statement.setInt(5, bookmark.chapterPos)
        statement.setString(6, bookmark.chapterName)
        statement.setString(7, bookmark.bookText)
        statement.setString(8, bookmark.content)
    }

    private fun bindReadRecord(statement: PreparedStatement, record: CoreReadRecord) {
        statement.setString(1, record.bookName)
        statement.setInt(2, record.day)
        statement.setLong(3, record.startSec)
        statement.setLong(4, record.endSec)
    }

    private fun readBook(resultSet: ResultSet) = CoreBook(
        bookUrl = resultSet.getString("bookUrl"),
        tocUrl = resultSet.getString("tocUrl"),
        origin = resultSet.getString("origin"),
        originName = resultSet.getString("originName"),
        name = resultSet.getString("name"),
        author = resultSet.getString("author"),
        kind = resultSet.getString("kind"),
        customTag = resultSet.getString("customTag"),
        coverUrl = resultSet.getString("coverUrl"),
        customCoverUrl = resultSet.getString("customCoverUrl"),
        intro = resultSet.getString("intro"),
        customIntro = resultSet.getString("customIntro"),
        charset = resultSet.getString("charset"),
        type = resultSet.getInt("type"),
        group = resultSet.getLong("group"),
        latestChapterTitle = resultSet.getString("latestChapterTitle"),
        latestChapterTime = resultSet.getLong("latestChapterTime"),
        lastCheckTime = resultSet.getLong("lastCheckTime"),
        lastCheckCount = resultSet.getInt("lastCheckCount"),
        totalChapterNum = resultSet.getInt("totalChapterNum"),
        durChapterIndex = resultSet.getInt("durChapterIndex"),
        durChapterPos = resultSet.getInt("durChapterPos"),
        durChapterTitle = resultSet.getString("durChapterTitle"),
        durChapterTime = resultSet.getLong("durChapterTime"),
        wordCount = resultSet.getString("wordCount"),
        canUpdate = resultSet.getBooleanAsInteger("canUpdate"),
        order = resultSet.getInt("order"),
        originOrder = resultSet.getInt("originOrder"),
        variable = resultSet.getString("variable"),
        readConfigJson = resultSet.getString("readConfig"),
        syncTime = resultSet.getLong("syncTime")
    )

    private fun readSource(resultSet: ResultSet) = CoreBookSource(
        bookSourceUrl = resultSet.getString("bookSourceUrl"),
        bookSourceName = resultSet.getString("bookSourceName"),
        bookSourceGroup = resultSet.getString("bookSourceGroup"),
        bookSourceType = resultSet.getInt("bookSourceType"),
        bookUrlPattern = resultSet.getString("bookUrlPattern"),
        customOrder = resultSet.getInt("customOrder"),
        enabled = resultSet.getBooleanAsInteger("enabled"),
        enabledExplore = resultSet.getBooleanAsInteger("enabledExplore"),
        enabledReview = resultSet.getBooleanAsInteger("enabledReview"),
        enabledCookieJar = resultSet.getNullableBooleanAsInteger("enabledCookieJar"),
        enableDangerousApi = resultSet.getNullableBooleanAsInteger("enableDangerousApi"),
        concurrentRate = resultSet.getString("concurrentRate"),
        header = resultSet.getString("header"),
        loginUrl = resultSet.getString("loginUrl"),
        loginUi = resultSet.getString("loginUi"),
        searchUrl = resultSet.getString("searchUrl"),
        ruleSearch = resultSet.getString("ruleSearch"),
        ruleBookInfo = resultSet.getString("ruleBookInfo"),
        ruleToc = resultSet.getString("ruleToc"),
        ruleContent = resultSet.getString("ruleContent"),
        ruleExplore = resultSet.getString("ruleExplore"),
        ruleReview = resultSet.getString("ruleReview"),
        jsLib = resultSet.getString("jsLib"),
        loginCheckJs = resultSet.getString("loginCheckJs"),
        coverDecodeJs = resultSet.getString("coverDecodeJs"),
        bookSourceComment = resultSet.getString("bookSourceComment"),
        variableComment = resultSet.getString("variableComment"),
        lastUpdateTime = resultSet.getLong("lastUpdateTime"),
        respondTime = resultSet.getLong("respondTime"),
        weight = resultSet.getInt("weight"),
        exploreUrl = resultSet.getString("exploreUrl"),
        exploreScreen = resultSet.getString("exploreScreen"),
        exploreStyle = resultSet.getInt("exploreStyle")
    )

    private fun readCookie(resultSet: ResultSet) = CoreCookie(
        domain = resultSet.getString("domain"),
        path = resultSet.getString("path"),
        name = resultSet.getString("name"),
        value = resultSet.getString("value"),
        persistent = resultSet.getBooleanAsInteger("persistent"),
        expiresAt = resultSet.getNullableLong("expiresAt")
    )

    private fun readChapter(resultSet: ResultSet) = CoreChapter(
        bookUrl = resultSet.getString("bookUrl"),
        url = resultSet.getString("url"),
        title = resultSet.getString("title"),
        index = resultSet.getInt("index"),
        isVolume = resultSet.getBooleanAsInteger("isVolume"),
        isVip = resultSet.getBooleanAsInteger("isVip"),
        isPay = resultSet.getBooleanAsInteger("isPay"),
        resourceUrl = resultSet.getString("resourceUrl"),
        tag = resultSet.getString("tag"),
        wordCount = resultSet.getString("wordCount"),
        variable = resultSet.getString("variable"),
        start = resultSet.getNullableLong("start"),
        end = resultSet.getNullableLong("end"),
        startFragmentId = resultSet.getString("startFragmentId"),
        endFragmentId = resultSet.getString("endFragmentId")
    )

    private fun readGroup(resultSet: ResultSet) = CoreBookGroup(
        groupId = resultSet.getLong("groupId"),
        groupName = resultSet.getString("groupName"),
        cover = resultSet.getString("cover"),
        order = resultSet.getInt("order"),
        enableRefresh = resultSet.getBooleanAsInteger("enableRefresh"),
        show = resultSet.getBooleanAsInteger("show"),
        bookSort = resultSet.getInt("bookSort")
    )

    private fun readBookmark(resultSet: ResultSet) = CoreBookmark(
        time = resultSet.getLong("time"),
        bookName = resultSet.getString("bookName"),
        bookAuthor = resultSet.getString("bookAuthor"),
        chapterIndex = resultSet.getInt("chapterIndex"),
        chapterPos = resultSet.getInt("chapterPos"),
        chapterName = resultSet.getString("chapterName"),
        bookText = resultSet.getString("bookText"),
        content = resultSet.getString("content")
    )

    private fun readRecord(resultSet: ResultSet) = CoreReadRecord(
        bookName = resultSet.getString("bookName"),
        day = resultSet.getInt("day"),
        startSec = resultSet.getLong("startSec"),
        endSec = resultSet.getLong("endSec")
    )

    private fun readReaderSettings(resultSet: ResultSet) = CoreReaderSettings(
        textSize = resultSet.getInt("textSize"),
        lineSpacingExtra = resultSet.getInt("lineSpacingExtra"),
        theme = resultSet.getString("theme").toCoreReaderTheme(),
        pageMode = resultSet.getString("pageMode").toCoreReaderPageMode(),
        autoRead = resultSet.getBooleanAsInteger("autoRead"),
        autoReadSpeedSeconds = resultSet.getInt("autoReadSpeedSeconds").coerceIn(1, 120)
    )

    private fun readReplaceRule(resultSet: ResultSet) = CoreReplaceRule(
        id = resultSet.getLong("id"),
        name = resultSet.getString("name"),
        group = resultSet.getString("group"),
        pattern = resultSet.getString("pattern"),
        replacement = resultSet.getString("replacement"),
        scope = resultSet.getString("scope"),
        scopeTitle = resultSet.getBooleanAsInteger("scopeTitle"),
        scopeContent = resultSet.getBooleanAsInteger("scopeContent"),
        excludeScope = resultSet.getString("excludeScope"),
        enabled = resultSet.getBooleanAsInteger("isEnabled"),
        isRegex = resultSet.getBooleanAsInteger("isRegex"),
        timeoutMillisecond = resultSet.getLong("timeoutMillisecond"),
        order = resultSet.getInt("sortOrder")
    )

    private fun readDictRule(resultSet: ResultSet) = CoreDictRule(
        name = resultSet.getString("name"),
        urlRule = resultSet.getString("urlRule"),
        showRule = resultSet.getString("showRule"),
        enabled = resultSet.getBooleanAsInteger("enabled"),
        sortNumber = resultSet.getInt("sortNumber")
    )

    private fun readTxtTocRule(resultSet: ResultSet) = CoreTxtTocRule(
        id = resultSet.getLong("id"),
        name = resultSet.getString("name"),
        rule = resultSet.getString("rule"),
        example = resultSet.getString("example"),
        serialNumber = resultSet.getInt("serialNumber"),
        enable = resultSet.getBooleanAsInteger("enable")
    )

    private fun readSourceFilterRule(resultSet: ResultSet) = CoreSourceFilterRule(
        id = resultSet.getString("id"),
        name = resultSet.getString("name"),
        enabled = resultSet.getBooleanAsInteger("enabled"),
        pattern = resultSet.getString("pattern"),
        fields = resultSet.getString("fields"),
        scope = resultSet.getString("scope"),
        order = resultSet.getInt("sortOrder"),
        createTime = resultSet.getLong("createTime")
    )

    private fun readSubscriptionPage(resultSet: ResultSet) = CoreSubscriptionPage(
        url = resultSet.getString("url"),
        title = resultSet.getString("title"),
        iconUrl = resultSet.getString("iconUrl"),
        category = resultSet.getString("category"),
        lastUpdatedAt = resultSet.getLong("lastUpdatedAt")
    )

    private fun readUpdateSchedule(resultSet: ResultSet) = CoreUpdateSchedule(
        enabled = resultSet.getBooleanAsInteger("enabled"),
        intervalMinutes = resultSet.getInt("intervalMinutes").coerceIn(15, 1440),
        nextRunAt = resultSet.getLong("nextRunAt"),
        lastRunAt = resultSet.getLong("lastRunAt"),
        lastSummary = resultSet.getString("lastSummary")
    )

    private fun readChapterDownloadTask(resultSet: ResultSet) = DesktopChapterDownloadTaskRecord(
        taskId = resultSet.getString("taskId"),
        bookUrl = resultSet.getString("bookUrl"),
        status = resultSet.getString("status"),
        total = resultSet.getInt("total"),
        completed = resultSet.getInt("completed"),
        skipped = resultSet.getInt("skipped"),
        downloaded = resultSet.getInt("downloaded"),
        failed = resultSet.getInt("failed"),
        items = chapterDownloadRecordCodec.decode(resultSet.getString("itemsJson")),
        error = resultSet.getString("error"),
        createdAt = resultSet.getLong("createdAt"),
        updatedAt = resultSet.getLong("updatedAt")
    )

    private fun readCoreChapterDownloadTask(resultSet: ResultSet) = CoreChapterDownloadTask(
        taskId = resultSet.getString("taskId"),
        bookUrl = resultSet.getString("bookUrl"),
        status = resultSet.getString("status"),
        total = resultSet.getInt("total"),
        completed = resultSet.getInt("completed"),
        skipped = resultSet.getInt("skipped"),
        downloaded = resultSet.getInt("downloaded"),
        failed = resultSet.getInt("failed"),
        items = chapterDownloadRecordCodec.decode(resultSet.getString("itemsJson")).map { item ->
            CoreChapterDownloadItem(
                chapter = item.chapter,
                status = item.status,
                error = item.error
            )
        },
        error = resultSet.getString("error"),
        createdAt = resultSet.getLong("createdAt"),
        updatedAt = resultSet.getLong("updatedAt")
    )

    private fun bindReplaceRule(statement: PreparedStatement, rule: CoreReplaceRule) {
        statement.setLong(1, rule.id)
        statement.setString(2, rule.name)
        statement.setNullableString(3, rule.group)
        statement.setString(4, rule.pattern)
        statement.setString(5, rule.replacement)
        statement.setNullableString(6, rule.scope)
        statement.setBooleanAsInteger(7, rule.scopeTitle)
        statement.setBooleanAsInteger(8, rule.scopeContent)
        statement.setNullableString(9, rule.excludeScope)
        statement.setBooleanAsInteger(10, rule.enabled)
        statement.setBooleanAsInteger(11, rule.isRegex)
        statement.setLong(12, rule.timeoutMillisecond)
        statement.setInt(13, rule.order)
    }

    private fun <T> queryList(
        sql: String,
        bind: (PreparedStatement) -> Unit = {},
        mapper: (ResultSet) -> T
    ): List<T> = connection.prepareStatement(sql).use { statement ->
        bind(statement)
        statement.executeQuery().use { resultSet ->
            buildList {
                while (resultSet.next()) add(mapper(resultSet))
            }
        }
    }

    private fun <T> queryOne(
        sql: String,
        bind: (PreparedStatement) -> Unit,
        mapper: (ResultSet) -> T
    ): T? = connection.prepareStatement(sql).use { statement ->
        bind(statement)
        statement.executeQuery().use { resultSet ->
            if (resultSet.next()) mapper(resultSet) else null
        }
    }

    private fun ensureColumn(
        statement: java.sql.Statement,
        table: String,
        column: String,
        definition: String
    ) {
        val exists = statement.executeQuery("PRAGMA table_info($table)").use { resultSet ->
            generateSequence {
                if (resultSet.next()) resultSet.getString("name") else null
            }.any { it == column }
        }
        if (!exists) {
            statement.execute("ALTER TABLE $table ADD COLUMN $column $definition")
        }
    }

    private fun PreparedStatement.setNullableString(index: Int, value: String?) {
        if (value == null) setNull(index, Types.VARCHAR) else setString(index, value)
    }

    private fun PreparedStatement.setNullableLong(index: Int, value: Long?) {
        if (value == null) setNull(index, Types.INTEGER) else setLong(index, value)
    }

    private fun PreparedStatement.setBooleanAsInteger(index: Int, value: Boolean) {
        setInt(index, if (value) 1 else 0)
    }

    private fun PreparedStatement.setNullableBooleanAsInteger(index: Int, value: Boolean?) {
        if (value == null) setNull(index, Types.INTEGER) else setBooleanAsInteger(index, value)
    }

    private fun ResultSet.getBooleanAsInteger(column: String): Boolean = getInt(column) != 0

    private fun ResultSet.getNullableBooleanAsInteger(column: String): Boolean? =
        getInt(column).let { if (wasNull()) null else it != 0 }

    private fun ResultSet.getNullableLong(column: String): Long? =
        getLong(column).let { if (wasNull()) null else it }

    private fun String?.toCoreReaderTheme(): CoreReaderTheme =
        runCatching { CoreReaderTheme.valueOf(this.orEmpty()) }.getOrDefault(CoreReaderTheme.DAY)

    private fun String?.toCoreReaderPageMode(): CoreReaderPageMode =
        runCatching { CoreReaderPageMode.valueOf(this.orEmpty()) }.getOrDefault(CoreReaderPageMode.SCROLL)

    private val chapterDownloadRecordCodec = ChapterDownloadRecordCodec()

    private companion object {
        const val BOOK_UPSERT = """
            INSERT INTO books (
                bookUrl, tocUrl, origin, originName, name, author, kind, customTag,
                coverUrl, customCoverUrl, intro, customIntro, charset, type, `group`,
                latestChapterTitle, latestChapterTime, lastCheckTime, lastCheckCount,
                totalChapterNum, durChapterTitle, durChapterIndex, durChapterPos,
                durChapterTime, wordCount, canUpdate, `order`, originOrder, variable,
                readConfig, syncTime
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(bookUrl) DO UPDATE SET
                tocUrl = excluded.tocUrl, origin = excluded.origin, originName = excluded.originName,
                name = excluded.name, author = excluded.author, kind = excluded.kind,
                customTag = excluded.customTag, coverUrl = excluded.coverUrl,
                customCoverUrl = excluded.customCoverUrl, intro = excluded.intro,
                customIntro = excluded.customIntro, charset = excluded.charset, type = excluded.type,
                `group` = excluded.`group`, latestChapterTitle = excluded.latestChapterTitle,
                latestChapterTime = excluded.latestChapterTime, lastCheckTime = excluded.lastCheckTime,
                lastCheckCount = excluded.lastCheckCount, totalChapterNum = excluded.totalChapterNum,
                durChapterTitle = excluded.durChapterTitle, durChapterIndex = excluded.durChapterIndex,
                durChapterPos = excluded.durChapterPos, durChapterTime = excluded.durChapterTime,
                wordCount = excluded.wordCount, canUpdate = excluded.canUpdate, `order` = excluded.`order`,
                originOrder = excluded.originOrder, variable = excluded.variable,
                readConfig = excluded.readConfig, syncTime = excluded.syncTime
        """
        const val SOURCE_UPSERT = """
            INSERT INTO book_sources (
                bookSourceUrl, bookSourceName, bookSourceGroup, bookSourceType, bookUrlPattern,
                customOrder, enabled, enabledExplore, enabledReview, jsLib, enabledCookieJar,
                enableDangerousApi, concurrentRate, header, loginUrl, loginUi, loginCheckJs,
                coverDecodeJs, bookSourceComment, variableComment, lastUpdateTime, respondTime,
                weight, exploreUrl, exploreScreen, exploreStyle, ruleExplore, searchUrl,
                ruleSearch, ruleBookInfo, ruleToc, ruleContent, ruleReview
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(bookSourceUrl) DO UPDATE SET
                bookSourceName = excluded.bookSourceName, bookSourceGroup = excluded.bookSourceGroup,
                bookSourceType = excluded.bookSourceType, bookUrlPattern = excluded.bookUrlPattern,
                customOrder = excluded.customOrder, enabled = excluded.enabled,
                enabledExplore = excluded.enabledExplore, enabledReview = excluded.enabledReview,
                jsLib = excluded.jsLib, enabledCookieJar = excluded.enabledCookieJar,
                enableDangerousApi = excluded.enableDangerousApi, concurrentRate = excluded.concurrentRate,
                header = excluded.header, loginUrl = excluded.loginUrl, loginUi = excluded.loginUi,
                loginCheckJs = excluded.loginCheckJs, coverDecodeJs = excluded.coverDecodeJs,
                bookSourceComment = excluded.bookSourceComment, variableComment = excluded.variableComment,
                lastUpdateTime = excluded.lastUpdateTime, respondTime = excluded.respondTime,
                weight = excluded.weight, exploreUrl = excluded.exploreUrl,
                exploreScreen = excluded.exploreScreen, exploreStyle = excluded.exploreStyle,
                ruleExplore = excluded.ruleExplore, searchUrl = excluded.searchUrl,
                ruleSearch = excluded.ruleSearch, ruleBookInfo = excluded.ruleBookInfo,
                ruleToc = excluded.ruleToc, ruleContent = excluded.ruleContent, ruleReview = excluded.ruleReview
        """
        const val COOKIE_UPSERT = """
            INSERT INTO cookies(domain, path, name, value, persistent, expiresAt)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(domain, path, name) DO UPDATE SET
                value = excluded.value,
                persistent = excluded.persistent,
                expiresAt = excluded.expiresAt
        """
        const val SOURCE_VARIABLE_UPSERT = """
            INSERT INTO source_variables(sourceUrl, value) VALUES (?, ?)
            ON CONFLICT(sourceUrl) DO UPDATE SET value = excluded.value
        """
        const val WEB_READ_CONFIG_UPSERT = """
            INSERT INTO desktop_web_read_config(id, configJson) VALUES (?, ?)
            ON CONFLICT(id) DO UPDATE SET configJson = excluded.configJson
        """
        const val WEB_DAV_CONFIG_UPSERT = """
            INSERT INTO desktop_webdav_config(id, url, username, password, bookDirectory) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                url = excluded.url, username = excluded.username, password = excluded.password,
                bookDirectory = excluded.bookDirectory
        """
        const val CHAPTER_UPSERT = """
            INSERT INTO chapters (
                url, title, isVolume, bookUrl, `index`, isVip, isPay, resourceUrl, tag,
                wordCount, start, end, startFragmentId, endFragmentId, variable
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(bookUrl, url) DO UPDATE SET
                title = excluded.title, isVolume = excluded.isVolume, `index` = excluded.`index`,
                isVip = excluded.isVip, isPay = excluded.isPay, resourceUrl = excluded.resourceUrl,
                tag = excluded.tag, wordCount = excluded.wordCount, start = excluded.start,
                end = excluded.end, startFragmentId = excluded.startFragmentId,
                endFragmentId = excluded.endFragmentId, variable = excluded.variable
        """
        const val CONTENT_UPSERT = """
            INSERT INTO chapter_contents (bookUrl, chapterUrl, content)
            VALUES (?, ?, ?)
            ON CONFLICT(bookUrl, chapterUrl) DO UPDATE SET content = excluded.content
        """
        const val GROUP_UPSERT = """
            INSERT INTO book_groups(
                groupId, groupName, cover, `order`, enableRefresh, show, bookSort
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(groupId) DO UPDATE SET
                groupName = excluded.groupName, cover = excluded.cover,
                `order` = excluded.`order`, enableRefresh = excluded.enableRefresh,
                show = excluded.show, bookSort = excluded.bookSort
        """
        const val BOOKMARK_UPSERT = """
            INSERT INTO bookmarks(
                time, bookName, bookAuthor, chapterIndex, chapterPos,
                chapterName, bookText, content
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(time) DO UPDATE SET
                bookName = excluded.bookName, bookAuthor = excluded.bookAuthor,
                chapterIndex = excluded.chapterIndex, chapterPos = excluded.chapterPos,
                chapterName = excluded.chapterName, bookText = excluded.bookText,
                content = excluded.content
        """
        const val READ_RECORD_UPSERT = """
            INSERT INTO readRecord(bookName, day, startSec, endSec)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(bookName, day, startSec) DO UPDATE SET endSec = excluded.endSec
        """
        const val READER_SETTINGS_UPSERT = """
            INSERT INTO desktop_settings(
                id, textSize, lineSpacingExtra, theme, pageMode, autoRead, autoReadSpeedSeconds
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                textSize = excluded.textSize, lineSpacingExtra = excluded.lineSpacingExtra,
                theme = excluded.theme, pageMode = excluded.pageMode,
                autoRead = excluded.autoRead,
                autoReadSpeedSeconds = excluded.autoReadSpeedSeconds
        """
        const val REPLACE_RULE_UPSERT = """
            INSERT INTO replace_rules(
                id, name, `group`, pattern, replacement, scope, scopeTitle,
                scopeContent, excludeScope, isEnabled, isRegex, timeoutMillisecond, sortOrder
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name, `group` = excluded.`group`, pattern = excluded.pattern,
                replacement = excluded.replacement, scope = excluded.scope,
                scopeTitle = excluded.scopeTitle, scopeContent = excluded.scopeContent,
                excludeScope = excluded.excludeScope, isEnabled = excluded.isEnabled,
                isRegex = excluded.isRegex, timeoutMillisecond = excluded.timeoutMillisecond,
                sortOrder = excluded.sortOrder
        """
        const val DICT_RULE_UPSERT = """
            INSERT INTO dictRules(name, urlRule, showRule, enabled, sortNumber)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(name) DO UPDATE SET
                urlRule = excluded.urlRule,
                showRule = excluded.showRule,
                enabled = excluded.enabled,
                sortNumber = excluded.sortNumber
        """
        const val TXT_TOC_RULE_UPSERT = """
            INSERT INTO txtTocRules(id, name, rule, example, serialNumber, enable)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                rule = excluded.rule,
                example = excluded.example,
                serialNumber = excluded.serialNumber,
                enable = excluded.enable
        """
        const val SOURCE_FILTER_RULE_UPSERT = """
            INSERT INTO source_filter_rules(id, name, enabled, pattern, fields, scope, sortOrder, createTime)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                enabled = excluded.enabled,
                pattern = excluded.pattern,
                fields = excluded.fields,
                scope = excluded.scope,
                sortOrder = excluded.sortOrder,
                createTime = excluded.createTime
        """
        const val SUBSCRIPTION_PAGE_UPSERT = """
            INSERT INTO subscription_pages(url, title, iconUrl, category, lastUpdatedAt)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(url) DO UPDATE SET
                title = excluded.title, iconUrl = excluded.iconUrl,
                category = excluded.category, lastUpdatedAt = excluded.lastUpdatedAt
        """
        const val UPDATE_SCHEDULE_UPSERT = """
            INSERT INTO desktop_update_schedule(
                id, enabled, intervalMinutes, nextRunAt, lastRunAt, lastSummary
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                enabled = excluded.enabled,
                intervalMinutes = excluded.intervalMinutes,
                nextRunAt = excluded.nextRunAt,
                lastRunAt = excluded.lastRunAt,
                lastSummary = excluded.lastSummary
        """
        const val CHAPTER_DOWNLOAD_TASK_UPSERT = """
            INSERT INTO desktop_chapter_download_tasks(
                taskId, bookUrl, status, total, completed, skipped, downloaded, failed,
                itemsJson, error, createdAt, updatedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(taskId) DO UPDATE SET
                bookUrl = excluded.bookUrl,
                status = excluded.status,
                total = excluded.total,
                completed = excluded.completed,
                skipped = excluded.skipped,
                downloaded = excluded.downloaded,
                failed = excluded.failed,
                itemsJson = excluded.itemsJson,
                error = excluded.error,
                createdAt = excluded.createdAt,
                updatedAt = excluded.updatedAt
        """

    }
}
