package io.legado.desktop.persistence

data class DesktopWebDavConfig(
    val url: String,
    val username: String,
    val password: String,
    val bookDirectory: String = "books"
)

interface DesktopWebDavConfigStore {
    fun webDavConfig(): DesktopWebDavConfig?

    fun saveWebDavConfig(config: DesktopWebDavConfig)
}
