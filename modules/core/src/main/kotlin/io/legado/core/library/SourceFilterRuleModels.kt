package io.legado.core.library

data class CoreSourceFilterRule(
    val id: String,
    val name: String = "",
    val enabled: Boolean = true,
    val pattern: String = "",
    val fields: String = "",
    val scope: String = "",
    val order: Int = 0,
    val createTime: Long = 0L
) {
    enum class Field {
        NAME,
        AUTHOR,
        INTRO,
        KIND,
        WORD_COUNT
    }

    sealed interface Scope {
        data object All : Scope
        data object None : Scope
        data class Source(val url: String) : Scope
        data class Groups(val names: Set<String>) : Scope
    }

    companion object {
        fun parseFields(raw: String): Set<Field> = raw
            .split(',')
            .mapNotNullTo(linkedSetOf()) { token ->
                runCatching { Field.valueOf(token.trim()) }.getOrNull()
            }

        fun formatFields(fields: Collection<Field>): String = fields.joinToString(",") { it.name }

        fun parseScope(raw: String): Scope {
            if (raw.isBlank()) return Scope.All
            if (raw.contains("::")) {
                val url = raw.substringAfter("::").trim()
                return if (url.isBlank()) Scope.None else Scope.Source(url)
            }
            val groups = raw.split(',').mapNotNullTo(linkedSetOf()) { it.trim().takeIf(String::isNotBlank) }
            return if (groups.isEmpty()) Scope.None else Scope.Groups(groups)
        }
    }
}
