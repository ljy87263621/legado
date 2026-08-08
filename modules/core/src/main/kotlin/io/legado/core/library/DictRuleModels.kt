package io.legado.core.library

/** A dictionary lookup rule compatible with the Android dictRules table. */
data class CoreDictRule(
    val name: String,
    val urlRule: String = "",
    val showRule: String = "",
    val enabled: Boolean = true,
    val sortNumber: Int = 0
)
