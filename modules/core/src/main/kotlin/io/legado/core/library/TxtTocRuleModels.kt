package io.legado.core.library

/** A configurable TXT table-of-contents rule compatible with Android Legado. */
data class CoreTxtTocRule(
    val id: Long = System.currentTimeMillis(),
    val name: String = "",
    val rule: String = "",
    val example: String? = null,
    val serialNumber: Int = -1,
    val enable: Boolean = true
)
