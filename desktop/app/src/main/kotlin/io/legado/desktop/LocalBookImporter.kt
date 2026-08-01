package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreLibrary
import java.nio.file.Path

class LocalBookImporter(
    private val library: CoreLibrary
) {

    fun importFile(path: Path): CoreBook {
        val parsed = LocalBookParser.parse(path)
        val existing = library.book(parsed.book.bookUrl)
        val book = parsed.book.copy(
            durChapterIndex = existing?.durChapterIndex ?: parsed.book.durChapterIndex,
            durChapterPos = existing?.durChapterPos ?: parsed.book.durChapterPos,
            durChapterTitle = existing?.durChapterTitle,
            durChapterTime = existing?.durChapterTime ?: parsed.book.durChapterTime
        )
        library.saveBook(book)
        parsed.chapters.forEach { parsedChapter ->
            library.saveChapter(parsedChapter.chapter)
            library.saveContent(parsedChapter.chapter, parsedChapter.content)
        }
        return book
    }
}
