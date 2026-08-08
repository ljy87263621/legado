package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreReadRecord
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadRecordModelTest {

    @Test
    fun summarizesRecordsByBookAndFiltersBySearchText() {
        val library = InMemoryCoreLibrary()
        library.saveBook(CoreBook("book-1", name = "星河", author = "甲作者", durChapterTime = 10L))
        library.saveBook(CoreBook("book-2", name = "山海", author = "乙作者", durChapterTime = 20L))
        library.saveReadRecord(CoreReadRecord("星河", 20260801, 100L, 160L))
        library.saveReadRecord(CoreReadRecord("星河", 20260802, 200L, 260L))
        library.saveReadRecord(CoreReadRecord("山海", 20260802, 300L, 330L))
        val model = ReadRecordModel(library)

        assertEquals(150L, model.summary.totalSeconds)
        assertEquals(2, model.summary.bookCount)
        assertEquals(listOf("山海", "星河"), model.visibleRecords().map { it.bookName })
        assertEquals(30L, model.visibleRecords().first().totalSeconds)

        model.setQuery("山")

        assertEquals(listOf("山海"), model.visibleRecords().map { it.bookName })
    }

    @Test
    fun canSortReadRecordsByDurationOrName() {
        val library = InMemoryCoreLibrary()
        library.saveReadRecord(CoreReadRecord("乙书", 20260801, 0L, 30L))
        library.saveReadRecord(CoreReadRecord("甲书", 20260801, 0L, 60L))
        val model = ReadRecordModel(library)

        model.setSort(ReadRecordSort.BOOK_NAME)
        assertEquals(listOf("甲书", "乙书"), model.visibleRecords().map { it.bookName })

        model.setSort(ReadRecordSort.TOTAL_SECONDS)
        assertEquals(listOf("甲书", "乙书"), model.visibleRecords().map { it.bookName })
    }

    @Test
    fun openingRecordFindsMatchingBookshelfBook() {
        val library = InMemoryCoreLibrary()
        library.saveBook(CoreBook("book-1", name = "星河", author = "甲作者"))
        library.saveReadRecord(CoreReadRecord("星河", 20260801, 0L, 30L))
        val model = ReadRecordModel(library)

        assertEquals("book-1", model.open(model.visibleRecords().single())?.bookUrl)
    }

    @Test
    fun deletesRecordsForABookAndRefreshesSummary() {
        val library = InMemoryCoreLibrary()
        library.saveReadRecord(CoreReadRecord("星河", 20260801, 0L, 30L))
        library.saveReadRecord(CoreReadRecord("山海", 20260801, 0L, 20L))
        val model = ReadRecordModel(library)

        assertTrue(model.deleteBookRecords("星河"))

        assertEquals(listOf("山海"), model.visibleRecords().map { it.bookName })
        assertEquals(20L, model.summary.totalSeconds)
        assertFalse(model.deleteBookRecords("星河"))
    }

    @Test
    fun exportsAllReadRecordsAsMarkdownWithSummaryAndSessionDetails() {
        val library = InMemoryCoreLibrary()
        library.saveReadRecord(CoreReadRecord("星河", 20260801, 100L, 160L))
        library.saveReadRecord(CoreReadRecord("星河", 20260802, 200L, 260L))
        library.saveReadRecord(CoreReadRecord("山海", 20260802, 300L, 330L))
        val markdown = ReadRecordModel(library).exportMarkdown()

        assertTrue(markdown.startsWith("# Legado 阅读记录"))
        assertTrue(markdown.contains("- 总阅读时长：150 秒"))
        assertTrue(markdown.contains("- 书籍：2 本"))
        assertTrue(markdown.contains("- 会话：3 次"))
        assertTrue(markdown.contains("### 星河\n- 总时长：120 秒\n- 会话：2 次\n- 最近阅读：260"))
        assertTrue(markdown.contains("| 星河 | 20260802 | 200 | 260 | 60 |"))
        assertTrue(markdown.contains("| 山海 | 20260802 | 300 | 330 | 30 |"))
    }
}
