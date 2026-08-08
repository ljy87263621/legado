package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreChapter
import io.legado.core.library.InMemoryCoreLibrary
import java.nio.file.Files
import java.util.Comparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioReaderModelTest {

    @Test
    fun playbackControlsAndChapterNavigationPersistAudioProgress() {
        val directory = Files.createTempDirectory("legado-audio-reader-test")
        val firstPath = directory.resolve("chapter-1.wav")
        val secondPath = directory.resolve("chapter-2.wav")
        Files.write(firstPath, byteArrayOf(1))
        Files.write(secondPath, byteArrayOf(2))
        val library = InMemoryCoreLibrary()
        val book = CoreBook(
            bookUrl = directory.resolve("album").toString(),
            name = "album",
            origin = "loc_book",
            type = DesktopBookType.AUDIO,
            durChapterIndex = 0
        )
        val first = CoreChapter(
            bookUrl = book.bookUrl,
            url = "chapter-1",
            title = "第一段",
            index = 0,
            resourceUrl = firstPath.toString()
        )
        val second = first.copy(
            url = "chapter-2",
            title = "第二段",
            index = 1,
            resourceUrl = secondPath.toString()
        )
        library.saveBook(book)
        library.saveChapter(first)
        library.saveChapter(second)
        val sessions = mutableListOf<FakeAudioPlaybackSession>()

        val model = AudioReaderModel(library, book.bookUrl) { path ->
            FakeAudioPlaybackSession(path).also(sessions::add)
        }

        assertEquals("第一段", model.currentChapterTitle)
        assertEquals(AudioPlaybackState.STOPPED, model.state)
        assertTrue(model.play())
        assertEquals(AudioPlaybackState.PLAYING, model.state)
        assertTrue(model.pause())
        assertEquals(AudioPlaybackState.PAUSED, model.state)
        assertTrue(model.nextChapter())
        assertEquals("第二段", model.currentChapterTitle)
        assertEquals(AudioPlaybackState.STOPPED, model.state)
        assertTrue(model.play())
        model.savePosition()

        assertEquals(1, library.book(book.bookUrl)?.durChapterIndex)
        assertEquals("第二段", library.book(book.bookUrl)?.durChapterTitle)
        assertEquals(2, sessions.size)
        assertFalse(model.nextChapter())
        model.close()
        assertTrue(sessions.all(FakeAudioPlaybackSession::closed))
        Files.walk(directory).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }

    private class FakeAudioPlaybackSession(val path: String) : AudioPlaybackSession {
        override var state: AudioPlaybackState = AudioPlaybackState.STOPPED
            private set
        var closed = false

        override fun play() {
            state = AudioPlaybackState.PLAYING
        }

        override fun pause() {
            state = AudioPlaybackState.PAUSED
        }

        override fun stop() {
            state = AudioPlaybackState.STOPPED
        }

        override fun close() {
            closed = true
            state = AudioPlaybackState.STOPPED
        }
    }
}
