package io.legado.core.source

import com.jayway.jsonpath.JsonPath
import io.legado.core.library.CoreDictRule
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreLibrary
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

data class CoreDictRuleResult(
    val rule: CoreDictRule,
    val text: String = "",
    val error: String? = null
)

/** Executes Android-compatible dictionary rules on the desktop JVM. */
class CoreDictRuleService(
    private val library: CoreLibrary,
    private val httpClient: CoreHttpClient
) {

    fun search(rule: CoreDictRule, key: String): String {
        val source = syntheticSource(rule.urlRule)
        val resolved = CoreUrlRuleSupport.resolve(
            source = source,
            rawUrl = rule.urlRule,
            keyword = key,
            ruleField = "dictRule.urlRule",
            library = library
        )
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
            CoreSourceHttpClient(library, httpClient).request(source, request)
        }
        check(response.statusCode in 200..399) { "字典请求失败: HTTP ${response.statusCode}" }
        return if (rule.showRule.isBlank()) {
            response.body
        } else {
            extract(rule.showRule, response.body, response.url)
        }
    }

    fun searchEnabled(key: String): List<CoreDictRuleResult> = library.enabledDictRules().map { rule ->
        runCatching { CoreDictRuleResult(rule = rule, text = search(rule, key)) }
            .getOrElse { exception ->
                CoreDictRuleResult(rule = rule, error = exception.message ?: exception::class.simpleName)
            }
    }

    private fun syntheticSource(rawUrl: String): CoreBookSource {
        val urlWithoutOptions = Regex(",\\s*(\\{[\\s\\S]*})\\s*$").replace(rawUrl, "")
        val baseUrl = runCatching {
            val uri = URI(urlWithoutOptions)
            if (uri.scheme.isNullOrBlank() || uri.authority.isNullOrBlank()) {
                "https://dict.invalid/"
            } else {
                "${uri.scheme}://${uri.authority}/"
            }
        }.getOrDefault("https://dict.invalid/")
        return CoreBookSource(bookSourceUrl = baseUrl, bookSourceName = "Dictionary")
    }

    private fun extract(rawRule: String, body: String, baseUrl: String): String {
        val rule = rawRule.trim()
        return when {
            rule.startsWith("@CSS:", ignoreCase = true) ->
                extractCss(rule.substringAfter(':'), body, baseUrl)
            rule.startsWith("@Json:", ignoreCase = true) ->
                stringify(JsonPath.parse(body).read<Any>(rule.substringAfter(':').trim()))
            rule.startsWith("@regex:", ignoreCase = true) || rule.startsWith(":") ->
                Regex(if (rule.startsWith(":") ) rule.substring(1) else rule.substringAfter(':'))
                    .find(body)
                    ?.value
                    .orEmpty()
            else -> extractCss(rule, body, baseUrl)
        }
    }

    private fun extractCss(rawRule: String, body: String, baseUrl: String): String {
        val normalized = rawRule.trim()
        if (normalized.isBlank()) return Jsoup.parse(body, baseUrl).text()
        val separator = normalized.lastIndexOf('@')
        val selector = if (separator > 0) normalized.substring(0, separator) else normalized
        val attribute = if (separator > 0) normalized.substring(separator + 1) else "text"
        return Jsoup.parse(body, baseUrl).select(selector)
            .mapNotNull { element ->
                when {
                    attribute.equals("text", true) -> element.text()
                    attribute.equals("html", true) -> element.html()
                    else -> element.attr(attribute)
                }.takeIf(String::isNotBlank)
            }
            .joinToString("\n")
    }

    private fun stringify(value: Any?): String = when (value) {
        null -> ""
        is Collection<*> -> value.joinToString("\n") { stringify(it) }
        is Element -> value.text()
        is Map<*, *> -> value.toString()
        else -> value.toString()
    }
}
