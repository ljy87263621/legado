package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookGroup
import io.legado.core.library.CoreBookGroupIds
import io.legado.core.library.CoreLibrary

class BookshelfModel(
    private val library: CoreLibrary
) {
    var query: String = ""
        private set

    var selectedGroupId: Long = CoreBookGroupIds.ALL
        private set

    val selectedGroup: CoreBookGroup?
        get() = library.groups().firstOrNull { it.groupId == selectedGroupId }

    fun availableGroups(): List<CoreBookGroup> = library.groups()

    val isEmpty: Boolean
        get() = visibleBooks().isEmpty()

    fun setQuery(value: String) {
        query = value.trim()
    }

    fun selectGroup(groupId: Long) {
        selectedGroupId = groupId
    }

    fun assignBookToGroup(bookUrl: String, groupId: Long) {
        val book = library.book(bookUrl) ?: return
        library.saveBook(book.copy(group = if (groupId > 0) groupId else 0L))
    }

    fun createGroup(name: String): CoreBookGroup {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "分组名称不能为空" }
        val usedIds = library.groups()
            .asSequence()
            .filter { it.groupId > 0 }
            .map(CoreBookGroup::groupId)
            .fold(0L, Long::or)
        val groupId = (0 until 63)
            .map { 1L shl it }
            .firstOrNull { usedIds and it == 0L }
            ?: error("分组数量已达到上限")
        val order = library.groups()
            .filter { it.groupId > 0 }
            .maxOfOrNull(CoreBookGroup::order)
            ?.plus(1)
            ?: 1
        return CoreBookGroup(
            groupId = groupId,
            groupName = normalizedName,
            order = order
        ).also(library::saveGroup)
    }

    fun renameGroup(groupId: Long, name: String): Boolean {
        val normalizedName = name.trim()
        if (groupId <= 0 || normalizedName.isEmpty()) return false
        val group = library.groups().firstOrNull { it.groupId == groupId } ?: return false
        library.saveGroup(group.copy(groupName = normalizedName))
        return true
    }

    fun deleteGroup(groupId: Long): Boolean {
        if (groupId <= 0 || library.groups().none { it.groupId == groupId }) return false
        library.books()
            .filter { it.group and groupId != 0L }
            .forEach { book -> library.saveBook(book.copy(group = book.group and groupId.inv())) }
        library.deleteGroup(groupId)
        if (selectedGroupId == groupId) selectedGroupId = CoreBookGroupIds.ALL
        return true
    }

    fun addBooksToGroup(bookUrls: Set<String>, groupId: Long): Int {
        if (groupId <= 0 || library.groups().none { it.groupId == groupId }) return 0
        return bookUrls.mapNotNull(library::book).onEach { book ->
            library.saveBook(book.copy(group = book.group or groupId))
        }.size
    }

    fun moveBooksToGroup(bookUrls: Set<String>, groupId: Long): Int {
        if (groupId <= 0 || library.groups().none { it.groupId == groupId }) return 0
        return bookUrls.mapNotNull(library::book).onEach { book ->
            library.saveBook(book.copy(group = groupId))
        }.size
    }

    fun removeBooksFromGroup(bookUrls: Set<String>, groupId: Long): Int {
        if (groupId <= 0 || library.groups().none { it.groupId == groupId }) return 0
        return bookUrls.mapNotNull(library::book).onEach { book ->
            library.saveBook(book.copy(group = book.group and groupId.inv()))
        }.size
    }

    fun deleteBooks(bookUrls: Set<String>): Int {
        val existingUrls = bookUrls.filter { library.book(it) != null }
        existingUrls.forEach(library::deleteBook)
        return existingUrls.size
    }

    fun refresh() {
        // The model reads directly from the library; this method gives the UI an explicit refresh boundary.
    }

    fun visibleBooks(): List<CoreBook> {
        val normalizedQuery = query.trim()
        return library.books()
            .asSequence()
            .filter(::matchesSelectedGroup)
            .filter { book ->
                normalizedQuery.isEmpty() ||
                    book.name.contains(normalizedQuery, ignoreCase = true) ||
                    book.author.contains(normalizedQuery, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<CoreBook> { it.durChapterTime }
                    .thenBy { it.order }
                    .thenBy { it.name }
            )
            .toList()
    }

    private fun matchesSelectedGroup(book: CoreBook): Boolean = when {
        selectedGroupId == CoreBookGroupIds.ALL -> true
        selectedGroupId == CoreBookGroupIds.LOCAL -> book.origin == "loc_book" || book.origin == "local"
        selectedGroupId == CoreBookGroupIds.UNGROUPED -> book.group == 0L
        selectedGroupId == CoreBookGroupIds.ERROR -> book.type < 0
        selectedGroupId > 0 -> (book.group and selectedGroupId) != 0L
        else -> true
    }
}
