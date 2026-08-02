package io.legado.desktop

import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreLibrary
import io.legado.core.source.BookSourceJsonCodec
import io.legado.core.source.CoreSourceScriptService

class SourceModel(
    private val library: CoreLibrary,
    private val scriptService: CoreSourceScriptService = CoreSourceScriptService()
) {
    var sources: List<CoreBookSource> = library.sources()
        private set

    fun save(source: CoreBookSource) {
        library.saveSource(source)
        refresh()
    }

    fun setEnabled(bookSourceUrl: String, enabled: Boolean) {
        library.source(bookSourceUrl)?.let { library.saveSource(it.copy(enabled = enabled)) }
        refresh()
    }

    fun delete(bookSourceUrl: String) {
        library.deleteSource(bookSourceUrl)
        refresh()
    }

    fun importJson(json: String): Int {
        val imported = BookSourceJsonCodec.decode(json)
        imported.forEach(library::saveSource)
        refresh()
        return imported.size
    }

    fun saveJson(json: String, originalBookSourceUrl: String? = null): CoreBookSource {
        val imported = runCatching { BookSourceJsonCodec.decode(json) }
            .getOrElse { error ->
                throw IllegalArgumentException(error.message ?: "书源 JSON 无效", error)
            }
        require(imported.size == 1) { "编辑器只能保存一个书源" }
        val source = imported.single()
        require(source.bookSourceUrl.isNotBlank()) { "书源地址不能为空" }
        if (!originalBookSourceUrl.isNullOrBlank() && originalBookSourceUrl != source.bookSourceUrl) {
            library.deleteSource(originalBookSourceUrl)
        }
        save(source)
        return source
    }

    fun testScript(
        sourceJson: String,
        ruleField: String,
        script: String,
        input: String,
        baseUrl: String
    ): String {
        val source = BookSourceJsonCodec.decode(sourceJson).singleOrNull()
            ?: error("脚本测试需要一个完整书源 JSON")
        require(source.bookSourceUrl.isNotBlank()) { "书源地址不能为空" }
        return scriptService.test(
            source = source,
            ruleField = ruleField,
            script = script,
            input = input,
            baseUrl = baseUrl.ifBlank { source.bookSourceUrl }
        )?.toString().orEmpty()
    }

    fun exportJson(): String = BookSourceJsonCodec.encode(sources)

    fun refresh() {
        sources = library.sources()
    }
}
