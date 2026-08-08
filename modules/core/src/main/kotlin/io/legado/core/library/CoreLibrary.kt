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

    fun cookies(): List<CoreCookie> = emptyList()
    fun saveCookie(cookie: CoreCookie) = Unit
    fun deleteCookie(domain: String, path: String, name: String) = Unit

    fun sourceVariable(sourceUrl: String): String? = null
    fun saveSourceVariable(sourceUrl: String, value: String?) = Unit
    fun sourceVariables(): List<CoreSourceVariable> = emptyList()

    fun enabledSources(): List<CoreBookSource> = sources().filter(CoreBookSource::enabled)

    fun chapters(bookUrl: String): List<CoreChapter>
    fun deleteChapters(bookUrl: String)
    fun saveChapter(chapter: CoreChapter)

    fun content(chapter: CoreChapter): String?
    fun saveContent(chapter: CoreChapter, content: String)

    fun groups(): List<CoreBookGroup>
    fun saveGroup(group: CoreBookGroup)
    fun deleteGroup(groupId: Long)

    fun bookmarks(bookName: String, bookAuthor: String): List<CoreBookmark>
    fun allBookmarks(): List<CoreBookmark>
    fun saveBookmark(bookmark: CoreBookmark)
    fun deleteBookmark(time: Long)

    fun readRecords(): List<CoreReadRecord>
    fun saveReadRecord(record: CoreReadRecord)
    fun deleteReadRecords(bookName: String)

    fun readerSettings(): CoreReaderSettings
    fun saveReaderSettings(settings: CoreReaderSettings)

    /** Raw JSON for the web reader configuration; its schema belongs to modules:web. */
    fun webReadConfigJson(): String?
    fun saveWebReadConfigJson(configJson: String)

    fun replaceRules(): List<CoreReplaceRule>
    fun saveReplaceRule(rule: CoreReplaceRule)
    fun deleteReplaceRule(id: Long)

    fun dictRules(): List<CoreDictRule>
    fun enabledDictRules(): List<CoreDictRule> = dictRules().filter(CoreDictRule::enabled)
    fun dictRule(name: String): CoreDictRule?
    fun saveDictRule(rule: CoreDictRule)
    fun deleteDictRule(name: String)

    fun txtTocRules(): List<CoreTxtTocRule>
    fun enabledTxtTocRules(): List<CoreTxtTocRule> = txtTocRules().filter(CoreTxtTocRule::enable)
    fun txtTocRule(id: Long): CoreTxtTocRule?
    fun saveTxtTocRule(rule: CoreTxtTocRule)
    fun deleteTxtTocRule(id: Long)

    fun sourceFilterRules(): List<CoreSourceFilterRule>
    fun sourceFilterRule(id: String): CoreSourceFilterRule?
    fun saveSourceFilterRule(rule: CoreSourceFilterRule)
    fun deleteSourceFilterRule(id: String)

    fun subscriptionPages(): List<CoreSubscriptionPage>
    fun saveSubscriptionPage(page: CoreSubscriptionPage)
    fun deleteSubscriptionPage(url: String)

    fun updateSchedule(): CoreUpdateSchedule
    fun saveUpdateSchedule(schedule: CoreUpdateSchedule)

    fun chapterDownloadTasks(): List<CoreChapterDownloadTask>
    fun saveChapterDownloadTask(task: CoreChapterDownloadTask)
}

class InMemoryCoreLibrary : CoreLibrary {
    private val booksByUrl = linkedMapOf<String, CoreBook>()
    private val sourcesByUrl = linkedMapOf<String, CoreBookSource>()
    private val cookiesByKey = linkedMapOf<Triple<String, String, String>, CoreCookie>()
    private val sourceVariablesByUrl = linkedMapOf<String, String>()
    private val chaptersByBook = linkedMapOf<String, LinkedHashMap<String, CoreChapter>>()
    private val groupsById = linkedMapOf<Long, CoreBookGroup>()
    private val bookmarksByTime = linkedMapOf<Long, CoreBookmark>()
    private val readRecordsByKey = linkedMapOf<Triple<String, Int, Long>, CoreReadRecord>()
    private val replaceRulesById = linkedMapOf<Long, CoreReplaceRule>()
    private val dictRulesByName = linkedMapOf<String, CoreDictRule>()
    private val txtTocRulesById = linkedMapOf<Long, CoreTxtTocRule>()
    private val sourceFilterRulesById = linkedMapOf<String, CoreSourceFilterRule>()
    private val subscriptionPagesByUrl = linkedMapOf<String, CoreSubscriptionPage>()
    private val chapterDownloadTasksById = linkedMapOf<String, CoreChapterDownloadTask>()
    private var currentReaderSettings = CoreReaderSettings()
    private var currentWebReadConfigJson: String? = null
    private var currentUpdateSchedule = CoreUpdateSchedule()

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

    override fun cookies(): List<CoreCookie> = cookiesByKey.values.toList()

    override fun saveCookie(cookie: CoreCookie) {
        cookiesByKey[Triple(cookie.domain, cookie.path, cookie.name)] = cookie
    }

    override fun deleteCookie(domain: String, path: String, name: String) {
        cookiesByKey.remove(Triple(domain, path, name))
    }

    override fun sourceVariable(sourceUrl: String): String? = sourceVariablesByUrl[sourceUrl]

    override fun saveSourceVariable(sourceUrl: String, value: String?) {
        if (value == null) sourceVariablesByUrl.remove(sourceUrl)
        else sourceVariablesByUrl[sourceUrl] = value
    }

    override fun sourceVariables(): List<CoreSourceVariable> = sourceVariablesByUrl.map { (sourceUrl, value) ->
        CoreSourceVariable(sourceUrl, value)
    }

    override fun chapters(bookUrl: String): List<CoreChapter> =
        chaptersByBook[bookUrl]
            ?.values
            ?.sortedBy(CoreChapter::index)
            ?: emptyList()

    override fun deleteChapters(bookUrl: String) {
        chaptersByBook.remove(bookUrl)
        contentsByChapter.remove(bookUrl)
    }

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

    override fun allBookmarks(): List<CoreBookmark> =
        bookmarksByTime.values.sortedByDescending(CoreBookmark::time)

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

    override fun webReadConfigJson(): String? = currentWebReadConfigJson

    override fun saveWebReadConfigJson(configJson: String) {
        currentWebReadConfigJson = configJson
    }

    override fun replaceRules(): List<CoreReplaceRule> =
        replaceRulesById.values.sortedWith(compareBy<CoreReplaceRule> { it.order }.thenBy { it.id })

    override fun saveReplaceRule(rule: CoreReplaceRule) {
        replaceRulesById[rule.id] = rule
    }

    override fun deleteReplaceRule(id: Long) {
        replaceRulesById.remove(id)
    }

    override fun dictRules(): List<CoreDictRule> = dictRulesByName.values.sortedWith(
        compareBy<CoreDictRule> { it.sortNumber }.thenBy { it.name }
    )

    override fun dictRule(name: String): CoreDictRule? = dictRulesByName[name]

    override fun saveDictRule(rule: CoreDictRule) {
        dictRulesByName[rule.name] = rule
    }

    override fun deleteDictRule(name: String) {
        dictRulesByName.remove(name)
    }

    override fun txtTocRules(): List<CoreTxtTocRule> = txtTocRulesById.values.sortedWith(
        compareBy<CoreTxtTocRule> { it.serialNumber }.thenBy { it.id }
    )

    override fun txtTocRule(id: Long): CoreTxtTocRule? = txtTocRulesById[id]

    override fun saveTxtTocRule(rule: CoreTxtTocRule) {
        txtTocRulesById[rule.id] = rule
    }

    override fun deleteTxtTocRule(id: Long) {
        txtTocRulesById.remove(id)
    }

    override fun sourceFilterRules(): List<CoreSourceFilterRule> = sourceFilterRulesById.values.sortedWith(
        compareBy<CoreSourceFilterRule> { it.order }.thenBy { it.createTime }.thenBy { it.id }
    )

    override fun sourceFilterRule(id: String): CoreSourceFilterRule? = sourceFilterRulesById[id]

    override fun saveSourceFilterRule(rule: CoreSourceFilterRule) {
        sourceFilterRulesById[rule.id] = rule
    }

    override fun deleteSourceFilterRule(id: String) {
        sourceFilterRulesById.remove(id)
    }

    override fun subscriptionPages(): List<CoreSubscriptionPage> = subscriptionPagesByUrl.values
        .sortedWith(compareByDescending<CoreSubscriptionPage> { it.lastUpdatedAt }.thenBy { it.url })

    override fun saveSubscriptionPage(page: CoreSubscriptionPage) {
        subscriptionPagesByUrl[page.url] = page
    }

    override fun deleteSubscriptionPage(url: String) {
        subscriptionPagesByUrl.remove(url)
    }

    override fun updateSchedule(): CoreUpdateSchedule = currentUpdateSchedule

    override fun saveUpdateSchedule(schedule: CoreUpdateSchedule) {
        currentUpdateSchedule = schedule.normalized()
    }

    override fun chapterDownloadTasks(): List<CoreChapterDownloadTask> =
        chapterDownloadTasksById.values.sortedWith(
            compareBy<CoreChapterDownloadTask> { it.updatedAt }.thenBy { it.taskId }
        )

    override fun saveChapterDownloadTask(task: CoreChapterDownloadTask) {
        chapterDownloadTasksById[task.taskId] = task
    }

    private val contentsByChapter = linkedMapOf<String, LinkedHashMap<String, String>>()
}
