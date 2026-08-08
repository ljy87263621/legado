package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreBookmark
import io.legado.core.library.InMemoryCoreLibrary
import io.legado.core.library.CoreReadRecord
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import io.legado.core.source.OnlineBookService
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderModelTest {

    @Test
    fun readerLoadsMissingContentThroughOnlineService() {
        val library = InMemoryCoreLibrary()
        val source = CoreBookSource(
            bookSourceUrl = "https://source.example",
            ruleContent = "{\"content\":\".content\"}"
        )
        val book = CoreBook("https://source.example/book/1", origin = source.bookSourceUrl)
        val chapter = CoreChapter(book.bookUrl, "https://source.example/chapter/1", "第一章", 0)
        library.saveSource(source)
        library.saveBook(book)
        library.saveChapter(chapter)
        val model = ReaderModel(
            library,
            book.bookUrl,
            OnlineBookService(library, object : CoreHttpClient {
                override fun get(url: String, headers: Map<String, String>): CoreHttpResponse =
                    CoreHttpResponse(url, "<div class='content'>在线正文</div>")
            })
        )

        assertTrue(model.loadCurrentContent())
        assertEquals("在线正文", model.currentContent)
        assertEquals("在线正文", library.content(chapter))
        assertTrue(model.error == null)
    }

    @Test
    fun readerLoadsMissingLocalTxtContentFromChapterByteRange() {
        val directory = Files.createTempDirectory("legado-reader-local-test")
        val file = directory.resolve("local.txt")
        Files.writeString(file, "第一章\n本地正文")
        val parsed = LocalBookParser.parse(file)
        val library = InMemoryCoreLibrary()
        library.saveBook(parsed.book)
        parsed.chapters.forEach { library.saveChapter(it.chapter) }

        try {
            val model = ReaderModel(library, parsed.book.bookUrl)

            assertTrue(model.loadCurrentContent())
            assertEquals("本地正文", model.currentContent)
            assertEquals("本地正文", library.content(parsed.chapters.single().chapter))
            assertTrue(model.error == null)
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun readerCanLoadAnExplicitChapterAfterTheCurrentChapterChanges() {
        val directory = Files.createTempDirectory("legado-reader-target-chapter-test")
        val file = directory.resolve("local.txt")
        val bytes = "第一章\n主角醒来\n第二章\n走出房门".toByteArray()
        Files.write(file, bytes)
        val first = CoreChapter(
            bookUrl = file.toString(),
            url = "${file}#chapter-0",
            title = "第一章",
            index = 0,
            start = 0L,
            end = "第一章\n主角醒来\n".toByteArray().size.toLong()
        )
        val second = CoreChapter(
            bookUrl = file.toString(),
            url = "${file}#chapter-1",
            title = "第二章",
            index = 1,
            start = first.end,
            end = bytes.size.toLong()
        )
        val library = InMemoryCoreLibrary()
        library.saveBook(
            CoreBook(
                bookUrl = file.toString(),
                name = "local",
                origin = "loc_book",
                charset = "UTF-8"
            )
        )
        library.saveChapter(first)
        library.saveChapter(second)

        try {
            val model = ReaderModel(library, file.toString())
            val firstChapter = model.currentChapter

            assertTrue(model.nextChapter())
            assertTrue(model.loadContent(firstChapter))
            assertEquals("主角醒来", library.content(firstChapter))
            assertEquals("第二章", model.currentChapter.title)
            assertEquals(null, library.content(model.currentChapter))
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun readerStartsAtSavedProgressAndCanMoveBetweenChapters() {
        val library = InMemoryCoreLibrary()
        val first = CoreChapter("book-1", "chapter-1", "第一章", 0)
        val second = CoreChapter("book-1", "chapter-2", "第二章", 1)
        library.saveBook(CoreBook("book-1", name = "书", durChapterIndex = 1, durChapterPos = 2))
        library.saveChapter(first)
        library.saveChapter(second)
        library.saveContent(first, "第一章正文")
        library.saveContent(second, "第二章正文")

        val model = ReaderModel(library, "book-1")

        assertEquals("第二章", model.currentChapter.title)
        assertEquals("第二章正文", model.currentContent)
        assertFalse(model.hasNext)

        model.previousChapter()

        assertEquals("第一章", model.currentChapter.title)
        assertTrue(model.hasNext)
    }

    @Test
    fun readerExposesReplacementProcessedTitleAndContentWithoutOverwritingCachedRawContent() {
        val library = InMemoryCoreLibrary()
        val chapter = CoreChapter("book-replaced", "chapter-1", "第12章 广告", 0)
        library.saveBook(CoreBook("book-replaced", name = "书"))
        library.saveChapter(chapter)
        library.saveContent(chapter, "正文广告")
        library.saveReplaceRule(
            io.legado.core.library.CoreReplaceRule(
                id = 1L,
                pattern = "广告",
                replacement = "",
                isRegex = false,
                scopeTitle = true,
                scopeContent = true
            )
        )
        library.saveReplaceRule(
            io.legado.core.library.CoreReplaceRule(
                id = 2L,
                pattern = "第(\\d+)章",
                replacement = "章节$1",
                scopeTitle = true,
                scopeContent = false
            )
        )

        val model = ReaderModel(library, "book-replaced")

        assertEquals("章节12 ", model.currentChapterTitle)
        assertEquals("正文", model.currentContent)
        assertEquals("正文广告", library.content(chapter))
    }

    @Test
    fun readerCanStartAtAChapterSelectedFromBookDetails() {
        val library = InMemoryCoreLibrary()
        val first = CoreChapter("book-selected", "chapter-1", "第一章", 0)
        val second = CoreChapter("book-selected", "chapter-2", "第二章", 1)
        library.saveBook(CoreBook("book-selected", name = "书", durChapterIndex = 0))
        library.saveChapter(first)
        library.saveChapter(second)
        library.saveContent(first, "第一章正文")
        library.saveContent(second, "第二章正文")

        val model = ReaderModel(library, "book-selected", startChapterIndex = 1)

        assertEquals("第二章", model.currentChapter.title)
        assertEquals("第二章正文", model.currentContent)
    }

    @Test
    fun pagedReaderSplitsContentAndRestoresTheSavedPage() {
        val library = InMemoryCoreLibrary()
        val chapter = CoreChapter("book-pages", "chapter-1", "第一章", 0)
        library.saveBook(CoreBook("book-pages", name = "书", durChapterPos = 5))
        library.saveChapter(chapter)
        library.saveContent(chapter, "ABCDEFGHIJKL")

        val model = ReaderModel(library, "book-pages")

        assertEquals(
            listOf("ABCD", "EFGH", "IJKL"),
            model.pages(pageSize = 4).map { it.text }
        )
        assertEquals(1, model.currentPageIndex(pageSize = 4))
        assertTrue(model.nextPage(pageSize = 4))
        assertEquals(2, model.currentPageIndex(pageSize = 4))
        assertFalse(model.nextPage(pageSize = 4))
        assertTrue(model.previousPage(pageSize = 4))
        assertEquals(4, model.currentPosition)
    }

    @Test
    fun autoReadTickAdvancesPagesAndThenChapters() {
        val library = InMemoryCoreLibrary()
        val first = CoreChapter("book-auto", "chapter-1", "第一章", 0)
        val second = CoreChapter("book-auto", "chapter-2", "第二章", 1)
        library.saveBook(CoreBook("book-auto", name = "书"))
        library.saveChapter(first)
        library.saveChapter(second)
        library.saveContent(first, "ABCDEFGH")
        library.saveContent(second, "第二章")
        val model = ReaderModel(library, "book-auto")

        assertEquals(ReaderAutoReadResult.PAGE_ADVANCED, model.autoReadTick(pageSize = 4))
        assertEquals(4, model.currentPosition)
        assertEquals(ReaderAutoReadResult.CHAPTER_ADVANCED, model.autoReadTick(pageSize = 4))
        assertEquals("第二章", model.currentChapter.title)
        assertEquals(ReaderAutoReadResult.END, model.autoReadTick(pageSize = 4))
    }

    @Test
    fun keyboardCommandsMapToReaderActions() {
        assertEquals(
            ReaderKeyboardCommand.PREVIOUS_PAGE,
            ReaderKeyboardCommand.from(ReaderKeyboardKey.LEFT)
        )
        assertEquals(
            ReaderKeyboardCommand.NEXT_PAGE,
            ReaderKeyboardCommand.from(ReaderKeyboardKey.SPACE)
        )
        assertEquals(
            ReaderKeyboardCommand.NEXT_CHAPTER,
            ReaderKeyboardCommand.from(ReaderKeyboardKey.RIGHT, ctrlPressed = true)
        )
        assertEquals(
            ReaderKeyboardCommand.SAVE_POSITION,
            ReaderKeyboardCommand.from(ReaderKeyboardKey.S)
        )
    }

    @Test
    fun readerSavesPositionIntoTheBookProgress() {
        val library = InMemoryCoreLibrary()
        val chapter = CoreChapter("book-2", "chapter-1", "第一章", 0)
        library.saveBook(CoreBook("book-2", name = "书"))
        library.saveChapter(chapter)
        library.saveContent(chapter, "正文")
        val model = ReaderModel(library, "book-2")

        model.savePosition(3)

        val saved = library.book("book-2")!!
        assertEquals(3, saved.durChapterPos)
        assertEquals("第一章", saved.durChapterTitle)
    }

    @Test
    fun readerTracksPositionForTheCurrentChapter() {
        val library = InMemoryCoreLibrary()
        val first = CoreChapter("book-3", "chapter-1", "第一章", 0)
        val second = CoreChapter("book-3", "chapter-2", "第二章", 1)
        library.saveBook(CoreBook("book-3", name = "书", durChapterIndex = 0, durChapterPos = 12))
        library.saveChapter(first)
        library.saveChapter(second)

        val model = ReaderModel(library, "book-3")

        assertEquals(12, model.currentPosition)
        assertTrue(model.nextChapter())
        assertEquals(0, model.currentPosition)

        model.savePosition(7)

        assertEquals(7, model.currentPosition)
    }

    @Test
    fun savingPositionPersistsAReadingSessionRecord() {
        val library = InMemoryCoreLibrary()
        val chapter = CoreChapter("book-4", "chapter-1", "第一章", 0)
        library.saveBook(CoreBook("book-4", name = "书", author = "作者"))
        library.saveChapter(chapter)
        library.saveContent(chapter, "正文")
        var now = 100L
        val model = ReaderModel(library, "book-4", clockSeconds = { now })

        now = 130L
        model.savePosition(4)

        assertEquals(
            listOf(
                io.legado.core.library.CoreReadRecord(
                    bookName = "书",
                    day = CoreReadRecord.dayKey(130L),
                    startSec = 100L,
                    endSec = 130L
                )
            ),
            library.readRecords()
        )
    }

    @Test
    fun readerCanCreateAndRemoveAChapterBookmark() {
        val library = InMemoryCoreLibrary()
        val chapter = CoreChapter("book-5", "chapter-1", "第一章", 0)
        library.saveBook(CoreBook("book-5", name = "书", author = "作者"))
        library.saveChapter(chapter)
        library.saveContent(chapter, "章节原文")
        val model = ReaderModel(library, "book-5")

        val bookmark = model.addBookmark(content = "值得回看")

        assertEquals(
            listOf(
                bookmark.copy(
                    time = bookmark.time,
                    bookName = "书",
                    bookAuthor = "作者",
                    chapterIndex = 0,
                    chapterName = "第一章",
                    bookText = "章节原文",
                    content = "值得回看"
                )
            ),
            library.bookmarks("书", "作者")
        )
        assertTrue(model.removeBookmark(bookmark.time))
        assertTrue(library.bookmarks("书", "作者").isEmpty())
    }

    @Test
    fun readerListsBookmarksAndCanJumpToOne() {
        val library = InMemoryCoreLibrary()
        val first = CoreChapter("book-bookmarks", "chapter-1", "第一章", 0)
        val second = CoreChapter("book-bookmarks", "chapter-2", "第二章", 1)
        library.saveBook(CoreBook("book-bookmarks", name = "书", author = "作者"))
        library.saveChapter(first)
        library.saveChapter(second)
        library.saveContent(first, "第一章正文")
        library.saveContent(second, "第二章正文")
        val model = ReaderModel(library, "book-bookmarks")
        val bookmark = CoreBookmark(
            time = 10L,
            bookName = "书",
            bookAuthor = "作者",
            chapterIndex = 1,
            chapterPos = 17,
            chapterName = "第二章",
            bookText = "第二章正文",
            content = "回看这里"
        )
        library.saveBookmark(bookmark)

        assertEquals(listOf(bookmark), model.bookmarks())
        assertTrue(model.openBookmark(bookmark))
        assertEquals("第二章", model.currentChapter.title)
        assertEquals(17, model.currentPosition)
    }
}
