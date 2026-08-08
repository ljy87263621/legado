package io.legado.desktop

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.legado.core.library.CoreLibrary
import io.legado.core.library.CoreTxtTocRule

class TxtTocRuleModel(
    private val library: CoreLibrary,
    private val gson: Gson = Gson()
) {
    var rules: List<CoreTxtTocRule> = library.txtTocRules()
        private set

    fun refresh() {
        rules = library.txtTocRules()
    }

    fun save(rule: CoreTxtTocRule): CoreTxtTocRule {
        require(rule.name.isNotBlank()) { "TXT目录规则名称不能为空" }
        require(rule.rule.isNotBlank()) { "TXT目录规则不能为空" }
        val existing = library.txtTocRule(rule.id)
        val serialNumber = when {
            rule.serialNumber >= 0 -> rule.serialNumber
            existing != null -> existing.serialNumber
            else -> (rules.maxOfOrNull(CoreTxtTocRule::serialNumber)?.coerceAtLeast(-1) ?: -1) + 1
        }
        val normalized = rule.copy(
            name = rule.name.trim(),
            rule = rule.rule.trim(),
            example = rule.example?.trim()?.ifBlank { null },
            serialNumber = serialNumber
        )
        library.saveTxtTocRule(normalized)
        refresh()
        return normalized
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        library.txtTocRule(id)?.let { library.saveTxtTocRule(it.copy(enable = enabled)) }
        refresh()
    }

    fun delete(id: Long) {
        library.deleteTxtTocRule(id)
        refresh()
    }

    fun importJson(json: String): Int {
        val root = runCatching { JsonParser.parseString(json.trim()) }
            .getOrElse { error -> throw IllegalArgumentException("TXT目录规则 JSON 无效", error) }
        val imported = when {
            root.isJsonArray -> root.asJsonArray.map { gson.fromJson(it, CoreTxtTocRule::class.java) }
            root.isJsonObject -> listOf(gson.fromJson(root, CoreTxtTocRule::class.java))
            else -> error("TXT目录规则 JSON 必须是对象或数组")
        }
        imported.forEach(::save)
        return imported.size
    }

    fun exportJson(): String = gson.toJson(rules)
}
