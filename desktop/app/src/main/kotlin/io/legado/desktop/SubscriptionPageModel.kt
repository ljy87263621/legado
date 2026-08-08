package io.legado.desktop

import io.legado.core.library.CoreLibrary
import io.legado.core.library.CoreSubscriptionPage
import java.net.URI

class SubscriptionPageModel(
    private val library: CoreLibrary,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    var pages: List<CoreSubscriptionPage> = library.subscriptionPages()
        private set

    fun refresh() {
        pages = library.subscriptionPages()
    }

    fun savePage(
        url: String,
        title: String = "",
        iconUrl: String? = null,
        category: String = "订阅页面"
    ): CoreSubscriptionPage {
        val normalizedUrl = normalizeUrl(url)
        val page = CoreSubscriptionPage(
            url = normalizedUrl,
            title = title.ifBlank { fallbackTitle(normalizedUrl) },
            iconUrl = iconUrl?.takeIf(String::isNotBlank),
            category = category.ifBlank { "订阅页面" },
            lastUpdatedAt = clock()
        )
        library.saveSubscriptionPage(page)
        refresh()
        return page
    }

    fun savePage(page: SubscriptionDirectoryPage): CoreSubscriptionPage = savePage(
        url = page.url,
        title = page.title,
        iconUrl = page.iconUrl
    )

    fun deletePage(url: String) {
        library.deleteSubscriptionPage(url)
        refresh()
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        require(trimmed.isNotBlank()) { "订阅页地址不能为空" }
        val uri = runCatching { URI(trimmed) }
            .getOrElse { error -> throw IllegalArgumentException("订阅页地址无效", error) }
        require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
            "订阅页地址必须使用 HTTP 或 HTTPS"
        }
        require(!uri.host.isNullOrBlank()) { "订阅页地址缺少主机名" }
        return trimmed
    }

    private fun fallbackTitle(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val lastPath = uri.path.orEmpty().trim('/').substringAfterLast('/').trim()
        return lastPath
            .substringBeforeLast('.', missingDelimiterValue = lastPath)
            .ifBlank { uri.host.orEmpty() }
            .ifBlank { url }
    }
}
