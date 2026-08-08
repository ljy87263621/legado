package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreChapter
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.Locale
import java.util.zip.ZipFile

data class LocalImagePage(
    val source: Path,
    val entryName: String?,
    val displayName: String,
    val chapterIndex: Int,
    val chapterPageIndex: Int
)

data class ParsedLocalImageBook(
    val book: CoreBook,
    val chapters: List<CoreChapter>,
    val pages: List<LocalImagePage>
)

object LocalImageBookParser {
    private val imageExtensions = setOf("bmp", "gif", "jpeg", "jpg", "png", "webp")
    private val archiveExtensions = setOf("cbz", "zip")

    fun canParse(path: Path): Boolean {
        val extension = path.fileName.toString().extension()
        return extension in imageExtensions || extension in archiveExtensions
    }

    fun parse(path: Path): ParsedLocalImageBook {
        val normalizedPath = path.toAbsolutePath().normalize()
        require(Files.isRegularFile(normalizedPath)) { "本地图片书不存在: $normalizedPath" }
        return if (normalizedPath.fileName.toString().extension() in archiveExtensions) {
            parseArchive(normalizedPath)
        } else {
            parseSingleImage(normalizedPath)
        }
    }

    private fun parseSingleImage(path: Path): ParsedLocalImageBook {
        require(path.fileName.toString().extension() in imageExtensions) { "不支持的图片格式: ${path.fileName}" }
        val bookUrl = path.toString()
        val bookName = path.fileName.toString().substringBeforeLast('.', path.fileName.toString())
        val chapter = CoreChapter(
            bookUrl = bookUrl,
            url = "$bookUrl#image-chapter-0",
            title = "正文",
            index = 0
        )
        return ParsedLocalImageBook(
            book = CoreBook(
                bookUrl = bookUrl,
                name = bookName,
                origin = "loc_book",
                originName = path.fileName.toString(),
                type = DesktopBookType.IMAGE,
                totalChapterNum = 1
            ),
            chapters = listOf(chapter),
            pages = listOf(
                LocalImagePage(
                    source = path,
                    entryName = null,
                    displayName = path.fileName.toString(),
                    chapterIndex = 0,
                    chapterPageIndex = 0
                )
            )
        )
    }

    private fun parseArchive(path: Path): ParsedLocalImageBook {
        val entries = ZipFile(path.toFile()).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name.replace('\\', '/').trim('/') }
                .filter { it.isNotBlank() && it.substringAfterLast('/').extension() in imageExtensions }
                .toList()
        }
        require(entries.isNotEmpty()) { "压缩包中没有可阅读的图片" }

        val groupedEntries = entries.groupBy(::chapterKey)
        val orderedChapterKeys = groupedEntries.keys.sortedWith(ChapterKeyComparator)
        val bookUrl = path.toString()
        val chapters = orderedChapterKeys.mapIndexed { index, key ->
            CoreChapter(
                bookUrl = bookUrl,
                url = "$bookUrl#image-chapter-$index",
                title = key.ifBlank { "正文" },
                index = index
            )
        }
        val pages = orderedChapterKeys.flatMapIndexed { chapterIndex, key ->
            groupedEntries.getValue(key)
                .sortedWith(NaturalStringComparator)
                .mapIndexed { chapterPageIndex, entry ->
                    LocalImagePage(
                        source = path,
                        entryName = entry,
                        displayName = entry.substringAfterLast('/'),
                        chapterIndex = chapterIndex,
                        chapterPageIndex = chapterPageIndex
                    )
                }
        }
        val bookName = path.fileName.toString().substringBeforeLast('.', path.fileName.toString())
        return ParsedLocalImageBook(
            book = CoreBook(
                bookUrl = bookUrl,
                name = bookName,
                origin = "loc_book",
                originName = path.fileName.toString(),
                type = DesktopBookType.IMAGE,
                totalChapterNum = chapters.size
            ),
            chapters = chapters,
            pages = pages
        )
    }

    private fun chapterKey(entryName: String): String = entryName.substringBeforeLast('/', missingDelimiterValue = "")

    private fun String.extension(): String = substringAfterLast('.', "").lowercase(Locale.ROOT)

    private object ChapterKeyComparator : Comparator<String> {
        override fun compare(first: String, second: String): Int = when {
            first.isBlank() && second.isNotBlank() -> -1
            first.isNotBlank() && second.isBlank() -> 1
            else -> NaturalStringComparator.compare(first, second)
        }
    }

    private object NaturalStringComparator : Comparator<String> {
        override fun compare(first: String, second: String): Int {
            var left = 0
            var right = 0
            while (left < first.length && right < second.length) {
                val leftChar = first[left]
                val rightChar = second[right]
                if (leftChar.isDigit() && rightChar.isDigit()) {
                    val numberResult = compareNumberRuns(first, left, second, right)
                    if (numberResult.value != 0) return numberResult.value
                    left = numberResult.nextLeft
                    right = numberResult.nextRight
                } else {
                    val charResult = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
                    if (charResult != 0) return charResult
                    left++
                    right++
                }
            }
            return first.length.compareTo(second.length)
        }

        private fun compareNumberRuns(first: String, leftStart: Int, second: String, rightStart: Int): NumberCompare {
            val leftEnd = first.nextNonDigit(leftStart)
            val rightEnd = second.nextNonDigit(rightStart)
            val leftDigits = first.substring(leftStart, leftEnd).trimStart('0').ifEmpty { "0" }
            val rightDigits = second.substring(rightStart, rightEnd).trimStart('0').ifEmpty { "0" }
            val result = leftDigits.length.compareTo(rightDigits.length).takeIf { it != 0 }
                ?: leftDigits.compareTo(rightDigits).takeIf { it != 0 }
                ?: (leftEnd - leftStart).compareTo(rightEnd - rightStart)
            return NumberCompare(result, leftEnd, rightEnd)
        }

        private fun String.nextNonDigit(start: Int): Int {
            var index = start
            while (index < length && this[index].isDigit()) index++
            return index
        }

        private data class NumberCompare(val value: Int, val nextLeft: Int, val nextRight: Int)
    }
}
