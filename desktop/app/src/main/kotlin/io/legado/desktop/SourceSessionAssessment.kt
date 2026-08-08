package io.legado.desktop

import java.net.URI
import java.util.Locale

enum class SourceSessionStatus {
    AUTHENTICATED,
    LOGIN_REQUIRED,
    HTTP_ERROR,
    TRANSPORT_ERROR
}

object SourceSessionAssessment {
    private val loginPathMarkers = setOf("login", "signin", "sign-in", "auth")

    fun classify(statusCode: Int?, finalUrl: String?, body: String): SourceSessionStatus {
        statusCode ?: return SourceSessionStatus.TRANSPORT_ERROR
        if (statusCode == 401 || statusCode == 403) return SourceSessionStatus.LOGIN_REQUIRED
        if (statusCode !in 200..399) return SourceSessionStatus.HTTP_ERROR
        if (finalUrl.isLoginPath()) return SourceSessionStatus.LOGIN_REQUIRED
        if (body.contains(Regex("<input\\b[^>]*\\btype\\s*=\\s*[\"']password[\"']", RegexOption.IGNORE_CASE))) {
            return SourceSessionStatus.LOGIN_REQUIRED
        }
        return SourceSessionStatus.AUTHENTICATED
    }

    private fun String?.isLoginPath(): Boolean {
        val path = runCatching { URI(this.orEmpty()).path.orEmpty() }.getOrDefault("")
        return path.split('/').any { segment -> segment.lowercase(Locale.ROOT) in loginPathMarkers }
    }
}
