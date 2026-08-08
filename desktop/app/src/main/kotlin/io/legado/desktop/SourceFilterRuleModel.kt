package io.legado.desktop

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.legado.core.library.CoreLibrary
import io.legado.core.library.CoreSourceFilterRule
import java.util.UUID

class SourceFilterRuleModel(
    private val library: CoreLibrary,
    private val gson: Gson = Gson()
) {
    var rules: List<CoreSourceFilterRule> = library.sourceFilterRules()
        private set

    fun refresh() {
        rules = library.sourceFilterRules()
    }

    fun save(rule: CoreSourceFilterRule): CoreSourceFilterRule {
        val existing = rule.id.trim().takeIf(String::isNotEmpty)?.let(library::sourceFilterRule)
        val normalizedFields = CoreSourceFilterRule.parseFields(rule.fields)
        require(rule.name.trim().isNotEmpty()) { "书源筛选规则名称不能为空" }
        require(rule.pattern.trim().isNotEmpty()) { "书源筛选规则正则不能为空" }
        require(normalizedFields.isNotEmpty()) { "书源筛选规则字段不能为空" }
        require(runCatching { rule.pattern.trim().toRegex() }.isSuccess) { "书源筛选规则正则无效" }
        require(CoreSourceFilterRule.parseScope(rule.scope.trim()) != CoreSourceFilterRule.Scope.None) {
            "书源筛选规则作用域无效"
        }

        val order = when {
            existing != null -> existing.order
            rule.order != 0 -> rule.order
            else -> (rules.maxOfOrNull(CoreSourceFilterRule::order) ?: -1) + 1
        }
        val normalized = rule.copy(
            id = rule.id.trim().ifEmpty { UUID.randomUUID().toString() },
            name = rule.name.trim(),
            pattern = rule.pattern.trim(),
            fields = CoreSourceFilterRule.formatFields(normalizedFields),
            scope = rule.scope.trim(),
            order = order,
            createTime = existing?.createTime?.takeIf { it > 0L }
                ?: rule.createTime.takeIf { it > 0L }
                ?: System.currentTimeMillis()
        )
        library.saveSourceFilterRule(normalized)
        refresh()
        return normalized
    }

    fun setEnabled(id: String, enabled: Boolean) {
        library.sourceFilterRule(id)?.let { library.saveSourceFilterRule(it.copy(enabled = enabled)) }
        refresh()
    }

    fun delete(id: String) {
        library.deleteSourceFilterRule(id)
        refresh()
    }

    fun importJson(json: String): Int {
        val root = runCatching { JsonParser.parseString(json.trim()) }
            .getOrElse { error -> throw IllegalArgumentException("书源筛选规则 JSON 无效", error) }
        val imported = when {
            root.isJsonArray -> root.asJsonArray.map(::parseRule)
            root.isJsonObject -> listOf(parseRule(root))
            else -> error("书源筛选规则 JSON 必须是对象或数组")
        }
        imported.forEach(::save)
        return imported.size
    }

    fun exportJson(): String = gson.toJson(rules)

    private fun parseRule(element: com.google.gson.JsonElement): CoreSourceFilterRule {
        require(element.isJsonObject) { "书源筛选规则 JSON 数组只能包含对象" }
        val objectValue = element.asJsonObject
        return CoreSourceFilterRule(
            id = objectValue.stringValue("id"),
            name = objectValue.stringValue("name"),
            enabled = objectValue.booleanValue("enabled", true),
            pattern = objectValue.stringValue("pattern"),
            fields = objectValue.stringValue("fields"),
            scope = objectValue.stringValue("scope"),
            order = objectValue.intValue("order", 0),
            createTime = objectValue.longValue("createTime", 0L)
        )
    }

    private fun com.google.gson.JsonObject.stringValue(name: String): String =
        get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private fun com.google.gson.JsonObject.booleanValue(name: String, default: Boolean): Boolean =
        get(name)?.takeUnless { it.isJsonNull }?.asBoolean ?: default

    private fun com.google.gson.JsonObject.intValue(name: String, default: Int): Int =
        get(name)?.takeUnless { it.isJsonNull }?.asInt ?: default

    private fun com.google.gson.JsonObject.longValue(name: String, default: Long): Long =
        get(name)?.takeUnless { it.isJsonNull }?.asLong ?: default
}
