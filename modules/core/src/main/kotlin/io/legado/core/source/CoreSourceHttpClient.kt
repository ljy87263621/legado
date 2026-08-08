package io.legado.core.source

import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreCookie
import io.legado.core.library.CoreLibrary
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.LinkedHashMap
import java.util.Locale

interface CoreSourceAwareHttpClient {
    fun get(source: CoreBookSource, url: String, headers: Map<String, String> = emptyMap()): CoreHttpResponse

    fun request(source: CoreBookSource, request: CoreHttpRequest): CoreHttpResponse {
        require(request.method.equals("GET", ignoreCase = true) && request.body == null) {
            "Source-aware HTTP client does not support ${request.method} requests"
        }
        return get(source, request.url, request.headers)
    }
}

class CoreSourceHttpClient(
    private val library: CoreLibrary,
    private val delegate: CoreHttpClient
) : CoreHttpClient, CoreSourceAwareHttpClient {
    private val sessionCookies = LinkedHashMap<CookieKey, CoreCookie>()

    override fun get(url: String, headers: Map<String, String>): CoreHttpResponse = delegate.get(url, headers)

    override fun get(source: CoreBookSource, url: String, headers: Map<String, String>): CoreHttpResponse {
        return request(source, CoreHttpRequest(url = url, headers = headers))
    }

    override fun request(request: CoreHttpRequest): CoreHttpResponse = delegate.request(request)

    override fun request(source: CoreBookSource, request: CoreHttpRequest): CoreHttpResponse {
        if (source.enabledCookieJar == false) {
            return delegateRequestWithoutCookies(request)
        }

        val requestUri = URI(request.url)
        val sourceHost = hostOf(source.bookSourceUrl)
        val requestHost = requestUri.host.orEmpty().lowercase(Locale.ROOT)
        val requestPath = requestUri.rawPath?.takeIf(String::isNotBlank) ?: "/"
        val effectiveHeaders = LinkedHashMap(request.headers)
        val cookieHeaderName = effectiveHeaders.keys.firstOrNull { it.equals("Cookie", ignoreCase = true) }
        val storedCookies = matchingCookies(sourceHost, requestHost, requestPath)
        val mergedCookie = mergeCookieHeader(effectiveHeaders[cookieHeaderName], storedCookies)
        if (!mergedCookie.isNullOrBlank()) {
            if (cookieHeaderName != null) effectiveHeaders[cookieHeaderName] = mergedCookie
            else effectiveHeaders["Cookie"] = mergedCookie
        }

        val response = delegateRequestWithoutCookies(request.copy(headers = effectiveHeaders))
        saveResponseCookies(source, requestUri, response)
        return response
    }

    private fun matchingCookies(sourceHost: String, requestHost: String, requestPath: String): List<CoreCookie> {
        if (sourceHost.isBlank() || !sourceHost.matchesHost(requestHost)) return emptyList()
        val now = System.currentTimeMillis()
        val persistent = library.cookies().filter { cookie ->
            if (cookie.expiresAt != null && cookie.expiresAt <= now) {
                library.deleteCookie(cookie.domain, cookie.path, cookie.name)
                false
            } else {
                cookie.domain.matchesHost(requestHost) &&
                    cookie.domain.matchesHost(sourceHost) &&
                    cookie.path.matchesPath(requestPath)
            }
        }
        return (persistent + sessionCookies.values).filter { cookie ->
            cookie.domain.matchesHost(requestHost) &&
                cookie.domain.matchesHost(sourceHost) &&
                cookie.path.matchesPath(requestPath)
        }
    }

    private fun mergeCookieHeader(manual: String?, stored: List<CoreCookie>): String? {
        val values = LinkedHashMap<String, String>()
        parseCookieHeader(manual).forEach { (name, value) -> values[name] = value }
        stored.forEach { cookie -> values.putIfAbsent(cookie.name, cookie.value) }
        return values.entries.joinToString("; ") { (name, value) -> "$name=$value" }.ifBlank { null }
    }

    private fun saveResponseCookies(source: CoreBookSource, requestUri: URI, response: CoreHttpResponse) {
        val sourceHost = hostOf(source.bookSourceUrl)
        val responseUri = runCatching { URI(response.url) }.getOrElse { requestUri }
        val responseHost = responseUri.host.orEmpty().lowercase(Locale.ROOT).ifBlank { requestUri.host.orEmpty() }
        response.headers.entries
            .filter { (name, _) -> name.equals("Set-Cookie", ignoreCase = true) }
            .flatMap { it.value }
            .mapNotNull { parseSetCookie(it, responseHost, responseUri.rawPath ?: "/") }
            .filter { cookie ->
                sourceHost.isNotBlank() &&
                    cookie.domain.matchesHost(sourceHost) &&
                    cookie.domain.matchesHost(responseHost)
            }
            .forEach { cookie ->
                val key = cookie.key()
                if (cookie.expiresAt != null && cookie.expiresAt <= System.currentTimeMillis()) {
                    sessionCookies.remove(key)
                    library.deleteCookie(cookie.domain, cookie.path, cookie.name)
                } else if (cookie.persistent) {
                    sessionCookies.remove(key)
                    library.saveCookie(cookie)
                } else {
                    library.deleteCookie(cookie.domain, cookie.path, cookie.name)
                    sessionCookies[key] = cookie
                }
            }
    }

    private fun parseSetCookie(raw: String, responseHost: String, responsePath: String): CoreCookie? {
        val parts = raw.split(';')
        val nameValue = parts.firstOrNull()?.trim() ?: return null
        val separator = nameValue.indexOf('=')
        if (separator <= 0) return null
        val name = nameValue.substring(0, separator).trim()
        val value = nameValue.substring(separator + 1).trim()
        if (name.isBlank()) return null
        var domain = responseHost.lowercase(Locale.ROOT).removePrefix(".")
        var path = defaultCookiePath(responsePath)
        var persistent = false
        var expiresAt: Long? = null
        var maxAge: Long? = null
        parts.drop(1).forEach { rawAttribute ->
            val attribute = rawAttribute.trim()
            val key = attribute.substringBefore('=', attribute).trim().lowercase(Locale.ROOT)
            val attributeValue = attribute.substringAfter('=', "").trim()
            when (key) {
                "domain" -> attributeValue.takeIf(String::isNotBlank)?.let {
                    domain = it.lowercase(Locale.ROOT).removePrefix(".")
                }
                "path" -> attributeValue.takeIf(String::isNotBlank)?.let { path = normalizePath(it) }
                "max-age" -> maxAge = attributeValue.toLongOrNull()
                "expires" -> expiresAt = parseExpires(attributeValue)
            }
        }
        if (maxAge != null) {
            persistent = true
            expiresAt = if (maxAge!! <= 0) 0L else System.currentTimeMillis() + maxAge!! * 1_000
        } else if (expiresAt != null) {
            persistent = true
        }
        return CoreCookie(domain, normalizePath(path), name, value, persistent, expiresAt)
    }

    private fun delegateRequestWithoutCookies(request: CoreHttpRequest): CoreHttpResponse =
        (delegate as? JavaNetHttpClient)?.requestWithoutCookies(request) ?: delegate.request(request)

    private fun parseCookieHeader(raw: String?): List<Pair<String, String>> = raw.orEmpty()
        .split(';')
        .mapNotNull { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) null else pair.substring(0, separator).trim() to pair.substring(separator + 1).trim()
        }

    private fun hostOf(url: String): String = runCatching { URI(url).host.orEmpty() }
        .getOrDefault("")
        .lowercase(Locale.ROOT)

    private fun parseExpires(raw: String): Long? = runCatching {
        ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrNull()

    private fun defaultCookiePath(raw: String): String {
        val path = normalizePath(raw)
        if (path == "/") return "/"
        return path.substringBeforeLast('/', "/").ifBlank { "/" }
    }

    private fun normalizePath(raw: String): String = if (raw.startsWith('/')) raw else "/$raw"

    private data class CookieKey(val domain: String, val path: String, val name: String)

    private fun CoreCookie.key(): CookieKey = CookieKey(domain, path, name)
}

private fun String.matchesHost(host: String): Boolean {
    val domain = trim().lowercase(Locale.ROOT).removePrefix(".")
    val normalizedHost = host.trim().lowercase(Locale.ROOT).removeSuffix(".")
    return domain.isNotBlank() && (normalizedHost == domain || normalizedHost.endsWith(".$domain"))
}

private fun String.matchesPath(requestPath: String): Boolean {
    val cookiePath = if (isBlank()) "/" else this
    val path = if (requestPath.startsWith('/')) requestPath else "/$requestPath"
    return path == cookiePath || path.startsWith("$cookiePath/") ||
        cookiePath.endsWith('/') && path.startsWith(cookiePath)
}
