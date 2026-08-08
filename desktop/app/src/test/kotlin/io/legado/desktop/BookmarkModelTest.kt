package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookmark
import io.legado.core.library.CoreChapter
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkModelTest {

    @Test
    fun listsAllBookmarksWithBookContextAndSearchesText() {
        val library = InMemoryCoreLibrary()
        library.saveBook(CoreBook("book-1", name = "星河", author = "甲作者"))
        library.saveBook(CoreBook("book-2", name = "山海", author = "乙作者"))
        library.saveBookmark(bookmark(30L, "山海", "乙作者", content = "海边摘录"))
        library.saveBookmark(bookmark(40L, "星河", "甲作者", chapterName = "第二章", content = "星空摘录"))
        library.saveBookmark(bookmark(20L, "星河", "甲作者", content = "旧摘录"))
        val model = BookmarkModel(library)

        assertEquals(listOf(40L, 30L, 20L), model.visibleBookmarks().map { it.bookmark.time })
        assertEquals("星河", model.visibleBookmarks().first().book?.name)

        model.setQuery("海边")

        assertEquals(listOf("山海"), model.visibleBookmarks().map { it.bookmark.bookName })
    }

    @Test
    fun openingBookmarkReturnsReaderTargetForExistingBook() {
        val library = InMemoryCoreLibrary()
        library.saveBook(CoreBook("book-1", name = "星河", author = "甲作者"))
        library.saveChapter(CoreChapter("book-1", "chapter-1", "第一章", 0))
        library.saveChapter(CoreChapter("book-1", "chapter-2", "第二章", 1))
        val target = bookmark(50L, "星河", "甲作者", chapterIndex = 1, chapterPos = 12)
        library.saveBookmark(target)
        val model = BookmarkModel(library)

        assertEquals(
            BookmarkOpenTarget(bookUrl = "book-1", chapterIndex = 1, chapterPos = 12),
            model.open(target)
        )
    }

    @Test
    fun openingBookmarkReturnsNullWhenBookIsMissing() {
        val model = BookmarkModel(InMemoryCoreLibrary())

        assertNull(model.open(bookmark(60L, "不存在", "作者")))
    }

    @Test
    fun deletesBookmarksAndRefreshesTheVisibleList() {
        val library = InMemoryCoreLibrary()
        library.saveBookmark(bookmark(70L, "星河", "甲作者"))
        val model = BookmarkModel(library)

        assertTrue(model.delete(70L))

        assertTrue(model.visibleBookmarks().isEmpty())
        assertFalse(model.delete(70L))
    }

    @Test
    fun bookmarksRouteIsInternalAndSettingsCanNavigateToIt() {
        assertEquals("书签", AppRoute.BOOKMARKS.label)
        assertFalse(AppRoute.BOOKMARKS in AppRoute.primary)
    }

    private fun bookmark(
        time: Long,
        bookName: String,
        bookAuthor: String,
        chapterIndex: Int = 0,
        chapterPos: Int = 0,
        chapterName: String = "第一章",
        content: String = "摘录"
    ) = CoreBookmark(
        time = time,
        bookName = bookName,
        bookAuthor = bookAuthor,
        chapterIndex = chapterIndex,
        chapterPos = chapterPos,
        chapterName = chapterName,
        bookText = "正文片段",
        content = content
    )
}
