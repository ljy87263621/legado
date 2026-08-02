package io.legado.desktop

import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreLibrary
import io.legado.core.source.BookSourceJsonCodec

class SourceModel(
    private val library: CoreLibrary
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

    fun exportJson(): String = BookSourceJsonCodec.encode(sources)

    fun refresh() {
        sources = library.sources()
    }
}
