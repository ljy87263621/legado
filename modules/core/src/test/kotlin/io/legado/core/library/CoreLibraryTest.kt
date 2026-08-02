package io.legado.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLibraryTest {

    private val library = InMemoryCoreLibrary()

    @Test
    fun booksAreIdentifiedByBookUrlAndCanBeUpdated() {
        library.saveBook(
            CoreBook(
                bookUrl = "https://example.com/book/1",
                name = "初始标题",
                author = "作者"
            )
        )
        library.saveBook(
            CoreBook(
                bookUrl = "https://example.com/book/1",
                name = "更新标题",
                author = "作者"
            )
        )

        assertEquals(1, library.books().size)
        assertEquals("更新标题", library.book("https://example.com/book/1")?.name)
    }

    @Test
    fun sourcesAreIdentifiedByBookSourceUrl() {
        library.saveSource(
            CoreBookSource(
                bookSourceUrl = "https://example.com/source",
                bookSourceName = "初始源"
            )
        )
        library.saveSource(
            CoreBookSource(
                bookSourceUrl = "https://example.com/source",
                bookSourceName = "更新源"
            )
        )

        assertEquals(1, library.sources().size)
        assertEquals("更新源", library.source("https://example.com/source")?.bookSourceName)
    }

    @Test
    fun sourcesCanBeDeletedAndEnabledSourcesAreFiltered() {
        library.saveSource(
            CoreBookSource(
                bookSourceUrl = "source-disabled",
                bookSourceName = "停用源",
                enabled = false
            )
        )
        library.saveSource(
            CoreBookSource(
                bookSourceUrl = "source-enabled",
                bookSourceName = "启用源"
            )
        )

        assertEquals(listOf("source-enabled"), library.enabledSources().map(CoreBookSource::bookSourceUrl))

        library.deleteSource("source-disabled")

        assertNull(library.source("source-disabled"))
    }

    @Test
    fun chaptersAreUniquePerBookAndSortedByOriginalIndex() {
        library.saveChapter(
            CoreChapter(
                bookUrl = "book-1",
                url = "chapter-2",
                title = "第二章",
                index = 1
            )
        )
        library.saveChapter(
            CoreChapter(
                bookUrl = "book-1",
                url = "chapter-1",
                title = "第一章",
                index = 0
            )
        )
        library.saveChapter(
            CoreChapter(
                bookUrl = "book-1",
                url = "chapter-2",
                title = "第二章（修订）",
                index = 1
            )
        )

        val chapters = library.chapters("book-1")
        assertEquals(listOf("第一章", "第二章（修订）"), chapters.map { it.title })
        assertEquals(2, chapters.size)
    }

    @Test
    fun deletingBookAlsoRemovesItsChapters() {
        library.saveBook(CoreBook(bookUrl = "book-1", name = "书"))
        val chapter = CoreChapter(bookUrl = "book-1", url = "chapter-1")
        library.saveChapter(chapter)
        library.saveContent(chapter, "正文")

        library.deleteBook("book-1")

        assertNull(library.book("book-1"))
        assertEquals(emptyList<CoreChapter>(), library.chapters("book-1"))
        assertNull(library.content(chapter))
    }

    @Test
    fun missingRecordsReturnNull() {
        assertNull(library.book("missing"))
        assertNull(library.source("missing"))
        assertNotNull(library.chapters("missing"))
    }

    @Test
    fun modelsRetainAndroidLibraryFields() {
        val book = CoreBook(
            bookUrl = "book-compat",
            kind = "玄幻",
            customTag = "重点",
            customCoverUrl = "custom-cover",
            customIntro = "自定义简介",
            charset = "UTF-8",
            latestChapterTitle = "最新章",
            latestChapterTime = 11,
            lastCheckTime = 12,
            lastCheckCount = 3,
            wordCount = "10万",
            syncTime = 13
        )
        val source = CoreBookSource(
            bookSourceUrl = "source-compat",
            loginCheckJs = "check()",
            coverDecodeJs = "decode()",
            bookSourceComment = "comment",
            variableComment = "variables",
            lastUpdateTime = 14,
            respondTime = 15,
            weight = 16,
            exploreUrl = "https://example.com/explore",
            exploreScreen = "玄幻",
            exploreStyle = 2
        )
        val chapter = CoreChapter(
            bookUrl = "book-compat",
            url = "chapter-compat",
            start = 17,
            end = 18,
            startFragmentId = "start-fragment",
            endFragmentId = "end-fragment"
        )

        assertEquals("玄幻", book.kind)
        assertEquals("重点", book.customTag)
        assertEquals(13, book.syncTime)
        assertEquals("check()", source.loginCheckJs)
        assertEquals(2, source.exploreStyle)
        assertEquals(17L, chapter.start)
        assertEquals("end-fragment", chapter.endFragmentId)
    }

    @Test
    fun chapterContentCanBeStoredAndReadBack() {
        val library = InMemoryCoreLibrary()
        val chapter = CoreChapter(
            bookUrl = "book-content",
            url = "chapter-1",
            title = "第一章"
        )

        library.saveContent(chapter, "正文内容")

        assertEquals("正文内容", library.content(chapter))
    }

    @Test
    fun readerDataCanBeSavedQueriedAndDeleted() {
        val library = InMemoryCoreLibrary()
        val group = CoreBookGroup(groupId = 1L, groupName = "玄幻", order = 2)
        val bookmark = CoreBookmark(
            time = 100L,
            bookName = "星河",
            bookAuthor = "甲作者",
            chapterIndex = 3,
            chapterName = "第三章",
            content = "值得回看的段落"
        )
        val record = CoreReadRecord(
            bookName = "星河",
            day = 20260802,
            startSec = 10L,
            endSec = 40L
        )

        library.saveGroup(group)
        library.saveBookmark(bookmark)
        library.saveReadRecord(record)

        assertEquals(listOf(group), library.groups())
        assertEquals(listOf(bookmark), library.bookmarks("星河", "甲作者"))
        assertEquals(listOf(record), library.readRecords())

        library.deleteGroup(group.groupId)
        library.deleteBookmark(bookmark.time)
        library.deleteReadRecords("星河")

        assertTrue(library.groups().isEmpty())
        assertTrue(library.bookmarks("星河", "甲作者").isEmpty())
        assertTrue(library.readRecords().isEmpty())
    }

    @Test
    fun readerSettingsExposeAndroidLikeDefaultsAndPersistUpdates() {
        val library = InMemoryCoreLibrary()

        assertEquals(20, library.readerSettings().textSize)
        assertEquals(12, library.readerSettings().lineSpacingExtra)
        assertEquals(CoreReaderTheme.DAY, library.readerSettings().theme)
        assertEquals(CoreReaderPageMode.SCROLL, library.readerSettings().pageMode)

        val updated = library.readerSettings().copy(
            textSize = 24,
            lineSpacingExtra = 16,
            theme = CoreReaderTheme.NIGHT,
            pageMode = CoreReaderPageMode.PAGED,
            autoRead = true
        )
        library.saveReaderSettings(updated)

        assertEquals(updated, library.readerSettings())
    }
}
