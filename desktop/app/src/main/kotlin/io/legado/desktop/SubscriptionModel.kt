package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreBookSourceType
import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreLibrary
import io.legado.core.source.BookSourceSearchService
import io.legado.core.source.CoreSearchResult

class SubscriptionModel(
    private val library: CoreLibrary,
    private val service: BookSourceSearchService
) {
    var sources: List<CoreBookSource> = emptyList()
        private set

    var selectedSourceUrl: String? = null
        private set

    var articles: List<CoreSearchResult> = emptyList()
        private set

    var error: String? = null
        private set

    var isLoading: Boolean = false
        private set

    val selectedSource: CoreBookSource?
        get() = sources.firstOrNull { it.bookSourceUrl == selectedSourceUrl }

    fun refreshSources() {
        sources = library.sources().filter {
            it.bookSourceType == CoreBookSourceType.RSS && it.enabled && it.enabledExplore
        }
        if (selectedSourceUrl !in sources.map(CoreBookSource::bookSourceUrl)) {
            selectedSourceUrl = sources.firstOrNull()?.bookSourceUrl
            articles = emptyList()
        }
    }

    fun selectSource(bookSourceUrl: String) {
        require(sources.any { it.bookSourceUrl == bookSourceUrl }) { "订阅源不存在: $bookSourceUrl" }
        selectedSourceUrl = bookSourceUrl
        articles = emptyList()
        error = null
    }

    fun refresh() {
        val source = selectedSource ?: run {
            articles = emptyList()
            error = "请先导入并启用订阅源"
            return
        }
        isLoading = true
        error = null
        try {
            articles = service.explore(source)
        } catch (throwable: Throwable) {
            error = throwable.message ?: "订阅刷新失败"
        } finally {
            isLoading = false
        }
    }

    fun openArticle(article: CoreSearchResult): CoreBook {
        val book = article.book.copy(
            tocUrl = article.book.bookUrl,
            type = CoreBookSourceType.RSS,
            origin = article.source.bookSourceUrl,
            originName = article.source.bookSourceName
        )
        library.saveBook(book)
        val chapter = CoreChapter(
            bookUrl = book.bookUrl,
            url = book.bookUrl,
            title = book.name,
            index = 0,
            tag = book.author
        )
        library.saveChapter(chapter)
        if (article.source.ruleContent.isNullOrBlank()) {
            library.saveContent(chapter, book.intro.orEmpty().ifBlank { book.bookUrl })
        }
        return book
    }
}
