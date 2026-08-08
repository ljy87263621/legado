package io.legado.core.library

import com.google.gson.JsonParser
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class CoreReplaceRule(
    val id: Long = System.currentTimeMillis(),
    val name: String = "",
    val group: String? = null,
    val pattern: String = "",
    val replacement: String = "",
    val scope: String? = null,
    val scopeTitle: Boolean = false,
    val scopeContent: Boolean = true,
    val excludeScope: String? = null,
    val enabled: Boolean = true,
    val isRegex: Boolean = true,
    val timeoutMillisecond: Long = 3000L,
    val order: Int = Int.MIN_VALUE
) {
    fun isValid(): Boolean {
        if (pattern.isEmpty()) return false
        if (!isRegex) return true
        return try {
            Pattern.compile(pattern)
            !pattern.endsWith('|') || pattern.endsWith("\\|")
        } catch (_: PatternSyntaxException) {
            false
        }
    }

    fun validTimeoutMillisecond(): Long = timeoutMillisecond.takeIf { it > 0 } ?: 3000L
}

data class CoreReplacementResult(
    val text: String,
    val appliedRuleIds: List<Long> = emptyList()
)

/** Applies Android-compatible global replacement rules to titles and chapter content. */
class CoreReplacementService(
    private val library: CoreLibrary,
    private val replace: (String, CoreReplaceRule) -> String = CoreReplacementService::replaceWithTimeout
) {

    fun processTitle(book: CoreBook, text: String): CoreReplacementResult =
        process(book, text, Scope.TITLE)

    fun processContent(book: CoreBook, text: String): CoreReplacementResult =
        process(book, text, Scope.CONTENT)

    fun test(rule: CoreReplaceRule, text: String): CoreReplacementResult {
        require(rule.pattern.isNotEmpty()) { "替换规则不能为空" }
        require(rule.isValid()) { "替换规则无效" }
        val updated = replace(text, rule)
        return CoreReplacementResult(
            text = updated,
            appliedRuleIds = if (updated == text) emptyList() else listOf(rule.id)
        )
    }

    private fun process(book: CoreBook, original: String, scope: Scope): CoreReplacementResult {
        if (!book.usesReplacementRules()) return CoreReplacementResult(original)
        var text = original
        val applied = mutableListOf<Long>()
        matchingRules(book, scope).forEach { rule ->
            if (rule.pattern.isEmpty() || !rule.isValid()) return@forEach
            try {
                val updated = replace(text, rule)
                if (updated != text) {
                    applied += rule.id
                    text = updated
                }
            } catch (_: Exception) {
                // A malformed or timed-out imported rule must not break reading.
                library.saveReplaceRule(rule.copy(enabled = false))
            }
        }
        return CoreReplacementResult(text, applied)
    }

    private fun matchingRules(book: CoreBook, scope: Scope): List<CoreReplaceRule> =
        library.replaceRules()
            .asSequence()
            .filter { it.enabled }
            .filter { if (scope == Scope.TITLE) it.scopeTitle else it.scopeContent }
            .filter { rule ->
                val included = rule.scope.isNullOrBlank() ||
                    rule.scope.contains(book.name) || rule.scope.contains(book.origin)
                val excluded = !rule.excludeScope.isNullOrBlank() &&
                    (rule.excludeScope!!.contains(book.name) || rule.excludeScope.contains(book.origin))
                included && !excluded
            }
            .sortedWith(compareBy<CoreReplaceRule> { it.order }.thenBy { it.id })
            .toList()

    private enum class Scope { TITLE, CONTENT }

    private fun CoreBook.usesReplacementRules(): Boolean {
        val config = readConfigJson ?: return true
        return runCatching {
            JsonParser.parseString(config).asJsonObject
                .get("useReplaceRule")
                ?.takeIf { !it.isJsonNull }
                ?.asBoolean
                ?: true
        }.getOrDefault(true)
    }

    companion object {
        private fun replaceWithTimeout(text: String, rule: CoreReplaceRule): String {
            if (!rule.isRegex) return text.replace(rule.pattern, rule.replacement)
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "legado-replace-rule").apply { isDaemon = true }
            }
            return try {
                val future = executor.submit<String> {
                    Regex(rule.pattern).replace(text, rule.replacement)
                }
                future.get(
                    rule.validTimeoutMillisecond(),
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
            } catch (error: java.util.concurrent.TimeoutException) {
                throw IllegalStateException("替换规则执行超时: ${rule.name}", error)
            } finally {
                executor.shutdownNow()
            }
        }
    }
}
