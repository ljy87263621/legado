package io.legado.core.source

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jayway.jsonpath.JsonPath
import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreLibrary
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class CoreSearchRule(
    val checkKeyWord: String? = null,
    val hasMoreRule: String? = null,
    val bookList: String? = null,
    val name: String? = null,
    val author: String? = null,
    val intro: String? = null,
    val kind: String? = null,
    val lastChapter: String? = null,
    val updateTime: String? = null,
    val bookUrl: String? = null,
    val coverUrl: String? = null,
    val wordCount: String? = null
)

data class CoreSearchResult(
    val book: CoreBook,
    val source: CoreBookSource
)

data class CoreHttpResponse(
    val url: String,
    val body: String,
    val statusCode: Int = 200,
    val headers: Map<String, List<String>> = emptyMap()
)

interface CoreHttpClient {
    fun get(url: String, headers: Map<String, String> = emptyMap()): CoreHttpResponse
}

/** Small JVM-only client used by the desktop app when no platform HTTP adapter is supplied. */
class JavaNetHttpClient(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000
) : CoreHttpClient {
    private val cookiesByHost = ConcurrentHashMap<String, MutableMap<String, String>>()

    override fun get(url: String, headers: Map<String, String>): CoreHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.instanceFollowRedirects = true
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val hostCookies = cookiesByHost[connection.url.host].orEmpty()
            if (hostCookies.isNotEmpty() && connection.getRequestProperty("Cookie") == null) {
                connection.setRequestProperty(
                    "Cookie",
                    hostCookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
                )
            }
            val statusCode = connection.responseCode
            val responseStream = if (statusCode >= 400) connection.errorStream else connection.inputStream
            val body = responseStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.headerFields["Set-Cookie"].orEmpty().forEach { cookie ->
                cookie.substringBefore(';').split('=', limit = 2).takeIf { it.size == 2 }?.let { pair ->
                    cookiesByHost.getOrPut(connection.url.host) { ConcurrentHashMap() }[pair[0]] = pair[1]
                }
            }
            return CoreHttpResponse(
                url = connection.url.toExternalForm(),
                body = body,
                statusCode = statusCode,
                headers = connection.headerFields.filterKeys { it != null }
                    .mapKeys { it.key!! }
            )
        } finally {
            connection.disconnect()
        }
    }
}

object SourceUrlTemplate {
    fun expand(template: String, keyword: String, page: Int): String {
        val queryStart = template.indexOf('?')
        return Regex("\\{\\{(key|keyword|page)}}", RegexOption.IGNORE_CASE).replace(template) { match ->
            val inQuery = queryStart >= 0 && match.range.first > queryStart
            when (match.groupValues[1].lowercase(Locale.ROOT)) {
                "page" -> page.toString()
                else -> if (inQuery) encode(keyword) else keyword
            }
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}

object BookSourceJsonCodec {
    private val gson = Gson()
    private val ruleFields = setOf("ruleExplore", "ruleSearch", "ruleBookInfo", "ruleToc", "ruleContent", "ruleReview")

    fun encode(source: CoreBookSource): String = gson.toJson(source)

    fun encode(sources: Collection<CoreBookSource>): String = gson.toJson(sources)

    fun decode(json: String): List<CoreBookSource> {
        val root = JsonParser.parseString(json)
        return when {
            root.isJsonArray -> root.asJsonArray.map(::decodeSource)
            root.isJsonObject -> listOf(decodeSource(root.asJsonObject))
            else -> error("书源 JSON 必须是对象或数组")
        }
    }

    private fun decodeSource(element: JsonElement): CoreBookSource {
        val objectValue = element.asJsonObject.deepCopy()
        ruleFields.forEach { field ->
            val value = objectValue.get(field)
            if (value?.isJsonObject == true || value?.isJsonArray == true) {
                objectValue.addProperty(field, value.toString())
            }
        }
        return CoreBookSource(
            bookSourceUrl = objectValue.requiredString("bookSourceUrl"),
            bookSourceName = objectValue.string("bookSourceName") ?: "",
            bookSourceGroup = objectValue.string("bookSourceGroup"),
            bookSourceType = objectValue.int("bookSourceType", 0),
            bookUrlPattern = objectValue.string("bookUrlPattern"),
            customOrder = objectValue.int("customOrder", 0),
            enabled = objectValue.boolean("enabled", true),
            enabledExplore = objectValue.boolean("enabledExplore", true),
            enabledReview = objectValue.boolean("enabledReview", true),
            enabledCookieJar = objectValue.nullableBoolean("enabledCookieJar", true),
            enableDangerousApi = objectValue.nullableBoolean("enableDangerousApi", false),
            concurrentRate = objectValue.string("concurrentRate"),
            header = objectValue.string("header"),
            loginUrl = objectValue.string("loginUrl"),
            loginUi = objectValue.string("loginUi"),
            searchUrl = objectValue.string("searchUrl"),
            ruleSearch = objectValue.string("ruleSearch"),
            ruleBookInfo = objectValue.string("ruleBookInfo"),
            ruleToc = objectValue.string("ruleToc"),
            ruleContent = objectValue.string("ruleContent"),
            ruleExplore = objectValue.string("ruleExplore"),
            ruleReview = objectValue.string("ruleReview"),
            jsLib = objectValue.string("jsLib"),
            loginCheckJs = objectValue.string("loginCheckJs"),
            coverDecodeJs = objectValue.string("coverDecodeJs"),
            bookSourceComment = objectValue.string("bookSourceComment"),
            variableComment = objectValue.string("variableComment"),
            lastUpdateTime = objectValue.long("lastUpdateTime", 0),
            respondTime = objectValue.long("respondTime", 180000),
            weight = objectValue.int("weight", 0),
            exploreUrl = objectValue.string("exploreUrl"),
            exploreScreen = objectValue.string("exploreScreen"),
            exploreStyle = objectValue.int("exploreStyle", 0)
        )
    }

    private fun JsonObject.requiredString(name: String): String =
        string(name) ?: error("书源缺少必填字段: $name")

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.asString

    private fun JsonObject.int(name: String, default: Int): Int =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.asInt ?: default

    private fun JsonObject.long(name: String, default: Long): Long =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.asLong ?: default

    private fun JsonObject.boolean(name: String, default: Boolean): Boolean =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.asBoolean ?: default

    private fun JsonObject.nullableBoolean(name: String, default: Boolean): Boolean? =
        get(name)?.let { if (it.isJsonNull) null else it.asBoolean } ?: default
}

class BookSourceSearchService(
    private val library: CoreLibrary,
    private val httpClient: CoreHttpClient
) {
    fun search(keyword: String, page: Int = 1): List<CoreSearchResult> {
        require(keyword.isNotBlank()) { "搜索关键字不能为空" }
        val results = mutableListOf<CoreSearchResult>()
        val failures = mutableListOf<Throwable>()
        val searchableSources = library.enabledSources()
            .filter { !it.searchUrl.isNullOrBlank() && !it.ruleSearch.isNullOrBlank() }
        searchableSources.forEach { source ->
            runCatching { searchSource(source, keyword, page) }
                .onSuccess(results::addAll)
                .onFailure(failures::add)
        }
        if (searchableSources.isNotEmpty() && failures.size == searchableSources.size) {
            throw failures.first().let { error ->
                IllegalStateException(error.message ?: "所有书源搜索失败", error)
            }
        }
        return deduplicate(results)
    }

    fun searchAndAddToBookshelf(keyword: String, page: Int = 1): List<CoreSearchResult> {
        val results = search(keyword, page)
        results.forEach { result -> library.saveBook(result.book) }
        return results
    }

    private fun searchSource(source: CoreBookSource, keyword: String, page: Int): List<CoreSearchResult> {
        val url = SourceUrlTemplate.expand(source.searchUrl!!, keyword, page)
        val response = httpClient.get(url, parseHeaders(source.header))
        check(response.statusCode in 200..399) { "书源请求失败: HTTP ${response.statusCode}" }
        val rule = parseRule(source.ruleSearch!!)
        val baseUrl = response.url.ifBlank { url }
        val records = when {
            isRegexList(rule.bookList) -> parseRegexRecords(rule, response.body)
            response.body.trimStart().startsWith("{") || response.body.trimStart().startsWith("[") ->
                parseJsonRecords(rule, response.body)
            else -> parseHtmlRecords(rule, response.body, baseUrl)
        }
        return records.mapNotNull { record -> toResult(source, rule, record, baseUrl) }
    }

    private fun parseRule(raw: String): CoreSearchRule {
        val element = JsonParser.parseString(raw)
        val objectValue = if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            JsonParser.parseString(element.asString).asJsonObject
        } else {
            element.asJsonObject
        }
        return Gson().fromJson(objectValue, CoreSearchRule::class.java)
    }

    private fun parseHtmlRecords(rule: CoreSearchRule, body: String, baseUrl: String): List<Any> {
        val document = Jsoup.parse(body, baseUrl)
        val selector = rule.bookList.orEmpty().removePrefix("@CSS:").trim()
        if (selector.isBlank()) return listOf(document)
        return document.select(selector)
    }

    private fun parseJsonRecords(rule: CoreSearchRule, body: String): List<Any> {
        val root = JsonPath.parse(body)
        val listRule = rule.bookList.orEmpty().ifBlank { "$" }
        val value = root.read<Any>(listRule)
        return when (value) {
            is Collection<*> -> value.filterNotNull()
            else -> listOf(value)
        }
    }

    private fun parseRegexRecords(rule: CoreSearchRule, body: String): List<Any> {
        val pattern = regexPattern(rule.bookList!!)
        return pattern.findAll(body).toList()
    }

    private fun toResult(source: CoreBookSource, rule: CoreSearchRule, record: Any, baseUrl: String): CoreSearchResult? {
        val name = extractField(rule.name, record, baseUrl).trim()
        if (name.isBlank()) return null
        val author = extractField(rule.author, record, baseUrl).trim()
        val bookUrl = resolveUrl(baseUrl, extractField(rule.bookUrl, record, baseUrl).trim().ifBlank { baseUrl })
        val book = CoreBook(
            bookUrl = bookUrl,
            tocUrl = bookUrl,
            name = name,
            author = author,
            origin = source.bookSourceUrl,
            originName = source.bookSourceName,
            coverUrl = resolveOptionalUrl(baseUrl, extractField(rule.coverUrl, record, baseUrl)),
            intro = extractField(rule.intro, record, baseUrl).ifBlank { null },
            kind = extractField(rule.kind, record, baseUrl).ifBlank { null },
            latestChapterTitle = extractField(rule.lastChapter, record, baseUrl).ifBlank { null },
            wordCount = extractField(rule.wordCount, record, baseUrl).ifBlank { null },
            type = source.bookSourceType,
            order = source.customOrder,
            originOrder = source.customOrder
        )
        return CoreSearchResult(book, source)
    }

    private fun extractField(rule: String?, record: Any, baseUrl: String): String {
        if (rule.isNullOrBlank()) return ""
        if (record is MatchResult) return extractRegexField(rule, record)
        if (record is Element) return extractHtmlField(rule, record)
        return extractJsonField(rule, record)
    }

    private fun extractHtmlField(rule: String, element: Element): String {
        val normalized = rule.removePrefix("@CSS:").trim()
        val separator = normalized.lastIndexOf('@')
        if (separator > 0) {
            val selector = normalized.substring(0, separator)
            val attribute = normalized.substring(separator + 1)
            val target = element.selectFirst(selector) ?: return ""
            return if (attribute.equals("text", true)) target.text() else target.attr(attribute)
        }
        return element.selectFirst(normalized)?.text().orEmpty()
    }

    private fun extractJsonField(rule: String, record: Any): String {
        return runCatching {
            val value = when {
                record is Map<*, *> && !rule.startsWith("$") -> record[rule]
                rule.startsWith("$") -> JsonPath.read(record, rule)
                else -> null
            }
            stringify(value)
        }.getOrDefault("")
    }

    private fun extractRegexField(rule: String, match: MatchResult): String {
        val group = rule.removePrefix("group:").removePrefix("$")
        return runCatching {
            if (group.toIntOrNull() != null) match.groupValues[group.toInt()] else match.groups[group]?.value.orEmpty()
        }.getOrDefault("")
    }

    private fun stringify(value: Any?): String = when (value) {
        null -> ""
        is Collection<*> -> value.joinToString("\n") { stringify(it) }
        is Map<*, *> -> value.toString()
        else -> value.toString()
    }

    private fun deduplicate(results: List<CoreSearchResult>): List<CoreSearchResult> {
        val seen = linkedSetOf<String>()
        return results.filter { result ->
            val book = result.book
            val key = if (book.bookUrl.isNotBlank()) {
                "url:${book.bookUrl}"
            } else {
                "name:${book.name.lowercase(Locale.ROOT)}|author:${book.author.lowercase(Locale.ROOT)}"
            }
            seen.add(key)
        }
    }

    private fun parseHeaders(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            JsonParser.parseString(raw).asJsonObject.entrySet().associate { (key, value) -> key to value.asString }
        }.getOrElse {
            raw.lineSequence()
                .mapNotNull { line -> line.split(':', limit = 2).takeIf { it.size == 2 } }
                .associate { it[0].trim() to it[1].trim() }
        }
    }

    private fun isRegexList(rule: String?): Boolean = rule?.trimStart()?.startsWith("@regex:", true) == true ||
        rule?.trimStart()?.startsWith(":") == true

    private fun regexPattern(rule: String): Regex {
        val pattern = rule.trim().let {
            when {
                it.startsWith("@regex:", true) -> it.substringAfter(':')
                it.startsWith(":") -> it.substring(1)
                else -> it
            }
        }
        return Regex(pattern, setOf(RegexOption.DOT_MATCHES_ALL))
    }

    private fun resolveOptionalUrl(baseUrl: String, value: String): String? = value.trim().ifBlank { null }?.let {
        resolveUrl(baseUrl, it)
    }

    private fun resolveUrl(baseUrl: String, value: String): String = runCatching {
        URI(baseUrl).resolve(value).toString()
    }.getOrDefault(value)
}
