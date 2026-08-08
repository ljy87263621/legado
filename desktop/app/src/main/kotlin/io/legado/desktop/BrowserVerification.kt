package io.legado.desktop

import java.awt.Desktop
import java.net.URI

data class BrowserVerificationRequest(
    val uri: URI,
    val url: String,
    val sourceUrl: String,
    val title: String
)

fun interface DesktopBrowserLauncher {
    fun open(request: BrowserVerificationRequest)
}

object SystemDesktopBrowserLauncher : DesktopBrowserLauncher {
    override fun open(request: BrowserVerificationRequest) {
        require(Desktop.isDesktopSupported()) { "系统不支持打开外部浏览器" }
        val desktop = Desktop.getDesktop()
        require(desktop.isSupported(Desktop.Action.BROWSE)) { "系统不支持打开外部浏览器" }
        desktop.browse(request.uri)
    }
}
