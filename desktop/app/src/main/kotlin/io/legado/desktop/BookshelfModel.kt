package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreLibrary

class BookshelfModel(
    private val library: CoreLibrary
) {
    var query: String = ""
        private set

    val isEmpty: Boolean
        get() = visibleBooks().isEmpty()

    fun setQuery(value: String) {
        query = value.trim()
    }

    fun visibleBooks(): List<CoreBook> {
        val normalizedQuery = query.trim()
        return library.books()
            .asSequence()
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
}
