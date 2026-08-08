package io.legado.core.library

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Portable backup format for the desktop reader.
 *
 * The archive is deliberately made of plain JSON files so it can be inspected
 * and converted by tools outside the desktop application.
 */
class CoreBackupService(
    private val gson: Gson = Gson()
) {

    fun export(library: CoreLibrary, archive: Path): CoreBackupSummary {
        val books = library.books()
        val chapters = books.flatMap { library.chapters(it.bookUrl) }
        val bookmarks = collectBookmarks(library)
        val replaceRules = library.replaceRules()
        val dictRules = library.dictRules()
        val txtTocRules = library.txtTocRules()
        val sourceFilterRules = library.sourceFilterRules()
        val subscriptionPages = library.subscriptionPages()
        val updateSchedule = library.updateSchedule()
        val chapterDownloadTasks = library.chapterDownloadTasks()
        val sourceVariables = library.sourceVariables()
        val cookies = library.cookies().filter(CoreCookie::persistent)
        val contents = chapters.mapNotNull { chapter ->
            library.content(chapter)?.let { CoreChapterContent(chapter.bookUrl, chapter.url, it) }
        }
        val manifest = CoreBackupManifest(
            format = FORMAT,
            version = VERSION,
            books = books.size,
            chapters = chapters.size,
            chapterContents = contents.size,
            groups = library.groups().size,
            sources = library.sources().size,
            bookmarks = bookmarks.size,
            readRecords = library.readRecords().size,
            replaceRules = replaceRules.size,
            dictRules = dictRules.size,
            txtTocRules = txtTocRules.size,
            sourceFilterRules = sourceFilterRules.size,
            subscriptionPages = subscriptionPages.size,
            chapterDownloadTasks = chapterDownloadTasks.size,
            sourceVariables = sourceVariables.size,
            cookies = cookies.size,
            hasReaderSettings = true
        )
        val files = linkedMapOf(
            MANIFEST_FILE to gson.toJson(manifest),
            BOOKS_FILE to gson.toJson(books),
            CHAPTERS_FILE to gson.toJson(chapters),
            CONTENTS_FILE to gson.toJson(contents),
            GROUPS_FILE to gson.toJson(library.groups()),
            SOURCES_FILE to gson.toJson(library.sources()),
            BOOKMARKS_FILE to gson.toJson(bookmarks),
            READ_RECORDS_FILE to gson.toJson(library.readRecords()),
            READER_SETTINGS_FILE to gson.toJson(library.readerSettings()),
            REPLACE_RULES_FILE to gson.toJson(replaceRules),
            DICT_RULES_FILE to gson.toJson(dictRules),
            TXT_TOC_RULES_FILE to gson.toJson(txtTocRules),
            SOURCE_FILTER_RULES_FILE to gson.toJson(sourceFilterRules),
            SUBSCRIPTION_PAGES_FILE to gson.toJson(subscriptionPages),
            UPDATE_SCHEDULE_FILE to gson.toJson(updateSchedule),
            CHAPTER_DOWNLOAD_TASKS_FILE to gson.toJson(chapterDownloadTasks),
            SOURCE_VARIABLES_FILE to gson.toJson(sourceVariables),
            COOKIES_FILE to gson.toJson(cookies)
        )
        archive.toAbsolutePath().parent?.let(Files::createDirectories)
        Files.newOutputStream(archive).use { output ->
            ZipOutputStream(output).use { zip ->
                files.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
        return manifest.toSummary()
    }

    fun import(library: CoreLibrary, archive: Path): CoreBackupSummary {
        require(Files.isRegularFile(archive)) { "备份文件不存在: $archive" }
        val entries = readEntries(archive)
        val payload = parsePayload(entries)

        // All parsing and relationship validation happens before the first write.
        payload.books.forEach { library.saveBook(it) }
        payload.sources.forEach { library.saveSource(it) }
        payload.groups.forEach { library.saveGroup(it) }
        payload.chapters.forEach { library.saveChapter(it) }
        payload.contents.forEach { content ->
            val chapter = payload.chapters.first { it.bookUrl == content.bookUrl && it.url == content.chapterUrl }
            library.saveContent(chapter, content.content)
        }
        payload.bookmarks.forEach(library::saveBookmark)
        payload.readRecords.forEach(library::saveReadRecord)
        payload.replaceRules.forEach(library::saveReplaceRule)
        payload.dictRules.forEach(library::saveDictRule)
        payload.txtTocRules.forEach(library::saveTxtTocRule)
        payload.sourceFilterRules.forEach(library::saveSourceFilterRule)
        payload.subscriptionPages.forEach(library::saveSubscriptionPage)
        library.saveReaderSettings(payload.readerSettings)
        library.saveUpdateSchedule(payload.updateSchedule)
        payload.chapterDownloadTasks.forEach(library::saveChapterDownloadTask)
        payload.sourceVariables.forEach { variable ->
            library.saveSourceVariable(variable.sourceUrl, variable.value)
        }
        payload.cookies.forEach(library::saveCookie)
        return payload.manifest.toSummary()
    }

    private fun collectBookmarks(library: CoreLibrary): List<CoreBookmark> {
        return library.allBookmarks().distinctBy(CoreBookmark::time)
    }

    private fun parsePayload(entries: Map<String, String>): ParsedPayload {
        val manifest = parseRequired<CoreBackupManifest>(entries, MANIFEST_FILE)
        require(manifest.format == FORMAT) { "不支持的备份格式: ${manifest.format}" }
        require(manifest.version == VERSION) { "不支持的备份版本: ${manifest.version}" }

        val books = parseRequiredList<CoreBook>(entries, BOOKS_FILE)
        val chapters = parseRequiredList<CoreChapter>(entries, CHAPTERS_FILE)
        val contents = parseRequiredList<CoreChapterContent>(entries, CONTENTS_FILE)
        val groups = parseRequiredList<CoreBookGroup>(entries, GROUPS_FILE)
        val sources = parseRequiredList<CoreBookSource>(entries, SOURCES_FILE)
        val bookmarks = parseRequiredList<CoreBookmark>(entries, BOOKMARKS_FILE)
        val readRecords = parseRequiredList<CoreReadRecord>(entries, READ_RECORDS_FILE)
        val settings = parseRequired<CoreReaderSettings>(entries, READER_SETTINGS_FILE)
        val replaceRules = parseOptionalList<CoreReplaceRule>(entries, REPLACE_RULES_FILE)
        val dictRules = parseOptionalList<CoreDictRule>(entries, DICT_RULES_FILE)
        val txtTocRules = parseOptionalList<CoreTxtTocRule>(entries, TXT_TOC_RULES_FILE)
        val sourceFilterRules = parseOptionalList<CoreSourceFilterRule>(entries, SOURCE_FILTER_RULES_FILE)
        val subscriptionPages = parseOptionalList<CoreSubscriptionPage>(entries, SUBSCRIPTION_PAGES_FILE)
        val updateSchedule = parseOptional<CoreUpdateSchedule>(entries, UPDATE_SCHEDULE_FILE)
        val chapterDownloadTasks = parseOptionalList<CoreChapterDownloadTask>(entries, CHAPTER_DOWNLOAD_TASKS_FILE)
        val sourceVariables = parseOptionalList<CoreSourceVariable>(entries, SOURCE_VARIABLES_FILE)
        val cookies = parseOptionalList<CoreCookie>(entries, COOKIES_FILE)

        require(manifest.books == books.size) { "备份书籍数量不一致" }
        require(manifest.chapters == chapters.size) { "备份章节数量不一致" }
        require(manifest.chapterContents == contents.size) { "备份正文数量不一致" }
        require(manifest.groups == groups.size) { "备份分组数量不一致" }
        require(manifest.sources == sources.size) { "备份书源数量不一致" }
        require(manifest.bookmarks == bookmarks.size) { "备份书签数量不一致" }
        require(manifest.readRecords == readRecords.size) { "备份阅读记录数量不一致" }
        require(manifest.replaceRules == replaceRules.size) { "备份替换规则数量不一致" }
        require(manifest.dictRules == dictRules.size) { "备份字典规则数量不一致" }
        require(manifest.txtTocRules == txtTocRules.size) { "备份TXT目录规则数量不一致" }
        require(manifest.sourceFilterRules == sourceFilterRules.size) { "备份书源筛选规则数量不一致" }
        require(manifest.subscriptionPages == subscriptionPages.size) { "备份订阅页数量不一致" }
        require(manifest.chapterDownloadTasks == chapterDownloadTasks.size) { "备份章节下载任务数量不一致" }
        require(manifest.sourceVariables == sourceVariables.size) { "备份书源变量数量不一致" }
        require(manifest.cookies == cookies.size) { "备份 Cookie 数量不一致" }
        require(manifest.hasReaderSettings) { "备份缺少阅读设置" }
        require(books.map(CoreBook::bookUrl).distinct().size == books.size) { "备份包含重复书籍" }
        require(chapters.map { it.bookUrl to it.url }.distinct().size == chapters.size) { "备份包含重复章节" }
        require(groups.map(CoreBookGroup::groupId).distinct().size == groups.size) { "备份包含重复分组" }
        require(sources.map(CoreBookSource::bookSourceUrl).distinct().size == sources.size) { "备份包含重复书源" }
        require(bookmarks.map(CoreBookmark::time).distinct().size == bookmarks.size) { "备份包含重复书签" }
        require(replaceRules.map(CoreReplaceRule::id).distinct().size == replaceRules.size) {
            "备份包含重复替换规则"
        }
        require(dictRules.map(CoreDictRule::name).distinct().size == dictRules.size) {
            "备份包含重复字典规则"
        }
        require(txtTocRules.map(CoreTxtTocRule::id).distinct().size == txtTocRules.size) {
            "备份包含重复TXT目录规则"
        }
        require(sourceFilterRules.map(CoreSourceFilterRule::id).distinct().size == sourceFilterRules.size) {
            "备份包含重复书源筛选规则"
        }
        require(subscriptionPages.map(CoreSubscriptionPage::url).distinct().size == subscriptionPages.size) {
            "备份包含重复订阅页"
        }
        require(chapterDownloadTasks.map(CoreChapterDownloadTask::taskId).distinct().size == chapterDownloadTasks.size) {
            "备份包含重复章节下载任务"
        }
        val bookUrls = books.mapTo(hashSetOf(), CoreBook::bookUrl)
        val chapterKeys = chapters.mapTo(hashSetOf()) { it.bookUrl to it.url }
        require(chapters.all { it.bookUrl in bookUrls }) { "备份包含无所属书籍的章节" }
        require(contents.all { it.bookUrl to it.chapterUrl in chapterKeys }) { "备份包含无所属章节的正文" }
        return ParsedPayload(
            manifest = manifest,
            books = books,
            chapters = chapters,
            contents = contents,
            groups = groups,
            sources = sources,
            bookmarks = bookmarks,
            readRecords = readRecords,
            replaceRules = replaceRules,
            dictRules = dictRules,
            txtTocRules = txtTocRules,
            sourceFilterRules = sourceFilterRules,
            subscriptionPages = subscriptionPages,
            chapterDownloadTasks = chapterDownloadTasks,
            updateSchedule = updateSchedule,
            readerSettings = settings,
            sourceVariables = sourceVariables,
            cookies = cookies
        )
    }

    private fun readEntries(archive: Path): Map<String, String> {
        val result = linkedMapOf<String, String>()
        Files.newInputStream(archive).use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory) { "备份包含目录条目: ${entry.name}" }
                    require(entry.name in FILES) { "备份包含未知条目: ${entry.name}" }
                    require(entry.name.indexOf('/') < 0 && entry.name.indexOf('\\') < 0) {
                        "备份条目路径非法: ${entry.name}"
                    }
                    require(result.put(entry.name, zip.readBytes().toString(StandardCharsets.UTF_8)) == null) {
                        "备份包含重复条目: ${entry.name}"
                    }
                }
            }
        }
        return result
    }

    private inline fun <reified T> parseRequired(entries: Map<String, String>, name: String): T {
        val json = entries[name] ?: error("备份缺少文件: $name")
        return try {
            gson.fromJson(json, T::class.java)
                ?: error("备份文件为空: $name")
        } catch (exception: JsonParseException) {
            throw IllegalArgumentException("备份文件格式错误: $name", exception)
        }
    }

    private inline fun <reified T> parseRequiredList(entries: Map<String, String>, name: String): List<T> {
        val json = entries[name] ?: error("备份缺少文件: $name")
        return try {
            val type = TypeToken.getParameterized(List::class.java, T::class.java).type
            gson.fromJson<List<T>>(json, type) ?: emptyList()
        } catch (exception: JsonParseException) {
            throw IllegalArgumentException("备份文件格式错误: $name", exception)
        }
    }

    private inline fun <reified T> parseOptionalList(entries: Map<String, String>, name: String): List<T> {
        val json = entries[name] ?: return emptyList()
        return try {
            val type = TypeToken.getParameterized(List::class.java, T::class.java).type
            gson.fromJson<List<T>>(json, type) ?: emptyList()
        } catch (exception: JsonParseException) {
            throw IllegalArgumentException("备份文件格式错误: $name", exception)
        }
    }

    private inline fun <reified T> parseOptional(entries: Map<String, String>, name: String): T {
        val json = entries[name] ?: return when (T::class) {
            CoreUpdateSchedule::class -> CoreUpdateSchedule() as T
            else -> error("备份缺少可选文件: $name")
        }
        return try {
            gson.fromJson(json, T::class.java)
                ?: error("备份文件为空: $name")
        } catch (exception: JsonParseException) {
            throw IllegalArgumentException("备份文件格式错误: $name", exception)
        }
    }

    private data class ParsedPayload(
        val manifest: CoreBackupManifest,
        val books: List<CoreBook>,
        val chapters: List<CoreChapter>,
        val contents: List<CoreChapterContent>,
        val groups: List<CoreBookGroup>,
        val sources: List<CoreBookSource>,
        val bookmarks: List<CoreBookmark>,
        val readRecords: List<CoreReadRecord>,
        val replaceRules: List<CoreReplaceRule>,
        val dictRules: List<CoreDictRule>,
        val txtTocRules: List<CoreTxtTocRule>,
        val sourceFilterRules: List<CoreSourceFilterRule>,
        val subscriptionPages: List<CoreSubscriptionPage>,
        val chapterDownloadTasks: List<CoreChapterDownloadTask>,
        val updateSchedule: CoreUpdateSchedule,
        val readerSettings: CoreReaderSettings,
        val sourceVariables: List<CoreSourceVariable>,
        val cookies: List<CoreCookie>
    )

    private data class CoreBackupManifest(
        val format: String,
        val version: Int,
        val books: Int,
        val chapters: Int,
        val chapterContents: Int,
        val groups: Int,
        val sources: Int,
        val bookmarks: Int,
        val readRecords: Int,
        val replaceRules: Int = 0,
        val dictRules: Int = 0,
        val txtTocRules: Int = 0,
        val sourceFilterRules: Int = 0,
        val subscriptionPages: Int = 0,
        val chapterDownloadTasks: Int = 0,
        val sourceVariables: Int = 0,
        val cookies: Int = 0,
        val hasReaderSettings: Boolean
    ) {
        fun toSummary() = CoreBackupSummary(
            books = books,
            chapters = chapters,
            chapterContents = chapterContents,
            groups = groups,
            sources = sources,
            bookmarks = bookmarks,
            readRecords = readRecords,
            replaceRules = replaceRules,
            dictRules = dictRules,
            txtTocRules = txtTocRules,
            sourceFilterRules = sourceFilterRules,
            subscriptionPages = subscriptionPages,
            chapterDownloadTasks = chapterDownloadTasks,
            sourceVariables = sourceVariables,
            cookies = cookies
        )
    }

    companion object {
        private const val FORMAT = "legado-desktop-backup"
        private const val VERSION = 1
        private const val MANIFEST_FILE = "manifest.json"
        private const val BOOKS_FILE = "bookshelf.json"
        private const val CHAPTERS_FILE = "chapters.json"
        private const val CONTENTS_FILE = "chapterContents.json"
        private const val GROUPS_FILE = "bookGroup.json"
        private const val SOURCES_FILE = "bookSource.json"
        private const val BOOKMARKS_FILE = "bookmark.json"
        private const val READ_RECORDS_FILE = "readRecord.json"
        private const val READER_SETTINGS_FILE = "readerSettings.json"
        private const val REPLACE_RULES_FILE = "replaceRule.json"
        private const val DICT_RULES_FILE = "dictRule.json"
        private const val TXT_TOC_RULES_FILE = "txtTocRule.json"
        private const val SOURCE_FILTER_RULES_FILE = "sourceFilterRule.json"
        private const val SUBSCRIPTION_PAGES_FILE = "subscriptionPage.json"
        private const val UPDATE_SCHEDULE_FILE = "updateSchedule.json"
        private const val CHAPTER_DOWNLOAD_TASKS_FILE = "chapterDownloadTask.json"
        private const val SOURCE_VARIABLES_FILE = "sourceVariables.json"
        private const val COOKIES_FILE = "cookies.json"
        private val FILES = setOf(
            MANIFEST_FILE,
            BOOKS_FILE,
            CHAPTERS_FILE,
            CONTENTS_FILE,
            GROUPS_FILE,
            SOURCES_FILE,
            BOOKMARKS_FILE,
            READ_RECORDS_FILE,
            READER_SETTINGS_FILE,
            REPLACE_RULES_FILE,
            DICT_RULES_FILE,
            TXT_TOC_RULES_FILE,
            SOURCE_FILTER_RULES_FILE,
            SUBSCRIPTION_PAGES_FILE,
            UPDATE_SCHEDULE_FILE,
            CHAPTER_DOWNLOAD_TASKS_FILE,
            SOURCE_VARIABLES_FILE,
            COOKIES_FILE
        )

        /** Test helper for malformed-archive validation without exposing ZIP details to callers. */
        fun writeTestArchive(archive: Path, entries: Map<String, String>) {
            archive.toAbsolutePath().parent?.let(Files::createDirectories)
            Files.newOutputStream(archive).use { output ->
                ZipOutputStream(output).use { zip ->
                    entries.forEach { (name, content) ->
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(content.toByteArray(StandardCharsets.UTF_8))
                        zip.closeEntry()
                    }
                }
            }
        }
    }
}

data class CoreChapterContent(
    val bookUrl: String,
    val chapterUrl: String,
    val content: String
)

data class CoreBackupSummary(
    val books: Int,
    val chapters: Int,
    val chapterContents: Int,
    val groups: Int,
    val sources: Int,
    val bookmarks: Int,
    val readRecords: Int,
    val replaceRules: Int = 0,
    val dictRules: Int = 0,
    val txtTocRules: Int = 0,
    val sourceFilterRules: Int = 0,
    val subscriptionPages: Int = 0,
    val chapterDownloadTasks: Int = 0,
    val sourceVariables: Int = 0,
    val cookies: Int = 0
)
