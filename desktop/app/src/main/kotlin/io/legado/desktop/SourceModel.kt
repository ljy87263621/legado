package io.legado.desktop

import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreLibrary
import io.legado.core.source.BookSourceJsonCodec
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreSourceScriptService
import io.legado.core.source.JavaNetHttpClient
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

enum class SourceImportKind {
    BOOK_SOURCE,
    GENERIC
}

object SourceImportUrl {
    fun extractSource(input: String): String? {
        val value = input.trim()
        if (!value.startsWith("yuedu://", ignoreCase = true) &&
            !value.startsWith("legado://", ignoreCase = true)
        ) {
            return value.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        }
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        return sourceFrom(uri)
    }

    fun resolve(input: String): String {
        val value = input.trim()
        require(value.isNotBlank()) { "订阅源地址不能为空" }

        val candidate = if (value.startsWith("yuedu://", ignoreCase = true) ||
            value.startsWith("legado://", ignoreCase = true)
        ) {
            val uri = runCatching { URI(value) }
                .getOrElse { error -> throw IllegalArgumentException("订阅源链接无效", error) }
            require(classify(uri) == SourceImportKind.BOOK_SOURCE) {
                "该链接不是书源导入链接"
            }
            sourceFrom(uri)
        } else {
            value
        }

        val resolved = runCatching { URI(candidate) }
            .getOrElse { error -> throw IllegalArgumentException("订阅源地址无效", error) }
        require(resolved.scheme.equals("http", ignoreCase = true) ||
            resolved.scheme.equals("https", ignoreCase = true)) {
            "订阅源地址必须使用 HTTP 或 HTTPS"
        }
        require(!resolved.host.isNullOrBlank()) { "订阅源地址缺少主机名" }
        return candidate
    }

    fun classify(input: String): SourceImportKind {
        val value = input.trim()
        if (!value.startsWith("yuedu://", ignoreCase = true) &&
            !value.startsWith("legado://", ignoreCase = true)
        ) {
            return SourceImportKind.BOOK_SOURCE
        }
        val uri = runCatching { URI(value) }.getOrElse { return SourceImportKind.GENERIC }
        return classify(uri)
    }

    private fun classify(uri: URI): SourceImportKind = if (
        uri.path.equals("/importonline", ignoreCase = true) &&
        (uri.host.equals("rsssource", ignoreCase = true) ||
            uri.host.equals("booksource", ignoreCase = true))
    ) {
        SourceImportKind.BOOK_SOURCE
    } else {
        SourceImportKind.GENERIC
    }

    private fun sourceFrom(uri: URI): String {
        val rawQuery = uri.rawQuery.orEmpty()
        val rawSource = rawQuery.split('&')
            .firstOrNull { it.substringBefore('=').equals("src", ignoreCase = true) }
            ?.substringAfter('=', missingDelimiterValue = "")
        require(!rawSource.isNullOrBlank()) { "yuedu 链接缺少 src 参数" }
        return URLDecoder.decode(rawSource, StandardCharsets.UTF_8)
    }
}

class SourceModel(
    private val library: CoreLibrary,
    private val scriptService: CoreSourceScriptService = CoreSourceScriptService(),
    private val httpClient: CoreHttpClient = JavaNetHttpClient(),
    private val browserLauncher: DesktopBrowserLauncher = SystemDesktopBrowserLauncher,
    private val embeddedBrowserLauncher: DesktopEmbeddedBrowserLauncher =
        JavaFxEmbeddedBrowserLauncher(library, httpClient)
) {
    var sources: List<CoreBookSource> = library.sources()
        private set

    fun save(source: CoreBookSource) {
        library.saveSource(source)
        refresh()
    }

    fun setEnabled(bookSourceUrl: String, enabled: Boolean) {
        library.source(bookSourceUrl)?.let { library.saveSource(it.copy(enabled = enabled)) }
        refresh()
    }

    fun delete(bookSourceUrl: String) {
        library.deleteSource(bookSourceUrl)
        refresh()
    }

    fun importJson(json: String): Int {
        val imported = BookSourceJsonCodec.decode(json)
        imported.forEach(library::saveSource)
        refresh()
        return imported.size
    }

    fun importOnline(url: String): Int {
        require(SourceImportUrl.classify(url) == SourceImportKind.BOOK_SOURCE) {
            "仅支持导入书源链接"
        }
        val resolvedUrl = SourceImportUrl.resolve(url)
        val response = httpClient.get(resolvedUrl)
        require(response.statusCode in 200..299) {
            "在线订阅源请求失败: HTTP ${response.statusCode}"
        }
        return importJson(response.body)
    }

    fun saveJson(json: String, originalBookSourceUrl: String? = null): CoreBookSource {
        val imported = runCatching { BookSourceJsonCodec.decode(json) }
            .getOrElse { error ->
                throw IllegalArgumentException(error.message ?: "书源 JSON 无效", error)
            }
        require(imported.size == 1) { "编辑器只能保存一个书源" }
        val source = imported.single()
        require(source.bookSourceUrl.isNotBlank()) { "书源地址不能为空" }
        if (!originalBookSourceUrl.isNullOrBlank() && originalBookSourceUrl != source.bookSourceUrl) {
            library.deleteSource(originalBookSourceUrl)
        }
        save(source)
        return source
    }

    fun testScript(
        sourceJson: String,
        ruleField: String,
        script: String,
        input: String,
        baseUrl: String
    ): String {
        val source = BookSourceJsonCodec.decode(sourceJson).singleOrNull()
            ?: error("脚本测试需要一个完整书源 JSON")
        require(source.bookSourceUrl.isNotBlank()) { "书源地址不能为空" }
        return scriptService.test(
            source = source,
            ruleField = ruleField,
            script = script,
            input = input,
            baseUrl = baseUrl.ifBlank { source.bookSourceUrl }
        )?.toString().orEmpty()
    }

    fun openBrowserVerification(
        url: String,
        sourceUrl: String = "",
        title: String = ""
    ): BrowserVerificationRequest {
        val normalizedUrl = url.trim()
        require(normalizedUrl.isNotBlank()) { "浏览器验证 URL 不能为空" }
        require(normalizedUrl.length < 64 * 1024) { "浏览器验证 URL 过长" }
        val uri = runCatching { URI(normalizedUrl) }
            .getOrElse { error -> throw IllegalArgumentException("浏览器验证 URL 无效", error) }
        require(uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)) {
            "浏览器验证 URL 必须使用 HTTP 或 HTTPS"
        }
        require(!uri.host.isNullOrBlank()) { "浏览器验证 URL 缺少主机名" }

        return BrowserVerificationRequest(
            uri = uri,
            url = normalizedUrl,
            sourceUrl = sourceUrl.trim(),
            title = title.trim()
        ).also(browserLauncher::open)
    }

    fun openEmbeddedBrowserVerification(
        url: String,
        sourceUrl: String,
        title: String = "",
        refetchAfterSuccess: Boolean = true
    ): EmbeddedBrowserVerificationRequest {
        val normalizedUrl = url.trim()
        require(normalizedUrl.isNotBlank()) { "浏览器验证 URL 不能为空" }
        require(normalizedUrl.length < 64 * 1024) { "浏览器验证 URL 过长" }
        val uri = runCatching { URI(normalizedUrl) }
            .getOrElse { error -> throw IllegalArgumentException("浏览器验证 URL 无效", error) }
        require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            "浏览器验证 URL 必须使用 HTTP 或 HTTPS"
        }
        require(!uri.host.isNullOrBlank()) { "浏览器验证 URL 缺少主机名" }
        require(sourceUrl.isNotBlank()) { "内置浏览器验证需要书源地址" }
        return EmbeddedBrowserVerificationRequest(normalizedUrl, sourceUrl, title.trim(), refetchAfterSuccess)
            .also(embeddedBrowserLauncher::open)
    }

    fun exportJson(): String = BookSourceJsonCodec.encode(sources)

    fun refresh() {
        sources = library.sources()
    }
}
