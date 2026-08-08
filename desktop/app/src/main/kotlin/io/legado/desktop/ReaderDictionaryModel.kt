package io.legado.desktop

import io.legado.core.library.CoreDictRule
import io.legado.core.library.CoreLibrary
import io.legado.core.source.CoreDictRuleService
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.JavaNetHttpClient

data class ReaderDictionaryItem(
    val rule: CoreDictRule,
    val text: String? = null,
    val error: String? = null
) {
    val isSuccess: Boolean
        get() = error == null
}

data class ReaderDictionaryLookup(
    val query: String,
    val items: List<ReaderDictionaryItem>
)

object ReaderDictionaryQuery {
    fun normalize(rawQuery: String): String? = rawQuery.lineSequence()
        .joinToString("\n") { it.trim() }
        .trim()
        .takeIf(String::isNotBlank)

    fun fromClipboard(clipboardText: String?): String? = clipboardText?.let(::normalize)
}

/** Coordinates the reader's multi-rule dictionary lookup without UI dependencies. */
class ReaderDictionaryModel(
    library: CoreLibrary,
    httpClient: CoreHttpClient = JavaNetHttpClient()
) {
    private val service = CoreDictRuleService(library, httpClient)

    fun lookup(rawQuery: String): ReaderDictionaryLookup {
        val query = ReaderDictionaryQuery.normalize(rawQuery)
            ?: error("查词内容不能为空")
        return ReaderDictionaryLookup(
            query = query,
            items = service.searchEnabled(query).map { result ->
                ReaderDictionaryItem(
                    rule = result.rule,
                    text = result.text.takeIf { result.error == null },
                    error = result.error
                )
            }
        )
    }
}
