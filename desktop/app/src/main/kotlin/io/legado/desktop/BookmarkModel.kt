package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookmark
import io.legado.core.library.CoreLibrary

data class BookmarkListItem(
    val bookmark: CoreBookmark,
    val book: CoreBook?
)

data class BookmarkOpenTarget(
    val bookUrl: String,
    val chapterIndex: Int,
    val chapterPos: Int
)

class BookmarkModel(
    private val library: CoreLibrary
) {
    var query: String = ""
        private set

    fun setQuery(value: String) {
        query = value.trim()
    }

    fun visibleBookmarks(): List<BookmarkListItem> {
        val normalizedQuery = query.trim()
        return library.allBookmarks()
            .asSequence()
            .map { bookmark ->
                BookmarkListItem(
                    bookmark = bookmark,
                    book = library.books().firstOrNull { book ->
                        book.name == bookmark.bookName && book.author == bookmark.bookAuthor
                    }
                )
            }
            .filter { item ->
                normalizedQuery.isEmpty() || listOf(
                    item.bookmark.bookName,
                    item.bookmark.bookAuthor,
                    item.bookmark.chapterName,
                    item.bookmark.bookText,
                    item.bookmark.content
                ).any { text -> text.contains(normalizedQuery, ignoreCase = true) }
            }
            .toList()
    }

    fun open(bookmark: CoreBookmark): BookmarkOpenTarget? {
        val book = library.books().firstOrNull {
            it.name == bookmark.bookName && it.author == bookmark.bookAuthor
        } ?: return null
        return BookmarkOpenTarget(
            bookUrl = book.bookUrl,
            chapterIndex = bookmark.chapterIndex,
            chapterPos = bookmark.chapterPos.coerceAtLeast(0)
        )
    }

    fun delete(time: Long): Boolean {
        if (library.allBookmarks().none { it.time == time }) return false
        library.deleteBookmark(time)
        return true
    }
}
