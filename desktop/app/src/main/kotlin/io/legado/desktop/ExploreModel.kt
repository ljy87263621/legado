package io.legado.desktop

import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreLibrary
import io.legado.core.source.BookSourceSearchService
import io.legado.core.source.CoreExploreOption
import io.legado.core.source.CoreSearchResult
import io.legado.core.source.CoreSourceFilterService
import io.legado.core.source.CoreSourceSessionException
import io.legado.core.source.CoreSourceSessionStatus

class ExploreModel(
    private val library: CoreLibrary,
    private val service: BookSourceSearchService
) {
    private val sourceFilterService = CoreSourceFilterService(library)

    var sources: List<CoreBookSource> = emptyList()
        private set

    var selectedSource: CoreBookSource? = null
        private set

    var options: List<CoreExploreOption> = emptyList()
        private set

    var results: List<CoreSearchResult> = emptyList()
        private set

    var page: Int = 1
        private set

    var error: String? = null
        private set

    var sessionStatus: CoreSourceSessionStatus? = null
        private set

    var loginUrl: String? = null
        private set

    var loginSourceUrl: String? = null
        private set

    var isLoading: Boolean = false
        private set

    var filteredCount: Int = 0
        private set

    var invalidRuleCount: Int = 0
        private set

    fun refreshSources() {
        sources = library.enabledSources()
            .filter { it.enabled && it.enabledExplore && !it.exploreUrl.isNullOrBlank() && !it.ruleExplore.isNullOrBlank() }
            .sortedWith(compareBy<CoreBookSource> { it.customOrder }.thenBy { it.bookSourceName })
        val selectedUrl = selectedSource?.bookSourceUrl
        val next = sources.firstOrNull { it.bookSourceUrl == selectedUrl } ?: sources.firstOrNull()
        selectSource(next)
    }

    fun selectSource(source: CoreBookSource?) {
        selectedSource = source
        options = source?.exploreUrl?.let { io.legado.core.source.CoreExploreUrlOptions.parse(it) }.orEmpty()
        page = 1
        results = emptyList()
        error = null
        clearSessionState()
        filteredCount = 0
        invalidRuleCount = 0
    }

    fun setOption(name: String, value: String) {
        val current = options.firstOrNull { it.name == name } ?: return
        if (current.options.none { it.second == value }) return
        if (current.selectedValue == value) return
        options = options.map { option ->
            if (option.name == name) option.copy(selectedValue = value) else option
        }
        page = 1
        results = emptyList()
        error = null
        clearSessionState()
    }

    fun load() {
        loadPage(1, append = false)
    }

    fun loadNextPage() {
        loadPage(page + 1, append = true)
    }

    fun addToBookshelf(result: CoreSearchResult) {
        library.saveBook(result.book)
    }

    private fun loadPage(targetPage: Int, append: Boolean) {
        val source = selectedSource
        if (!append) {
            filteredCount = 0
            invalidRuleCount = 0
        }
        if (source == null || source.exploreUrl.isNullOrBlank() || source.ruleExplore.isNullOrBlank()) {
            error = "该书源没有发现配置"
            results = if (append) results else emptyList()
            return
        }
        isLoading = true
        error = null
        clearSessionState()
        try {
            val loaded = service.explore(
                source = source,
                page = targetPage,
                selectedOptions = options.associate { it.name to it.selectedValue }
            )
            val report = sourceFilterService.apply(loaded)
            filteredCount += report.filteredCount
            invalidRuleCount = report.invalidRuleCount
            results = if (append) (results + report.results).distinctBy { it.book.bookUrl } else report.results
            page = targetPage
        } catch (throwable: Throwable) {
            error = throwable.message ?: "发现加载失败"
            captureSessionFailure(throwable)
            if (!append) results = emptyList()
        } finally {
            isLoading = false
        }
    }

    private fun clearSessionState() {
        sessionStatus = null
        loginUrl = null
        loginSourceUrl = null
    }

    private fun captureSessionFailure(throwable: Throwable) {
        if (throwable is CoreSourceSessionException) {
            sessionStatus = throwable.status
            loginUrl = throwable.loginUrl
            loginSourceUrl = throwable.sourceUrl
        }
    }
}
