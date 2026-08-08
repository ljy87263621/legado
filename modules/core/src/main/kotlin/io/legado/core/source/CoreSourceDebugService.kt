package io.legado.core.source

import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreLibrary

/** Exposes the source-header subset needed by platform request inspection tools. */
class CoreSourceDebugService {
    fun expandUrl(
        source: CoreBookSource,
        template: String,
        keyword: String,
        page: Int,
        library: CoreLibrary? = null
    ): String = CoreSourceScriptSupport.expandUrl(
        source = source,
        template = template,
        keyword = keyword,
        page = page,
        ruleField = "searchUrl",
        library = library
    )

    fun sourceHeaders(
        source: CoreBookSource,
        baseUrl: String,
        bindings: Map<String, Any?> = emptyMap(),
        library: CoreLibrary? = null
    ): Map<String, String> = CoreSourceScriptSupport.headers(
        source,
        baseUrl,
        bindings = bindings,
        library = library
    )
}
