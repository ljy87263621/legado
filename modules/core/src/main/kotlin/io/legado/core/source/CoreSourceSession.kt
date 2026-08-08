package io.legado.core.source

import java.net.URI
import java.util.Locale

enum class CoreSourceSessionStatus {
    AUTHENTICATED,
    LOGIN_REQUIRED,
    HTTP_ERROR,
    TRANSPORT_ERROR
}

class CoreSourceSessionException(
    val status: CoreSourceSessionStatus,
    val sourceUrl: String,
    val loginUrl: String?,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

object CoreSourceSessionAssessment {
    private val loginPathMarkers = setOf("login", "signin", "sign-in", "auth")

    fun classify(statusCode: Int?, finalUrl: String?, body: String): CoreSourceSessionStatus {
        statusCode ?: return CoreSourceSessionStatus.TRANSPORT_ERROR
        if (statusCode == 401 || statusCode == 403) return CoreSourceSessionStatus.LOGIN_REQUIRED
        if (statusCode !in 200..399) return CoreSourceSessionStatus.HTTP_ERROR
        if (finalUrl.isLoginPath()) return CoreSourceSessionStatus.LOGIN_REQUIRED
        if (body.contains(Regex("<input\\b[^>]*\\btype\\s*=\\s*[\"']password[\"']", RegexOption.IGNORE_CASE))) {
            return CoreSourceSessionStatus.LOGIN_REQUIRED
        }
        return CoreSourceSessionStatus.AUTHENTICATED
    }

    fun loginUrl(status: CoreSourceSessionStatus, finalUrl: String?): String? = finalUrl
        ?.takeIf { status == CoreSourceSessionStatus.LOGIN_REQUIRED }
        ?.takeIf { url ->
            runCatching {
                val uri = URI(url)
                (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank()
            }.getOrDefault(false)
        }

    fun requireSuccess(response: CoreHttpResponse, sourceUrl: String, context: String): CoreHttpResponse {
        val status = classify(response.statusCode, response.url, response.body)
        if (status == CoreSourceSessionStatus.AUTHENTICATED) return response
        val loginUrl = loginUrl(status, response.url)
        val detail = when (status) {
            CoreSourceSessionStatus.LOGIN_REQUIRED -> "需要重新登录"
            CoreSourceSessionStatus.HTTP_ERROR -> "HTTP ${response.statusCode}"
            CoreSourceSessionStatus.TRANSPORT_ERROR -> "请求未返回 HTTP 响应"
            CoreSourceSessionStatus.AUTHENTICATED -> return response
        }
        throw CoreSourceSessionException(status, sourceUrl, loginUrl, "$context：$detail")
    }

    private fun String?.isLoginPath(): Boolean {
        val path = runCatching { URI(this.orEmpty()).path.orEmpty() }.getOrDefault("")
        return path.split('/').any { segment -> segment.lowercase(Locale.ROOT) in loginPathMarkers }
    }
}
