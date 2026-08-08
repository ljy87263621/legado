package io.legado.core.source

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.nodes.Document

/** Small, static-HTML XPath subset shared by the desktop source parsers. */
object CoreXPathRuleSupport {

    fun isRule(rawRule: String?): Boolean {
        val rule = rawRule?.trimStart() ?: return false
        return rule.startsWith("@XPath:", ignoreCase = true) ||
            rule.startsWith("/") ||
            rule.startsWith("./") ||
            rule.startsWith("../")
    }

    fun select(root: Element, rawRule: String): List<Node> {
        val rule = normalize(rawRule)
        val combination = splitCombination(rule)
        if (combination.parts.size == 1) return selectSingle(root, combination.parts.single())
        if (combination.operator == "||") {
            return combination.parts
                .asSequence()
                .map { part -> selectSingle(root, part) }
                .firstOrNull(List<Node>::isNotEmpty)
                .orEmpty()
        }

        val results = combination.parts.map { part -> selectSingle(root, part) }
            .filter(List<Node>::isNotEmpty)
        if (results.isEmpty()) return emptyList()
        return when (combination.operator) {
            "%%" -> results.first().indices.flatMap { index ->
                results.mapNotNull { result -> result.getOrNull(index) }
            }

            else -> results.flatten()
        }
    }

    fun firstElement(root: Element, rawRule: String): Element? =
        select(root, rawRule).firstOrNull { it is Element } as? Element

    fun firstText(root: Element, rawRule: String): String =
        select(root, rawRule).firstOrNull()?.let(::nodeText).orEmpty()

    fun nodeText(node: Node): String = when (node) {
        is Element -> node.text()
        is TextNode -> node.text()
        else -> node.toString()
    }

    fun normalize(rawRule: String): String = rawRule.trim().let { rule ->
        if (rule.startsWith("@XPath:", ignoreCase = true)) rule.substring(7).trim() else rule
    }

    private fun selectSingle(root: Element, rule: String): List<Node> {
        if (rule.isBlank() || rule == ".") return listOf(root)
        val attributeSeparator = rule.lastIndexOf("/@")
        if (attributeSeparator >= 0 && attributeSeparator + 2 < rule.length) {
            val elementPath = scopedPath(root, rule.substring(0, attributeSeparator).ifBlank { "." })
            val attribute = rule.substring(attributeSeparator + 2)
            return runCatching {
                root.selectXpath(elementPath, Element::class.java)
                    .map { TextNode(it.attr(attribute)) }
            }.getOrDefault(emptyList())
        }
        return runCatching {
            root.selectXpath(scopedPath(root, rule), Node::class.java)
        }.getOrDefault(emptyList())
    }

    private fun scopedPath(root: Element, rule: String): String =
        if (root !is Document && rule.startsWith("//")) ".${rule}" else rule

    private fun splitCombination(rule: String): Combination {
        val parts = mutableListOf<String>()
        var start = 0
        var bracketDepth = 0
        var quote: Char? = null
        var operator: String? = null
        var index = 0
        while (index < rule.length - 1) {
            val character = rule[index]
            if (quote != null) {
                if (character == quote) quote = null
                index++
                continue
            }
            if (character == '\'' || character == '"') {
                quote = character
                index++
                continue
            }
            when (character) {
                '[' -> bracketDepth++
                ']' -> if (bracketDepth > 0) bracketDepth--
            }
            val candidate = rule.substring(index, index + 2)
            if (bracketDepth == 0 && candidate in setOf("&&", "||", "%%")) {
                if (operator == null) operator = candidate
                parts += rule.substring(start, index).trim()
                start = index + 2
                index += 2
                continue
            }
            index++
        }
        if (operator == null) return Combination(listOf(rule), null)
        parts += rule.substring(start).trim()
        return Combination(parts.filter(String::isNotBlank), operator)
    }

    private data class Combination(
        val parts: List<String>,
        val operator: String?
    )
}
