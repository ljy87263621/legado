package io.legado.core.source

import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreLibrary
import io.legado.core.library.CoreSourceFilterRule

data class CoreSourceFilterReport(
    val results: List<CoreSearchResult>,
    val filteredCount: Int,
    val invalidRuleCount: Int
)

class CoreSourceFilterService(
    private val library: CoreLibrary
) {
    fun apply(results: List<CoreSearchResult>): CoreSourceFilterReport {
        val compiled = mutableListOf<CompiledRule>()
        var invalidRuleCount = 0
        library.sourceFilterRules().forEach { rule ->
            if (!rule.enabled) return@forEach
            val regex = runCatching { rule.pattern.takeIf(String::isNotBlank)?.toRegex() }.getOrNull()
            val fields = CoreSourceFilterRule.parseFields(rule.fields)
            val scope = CoreSourceFilterRule.parseScope(rule.scope)
            if (regex == null || fields.isEmpty() || scope == CoreSourceFilterRule.Scope.None) {
                invalidRuleCount++
            } else {
                compiled += CompiledRule(regex, fields, scope)
            }
        }
        val filtered = results.filterNot { result -> compiled.any { it.matches(result) } }
        return CoreSourceFilterReport(
            results = filtered,
            filteredCount = results.size - filtered.size,
            invalidRuleCount = invalidRuleCount
        )
    }

    private data class CompiledRule(
        val regex: Regex,
        val fields: Set<CoreSourceFilterRule.Field>,
        val scope: CoreSourceFilterRule.Scope
    ) {
        fun matches(result: CoreSearchResult): Boolean {
            if (!scopeMatches(result.source)) return false
            return fields.any { field -> regex.containsMatchIn(field.value(result)) }
        }

        private fun scopeMatches(source: CoreBookSource): Boolean = when (scope) {
            CoreSourceFilterRule.Scope.All -> true
            CoreSourceFilterRule.Scope.None -> false
            is CoreSourceFilterRule.Scope.Source -> source.bookSourceUrl == scope.url
            is CoreSourceFilterRule.Scope.Groups -> sourceGroups(source).any { it in scope.names }
        }

        private fun CoreSourceFilterRule.Field.value(result: CoreSearchResult): String = when (this) {
            CoreSourceFilterRule.Field.NAME -> result.book.name
            CoreSourceFilterRule.Field.AUTHOR -> result.book.author
            CoreSourceFilterRule.Field.INTRO -> result.book.intro.orEmpty()
            CoreSourceFilterRule.Field.KIND -> result.book.kind.orEmpty()
            CoreSourceFilterRule.Field.WORD_COUNT -> result.book.wordCount.orEmpty()
        }

        private fun sourceGroups(source: CoreBookSource): Set<String> = source.bookSourceGroup
            ?.split(',')
            ?.mapNotNullTo(linkedSetOf()) { it.trim().takeIf(String::isNotBlank) }
            ?: emptySet()
    }
}
