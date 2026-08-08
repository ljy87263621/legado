package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreLibrary
import java.nio.file.Path

class LocalBookImporter(
    private val library: CoreLibrary
) {

    fun importFile(path: Path, replaceExistingChapters: Boolean = false): CoreBook {
        if (LocalAudioBookParser.canParse(path)) {
            val parsed = LocalAudioBookParser.parse(path)
            val book = preserveProgress(parsed.book)
            library.saveBook(book)
            if (replaceExistingChapters) library.deleteChapters(book.bookUrl)
            parsed.chapters.forEach(library::saveChapter)
            return book
        }
        if (LocalImageBookParser.canParse(path)) {
            val parsed = LocalImageBookParser.parse(path)
            val book = preserveProgress(parsed.book)
            library.saveBook(book)
            if (replaceExistingChapters) library.deleteChapters(book.bookUrl)
            parsed.chapters.forEach(library::saveChapter)
            return book
        }
        val savedTocRules = library.txtTocRules()
        val parsed = LocalBookParser.parse(path, savedTocRules.takeIf { it.isNotEmpty() })
        val book = preserveProgress(parsed.book)
        library.saveBook(book)
        if (replaceExistingChapters) library.deleteChapters(book.bookUrl)
        parsed.chapters.forEach { parsedChapter ->
            library.saveChapter(parsedChapter.chapter)
            library.saveContent(parsedChapter.chapter, parsedChapter.content)
        }
        return book
    }

    private fun preserveProgress(parsedBook: CoreBook): CoreBook {
        val existing = library.book(parsedBook.bookUrl)
        return parsedBook.copy(
            durChapterIndex = existing?.durChapterIndex ?: parsedBook.durChapterIndex,
            durChapterPos = existing?.durChapterPos ?: parsedBook.durChapterPos,
            durChapterTitle = existing?.durChapterTitle,
            durChapterTime = existing?.durChapterTime ?: parsedBook.durChapterTime
        )
    }
}
