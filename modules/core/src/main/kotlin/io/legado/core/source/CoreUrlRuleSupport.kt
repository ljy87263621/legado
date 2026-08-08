package io.legado.core.source

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreLibrary
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.Locale

data class CoreResolvedUrl(
    val url: String,
    val requestUrl: String,
    val urlAfterJs: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val encodedParams: String? = null,
    val charset: String? = null
)

class CoreUrlRuleException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Resolves the URL and request-option subset shared by desktop source rules. */
object CoreUrlRuleSupport {
    private val jsPattern = Regex("<js>([\\s\\S]*?)</js>|@js:([\\s\\S]*)$", RegexOption.IGNORE_CASE)
    private val embeddedPattern = Regex("\\{\\{([\\s\\S]*?)}}")
    private val dynamicOptionPattern = Regex("<([^()<> \\s]+)\\(([^()<>]*)\\)>")
    private val optionSuffixPattern = Regex(",\\s*(\\{[\\s\\S]*})\\s*$")

    fun resolve(
        source: CoreBookSource,
        rawUrl: String,
        keyword: String = "",
        page: Int = 1,
        selectedOptions: Map<String, String> = emptyMap(),
        bindings: Map<String, Any?> = emptyMap(),
        ruleField: String = "url",
        library: CoreLibrary? = null
    ): CoreResolvedUrl {
        val urlAfterJs = evaluateFragments(
            source = source,
            rawUrl = rawUrl,
            keyword = keyword,
            page = page,
            bindings = bindings,
            ruleField = ruleField,
            library = library
        )
        val withTemplates = replaceEmbeddedRules(
            source = source,
            value = urlAfterJs,
            keyword = keyword,
            page = page,
            bindings = bindings,
            ruleField = ruleField,
            library = library
        )
        val withDynamicOptions = replaceDynamicOptions(withTemplates, selectedOptions)
        val (urlText, urlOptions) = parseUrlOptions(withDynamicOptions)
        if (urlOptions.useWebView || !urlOptions.webJs.isNullOrBlank()) {
            throw CoreUrlRuleException(
                "Browser-dependent URL options require a desktop browser runtime"
            )
        }

        val absoluteUrl = if (isDataUrl(urlText)) urlText else resolveAbsolute(source.bookSourceUrl, urlText)
        val optionUrl = urlOptions.js?.let { script ->
            evaluate(
                source = source,
                script = script,
                result = absoluteUrl,
                keyword = keyword,
                page = page,
                bindings = bindings,
                ruleField = "$ruleField.options.js",
                baseUrl = absoluteUrl,
                library = library
            )?.toString()?.takeIf(String::isNotBlank) ?: absoluteUrl
        } ?: absoluteUrl
        val effectiveHeaders = LinkedHashMap<String, String>()
        effectiveHeaders.putAll(urlOptions.headers)
        val method = urlOptions.method?.uppercase(Locale.ROOT)?.ifBlank { "GET" } ?: "GET"
        val urlNoQuery = optionUrl.substringBefore('?')
        val rawQuery = optionUrl.substringAfter('?', "").takeIf { '?' in optionUrl }
        val body = urlOptions.body
        val encodedQuery = rawQuery?.let { encodeQuery(it, urlOptions.charset) }
        val encodedParams = when {
            method == "POST" && body != null && !isJsonOrXml(body) ->
                encodeForm(body, urlOptions.charset)
            method != "POST" -> encodedQuery
            else -> null
        }
        val effectiveBody = if (method == "POST") {
            when {
                encodedParams != null -> encodedParams
                body != null -> body
                else -> null
            }
        } else {
            null
        }
        val requestUrl = encodedQuery?.let { "$urlNoQuery?$it" } ?: urlNoQuery
        return CoreResolvedUrl(
            url = optionUrl,
            requestUrl = requestUrl,
            urlAfterJs = urlAfterJs,
            method = method,
            headers = effectiveHeaders,
            body = effectiveBody,
            encodedParams = encodedParams,
            charset = urlOptions.charset
        )
    }

    fun mergeHeaders(
        base: Map<String, String>,
        override: Map<String, String>
    ): Map<String, String> {
        val result = LinkedHashMap(base)
        override.forEach { (name, value) ->
            result.keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(result::remove)
            result[name] = value
        }
        return result
    }

    private fun evaluateFragments(
        source: CoreBookSource,
        rawUrl: String,
        keyword: String,
        page: Int,
        bindings: Map<String, Any?>,
        ruleField: String,
        library: CoreLibrary?
    ): String {
        if (!jsPattern.containsMatchIn(rawUrl)) return rawUrl
        var result = ""
        var start = 0
        jsPattern.findAll(rawUrl).forEach { match ->
            rawUrl.substring(start, match.range.first).takeIf(String::isNotEmpty)?.let { result += it }
            val script = match.groupValues[1].ifBlank { match.groupValues[2] }
            result = evaluate(
                source = source,
                script = script,
                result = result,
                keyword = keyword,
                page = page,
                bindings = bindings,
                ruleField = ruleField,
                baseUrl = source.bookSourceUrl,
                library = library
            )?.toString().orEmpty()
            start = match.range.last + 1
        }
        rawUrl.substring(start).takeIf(String::isNotEmpty)?.let { result += it }
        return result
    }

    private fun replaceEmbeddedRules(
        source: CoreBookSource,
        value: String,
        keyword: String,
        page: Int,
        bindings: Map<String, Any?>,
        ruleField: String,
        library: CoreLibrary?
    ): String = embeddedPattern.replace(value) { match ->
        evaluate(
            source = source,
            script = match.groupValues[1],
            result = null,
            keyword = keyword,
            page = page,
            bindings = bindings,
            ruleField = ruleField,
            baseUrl = source.bookSourceUrl,
            library = library
        )?.let(::scriptValueToString).orEmpty()
    }

    private fun evaluate(
        source: CoreBookSource,
        script: String,
        result: Any?,
        keyword: String,
        page: Int,
        bindings: Map<String, Any?>,
        ruleField: String,
        baseUrl: String,
        library: CoreLibrary?
    ): Any? = CoreSourceScriptSupport.evaluate(
        source = source,
        ruleField = ruleField,
        rawRule = script,
        bindings = mapOf(
            "baseUrl" to baseUrl,
            "key" to keyword,
            "keyword" to keyword,
            "page" to page,
            "result" to result,
            "url" to result
        ) + bindings,
        library = library
    )

    private fun replaceDynamicOptions(url: String, selectedOptions: Map<String, String>): String =
        dynamicOptionPattern.replace(url) { match ->
            val name = match.groupValues[1]
            selectedOptions[name] ?: firstDynamicOptionValue(match.groupValues[2]) ?: match.value
        }

    private fun firstDynamicOptionValue(raw: String): String? = raw.split(',').firstNotNullOfOrNull { item ->
        val parts = item.split(':', limit = 2)
        val label = parts.firstOrNull()?.trim().orEmpty().takeIf(String::isNotBlank) ?: return@firstNotNullOfOrNull null
        parts.getOrNull(1)?.trim().takeUnless { it.isNullOrBlank() } ?: label
    }

    private fun parseUrlOptions(value: String): Pair<String, ParsedUrlOptions> {
        val suffix = optionSuffixPattern.find(value) ?: return value to ParsedUrlOptions()
        val parsed = runCatching { JsonParser.parseString(suffix.groupValues[1]) }
            .getOrElse { return value to ParsedUrlOptions() }
            .takeIf { it.isJsonObject }
            ?: return value to ParsedUrlOptions()
        return value.substring(0, suffix.range.first) to ParsedUrlOptions.from(parsed.asJsonObject)
    }

    private fun resolveAbsolute(baseUrl: String, value: String): String = runCatching {
        URI(baseUrl).resolve(value).toString()
    }.getOrDefault(value)

    private fun isDataUrl(value: String): Boolean = value.startsWith("data:", ignoreCase = true)

    private fun isJsonOrXml(value: String): Boolean {
        val trimmed = value.trimStart()
        return trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("<")
    }

    private fun encodeForm(value: String, charsetName: String?): String =
        value.removeSuffix("&").split('&').dropWhile(String::isEmpty).joinToString("&") { pair ->
            val index = pair.indexOf('=')
            if (index < 0) encodeFormPart(pair, charsetName)
            else encodeFormPart(pair.substring(0, index), charsetName) + "=" +
                encodeFormPart(pair.substring(index + 1), charsetName)
        }

    private fun encodeFormPart(value: String, charsetName: String?): String {
        if (charsetName.equals("escape", true)) return percentEncode(value, StandardCharsets.UTF_8, form = false)
        val charset = charsetName.toCharsetOrUtf8()
        if (charsetName.isNullOrBlank() && isEncodedForm(value)) return value
        return percentEncode(value, charset, form = true)
    }

    private fun encodeQuery(value: String, charsetName: String?): String {
        if (charsetName.equals("escape", true)) return percentEncode(value, StandardCharsets.UTF_8, form = false)
        val charset = charsetName.toCharsetOrUtf8()
        if (charsetName.isNullOrBlank() && isEncodedQuery(value)) return value
        return percentEncode(value, charset, form = false)
    }

    private fun percentEncode(value: String, charset: Charset, form: Boolean): String {
        val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~!$%&()*+,/:;=?@[\\]^`{|}"
        val result = StringBuilder()
        value.forEach { char ->
            if (char.code < 128 && char in allowed && (!form || char != '+')) {
                result.append(char)
            } else if (form && char == ' ') {
                result.append('+')
            } else {
                char.toString().toByteArray(charset).forEach { byte ->
                    result.append('%').append(byte.toInt().and(0xff).toString(16).uppercase(Locale.ROOT).padStart(2, '0'))
                }
            }
        }
        return result.toString()
    }

    private fun isEncodedQuery(value: String): Boolean =
        value.contains(Regex("%(?:[0-9a-fA-F]{2})"))

    private fun isEncodedForm(value: String): Boolean =
        value.split('&').all { part ->
            val item = part.substringAfter('=', part)
            !item.contains(Regex("[^%]%(?![0-9a-fA-F]{2})"))
        } && value.contains('%')

    private fun String?.toCharsetOrUtf8(): Charset =
        this?.takeIf(String::isNotBlank)?.let { runCatching { Charset.forName(it) }.getOrNull() }
            ?: StandardCharsets.UTF_8

    private fun scriptValueToString(value: Any): String = when (value) {
        is Number -> value.toDouble().let { number ->
            if (number % 1.0 == 0.0) number.toLong().toString() else value.toString()
        }
        else -> value.toString()
    }

    private data class ParsedUrlOptions(
        val method: String? = null,
        val charset: String? = null,
        val body: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val js: String? = null,
        val webJs: String? = null,
        val useWebView: Boolean = false
    ) {
        companion object {
            fun from(objectValue: com.google.gson.JsonObject): ParsedUrlOptions {
                val headers = LinkedHashMap<String, String>()
                objectValue["headers"]?.let { element ->
                    when {
                        element.isJsonObject -> element.asJsonObject.entrySet().forEach { (key, value) ->
                            headers[key] = jsonValueToString(value)
                        }
                        element.isJsonPrimitive && element.asJsonPrimitive.isString -> runCatching {
                            JsonParser.parseString(element.asString).asJsonObject.entrySet().forEach { (key, value) ->
                                headers[key] = jsonValueToString(value)
                            }
                        }
                    }
                }
                return ParsedUrlOptions(
                    method = objectValue.string("method"),
                    charset = objectValue.string("charset"),
                    body = objectValue["body"]?.let { value ->
                        when {
                            value.isJsonNull -> null
                            value.isJsonPrimitive -> value.asString
                            else -> value.toString()
                        }
                    },
                    headers = headers,
                    js = objectValue.string("js"),
                    webJs = objectValue.string("webJs"),
                    useWebView = objectValue.boolean("webView")
                )
            }

            private fun com.google.gson.JsonObject.string(key: String): String? = get(key)
                ?.takeUnless(JsonElement::isJsonNull)
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
                ?.takeIf(String::isNotBlank)

            private fun com.google.gson.JsonObject.boolean(key: String): Boolean = get(key)?.let { value ->
                when {
                    value.isJsonNull -> false
                    value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> value.asBoolean
                    value.isJsonPrimitive -> value.asString.lowercase(Locale.ROOT) !in setOf("", "false")
                    else -> true
                }
            } ?: false

            private fun jsonValueToString(value: JsonElement): String = when {
                value.isJsonNull -> ""
                value.isJsonPrimitive -> value.asString
                else -> value.toString()
            }
        }
    }
}
