package io.legado.core.source

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreChapter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ContextAction
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper

interface CoreScriptRuntime {
    fun evaluate(
        script: String,
        bindings: Map<String, Any?> = emptyMap(),
        sharedLibrary: String? = null,
        context: CoreScriptContext = CoreScriptContext()
    ): Any?
}

data class CoreScriptContext(
    val sourceUrl: String? = null,
    val ruleField: String? = null,
    val baseUrl: String? = null,
    val timeoutMillis: Long = 2_000
)

class CoreScriptException(
    message: String,
    val sourceUrl: String? = null,
    val ruleField: String? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class RhinoCoreScriptRuntime : CoreScriptRuntime {
    private val factory = object : ContextFactory() {
        override fun makeContext(): Context {
            return CoreRhinoContext(this).apply {
                languageVersion = Context.VERSION_ES6
                optimizationLevel = -1
                instructionObserverThreshold = 10_000
                setClassShutter { false }
            }
        }

        override fun observeInstructionCount(cx: Context, instructionCount: Int) {
            if (cx is CoreRhinoContext && System.nanoTime() > cx.deadlineNanos) {
                throw EvaluatorException("JavaScript execution timed out")
            }
        }
    }

    override fun evaluate(
        script: String,
        bindings: Map<String, Any?>,
        sharedLibrary: String?,
        context: CoreScriptContext
    ): Any? {
        if (script.isBlank()) return null
        val raw = script.trim()
        return try {
            evaluateInternal(raw, bindings, sharedLibrary, context)
        } catch (error: Throwable) {
            if (isTopLevelReturn(error)) {
                evaluateInternal("(function(){\n$raw\n}).call(this);", bindings, sharedLibrary, context)
            } else {
                throw wrap(error, context)
            }
        }
    }

    private fun evaluateInternal(
        script: String,
        bindings: Map<String, Any?>,
        sharedLibrary: String?,
        context: CoreScriptContext
    ): Any? = factory.call(ContextAction { cx ->
        cx as CoreRhinoContext
        cx.deadlineNanos = System.nanoTime() + context.timeoutMillis.coerceAtLeast(1) * 1_000_000
        val scope = cx.initSafeStandardObjects(null, false)
        if (!sharedLibrary.isNullOrBlank()) {
            cx.evaluateString(scope, sharedLibrary, sourceName(context, "jsLib"), 1, null)
        }
        bindings.forEach { (key, value) ->
            ScriptableObject.putProperty(scope, key, toJsValue(cx, scope, value))
        }
        fromJsValue(cx.evaluateString(scope, script, sourceName(context, context.ruleField), 1, null))
    })

    private fun toJsValue(cx: Context, scope: Scriptable, value: Any?): Any? = when (value) {
        null -> null
        is String, is Number, is Boolean -> value
        is CoreBook -> toJsObject(cx, scope, value.toScriptMap())
        is CoreBookSource -> toJsObject(cx, scope, value.toScriptMap())
        is CoreChapter -> toJsObject(cx, scope, value.toScriptMap())
        is Map<*, *> -> toJsObject(cx, scope, value)
        is Iterable<*> -> cx.newArray(scope, value.map { toJsValue(cx, scope, it) }.toTypedArray())
        is Array<*> -> cx.newArray(scope, value.map { toJsValue(cx, scope, it) }.toTypedArray())
        else -> Context.javaToJS(value, scope)
    }

    private fun toJsObject(cx: Context, scope: Scriptable, value: Map<*, *>): NativeObject {
        val obj = NativeObject()
        obj.parentScope = scope
        obj.prototype = ScriptableObject.getObjectPrototype(scope)
        value.forEach { (key, item) ->
            if (key != null) ScriptableObject.putProperty(obj, key.toString(), toJsValue(cx, scope, item))
        }
        return obj
    }

    private fun fromJsValue(value: Any?): Any? = when (value) {
        null, Undefined.instance -> null
        is Wrapper -> fromJsValue(value.unwrap())
        is CharSequence -> value.toString()
        is Number, is Boolean -> value
        is NativeArray -> (0 until value.length.toInt()).map { index ->
            fromJsValue(value.get(index, value))
        }
        is NativeObject -> value.ids.associate { key ->
            key.toString() to fromJsValue(value.get(key.toString(), value))
        }
        else -> value.toString()
    }

    private fun wrap(error: Throwable, context: CoreScriptContext): CoreScriptException {
        val detail = when (error) {
            is RhinoException -> error.details()
            else -> error.message
        }.orEmpty().ifBlank { error::class.java.simpleName }
        val source = context.sourceUrl?.let { " source=$it" }.orEmpty()
        val field = context.ruleField?.let { " field=$it" }.orEmpty()
        return CoreScriptException("JavaScript execution failed:$source$field: $detail", context.sourceUrl, context.ruleField, error)
    }

    private fun isTopLevelReturn(error: Throwable): Boolean {
        if (error !is EvaluatorException) return false
        val message = error.message.orEmpty()
        return message.contains("invalid return", ignoreCase = true) ||
            message.contains("return", ignoreCase = true) &&
            (message.contains("invalid", ignoreCase = true) || message.contains("无效")) ||
            message.contains("返回") && message.contains("无效")
    }

    private fun sourceName(context: CoreScriptContext, suffix: String?): String =
        listOfNotNull(context.sourceUrl, suffix).joinToString("#").ifBlank { "<core-script>" }

    private class CoreRhinoContext(factory: ContextFactory) : Context(factory) {
        var deadlineNanos: Long = Long.MAX_VALUE
    }
}

private fun CoreBook.toScriptMap(): Map<String, Any?> = mapOf(
    "bookUrl" to bookUrl,
    "name" to name,
    "author" to author,
    "origin" to origin,
    "originName" to originName,
    "tocUrl" to tocUrl,
    "coverUrl" to coverUrl,
    "intro" to intro,
    "kind" to kind,
    "latestChapterTitle" to latestChapterTitle,
    "totalChapterNum" to totalChapterNum,
    "durChapterIndex" to durChapterIndex,
    "wordCount" to wordCount,
    "variable" to variable
)

private fun CoreBookSource.toScriptMap(): Map<String, Any?> = mapOf(
    "bookSourceUrl" to bookSourceUrl,
    "bookSourceName" to bookSourceName,
    "bookSourceGroup" to bookSourceGroup,
    "bookSourceType" to bookSourceType,
    "header" to header,
    "searchUrl" to searchUrl,
    "loginUrl" to loginUrl,
    "variableComment" to variableComment
)

private fun CoreChapter.toScriptMap(): Map<String, Any?> = mapOf(
    "bookUrl" to bookUrl,
    "url" to url,
    "title" to title,
    "index" to index,
    "isVolume" to isVolume,
    "isVip" to isVip,
    "isPay" to isPay,
    "resourceUrl" to resourceUrl,
    "tag" to tag,
    "wordCount" to wordCount,
    "variable" to variable
)
