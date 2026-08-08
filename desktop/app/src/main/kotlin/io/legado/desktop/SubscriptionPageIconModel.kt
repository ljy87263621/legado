package io.legado.desktop

import io.legado.core.library.CoreSubscriptionPage
import java.net.URI

object SubscriptionPageIconModel {
    fun candidateUrls(page: CoreSubscriptionPage): List<String> {
        val explicit = page.iconUrl?.trim()?.takeIf(String::isNotBlank)
        val fallback = faviconUrl(page.url)
        return listOfNotNull(explicit, fallback).distinct()
    }

    private fun faviconUrl(pageUrl: String): String? {
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https" || uri.host.isNullOrBlank()) return null
        return URI(scheme, uri.userInfo, uri.host, uri.port, "/favicon.ico", null, null).toString()
    }
}
