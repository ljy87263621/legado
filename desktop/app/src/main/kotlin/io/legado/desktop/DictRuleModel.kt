package io.legado.desktop

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.legado.core.library.CoreDictRule
import io.legado.core.library.CoreLibrary
import io.legado.core.source.CoreDictRuleService
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.JavaNetHttpClient

class DictRuleModel(
    private val library: CoreLibrary,
    httpClient: CoreHttpClient = JavaNetHttpClient(),
    private val gson: Gson = Gson()
) {
    private val service = CoreDictRuleService(library, httpClient)

    var rules: List<CoreDictRule> = library.dictRules()
        private set

    fun refresh() {
        rules = library.dictRules()
    }

    fun save(rule: CoreDictRule, previousName: String? = null): CoreDictRule {
        require(rule.name.isNotBlank()) { "字典规则名称不能为空" }
        val normalized = when {
            !previousName.isNullOrBlank() && previousName != rule.name ->
                rule.copy(sortNumber = library.dictRule(previousName)?.sortNumber ?: rule.sortNumber)
            library.dictRule(rule.name) == null && rule.sortNumber == 0 ->
                rule.copy(sortNumber = (rules.maxOfOrNull(CoreDictRule::sortNumber) ?: -1) + 1)
            else -> rule
        }
        library.saveDictRule(normalized)
        if (!previousName.isNullOrBlank() && previousName != normalized.name) {
            library.deleteDictRule(previousName)
        }
        refresh()
        return normalized
    }

    fun setEnabled(name: String, enabled: Boolean) {
        library.dictRule(name)?.let { library.saveDictRule(it.copy(enabled = enabled)) }
        refresh()
    }

    fun delete(name: String) {
        library.deleteDictRule(name)
        refresh()
    }

    fun test(rule: CoreDictRule, key: String): DictRuleTestResult =
        runCatching { service.search(rule, key) }
            .fold(
                onSuccess = { DictRuleTestResult(rule = rule, text = it) },
                onFailure = { error ->
                    DictRuleTestResult(rule = rule, error = error.message ?: "字典规则测试失败")
                }
            )

    fun testEnabled(input: List<CoreDictRule> = library.enabledDictRules(), key: String): List<DictRuleTestResult> =
        input.map { rule -> test(rule, key) }

    fun importJson(json: String): Int {
        val root = runCatching { JsonParser.parseString(json.trim()) }
            .getOrElse { error -> throw IllegalArgumentException("字典规则 JSON 无效", error) }
        val imported = when {
            root.isJsonArray -> root.asJsonArray.map { gson.fromJson(it, CoreDictRule::class.java) }
            root.isJsonObject -> listOf(gson.fromJson(root, CoreDictRule::class.java))
            else -> error("字典规则 JSON 必须是对象或数组")
        }
        imported.forEach { rule ->
            require(rule.name.isNotBlank()) { "字典规则名称不能为空" }
            library.saveDictRule(rule)
        }
        refresh()
        return imported.size
    }

fun exportJson(): String = gson.toJson(rules)
}

data class DictRuleTestResult(
    val rule: CoreDictRule,
    val text: String? = null,
    val error: String? = null
) {
    val isSuccess: Boolean
        get() = error == null
}
