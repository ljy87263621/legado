package io.legado.core.source

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreBook
import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreLibrary
import java.util.LinkedHashMap

/** JVM-safe support for the script fragments used by book-source rules. */
internal object CoreSourceScriptSupport {
    private val runtime: CoreScriptRuntime = RhinoCoreScriptRuntime()

    fun evaluate(
        source: CoreBookSource,
        ruleField: String,
        rawRule: String,
        bindings: Map<String, Any?> = emptyMap(),
        library: CoreLibrary? = null
    ): Any? {
        val script = extractScript(rawRule) ?: rawRule
        return runtime.evaluate(
            script = script,
            bindings = mapOf("source" to (library?.let { CoreScriptSourceBinding(source, it) } ?: source)) + bindings,
            sharedLibrary = source.jsLib,
            context = CoreScriptContext(
                sourceUrl = source.bookSourceUrl,
                ruleField = ruleField,
                baseUrl = bindings["baseUrl"]?.toString()
            )
        )
    }

    fun expandUrl(
        source: CoreBookSource,
        template: String,
        keyword: String,
        page: Int,
        bindings: Map<String, Any?> = emptyMap(),
        ruleField: String,
        library: CoreLibrary? = null
    ): String {
        val value = if (extractScript(template) != null) {
            evaluate(
                source = source,
                ruleField = ruleField,
                rawRule = template,
                bindings = mapOf(
                    "source" to (library?.let { CoreScriptSourceBinding(source, it) } ?: source),
                    "baseUrl" to source.bookSourceUrl,
                    "key" to keyword,
                    "keyword" to keyword,
                    "page" to page
                ) + bindings,
                library = library
            )?.toString().orEmpty()
        } else {
            template
        }
        return SourceUrlTemplate.expand(value, keyword, page)
    }

    fun applyBookScript(
        source: CoreBookSource,
        ruleField: String,
        script: String,
        book: CoreBook,
        bindings: Map<String, Any?> = emptyMap(),
        library: CoreLibrary? = null
    ): CoreBook {
        val scriptBody = extractScript(script) ?: script
        val envelope = """
            (function() {
                var __result = (function() {
                    $scriptBody
                }).call(this);
                return { book: book, result: __result };
            }).call(this)
        """.trimIndent()
        val evaluated = evaluate(
            source = source,
            ruleField = ruleField,
            rawRule = envelope,
            bindings = mapOf(
                "book" to book,
                "source" to (library?.let { CoreScriptSourceBinding(source, it) } ?: source)
            ) + bindings,
            library = library
        )
        val result = evaluated as? Map<*, *> ?: return book
        val updated = (result["result"] as? Map<*, *>)
            ?: (result["book"] as? Map<*, *>)
            ?: return book
        return book.copyFromScriptMap(updated)
    }

    fun applyChapterScript(
        source: CoreBookSource,
        ruleField: String,
        script: String,
        chapter: CoreChapter,
        bindings: Map<String, Any?> = emptyMap(),
        library: CoreLibrary? = null
    ): Pair<CoreChapter, Any?> {
        val scriptBody = extractScript(script) ?: script
        val envelope = """
            (function() {
                var __result = (function() {
                    $scriptBody
                }).call(this);
                return { chapter: chapter, result: __result };
            }).call(this)
        """.trimIndent()
        val evaluated = evaluate(
            source = source,
            ruleField = ruleField,
            rawRule = envelope,
            bindings = mapOf(
                "chapter" to chapter,
                "source" to (library?.let { CoreScriptSourceBinding(source, it) } ?: source)
            ) + bindings,
            library = library
        )
        val result = evaluated as? Map<*, *> ?: return chapter to evaluated
        val updated = (result["result"] as? Map<*, *>)
            ?: (result["chapter"] as? Map<*, *>)
        return (updated?.let(chapter::copyFromScriptMap) ?: chapter) to result["result"]
    }

    fun headers(
        source: CoreBookSource,
        baseUrl: String,
        bindings: Map<String, Any?> = emptyMap(),
        library: CoreLibrary? = null
    ): Map<String, String> {
        val raw = source.header?.takeIf(String::isNotBlank) ?: ""
        val value = if (extractScript(raw) != null) {
            evaluate(
                source = source,
                ruleField = "header",
                rawRule = raw,
                bindings = mapOf(
                    "source" to (library?.let { CoreScriptSourceBinding(source, it) } ?: source),
                    "baseUrl" to baseUrl
                ) + bindings,
                library = library
            )
        } else {
            raw
        }
        val parsed = parseHeaderValue(value)
        if (parsed.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            parsed["User-Agent"] = "Legado/Windows"
        }
        return parsed
    }

    fun extractScript(rawRule: String): String? {
        val trimmed = rawRule.trim()
        return when {
            trimmed.startsWith("@js:", ignoreCase = true) -> trimmed.substring(4)
            trimmed.startsWith("<js>", ignoreCase = true) -> {
                val end = trimmed.lastIndexOf("</js>", ignoreCase = true)
                if (end < 4) trimmed.substring(4) else trimmed.substring(4, end)
            }
            else -> null
        }
    }

    private fun parseHeaderValue(value: Any?): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        when (value) {
            is Map<*, *> -> value.forEach { (key, item) ->
                if (key != null && item != null) result[key.toString()] = item.toString()
            }
            null -> Unit
            else -> parseHeaderText(value.toString(), result)
        }
        return result
    }

    private fun parseHeaderText(raw: String, result: LinkedHashMap<String, String>) {
        runCatching {
            val json = JsonParser.parseString(raw)
            if (json.isJsonObject) {
                json.asJsonObject.entrySet().forEach { (key, value) ->
                    result[key] = jsonValueToString(value)
                }
                return
            }
        }
        raw.lineSequence()
            .mapNotNull { line -> line.split(':', limit = 2).takeIf { it.size == 2 } }
            .forEach { parts -> result[parts[0].trim()] = parts[1].trim() }
    }

    private fun jsonValueToString(value: JsonElement): String = when {
        value.isJsonNull -> ""
        value.isJsonPrimitive -> value.asString
        else -> value.toString()
    }

    private fun Map<*, *>.string(key: String): String? =
        this[key]?.toString()?.takeIf { it != "null" }
}

private fun Map<*, *>.hasScriptKey(key: String): Boolean = keys.any { it?.toString() == key }

private fun Map<*, *>.scriptValue(key: String): Any? = entries
    .firstOrNull { it.key?.toString() == key }
    ?.value

private fun Map<*, *>.scriptString(key: String, fallback: String): String =
    if (!hasScriptKey(key)) fallback else scriptValue(key)?.toString()?.takeUnless { it == "null" } ?: fallback

private fun Map<*, *>.scriptNullableString(key: String, fallback: String?): String? =
    if (!hasScriptKey(key)) fallback else scriptValue(key)?.toString()?.takeUnless { it == "null" }

private fun Map<*, *>.scriptInt(key: String, fallback: Int): Int = when (val value = scriptValue(key)) {
    null -> fallback
    is Number -> value.toInt()
    else -> value.toString().toIntOrNull() ?: fallback
}

private fun Map<*, *>.scriptLong(key: String, fallback: Long): Long = when (val value = scriptValue(key)) {
    null -> fallback
    is Number -> value.toLong()
    else -> value.toString().toLongOrNull() ?: fallback
}

private fun Map<*, *>.scriptBoolean(key: String, fallback: Boolean): Boolean = when (val value = scriptValue(key)) {
    null -> fallback
    is Boolean -> value
    else -> value.toString().toBooleanStrictOrNull() ?: fallback
}

private fun CoreBook.copyFromScriptMap(values: Map<*, *>): CoreBook = copy(
    bookUrl = values.scriptString("bookUrl", bookUrl),
    name = values.scriptString("name", name),
    author = values.scriptString("author", author),
    origin = values.scriptString("origin", origin),
    originName = values.scriptString("originName", originName),
    tocUrl = values.scriptString("tocUrl", tocUrl),
    coverUrl = values.scriptNullableString("coverUrl", coverUrl),
    intro = values.scriptNullableString("intro", intro),
    kind = values.scriptNullableString("kind", kind),
    customTag = values.scriptNullableString("customTag", customTag),
    customCoverUrl = values.scriptNullableString("customCoverUrl", customCoverUrl),
    customIntro = values.scriptNullableString("customIntro", customIntro),
    charset = values.scriptNullableString("charset", charset),
    type = values.scriptInt("type", type),
    group = values.scriptLong("group", group),
    latestChapterTitle = values.scriptNullableString("latestChapterTitle", latestChapterTitle),
    latestChapterTime = values.scriptLong("latestChapterTime", latestChapterTime),
    lastCheckTime = values.scriptLong("lastCheckTime", lastCheckTime),
    lastCheckCount = values.scriptInt("lastCheckCount", lastCheckCount),
    totalChapterNum = values.scriptInt("totalChapterNum", totalChapterNum),
    durChapterIndex = values.scriptInt("durChapterIndex", durChapterIndex),
    durChapterPos = values.scriptInt("durChapterPos", durChapterPos),
    durChapterTitle = values.scriptNullableString("durChapterTitle", durChapterTitle),
    durChapterTime = values.scriptLong("durChapterTime", durChapterTime),
    wordCount = values.scriptNullableString("wordCount", wordCount),
    canUpdate = values.scriptBoolean("canUpdate", canUpdate),
    order = values.scriptInt("order", order),
    originOrder = values.scriptInt("originOrder", originOrder),
    variable = values.scriptNullableString("variable", variable),
    readConfigJson = values.scriptNullableString("readConfigJson", readConfigJson),
    syncTime = values.scriptLong("syncTime", syncTime)
)

private fun CoreChapter.copyFromScriptMap(values: Map<*, *>): CoreChapter = copy(
    bookUrl = values.scriptString("bookUrl", bookUrl),
    url = values.scriptString("url", url),
    title = values.scriptString("title", title),
    index = values.scriptInt("index", index),
    isVolume = values.scriptBoolean("isVolume", isVolume),
    isVip = values.scriptBoolean("isVip", isVip),
    isPay = values.scriptBoolean("isPay", isPay),
    resourceUrl = values.scriptNullableString("resourceUrl", resourceUrl),
    tag = values.scriptNullableString("tag", tag),
    wordCount = values.scriptNullableString("wordCount", wordCount),
    variable = values.scriptNullableString("variable", variable),
    start = values.scriptValue("start")?.let { value ->
        when (value) {
            is Number -> value.toLong()
            else -> value.toString().toLongOrNull()
        }
    } ?: start,
    end = values.scriptValue("end")?.let { value ->
        when (value) {
            is Number -> value.toLong()
            else -> value.toString().toLongOrNull()
        }
    } ?: end,
    startFragmentId = values.scriptNullableString("startFragmentId", startFragmentId),
    endFragmentId = values.scriptNullableString("endFragmentId", endFragmentId)
)
