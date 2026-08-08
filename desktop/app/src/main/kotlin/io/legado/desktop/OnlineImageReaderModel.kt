package io.legado.desktop

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreLibrary
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpRequest
import io.legado.core.source.CoreHttpResponse
import io.legado.core.source.CoreSourceAwareHttpClient
import io.legado.core.source.CoreUrlRuleSupport
import io.legado.core.source.OnlineBookService
import org.jsoup.Jsoup
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.LinkedHashMap

data class OnlineImagePage(
    val url: String,
    val chapterIndex: Int,
    val pageIndex: Int
)

object OnlineImagePageParser {
    private val imageSuffix = Regex("\\.(?:avif|bmp|gif|jpe?g|png|svg|webp)(?:[?#].*)?$", RegexOption.IGNORE_CASE)
    private val urlPattern = Regex("(?:https?://|/|\\./|../)[^\\s\\\"'<>]+", RegexOption.IGNORE_CASE)

    fun parse(content: String, baseUrl: String): List<String> {
        val candidates = LinkedHashMap<String, Unit>()
        val document = Jsoup.parse(content, baseUrl)
        document.select("img, source")
            .forEach { element ->
                listOf("src", "data-src", "data-original")
                    .firstNotNullOfOrNull { attribute -> element.attr(attribute).takeIf(String::isNotBlank) }
                    ?.let { candidates[resolve(baseUrl, it)] = Unit }
            }

        runCatching { JsonParser.parseString(content) }
            .getOrNull()
            ?.let { collectJsonStrings(it, baseUrl, candidates) }
        content.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { line ->
                runCatching { JsonParser.parseString(line) }
                    .getOrNull()
                    ?.let { collectJsonStrings(it, baseUrl, candidates) }
            }

        urlPattern.findAll(content).forEach { match ->
            val value = match.value.trimEnd(',', '.', ';', ')', ']')
            if (looksLikeImage(value)) candidates[resolve(baseUrl, value)] = Unit
        }
        content.lineSequence().map(String::trim).filter(::looksLikeImage)
            .forEach { candidates[resolve(baseUrl, it)] = Unit }
        return candidates.keys.toList()
    }

    private fun collectJsonStrings(
        element: JsonElement,
        baseUrl: String,
        candidates: MutableMap<String, Unit>
    ) {
        when {
            element.isJsonArray -> element.asJsonArray.forEach { collectJsonStrings(it, baseUrl, candidates) }
            element.isJsonObject -> element.asJsonObject.entrySet()
                .forEach { collectJsonStrings(it.value, baseUrl, candidates) }
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                val value = element.asString
                if (looksLikeImage(value)) candidates[resolve(baseUrl, value)] = Unit
            }
        }
    }

    private fun looksLikeImage(value: String): Boolean =
        (value.startsWith("http://", true) || value.startsWith("https://", true)) && imageSuffix.containsMatchIn(value) ||
            (value.startsWith('/') || value.startsWith("./") || value.startsWith("../")) &&
                imageSuffix.containsMatchIn(value)

    private fun resolve(baseUrl: String, value: String): String = runCatching {
        URI(baseUrl).resolve(value).toString()
    }.getOrDefault(value)
}

class OnlineImageReaderModel(
    private val library: CoreLibrary,
    bookUrl: String,
    private val onlineService: OnlineBookService,
    private val httpClient: CoreHttpClient,
    private val cacheDirectory: Path = Path.of(System.getProperty("java.io.tmpdir"), "legado-image-cache")
) {
    private val book = requireNotNull(library.book(bookUrl)) { "Book does not exist: $bookUrl" }
    private val chapters = library.chapters(bookUrl)
    private var chapterIndex = book.durChapterIndex.coerceIn(chapters.indices)
    private var pageIndex = book.durChapterPos.coerceAtLeast(0)
    private var pages: List<OnlineImagePage> = emptyList()
    private val memoryCache = mutableMapOf<String, ByteArray>()

    val currentChapter: CoreChapter
        get() = chapters[chapterIndex]

    val currentChapterPages: List<OnlineImagePage>
        get() = pages

    val currentPageIndex: Int
        get() = pageIndex

    val hasPrevious: Boolean
        get() = pageIndex > 0

    val hasNext: Boolean
        get() = pageIndex < pages.lastIndex

    val hasPreviousChapter: Boolean
        get() = chapterIndex > 0

    val hasNextChapter: Boolean
        get() = chapterIndex < chapters.lastIndex

    fun loadCurrentChapter(): Boolean {
        val content = runCatching {
            onlineService.loadContent(book, currentChapter)
        }.getOrElse { return false }
        pages = OnlineImagePageParser.parse(content, currentChapter.url)
            .mapIndexed { index, url -> OnlineImagePage(url, chapterIndex, index) }
        if (pages.isEmpty()) return false
        pageIndex = pageIndex.coerceIn(pages.indices)
        return true
    }

    fun nextPage(): Boolean = if (hasNext) {
        pageIndex++
        true
    } else false

    fun previousPage(): Boolean = if (hasPrevious) {
        pageIndex--
        true
    } else false

    fun nextChapter(): Boolean = if (hasNextChapter) {
        chapterIndex++
        pageIndex = 0
        pages = emptyList()
        true
    } else false

    fun previousChapter(): Boolean = if (hasPreviousChapter) {
        chapterIndex--
        pageIndex = 0
        pages = emptyList()
        true
    } else false

    fun savePosition() {
        library.saveBook(
            (library.book(book.bookUrl) ?: book).copy(
                durChapterIndex = chapterIndex,
                durChapterPos = pageIndex,
                durChapterTitle = currentChapter.title,
                durChapterTime = System.currentTimeMillis()
            )
        )
    }

    fun currentPageBytes(): ByteArray {
        val page = pages.getOrNull(pageIndex) ?: error("当前章节尚未加载图片")
        memoryCache[page.url]?.let { return it }
        val key = sha256(page.url)
        val cacheFile = cacheDirectory.resolve(key)
        if (Files.isRegularFile(cacheFile)) {
            return Files.readAllBytes(cacheFile).also { memoryCache[page.url] = it }
        }
        val source = requireNotNull(library.source(book.origin)) { "未找到书源: ${book.origin}" }
        val resolved = CoreUrlRuleSupport.resolve(source, page.url, ruleField = "ruleContent.image")
        val request = CoreHttpRequest(
            url = resolved.requestUrl,
            method = resolved.method,
            headers = resolved.headers,
            body = resolved.body,
            charset = resolved.charset
        )
        val response = if (httpClient is CoreSourceAwareHttpClient) {
            httpClient.request(source, request)
        } else {
            httpClient.request(request)
        }
        require(response.statusCode in 200..299) { "图片请求失败：HTTP ${response.statusCode}" }
        val bytes = response.bodyBytes ?: response.body.toByteArray(Charsets.ISO_8859_1)
        Files.createDirectories(cacheDirectory)
        Files.write(cacheFile, bytes)
        memoryCache[page.url] = bytes
        return bytes
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
