package io.legado.desktop

import io.legado.core.library.CoreLibrary
import java.nio.file.Files
import java.util.zip.ZipFile

class ImageReaderModel(
    private val library: CoreLibrary,
    bookUrl: String,
    startPageIndex: Int? = null,
    startChapterIndex: Int? = null,
    startChapterPageIndex: Int? = null
) {
    private val book = requireNotNull(library.book(bookUrl)) { "Book does not exist: $bookUrl" }
    private val parsed = LocalImageBookParser.parse(java.nio.file.Path.of(book.bookUrl))
    private val pages = parsed.pages
    private var pageIndex = initialPageIndex(startPageIndex, startChapterIndex, startChapterPageIndex)

    var zoom: Float = 1f
        private set

    val currentPageIndex: Int
        get() = pageIndex

    val currentPage: LocalImagePage
        get() = pages[pageIndex]

    val currentChapterTitle: String
        get() = parsed.chapters[currentPage.chapterIndex].title

    val currentPageLabel: String
        get() = currentPage.displayName

    val pageCount: Int
        get() = pages.size

    val hasPrevious: Boolean
        get() = pageIndex > 0

    val hasNext: Boolean
        get() = pageIndex < pages.lastIndex

    val hasPreviousChapter: Boolean
        get() = currentPage.chapterIndex > 0

    val hasNextChapter: Boolean
        get() = pages.any { it.chapterIndex > currentPage.chapterIndex }

    fun nextPage(): Boolean {
        if (!hasNext) return false
        pageIndex++
        return true
    }

    fun previousPage(): Boolean {
        if (!hasPrevious) return false
        pageIndex--
        return true
    }

    fun nextChapter(): Boolean {
        val target = pages.indexOfFirst { it.chapterIndex > currentPage.chapterIndex }
        if (target < 0) return false
        pageIndex = target
        return true
    }

    fun previousChapter(): Boolean {
        val targetChapter = currentPage.chapterIndex - 1
        if (targetChapter < 0) return false
        pageIndex = pages.indexOfFirst { it.chapterIndex == targetChapter }
        return true
    }

    fun setZoom(value: Float) {
        zoom = value.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    fun zoomIn() = setZoom(zoom + ZOOM_STEP)

    fun zoomOut() = setZoom(zoom - ZOOM_STEP)

    fun resetZoom() = setZoom(1f)

    fun savePosition() {
        val page = currentPage
        val chapterTitle = parsed.chapters[page.chapterIndex].title
        val currentBook = library.book(book.bookUrl) ?: book
        library.saveBook(
            currentBook.copy(
                durChapterIndex = page.chapterIndex,
                durChapterPos = page.chapterPageIndex,
                durChapterTitle = chapterTitle,
                durChapterTime = System.currentTimeMillis()
            )
        )
    }

    fun currentPageBytes(): ByteArray {
        val page = currentPage
        val entryName = page.entryName
        return if (entryName == null) {
            Files.readAllBytes(page.source)
        } else {
            ZipFile(page.source.toFile()).use { zip ->
                val entry = requireNotNull(zip.getEntry(entryName)) { "压缩包图片缺失: $entryName" }
                zip.getInputStream(entry).use { it.readBytes() }
            }
        }
    }

    private fun initialPageIndex(
        startPageIndex: Int?,
        startChapterIndex: Int?,
        startChapterPageIndex: Int?
    ): Int {
        startPageIndex?.let { return it.coerceIn(pages.indices) }
        val chapterIndex = startChapterIndex ?: book.durChapterIndex
        val chapterPageIndex = startChapterPageIndex ?: book.durChapterPos
        return pages.indexOfFirst { page ->
            page.chapterIndex == chapterIndex && page.chapterPageIndex == chapterPageIndex
        }.takeIf { it >= 0 }
            ?: pages.indexOfFirst { it.chapterIndex == chapterIndex }.takeIf { it >= 0 }
            ?: 0
    }

    companion object {
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 3f
        private const val ZOOM_STEP = 0.25f
    }
}
