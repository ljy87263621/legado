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
