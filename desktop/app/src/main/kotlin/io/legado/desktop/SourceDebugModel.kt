package io.legado.desktop

import com.google.gson.JsonParser
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreLibrary
import io.legado.core.source.BookSourceJsonCodec
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpRequest
import io.legado.core.source.CoreSourceDebugService
import io.legado.core.source.CoreSourceAwareHttpClient
import io.legado.core.source.CoreSourceHttpClient
import io.legado.core.source.CoreUrlRuleSupport
import io.legado.core.source.JavaNetHttpClient
import java.util.LinkedHashMap

data class SourceDebugRequest(
    val url: String,
    val headersText: String = "",
    val sourceJson: String = ""
)

data class SourceDebugResult(
    val finalUrl: String? = null,
    val requestUrl: String? = null,
    val requestMethod: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val statusCode: Int? = null,
    val sessionStatus: SourceSessionStatus = SourceSessionStatus.TRANSPORT_ERROR,
    val loginUrl: String? = null,
    val responseHeaders: Map<String, List<String>> = emptyMap(),
    val body: String = "",
    val error: String? = null
)

class SourceDebugModel(
    private val httpClient: CoreHttpClient = JavaNetHttpClient(),
    private val sourceDebugService: CoreSourceDebugService = CoreSourceDebugService(),
    private val library: CoreLibrary? = null
) {
    fun execute(request: SourceDebugRequest): SourceDebugResult {
        return try {
            require(request.url.isNotBlank()) { "请求 URL 不能为空" }
            val source = decodeSource(request.sourceJson)
            val resolved = source?.let {
                CoreUrlRuleSupport.resolve(
                    source = it,
                    rawUrl = request.url.trim(),
                    ruleField = "searchUrl",
                    library = library
                )
            }
            val sourceHeaders = if (source != null && resolved != null) {
                sourceDebugService.sourceHeaders(
                    source = source,
                    baseUrl = resolved.url,
                    bindings = mapOf("key" to "", "keyword" to "", "page" to 1),
                    library = library
                )
            } else {
                emptyMap()
            }
            val headers = CoreUrlRuleSupport.mergeHeaders(
                CoreUrlRuleSupport.mergeHeaders(sourceHeaders, resolved?.headers.orEmpty()),
                parseHeaders(request.headersText)
            )
            val httpRequest = resolved?.let {
                CoreHttpRequest(
                    url = it.requestUrl,
                    method = it.method,
                    headers = headers,
                    body = it.body,
                    charset = it.charset
                )
            } ?: CoreHttpRequest(
                url = request.url.trim(),
                headers = headers
            )
            val response = when {
                source != null && library != null && httpClient is CoreSourceAwareHttpClient ->
                    httpClient.request(source, httpRequest)
                source != null && library != null ->
                    CoreSourceHttpClient(library, httpClient).request(source, httpRequest)
                else -> httpClient.request(httpRequest)
            }
            val sessionStatus = SourceSessionAssessment.classify(response.statusCode, response.url, response.body)
            SourceDebugResult(
                finalUrl = response.url,
                requestUrl = httpRequest.url,
                requestMethod = httpRequest.method,
                requestHeaders = httpRequest.headers,
                requestBody = httpRequest.body,
                statusCode = response.statusCode,
                sessionStatus = sessionStatus,
                loginUrl = SourceSessionAssessment.loginUrl(sessionStatus, response.url),
                responseHeaders = response.headers,
                body = response.body,
                error = response.statusCode.takeUnless { it in 200..299 }?.let { "HTTP $it" }
            )
        } catch (error: Throwable) {
            SourceDebugResult(
                sessionStatus = SourceSessionStatus.TRANSPORT_ERROR,
                error = error.message ?: error::class.java.simpleName
            )
        }
    }

    private fun decodeSource(sourceJson: String): CoreBookSource? {
        if (sourceJson.isBlank()) return null
        return BookSourceJsonCodec.decode(sourceJson).singleOrNull()
            ?: error("请求检查需要一个完整书源 JSON")
    }

    private fun parseHeaders(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        val json = runCatching { JsonParser.parseString(raw) }.getOrNull()
        if (json?.isJsonObject == true) {
            json.asJsonObject.entrySet().forEach { (name, value) ->
                result[name] = if (value.isJsonNull) "" else value.asString
            }
            return result
        }
        raw.lineSequence().forEach { line ->
            val parts = line.split(':', limit = 2)
            if (parts.size == 2 && parts[0].trim().isNotBlank()) {
                result[parts[0].trim()] = parts[1].trim()
            }
        }
        return result
    }
}
