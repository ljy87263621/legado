package io.legado.core.library

interface CoreLibrary {
    fun books(): List<CoreBook>
    fun book(bookUrl: String): CoreBook?
    fun saveBook(book: CoreBook)
    fun deleteBook(bookUrl: String)

    fun sources(): List<CoreBookSource>
    fun source(bookSourceUrl: String): CoreBookSource?
    fun saveSource(source: CoreBookSource)

    fun chapters(bookUrl: String): List<CoreChapter>
    fun saveChapter(chapter: CoreChapter)

    fun content(chapter: CoreChapter): String?
    fun saveContent(chapter: CoreChapter, content: String)
}

class InMemoryCoreLibrary : CoreLibrary {
    private val booksByUrl = linkedMapOf<String, CoreBook>()
    private val sourcesByUrl = linkedMapOf<String, CoreBookSource>()
    private val chaptersByBook = linkedMapOf<String, LinkedHashMap<String, CoreChapter>>()

    override fun books(): List<CoreBook> = booksByUrl.values.toList()

    override fun book(bookUrl: String): CoreBook? = booksByUrl[bookUrl]

    override fun saveBook(book: CoreBook) {
        booksByUrl[book.bookUrl] = book
    }

    override fun deleteBook(bookUrl: String) {
        booksByUrl.remove(bookUrl)
        chaptersByBook.remove(bookUrl)
        contentsByChapter.remove(bookUrl)
    }

    override fun sources(): List<CoreBookSource> = sourcesByUrl.values.toList()

    override fun source(bookSourceUrl: String): CoreBookSource? = sourcesByUrl[bookSourceUrl]

    override fun saveSource(source: CoreBookSource) {
        sourcesByUrl[source.bookSourceUrl] = source
    }

    override fun chapters(bookUrl: String): List<CoreChapter> =
        chaptersByBook[bookUrl]
            ?.values
            ?.sortedBy(CoreChapter::index)
            ?: emptyList()

    override fun saveChapter(chapter: CoreChapter) {
        chaptersByBook
            .getOrPut(chapter.bookUrl) { linkedMapOf() }[chapter.url] = chapter
    }

    override fun content(chapter: CoreChapter): String? =
        contentsByChapter[chapter.bookUrl]?.get(chapter.url)

    override fun saveContent(chapter: CoreChapter, content: String) {
        contentsByChapter
            .getOrPut(chapter.bookUrl) { linkedMapOf() }[chapter.url] = content
    }

    private val contentsByChapter = linkedMapOf<String, LinkedHashMap<String, String>>()
}
