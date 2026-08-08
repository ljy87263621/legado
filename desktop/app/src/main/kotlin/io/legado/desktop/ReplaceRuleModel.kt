package io.legado.desktop

import io.legado.core.library.CoreLibrary
import io.legado.core.library.CoreReplaceRule
import io.legado.core.library.CoreReplacementService

data class ReplaceRuleTestResult(
    val text: String? = null,
    val error: String? = null
) {
    val isSuccess: Boolean
        get() = error == null
}

class ReplaceRuleModel(
    private val library: CoreLibrary,
    private val replacementService: CoreReplacementService = CoreReplacementService(library)
) {
    var rules: List<CoreReplaceRule> = library.replaceRules()
        private set

    fun refresh() {
        rules = library.replaceRules()
    }

    fun save(rule: CoreReplaceRule): CoreReplaceRule {
        val normalized = if (rule.order == Int.MIN_VALUE) {
            rule.copy(order = (rules.maxOfOrNull(CoreReplaceRule::order) ?: -1) + 1)
        } else {
            rule
        }
        library.saveReplaceRule(normalized)
        refresh()
        return normalized
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        rules.firstOrNull { it.id == id }?.let { library.saveReplaceRule(it.copy(enabled = enabled)) }
        refresh()
    }

    fun delete(id: Long) {
        library.deleteReplaceRule(id)
        refresh()
    }

    fun test(rule: CoreReplaceRule, text: String): ReplaceRuleTestResult =
        runCatching { replacementService.test(rule, text).text }
            .fold(
                onSuccess = { ReplaceRuleTestResult(text = it) },
                onFailure = { ReplaceRuleTestResult(error = it.message ?: "替换规则测试失败") }
            )
}
