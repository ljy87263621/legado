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
            READER_SETTINGS_FILE to gson.toJson(library.readerSettings())
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
        library.saveReaderSettings(payload.readerSettings)
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

        require(manifest.books == books.size) { "备份书籍数量不一致" }
        require(manifest.chapters == chapters.size) { "备份章节数量不一致" }
        require(manifest.chapterContents == contents.size) { "备份正文数量不一致" }
        require(manifest.groups == groups.size) { "备份分组数量不一致" }
        require(manifest.sources == sources.size) { "备份书源数量不一致" }
        require(manifest.bookmarks == bookmarks.size) { "备份书签数量不一致" }
        require(manifest.readRecords == readRecords.size) { "备份阅读记录数量不一致" }
        require(manifest.hasReaderSettings) { "备份缺少阅读设置" }
        require(books.map(CoreBook::bookUrl).distinct().size == books.size) { "备份包含重复书籍" }
        require(chapters.map { it.bookUrl to it.url }.distinct().size == chapters.size) { "备份包含重复章节" }
        require(groups.map(CoreBookGroup::groupId).distinct().size == groups.size) { "备份包含重复分组" }
        require(sources.map(CoreBookSource::bookSourceUrl).distinct().size == sources.size) { "备份包含重复书源" }
        require(bookmarks.map(CoreBookmark::time).distinct().size == bookmarks.size) { "备份包含重复书签" }
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
            readerSettings = settings
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

    private data class ParsedPayload(
        val manifest: CoreBackupManifest,
        val books: List<CoreBook>,
        val chapters: List<CoreChapter>,
        val contents: List<CoreChapterContent>,
        val groups: List<CoreBookGroup>,
        val sources: List<CoreBookSource>,
        val bookmarks: List<CoreBookmark>,
        val readRecords: List<CoreReadRecord>,
        val readerSettings: CoreReaderSettings
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
        val hasReaderSettings: Boolean
    ) {
        fun toSummary() = CoreBackupSummary(
            books = books,
            chapters = chapters,
            chapterContents = chapterContents,
            groups = groups,
            sources = sources,
            bookmarks = bookmarks,
            readRecords = readRecords
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
        private val FILES = setOf(
            MANIFEST_FILE,
            BOOKS_FILE,
            CHAPTERS_FILE,
            CONTENTS_FILE,
            GROUPS_FILE,
            SOURCES_FILE,
            BOOKMARKS_FILE,
            READ_RECORDS_FILE,
            READER_SETTINGS_FILE
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
    val readRecords: Int
)
