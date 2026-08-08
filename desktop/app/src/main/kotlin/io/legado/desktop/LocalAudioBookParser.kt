package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreChapter
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

data class ParsedLocalAudioBook(
    val book: CoreBook,
    val chapters: List<CoreChapter>
)

object LocalAudioBookParser {
    private val audioExtensions = setOf("aif", "aiff", "au", "snd", "wav")

    fun canParse(path: Path): Boolean = path.fileName.toString().extension() in audioExtensions

    fun parse(path: Path): ParsedLocalAudioBook {
        val normalizedPath = path.toAbsolutePath().normalize()
        require(Files.isRegularFile(normalizedPath)) { "本地音频不存在: $normalizedPath" }
        require(canParse(normalizedPath)) { "不支持的本地音频格式: ${normalizedPath.fileName}" }

        val bookUrl = normalizedPath.toString()
        val fileName = normalizedPath.fileName.toString()
        val chapter = CoreChapter(
            bookUrl = bookUrl,
            url = "$bookUrl#audio-chapter-0",
            title = fileName.substringBeforeLast('.', fileName),
            index = 0,
            resourceUrl = bookUrl
        )
        return ParsedLocalAudioBook(
            book = CoreBook(
                bookUrl = bookUrl,
                name = fileName.substringBeforeLast('.', fileName),
                origin = "loc_book",
                originName = fileName,
                type = DesktopBookType.AUDIO,
                totalChapterNum = 1
            ),
            chapters = listOf(chapter)
        )
    }

    private fun String.extension(): String = substringAfterLast('.', "").lowercase(Locale.ROOT)
}
