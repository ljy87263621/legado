package io.legado.core.source

import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreLibrary

/** Executes one source rule in the same restricted runtime used by source parsing. */
class CoreSourceScriptService(
    private val library: CoreLibrary? = null,
    private val runtime: CoreScriptRuntime = RhinoCoreScriptRuntime()
) {
    fun test(
        source: CoreBookSource,
        ruleField: String,
        script: String,
        input: String = "",
        baseUrl: String = source.bookSourceUrl,
        bindings: Map<String, Any?> = emptyMap()
    ): Any? = runtime.evaluate(
        script = script,
        bindings = mapOf(
            "source" to (library?.let { CoreScriptSourceBinding(source, it) } ?: source),
            "baseUrl" to baseUrl,
            "result" to input,
            "input" to input,
            "src" to input
        ) + bindings,
        sharedLibrary = source.jsLib,
        context = CoreScriptContext(
            sourceUrl = source.bookSourceUrl,
            ruleField = ruleField,
            baseUrl = baseUrl
        )
    )
}
