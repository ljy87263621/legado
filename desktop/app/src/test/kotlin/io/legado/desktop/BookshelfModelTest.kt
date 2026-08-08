package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookGroup
import io.legado.core.library.CoreBookGroupIds
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfModelTest {

    @Test
    fun emptyLibraryProducesAnEmptyBookshelfState() {
        val model = BookshelfModel(InMemoryCoreLibrary())

        assertTrue(model.visibleBooks().isEmpty())
        assertTrue(model.isEmpty)
    }

    @Test
    fun visibleBooksAreSortedByMostRecentReadingTime() {
        val library = InMemoryCoreLibrary()
        library.saveBook(
            CoreBook(
                bookUrl = "book-old",
                name = "旧书",
                durChapterTime = 10
            )
        )
        library.saveBook(
            CoreBook(
                bookUrl = "book-new",
                name = "新书",
                durChapterTime = 20
            )
        )
        val model = BookshelfModel(library)

        assertEquals(listOf("新书", "旧书"), model.visibleBooks().map(CoreBook::name))
    }

    @Test
    fun searchMatchesBookNameOrAuthor() {
        val library = InMemoryCoreLibrary()
        library.saveBook(CoreBook("book-1", name = "星河", author = "甲作者"))
        library.saveBook(CoreBook("book-2", name = "山海", author = "乙作者"))
        val model = BookshelfModel(library)

        model.setQuery("乙作者")

        assertEquals(listOf("山海"), model.visibleBooks().map(CoreBook::name))
    }

    @Test
    fun blankSearchRestoresAllBooks() {
        val library = InMemoryCoreLibrary()
        library.saveBook(CoreBook("book-1", name = "星河"))
        library.saveBook(CoreBook("book-2", name = "山海"))
        val model = BookshelfModel(library)

        model.setQuery("星")
        model.setQuery("   ")

        assertEquals(2, model.visibleBooks().size)
        assertEquals("", model.query)
    }

    @Test
    fun selectingAGroupFiltersBooksUsingAndroidBitmaskSemantics() {
        val library = InMemoryCoreLibrary()
        library.saveGroup(CoreBookGroup(groupId = 1L, groupName = "玄幻", order = 1))
        library.saveGroup(CoreBookGroup(groupId = 2L, groupName = "科幻", order = 2))
        library.saveBook(CoreBook("book-fantasy", name = "星河", group = 1L))
        library.saveBook(CoreBook("book-scifi", name = "远航", group = 2L))
        library.saveBook(CoreBook("book-both", name = "交界", group = 3L))
        val model = BookshelfModel(library)

        model.selectGroup(1L)

        assertEquals(setOf("星河", "交界"), model.visibleBooks().map(CoreBook::name).toSet())
        assertEquals("玄幻", model.selectedGroup?.groupName)
    }

    @Test
    fun assigningBookToGroupUpdatesItsBitmaskAndUngroupedSelection() {
        val library = InMemoryCoreLibrary()
        library.saveGroup(CoreBookGroup(groupId = 1L, groupName = "玄幻", order = 1))
        library.saveBook(CoreBook("book-1", name = "星河"))
        val model = BookshelfModel(library)

        model.assignBookToGroup("book-1", 1L)

        assertEquals(1L, library.book("book-1")?.group)
        model.selectGroup(CoreBookGroupIds.UNGROUPED)
        assertTrue(model.visibleBooks().isEmpty())
    }

    @Test
    fun creatingRenamingAndDeletingAUserGroupCleansItsBookAssignments() {
        val library = InMemoryCoreLibrary()
        library.saveBook(CoreBook("book-1", name = "星河", group = 1L))
        val model = BookshelfModel(library)

        val created = model.createGroup("  重点阅读  ")

        assertEquals(1L, created.groupId)
        assertEquals("重点阅读", created.groupName)
        assertTrue(model.renameGroup(created.groupId, "本周阅读"))
        assertEquals("本周阅读", library.groups().single { it.groupId == created.groupId }.groupName)

        assertTrue(model.deleteGroup(created.groupId))
        assertEquals(0L, library.book("book-1")?.group)
        assertTrue(library.groups().none { it.groupId == created.groupId })
    }

    @Test
    fun batchMoveAndRemoveBooksUsesGroupBitmasks() {
        val library = InMemoryCoreLibrary()
        library.saveGroup(CoreBookGroup(groupId = 1L, groupName = "玄幻", order = 1))
        library.saveGroup(CoreBookGroup(groupId = 2L, groupName = "科幻", order = 2))
        library.saveBook(CoreBook("book-1", name = "星河", group = 1L))
        library.saveBook(CoreBook("book-2", name = "远航", group = 3L))
        val model = BookshelfModel(library)

        assertEquals(2, model.addBooksToGroup(setOf("book-1", "book-2"), 2L))
        assertEquals(3L, library.book("book-1")?.group)
        assertEquals(3L, library.book("book-2")?.group)

        assertEquals(2, model.removeBooksFromGroup(setOf("book-1", "book-2"), 1L))
        assertEquals(2L, library.book("book-1")?.group)
        assertEquals(2L, library.book("book-2")?.group)
    }

    @Test
    fun movingBooksToAGroupReplacesTheirExistingGroupMembership() {
        val library = InMemoryCoreLibrary()
        library.saveGroup(CoreBookGroup(groupId = 1L, groupName = "玄幻", order = 1))
        library.saveGroup(CoreBookGroup(groupId = 2L, groupName = "科幻", order = 2))
        library.saveBook(CoreBook("book-1", name = "星河", group = 1L))
        library.saveBook(CoreBook("book-2", name = "远航", group = 3L))
        val model = BookshelfModel(library)

        assertEquals(2, model.moveBooksToGroup(setOf("book-1", "book-2"), 2L))
        assertEquals(2L, library.book("book-1")?.group)
        assertEquals(2L, library.book("book-2")?.group)
    }

    @Test
    fun batchDeleteRemovesOnlyExistingBooks() {
        val library = InMemoryCoreLibrary()
        library.saveBook(CoreBook("book-1", name = "星河"))
        library.saveBook(CoreBook("book-2", name = "远航"))
        val model = BookshelfModel(library)

        assertEquals(2, model.deleteBooks(setOf("book-1", "book-2", "missing")))
        assertTrue(library.books().isEmpty())
    }
}
