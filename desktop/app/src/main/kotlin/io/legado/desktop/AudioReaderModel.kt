package io.legado.desktop

import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreLibrary
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

enum class AudioPlaybackState {
    STOPPED,
    PLAYING,
    PAUSED
}

interface AudioPlaybackSession : AutoCloseable {
    val state: AudioPlaybackState

    fun play()

    fun pause()

    fun stop()

    override fun close()
}

class AudioReaderModel(
    private val library: CoreLibrary,
    bookUrl: String,
    startChapterIndex: Int? = null,
    private val sessionFactory: (String) -> AudioPlaybackSession = ::JavaSoundAudioPlaybackSession
) : AutoCloseable {
    private val book = requireNotNull(library.book(bookUrl)) { "Book does not exist: $bookUrl" }
    private val chapters = library.chapters(bookUrl).sortedBy(CoreChapter::index)
    private var chapterIndex = if (chapters.isEmpty()) {
        0
    } else {
        (startChapterIndex ?: book.durChapterIndex).coerceIn(chapters.indices)
    }
    private var session: AudioPlaybackSession? = null

    init {
        require(DesktopBookType.isLocalAudioBook(book)) { "Book is not a local audio book: $bookUrl" }
        require(chapters.isNotEmpty()) { "本地音频书没有可播放章节: $bookUrl" }
    }

    val currentChapter: CoreChapter
        get() = chapters[chapterIndex]

    val currentChapterIndex: Int
        get() = chapterIndex

    val currentChapterTitle: String
        get() = currentChapter.title.ifBlank { "音频 ${chapterIndex + 1}" }

    val currentResource: String
        get() = currentChapter.resourceUrl ?: currentChapter.url

    val state: AudioPlaybackState
        get() = session?.state ?: AudioPlaybackState.STOPPED

    val hasPreviousChapter: Boolean
        get() = chapterIndex > 0

    val hasNextChapter: Boolean
        get() = chapterIndex < chapters.lastIndex

    val chapterCount: Int
        get() = chapters.size

    fun chapterTitle(index: Int): String = chapters[index].title.ifBlank { "音频 ${index + 1}" }

    fun play(): Boolean {
        val active = session ?: sessionFactory(resolveResource(currentResource)).also { session = it }
        active.play()
        return true
    }

    fun pause(): Boolean {
        val active = session ?: return false
        if (active.state != AudioPlaybackState.PLAYING) return false
        active.pause()
        return true
    }

    fun stop(): Boolean {
        val active = session ?: return false
        if (active.state == AudioPlaybackState.STOPPED) return false
        active.stop()
        return true
    }

    fun nextChapter(): Boolean = moveToChapter(chapterIndex + 1)

    fun previousChapter(): Boolean = moveToChapter(chapterIndex - 1)

    fun selectChapter(index: Int): Boolean {
        require(index in chapters.indices) { "音频章节索引越界: $index" }
        return moveToChapter(index)
    }

    fun savePosition() {
        val currentBook = library.book(book.bookUrl) ?: book
        library.saveBook(
            currentBook.copy(
                durChapterIndex = chapterIndex,
                durChapterPos = 0,
                durChapterTitle = currentChapterTitle,
                durChapterTime = System.currentTimeMillis()
            )
        )
    }

    override fun close() {
        session?.close()
        session = null
    }

    private fun moveToChapter(target: Int): Boolean {
        if (target !in chapters.indices) return false
        if (target == chapterIndex) return false
        session?.close()
        session = null
        chapterIndex = target
        return true
    }

    private fun resolveResource(resource: String): String {
        val path = runCatching {
            if (resource.startsWith("file:", ignoreCase = true)) Path.of(URI(resource)) else Path.of(resource)
        }.getOrElse { error("无法解析本地音频路径: $resource") }
            .toAbsolutePath()
            .normalize()
        require(Files.isRegularFile(path)) { "本地音频不存在: $path" }
        return path.toString()
    }
}

private class JavaSoundAudioPlaybackSession(path: String) : AudioPlaybackSession {
    private val clip: Clip = AudioSystem.getClip().also { target ->
        AudioSystem.getAudioInputStream(Path.of(path).toFile()).use { input -> target.open(input) }
    }
    override var state: AudioPlaybackState = AudioPlaybackState.STOPPED
        private set

    override fun play() {
        clip.start()
        state = AudioPlaybackState.PLAYING
    }

    override fun pause() {
        clip.stop()
        state = AudioPlaybackState.PAUSED
    }

    override fun stop() {
        clip.stop()
        clip.framePosition = 0
        state = AudioPlaybackState.STOPPED
    }

    override fun close() {
        clip.close()
        state = AudioPlaybackState.STOPPED
    }
}
