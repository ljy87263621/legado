package io.legado.desktop

import io.legado.core.source.CoreHttpClient
import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

enum class SubscriptionEntryKind {
    WEB_PAGE,
    BOOK_SOURCE,
    RESOURCE,
    NAVIGATION
}

enum class SubscriptionResourceCategory {
    WEB_PAGE,
    BOOK_SOURCE,
    RULE,
    TTS,
    RESOURCE
}

data class SubscriptionDirectoryPage(
    val url: String,
    val title: String,
    val iconUrl: String? = null,
    val entries: List<SubscriptionDirectoryEntry> = emptyList(),
    val navigationEntries: List<SubscriptionDirectoryEntry> = emptyList()
)

data class SubscriptionDirectoryEntry(
    val title: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val updatedAt: String = "",
    val importUrl: String? = null,
    val externalUrl: String? = null,
    val kind: SubscriptionEntryKind = SubscriptionEntryKind.WEB_PAGE,
    val resourceCategory: SubscriptionResourceCategory = SubscriptionResourceCategory.WEB_PAGE
) {
    val isImportable: Boolean
        get() = !importUrl.isNullOrBlank()
}

data class SubscriptionDirectoryResourceSections(
    val bookSources: List<SubscriptionDirectoryEntry> = emptyList(),
    val rules: List<SubscriptionDirectoryEntry> = emptyList(),
    val tts: List<SubscriptionDirectoryEntry> = emptyList(),
    val resources: List<SubscriptionDirectoryEntry> = emptyList(),
    val webPages: List<SubscriptionDirectoryEntry> = emptyList()
)

fun SubscriptionDirectoryPage.resourceSections(): SubscriptionDirectoryResourceSections {
    val visibleEntries = entries.filter { it.externalUrl != null || it.isImportable }
    return SubscriptionDirectoryResourceSections(
        bookSources = visibleEntries.filter {
            it.resourceCategory == SubscriptionResourceCategory.BOOK_SOURCE
        },
        rules = visibleEntries.filter {
            it.resourceCategory == SubscriptionResourceCategory.RULE
        },
        tts = visibleEntries.filter {
            it.resourceCategory == SubscriptionResourceCategory.TTS
        },
        resources = visibleEntries.filter {
            it.resourceCategory == SubscriptionResourceCategory.RESOURCE
        },
        webPages = visibleEntries.filter {
            it.resourceCategory == SubscriptionResourceCategory.WEB_PAGE
        }
    )
}

object SubscriptionDirectoryModel {
    const val DEFAULT_DIRECTORY_URL = "http://yuedu.miaogongzi.net/"

    fun parseHtml(html: String, baseUrl: String): List<SubscriptionDirectoryEntry> {
        val document = Jsoup.parse(html, baseUrl)
        return parseEntries(document, baseUrl)
    }

    fun parsePage(html: String, baseUrl: String): SubscriptionDirectoryPage {
        val document = Jsoup.parse(html, baseUrl)
        val title = document.selectFirst("meta[property=og:title], meta[name=title]")
            ?.attr("content")
            ?.trim()
            .orEmpty()
            .ifBlank { document.title().trim() }
            .ifBlank { document.selectFirst("h1, h2")?.text()?.trim().orEmpty() }
            .ifBlank { fallbackTitle(baseUrl) }
        val iconUrl = document.selectFirst("link[rel~=(?i)icon], link[rel~=(?i)apple-touch-icon]")
            ?.attr("href")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: document.selectFirst("meta[property=og:image], meta[name=twitter:image], meta[name=msapplication-TileImage]")
                ?.attr("content")
                ?.trim()
                ?.takeIf(String::isNotBlank)
        val resolvedIconUrl = iconUrl?.let { resolveUrl(it, baseUrl) }

        return SubscriptionDirectoryPage(
            url = baseUrl,
            title = title,
            iconUrl = resolvedIconUrl,
            entries = parseEntries(document, baseUrl),
            navigationEntries = parseNavigationEntries(document, baseUrl)
        )
    }

    private fun parseEntries(document: org.jsoup.nodes.Document, baseUrl: String): List<SubscriptionDirectoryEntry> {
        val cards = document.select(".aui-flex")
        val elements = if (cards.isEmpty()) {
            document.select("article, .subscription, .source-card, li").ifEmpty {
                document.select("h1, h2, h3")
            }
        } else {
            cards
        }

        return elements.mapNotNull { element ->
            if (element.closest("footer, nav") != null || element.selectFirst("footer, nav") != null) {
                null
            } else {
                parseEntry(element, baseUrl)
            }
        }.filterNot { it.kind == SubscriptionEntryKind.NAVIGATION }
    }

    private fun parseNavigationEntries(
        document: org.jsoup.nodes.Document,
        baseUrl: String
    ): List<SubscriptionDirectoryEntry> {
        return document.select("footer a[href], nav a[href], .aui-footer a[href]")
            .mapNotNull { link ->
                val title = link.text().trim()
                val href = link.attr("href").trim()
                if (title.isBlank() || href.isBlank()) return@mapNotNull null
                val url = resolveUrl(href, baseUrl)
                val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
                if (scheme !in setOf("http", "https")) return@mapNotNull null
                SubscriptionDirectoryEntry(
                    title = title,
                    externalUrl = url,
                    kind = SubscriptionEntryKind.WEB_PAGE,
                    resourceCategory = SubscriptionResourceCategory.WEB_PAGE
                )
            }
            .distinctBy { it.externalUrl }
    }

    fun load(httpClient: CoreHttpClient, directoryUrl: String): List<SubscriptionDirectoryEntry> =
        loadPage(httpClient, directoryUrl).entries

    fun loadPage(httpClient: CoreHttpClient, directoryUrl: String): SubscriptionDirectoryPage {
        val response = httpClient.get(directoryUrl)
        require(response.statusCode in 200..299) {
            "订阅目录请求失败: HTTP ${response.statusCode}"
        }
        return parsePage(response.body, response.url.ifBlank { directoryUrl })
    }

    fun pageEntry(url: String, title: String): SubscriptionDirectoryEntry =
        SubscriptionDirectoryEntry(
            title = title.ifBlank { url },
            externalUrl = url,
            kind = SubscriptionEntryKind.WEB_PAGE
        )

    private fun parseEntry(element: Element, baseUrl: String): SubscriptionDirectoryEntry? {
        val title = element.selectFirst("h1, h2, h3, .title")?.text()?.trim().orEmpty()
        val tags = element.select("em, .tag, .badge")
            .map { it.text().trim() }
            .filter(String::isNotBlank)
            .distinct()
        val description = element.selectFirst(
            ".aui-flex-box > div:not(.aui-film-button), .description, .intro, p"
        )?.text()?.trim().orEmpty()
        val updatedAt = element.selectFirst("time, .update, .updated, .date, [data-update], [data-updated]")
            ?.text()?.trim().orEmpty()
        if (title.isBlank()) {
            return null
        }
        val links = element.select("a[href]")
        val linkCandidates = links.mapNotNull { link ->
            val href = link.attr("href").trim()
            if (href.isBlank()) return@mapNotNull null
            val url = resolveUrl(href, baseUrl)
            val actionText = link.text().ifBlank { element.selectFirst("button")?.text().orEmpty() }
            Triple(url, actionText, link)
        }
        val importCandidate = linkCandidates.firstOrNull { (url, actionText, _) ->
            isImportLink(url, actionText)
        }
        val resolvedUrl = importCandidate?.first ?: linkCandidates.firstOrNull()?.first
        val importKind = importCandidate?.first?.let(SourceImportUrl::classify)
        val hasNonBookSourceSemantics = isNonBookSourceSemantic(title, tags)
        val importUrl = when {
            importCandidate == null -> null
            importKind == SourceImportKind.BOOK_SOURCE && !hasNonBookSourceSemantics -> importCandidate.first
            else -> null
        }
        val externalUrl = when {
            importCandidate != null && importUrl == null -> SourceImportUrl.extractSource(importCandidate.first)
            importCandidate == null -> resolvedUrl
            else -> linkCandidates.firstOrNull { it.first != importCandidate.first }?.first
        }
        val kind = when {
            importCandidate != null && importUrl != null -> SubscriptionEntryKind.BOOK_SOURCE
            importCandidate != null -> SubscriptionEntryKind.RESOURCE
            resolvedUrl.isNullOrBlank() -> SubscriptionEntryKind.WEB_PAGE
            else -> SubscriptionEntryKind.WEB_PAGE
        }
        return SubscriptionDirectoryEntry(
            title = title,
            description = description,
            tags = tags,
            updatedAt = updatedAt,
            importUrl = importUrl,
            externalUrl = externalUrl,
            kind = kind,
            resourceCategory = classifyResourceCategory(title, tags, resolvedUrl, kind)
        )
    }

    private fun classifyResourceCategory(
        title: String,
        tags: List<String>,
        linkUrl: String?,
        kind: SubscriptionEntryKind
    ): SubscriptionResourceCategory {
        if (kind == SubscriptionEntryKind.BOOK_SOURCE) return SubscriptionResourceCategory.BOOK_SOURCE

        val text = (listOf(title) + tags + listOf(linkUrl.orEmpty()))
            .joinToString(" ")
            .lowercase()
        return when {
            "tts" in text || "语音" in text || "朗读" in text -> SubscriptionResourceCategory.TTS
            "规则" in text || "净化" in text || "replace" in text -> SubscriptionResourceCategory.RULE
            kind == SubscriptionEntryKind.RESOURCE -> SubscriptionResourceCategory.RESOURCE
            else -> SubscriptionResourceCategory.WEB_PAGE
        }
    }

    private fun isImportLink(url: String, actionText: String): Boolean {
        val normalized = url.lowercase()
        if (normalized.startsWith("yuedu://") || normalized.startsWith("legado://")) {
            return true
        }
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme !in setOf("http", "https")) return false
        val saysImport = actionText.contains("导入") || actionText.contains("import", ignoreCase = true)
        return saysImport
    }

    private fun isNonBookSourceSemantic(title: String, tags: List<String>): Boolean {
        val text = (listOf(title) + tags).joinToString(" ").lowercase()
        return "规则" in text || "净化" in text || "replace" in text ||
            "tts" in text || "语音" in text || "朗读" in text
    }

    private fun resolveUrl(href: String, baseUrl: String): String {
        if (href.startsWith("yuedu://", ignoreCase = true) ||
            href.startsWith("legado://", ignoreCase = true)
        ) {
            return href
        }
        return runCatching { URI(baseUrl).resolve(href).toString() }
            .getOrElse { href }
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
