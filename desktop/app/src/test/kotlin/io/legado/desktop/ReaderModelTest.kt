package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreChapter
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderModelTest {

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
}
