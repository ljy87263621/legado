package io.legado.core.source

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.jayway.jsonpath.JsonPath
import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreLibrary
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.util.Locale

data class CoreBookInfoRule(
    val init: String? = null,
    val name: String? = null,
    val author: String? = null,
    val intro: String? = null,
    val kind: String? = null,
    val lastChapter: String? = null,
    val updateTime: String? = null,
    val coverUrl: String? = null,
    val tocUrl: String? = null,
    val wordCount: String? = null,
    val canReName: String? = null,
    val downloadUrls: String? = null
)

data class CoreTocRule(
    val preUpdateJs: String? = null,
    val chapterList: String? = null,
    val chapterName: String? = null,
    val chapterUrl: String? = null,
    val formatJs: String? = null,
    val isVolume: String? = null,
    val isVip: String? = null,
    val isPay: String? = null,
    val updateTime: String? = null,
    val nextTocUrl: String? = null
)

data class CoreContentRule(
    val content: String? = null,
    val title: String? = null,
    val nextContentUrl: String? = null,
    val webJs: String? = null,
    val sourceRegex: String? = null,
    val replaceRegex: String? = null
)

/** Fetches and parses online books while keeping chapters and content in CoreLibrary. */
class OnlineBookService(
    private val library: CoreLibrary,
    private val httpClient: CoreHttpClient
) {

    fun loadBookInfo(book: CoreBook, body: String? = null): CoreBook {
        val source = sourceFor(book)
        val response = body?.let { CoreHttpResponse(book.bookUrl, it) } ?: request(source, book.bookUrl)
        val rule = parseRule(source.ruleBookInfo, CoreBookInfoRule::class.java)
        val regexContext = regexContext(ruleFields(rule), response.body)
        val initElement = rule.init?.takeIf(String::isNotBlank)?.let {
            Jsoup.parse(response.body, response.url).selectFirst(normalizeCssRule(it))
        }
        val updated = book.copy(
            name = extract(source, "ruleBookInfo.name", rule.name, response.body, response.url, regexContext, initElement)
                .ifBlank { book.name },
            author = extract(source, "ruleBookInfo.author", rule.author, response.body, response.url, regexContext, initElement)
                .ifBlank { book.author },
            intro = extract(source, "ruleBookInfo.intro", rule.intro, response.body, response.url, regexContext, initElement)
                .ifBlank { book.intro },
            kind = extract(source, "ruleBookInfo.kind", rule.kind, response.body, response.url, regexContext, initElement)
                .ifBlank { book.kind },
            latestChapterTitle = extract(
                source, "ruleBookInfo.lastChapter", rule.lastChapter, response.body, response.url, regexContext, initElement
            )
                .ifBlank { book.latestChapterTitle },
            coverUrl = extract(source, "ruleBookInfo.coverUrl", rule.coverUrl, response.body, response.url, regexContext, initElement)
                .trim()
                .ifBlank { book.coverUrl }
                ?.let { resolve(response.url, it) },
            tocUrl = extract(source, "ruleBookInfo.tocUrl", rule.tocUrl, response.body, response.url, regexContext, initElement)
                .trim()
                .ifBlank { book.tocUrl.ifBlank { book.bookUrl } }
                .let { resolve(response.url, it) },
            wordCount = extract(source, "ruleBookInfo.wordCount", rule.wordCount, response.body, response.url, regexContext, initElement)
                .ifBlank { book.wordCount }
        )
        library.saveBook(updated)
        return updated
    }

    fun refreshChapters(book: CoreBook, runPreUpdateJs: Boolean = true): List<CoreChapter> {
        val source = sourceFor(book)
        val rule = parseRule(source.ruleToc, CoreTocRule::class.java)
        val effectiveBook = if (runPreUpdateJs && !rule.preUpdateJs.isNullOrBlank()) {
            CoreSourceScriptSupport.applyBookScript(
                source = source,
                ruleField = "ruleToc.preUpdateJs",
                script = rule.preUpdateJs,
                book = book
            ).also { if (it != book) library.saveBook(it) }
        } else {
            book
        }
        val preserveScriptedTotalChapterNum = effectiveBook.totalChapterNum != book.totalChapterNum
        val preserveScriptedLatestChapterTitle = effectiveBook.latestChapterTitle != book.latestChapterTitle
        val firstUrl = effectiveBook.tocUrl.ifBlank { effectiveBook.bookUrl }
        if (rule.chapterList.isNullOrBlank()) {
            val chapter = CoreChapter(effectiveBook.bookUrl, firstUrl, "共一章", 0)
            library.saveChapter(chapter)
            saveChapterCount(
                effectiveBook,
                listOf(chapter),
                updateTotalChapterNum = !preserveScriptedTotalChapterNum,
                updateLatestChapterTitle = !preserveScriptedLatestChapterTitle
            )
            return listOf(chapter)
        }

        val chapters = mutableListOf<CoreChapter>()
        val visitedPages = linkedSetOf<String>()
        var nextUrl: String? = firstUrl
        while (!nextUrl.isNullOrBlank() && visitedPages.add(nextUrl)) {
            val response = request(source, nextUrl!!)
            val records = records(rule.chapterList, response.body)
            val regexContext = regexContext(
                listOf(rule.chapterName, rule.chapterUrl, rule.isVolume, rule.isVip, rule.isPay, rule.updateTime),
                response.body
            )
            records.forEach { record ->
                val title = extract(
                    source, "ruleToc.chapterName", rule.chapterName, response.body, response.url, regexContext, record
                ).trim()
                if (title.isBlank()) return@forEach
                val rawUrl = extract(
                    source, "ruleToc.chapterUrl", rule.chapterUrl, response.body, response.url, regexContext, record
                )
                    .trim()
                    .ifBlank { response.url }
                val chapterUrl = resolve(response.url, rawUrl)
                if (chapters.none { it.url == chapterUrl }) {
                    var chapter = CoreChapter(
                        bookUrl = effectiveBook.bookUrl,
                        url = chapterUrl,
                        title = title,
                        index = chapters.size,
                        isVolume = extract(source, "ruleToc.isVolume", rule.isVolume, response.body, response.url, regexContext, record)
                            .isTrueValue(),
                        isVip = extract(source, "ruleToc.isVip", rule.isVip, response.body, response.url, regexContext, record)
                            .isTrueValue(),
                        isPay = extract(source, "ruleToc.isPay", rule.isPay, response.body, response.url, regexContext, record)
                            .isTrueValue(),
                        tag = extract(source, "ruleToc.updateTime", rule.updateTime, response.body, response.url, regexContext, record)
                            .ifBlank { null }
                    )
                    if (!rule.formatJs.isNullOrBlank()) {
                        val originalTitle = chapter.title
                        val scriptResult = CoreSourceScriptSupport.applyChapterScript(
                            source = source,
                            ruleField = "ruleToc.formatJs",
                            script = rule.formatJs,
                            chapter = chapter,
                            bindings = mapOf(
                                "gInt" to 0,
                                "index" to chapters.size + 1,
                                "title" to chapter.title
                            )
                        )
                        val formattedChapter = scriptResult.first
                        val formatted = scriptResult.second?.toString()?.takeIf { it != "null" }
                        chapter = formattedChapter
                        if (!formatted.isNullOrBlank() && formattedChapter.title == originalTitle) {
                            chapter = chapter.copy(title = formatted)
                        }
                    }
                    chapters += chapter
                }
            }
            nextUrl = extract(source, "ruleToc.nextTocUrl", rule.nextTocUrl, response.body, response.url, regexContext)
                .trim()
                .ifBlank { null }
                ?.let { resolve(response.url, it) }
        }
        require(chapters.isNotEmpty()) { "目录解析结果为空: $firstUrl" }
        chapters.forEachIndexed { index, chapter ->
            val normalized = chapter.copy(index = index)
            library.saveChapter(normalized)
        }
        saveChapterCount(
            effectiveBook,
            chapters,
            updateTotalChapterNum = !preserveScriptedTotalChapterNum,
            updateLatestChapterTitle = !preserveScriptedLatestChapterTitle
        )
        return chapters.mapIndexed { index, chapter -> chapter.copy(index = index) }
    }

    fun loadContent(book: CoreBook, chapter: CoreChapter): String {
        val source = sourceFor(book)
        val rule = parseRule(source.ruleContent, CoreContentRule::class.java)
        if (!rule.webJs.isNullOrBlank()) {
            throw CoreScriptException(
                "Browser-dependent webJs requires a desktop browser runtime",
                source.bookSourceUrl,
                "ruleContent.webJs"
            )
        }
        library.content(chapter)?.let { return it }
        if (rule.content.isNullOrBlank()) {
            library.saveContent(chapter, chapter.url)
            return chapter.url
        }

        var effectiveChapter = chapter
        val pages = mutableListOf<String>()
        val visitedPages = linkedSetOf<String>()
        var nextUrl: String? = chapter.url
        while (!nextUrl.isNullOrBlank() && visitedPages.add(nextUrl)) {
            val response = request(source, nextUrl!!)
            val body = rule.sourceRegex?.let { applyReplacement(response.body, it) } ?: response.body
            val regexContext = regexContext(listOf(rule.content, rule.nextContentUrl), body)
            if (pages.isEmpty() && !rule.title.isNullOrBlank()) {
                val title = extract(
                    source,
                    "ruleContent.title",
                    rule.title,
                    body,
                    response.url,
                    regexContext,
                    effectiveChapter,
                    mapOf(
                        "book" to book,
                        "chapter" to effectiveChapter,
                        "title" to effectiveChapter.title
                    )
                ).trim()
                if (title.isNotBlank()) {
                    effectiveChapter = effectiveChapter.copy(title = title)
                    library.saveChapter(effectiveChapter)
                }
            }
            val pageContent = extract(source, "ruleContent.content", rule.content, body, response.url, regexContext)
            if (pageContent.isNotBlank()) pages += pageContent
            nextUrl = extract(source, "ruleContent.nextContentUrl", rule.nextContentUrl, body, response.url, regexContext)
                .trim()
                .ifBlank { null }
                ?.let { resolve(response.url, it) }
        }
        val content = pages.joinToString("\n")
        require(content.isNotBlank()) { "正文解析结果为空: ${chapter.url}" }
        val processed = rule.replaceRegex?.let { applyReplacement(content, it) } ?: content
        library.saveContent(effectiveChapter, processed)
        return processed
    }

    private fun sourceFor(book: CoreBook): CoreBookSource =
        requireNotNull(library.source(book.origin)) { "未找到书源: ${book.origin}" }

    private fun request(source: CoreBookSource, url: String): CoreHttpResponse {
        val response = httpClient.get(url, CoreSourceScriptSupport.headers(source, url))
        check(response.statusCode in 200..399) { "书源请求失败: HTTP ${response.statusCode}" }
        return response
    }

    private fun records(rule: String, body: String): List<Any> {
        if (isRegexRule(rule)) return regexMatches(rule, body)
        if (body.trimStart().startsWith("{") || body.trimStart().startsWith("[")) {
            val value = JsonPath.parse(body).read<Any>(normalizeJsonRule(rule))
            return when (value) {
                is Collection<*> -> value.filterNotNull()
                null -> emptyList()
                else -> listOf(value)
            }
        }
        val selector = normalizeCssRule(rule)
        if (selector.isBlank()) return listOf(Jsoup.parse(body))
        return Jsoup.parse(body).select(selector)
    }

    private fun extract(
        source: CoreBookSource,
        ruleField: String,
        rawRule: String?,
        body: String,
        baseUrl: String,
        regexContext: MatchResult?,
        record: Any? = null,
        bindings: Map<String, Any?> = emptyMap()
    ): String {
        if (rawRule.isNullOrBlank()) return ""
        val (rule, replacement) = splitReplacement(rawRule)
        val value = when {
            CoreSourceScriptSupport.extractScript(rule) != null -> CoreSourceScriptSupport.evaluate(
                source = source,
                ruleField = ruleField,
                rawRule = rule,
                bindings = mapOf(
                    "source" to source,
                    "baseUrl" to baseUrl,
                    "result" to (record ?: body),
                    "src" to body
                ) + bindings
            )?.toString().orEmpty()
            rule.startsWith("group:", true) -> regexContext?.groupValue(rule.substringAfter(':'))
                .orEmpty()
            isRegexRule(rule) -> regexMatches(rule, record?.toString() ?: body).firstOrNull()
                ?.let { match ->
                    val pattern = regexPattern(rule)
                    namedGroups(pattern).firstOrNull()?.let { group -> match.groupValue(group) } ?: match.value
                }
                .orEmpty()
            record is Element -> extractHtml(rule, record)
            isJsonBody(body) -> extractJson(rule, body, record)
            else -> extractHtml(rule, Jsoup.parse(body, baseUrl))
        }
        return replacement?.let { applyReplacement(value, it) } ?: value
    }

    private fun extractHtml(rawRule: String, element: Element): String {
        val rule = normalizeCssRule(rawRule)
        if (rule.isBlank()) return element.text()
        val separator = rule.lastIndexOf('@')
        if (separator > 0) {
            val target = element.selectFirst(rule.substring(0, separator)) ?: return ""
            return attributeValue(target, rule.substring(separator + 1))
        }
        return element.selectFirst(rule)?.text().orEmpty()
    }

    private fun extractJson(rawRule: String, body: String, record: Any?): String {
        val rule = normalizeJsonRule(rawRule)
        return runCatching {
            val value = if (record != null && !rule.startsWith("$")) {
                if (record is Map<*, *>) record[rule] else null
            } else if (record != null) {
                JsonPath.read(record, rule)
            } else {
                JsonPath.parse(body).read<Any>(rule)
            }
            stringify(value)
        }.getOrDefault("")
    }

    private fun regexMatches(rule: String, value: String): List<MatchResult> =
        Regex(regexPattern(rule), setOf(RegexOption.DOT_MATCHES_ALL)).findAll(value).toList()

    private fun regexContext(rules: List<String?>, body: String): MatchResult? =
        rules.firstNotNullOfOrNull { rule ->
            rule?.takeIf(::isRegexRule)?.let { regexMatches(it, body).firstOrNull() }
        }

    private fun ruleFields(rule: CoreBookInfoRule): List<String?> = listOf(
        rule.name, rule.author, rule.intro, rule.kind, rule.lastChapter,
        rule.updateTime, rule.coverUrl, rule.tocUrl, rule.wordCount
    )

    private fun saveChapterCount(
        book: CoreBook,
        chapters: List<CoreChapter>,
        updateTotalChapterNum: Boolean = true,
        updateLatestChapterTitle: Boolean = true
    ) {
        library.saveBook(
            book.copy(
                totalChapterNum = if (updateTotalChapterNum) chapters.size else book.totalChapterNum,
                latestChapterTitle = if (updateLatestChapterTitle) {
                    chapters.lastOrNull()?.title ?: book.latestChapterTitle
                } else {
                    book.latestChapterTitle
                }
            )
        )
    }

    private fun parseHeadersRule(raw: String?): String? = raw

    private fun <T> parseRule(raw: String?, type: Class<T>): T {
        if (raw.isNullOrBlank()) return Gson().fromJson("{}", type)
        val element = JsonParser.parseString(raw)
        return if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            Gson().fromJson(element.asString, type)
        } else {
            Gson().fromJson(element, type)
        }
    }

    private fun splitReplacement(rawRule: String): Pair<String, String?> {
        val parts = rawRule.split("##", limit = 3)
        return parts[0].trim() to if (parts.size == 3) parts[1] + "##" + parts[2] else null
    }

    private fun applyReplacement(value: String, rule: String): String {
        val parts = rule.split("##", limit = 2)
        if (parts.size == 2) return runCatching { value.replace(Regex(parts[0]), parts[1]) }.getOrDefault(value)
        return value
    }

    private fun attributeValue(element: Element, attribute: String): String = when {
        attribute.equals("text", true) -> element.text()
        attribute.equals("html", true) -> element.html()
        else -> element.attr(attribute)
    }

    private fun normalizeCssRule(rule: String): String = rule.removePrefix("@CSS:").trim()

    private fun normalizeJsonRule(rule: String): String = rule.removePrefix("@Json:").trim()

    private fun isJsonBody(body: String): Boolean =
        body.trimStart().startsWith("{") || body.trimStart().startsWith("[")

    private fun isRegexRule(rule: String): Boolean =
        rule.trimStart().startsWith("@regex:", true) || rule.trimStart().startsWith(":")

    private fun regexPattern(rule: String): String = rule.trim().let {
        when {
            it.startsWith("@regex:", true) -> it.substringAfter(':')
            it.startsWith(":") -> it.substring(1)
            else -> it
        }
    }

    private fun MatchResult.groupValue(group: String): String = runCatching {
        group.toIntOrNull()?.let { groupValues[it] } ?: groups[group]?.value.orEmpty()
    }.getOrDefault("")

    private fun namedGroups(pattern: String): List<String> =
        Regex("\\(\\?<([A-Za-z][A-Za-z0-9_]*)>")
            .findAll(pattern)
            .map { it.groupValues[1] }
            .toList()

    private fun String.isTrueValue(): Boolean = lowercase(Locale.ROOT) in setOf("1", "true", "yes", "是", "vip", "付费")

    private fun stringify(value: Any?): String = when (value) {
        null -> ""
        is Collection<*> -> value.joinToString("\n") { stringify(it) }
        is Map<*, *> -> value.toString()
        else -> value.toString()
    }

    private fun resolve(baseUrl: String, value: String): String = runCatching {
        URI(baseUrl).resolve(value).toString()
    }.getOrDefault(value)
}
