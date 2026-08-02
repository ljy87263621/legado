package io.legado.core.library

interface CoreLibrary {
    fun books(): List<CoreBook>
    fun book(bookUrl: String): CoreBook?
    fun saveBook(book: CoreBook)
    fun deleteBook(bookUrl: String)

    fun sources(): List<CoreBookSource>
    fun source(bookSourceUrl: String): CoreBookSource?
    fun saveSource(source: CoreBookSource)
    fun deleteSource(bookSourceUrl: String)

    fun enabledSources(): List<CoreBookSource> = sources().filter(CoreBookSource::enabled)

    fun chapters(bookUrl: String): List<CoreChapter>
    fun saveChapter(chapter: CoreChapter)

    fun content(chapter: CoreChapter): String?
    fun saveContent(chapter: CoreChapter, content: String)

    fun groups(): List<CoreBookGroup>
    fun saveGroup(group: CoreBookGroup)
    fun deleteGroup(groupId: Long)

    fun bookmarks(bookName: String, bookAuthor: String): List<CoreBookmark>
    fun saveBookmark(bookmark: CoreBookmark)
    fun deleteBookmark(time: Long)

    fun readRecords(): List<CoreReadRecord>
    fun saveReadRecord(record: CoreReadRecord)
    fun deleteReadRecords(bookName: String)

    fun readerSettings(): CoreReaderSettings
    fun saveReaderSettings(settings: CoreReaderSettings)
}

class InMemoryCoreLibrary : CoreLibrary {
    private val booksByUrl = linkedMapOf<String, CoreBook>()
    private val sourcesByUrl = linkedMapOf<String, CoreBookSource>()
    private val chaptersByBook = linkedMapOf<String, LinkedHashMap<String, CoreChapter>>()
    private val groupsById = linkedMapOf<Long, CoreBookGroup>()
    private val bookmarksByTime = linkedMapOf<Long, CoreBookmark>()
    private val readRecordsByKey = linkedMapOf<Triple<String, Int, Long>, CoreReadRecord>()
    private var currentReaderSettings = CoreReaderSettings()

    override fun books(): List<CoreBook> = booksByUrl.values.toList()

    override fun book(bookUrl: String): CoreBook? = booksByUrl[bookUrl]

    override fun saveBook(book: CoreBook) {
        booksByUrl[book.bookUrl] = book
    }

    override fun deleteBook(bookUrl: String) {
        booksByUrl.remove(bookUrl)
        chaptersByBook.remove(bookUrl)
        contentsByChapter.remove(bookUrl)
    }

    override fun sources(): List<CoreBookSource> = sourcesByUrl.values.toList()

    override fun source(bookSourceUrl: String): CoreBookSource? = sourcesByUrl[bookSourceUrl]

    override fun saveSource(source: CoreBookSource) {
        sourcesByUrl[source.bookSourceUrl] = source
    }

    override fun deleteSource(bookSourceUrl: String) {
        sourcesByUrl.remove(bookSourceUrl)
    }

    override fun chapters(bookUrl: String): List<CoreChapter> =
        chaptersByBook[bookUrl]
            ?.values
            ?.sortedBy(CoreChapter::index)
            ?: emptyList()

    override fun saveChapter(chapter: CoreChapter) {
        chaptersByBook
            .getOrPut(chapter.bookUrl) { linkedMapOf() }[chapter.url] = chapter
    }

    override fun content(chapter: CoreChapter): String? =
        contentsByChapter[chapter.bookUrl]?.get(chapter.url)

    override fun saveContent(chapter: CoreChapter, content: String) {
        contentsByChapter
            .getOrPut(chapter.bookUrl) { linkedMapOf() }[chapter.url] = content
    }

    override fun groups(): List<CoreBookGroup> = groupsById.values.sortedWith(
        compareBy<CoreBookGroup> { it.order }.thenBy { it.groupId }
    )

    override fun saveGroup(group: CoreBookGroup) {
        groupsById[group.groupId] = group
    }

    override fun deleteGroup(groupId: Long) {
        groupsById.remove(groupId)
    }

    override fun bookmarks(bookName: String, bookAuthor: String): List<CoreBookmark> =
        bookmarksByTime.values
            .filter { it.bookName == bookName && it.bookAuthor == bookAuthor }
            .sortedByDescending(CoreBookmark::time)

    override fun saveBookmark(bookmark: CoreBookmark) {
        bookmarksByTime[bookmark.time] = bookmark
    }

    override fun deleteBookmark(time: Long) {
        bookmarksByTime.remove(time)
    }

    override fun readRecords(): List<CoreReadRecord> = readRecordsByKey.values.toList()

    override fun saveReadRecord(record: CoreReadRecord) {
        readRecordsByKey[Triple(record.bookName, record.day, record.startSec)] = record
    }

    override fun deleteReadRecords(bookName: String) {
        readRecordsByKey.keys.removeIf { it.first == bookName }
    }

    override fun readerSettings(): CoreReaderSettings = currentReaderSettings

    override fun saveReaderSettings(settings: CoreReaderSettings) {
        currentReaderSettings = settings
    }

    private val contentsByChapter = linkedMapOf<String, LinkedHashMap<String, String>>()
}
