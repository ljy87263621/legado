package io.legado.desktop

import io.legado.core.library.CoreLibrary
import io.legado.core.source.BookSourceSearchService
import io.legado.core.source.CoreSearchResult
import io.legado.core.source.CoreSourceSessionException
import io.legado.core.source.CoreSourceSessionStatus

class SearchModel(
    private val library: CoreLibrary,
    private val service: BookSourceSearchService
) {
    var query: String = ""
        private set

    var page: Int = 1
        private set

    var results: List<CoreSearchResult> = emptyList()
        private set

    var error: String? = null
        private set

    var sessionStatus: CoreSourceSessionStatus? = null
        private set

    var loginUrl: String? = null
        private set

    var loginSourceUrl: String? = null
        private set

    var isSearching: Boolean = false
        private set

    fun setQuery(value: String) {
        query = value
    }

    fun search(page: Int = 1) {
        val keyword = query.trim()
        if (keyword.isBlank()) {
            results = emptyList()
            error = null
            return
        }
        isSearching = true
        error = null
        sessionStatus = null
        loginUrl = null
        loginSourceUrl = null
        try {
            this.page = page.coerceAtLeast(1)
            results = service.search(keyword, this.page)
        } catch (throwable: Throwable) {
            results = emptyList()
            error = throwable.message ?: "搜索失败"
            if (throwable is CoreSourceSessionException) {
                sessionStatus = throwable.status
                loginUrl = throwable.loginUrl
                loginSourceUrl = throwable.sourceUrl
            }
        } finally {
            isSearching = false
        }
    }

    fun addToBookshelf(result: CoreSearchResult) {
        library.saveBook(result.book)
    }
}
