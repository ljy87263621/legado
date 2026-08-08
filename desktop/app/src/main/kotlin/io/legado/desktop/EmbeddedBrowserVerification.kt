package io.legado.desktop

import io.legado.core.library.CoreCookie
import io.legado.core.library.CoreLibrary
import io.legado.core.library.CoreBookSource
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpRequest
import io.legado.core.source.CoreSourceAwareHttpClient
import io.legado.core.source.CoreUrlRuleSupport
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.web.WebView
import javafx.stage.Stage

data class EmbeddedBrowserVerificationRequest(
    val url: String,
    val sourceUrl: String,
    val title: String = "",
    val refetchAfterSuccess: Boolean = true
)

data class EmbeddedBrowserVerificationResult(
    val sourceUrl: String,
    val finalUrl: String,
    val html: String,
    val cookies: List<CoreCookie>
)

interface EmbeddedBrowserVerificationSession : AutoCloseable {
    fun navigate(request: EmbeddedBrowserVerificationRequest)

    fun complete(): EmbeddedBrowserVerificationResult
}

fun interface EmbeddedBrowserVerificationSessionFactory {
    fun create(
        library: CoreLibrary,
        source: CoreBookSource,
        onComplete: (EmbeddedBrowserVerificationResult) -> Unit
    ): EmbeddedBrowserVerificationSession
}

fun interface DesktopEmbeddedBrowserLauncher {
    fun open(request: EmbeddedBrowserVerificationRequest)
}

class JavaFxEmbeddedBrowserLauncher(
    private val library: CoreLibrary,
    private val httpClient: CoreHttpClient
) : DesktopEmbeddedBrowserLauncher {
    override fun open(request: EmbeddedBrowserVerificationRequest) {
        val source = requireNotNull(library.source(request.sourceUrl)) { "未找到书源: ${request.sourceUrl}" }
        JavaFxRuntime.run {
            val webView = WebView()
            val engine = webView.engine
            val progress = ProgressBar().apply { prefWidth = 180.0 }
            val address = Label(request.url).apply { style = "-fx-text-fill: #525866;" }
            val complete = Button("完成验证")
            val close = Button("关闭")
            val status = Label("正在加载")
            val controls = HBox(10.0, complete, close, progress, status).apply {
                padding = Insets(8.0)
            }
            val root = BorderPane(webView).apply {
                top = address
                bottom = controls
                BorderPane.setMargin(address, Insets(8.0, 12.0, 0.0, 12.0))
            }
            val stage = Stage().apply {
                title = request.title.ifBlank { "书源验证" }
                scene = Scene(root, 1120.0, 760.0)
                minWidth = 760.0
                minHeight = 560.0
            }
            engine.loadWorker.progressProperty().addListener { _, _, value -> progress.progress = value.toDouble() }
            engine.locationProperty().addListener { _, _, value -> address.text = value }
            engine.loadWorker.stateProperty().addListener { _, _, state ->
                when (state) {
                    Worker.State.SUCCEEDED -> status.text = "页面已加载"
                    Worker.State.FAILED -> status.text = "页面加载失败"
                    Worker.State.RUNNING -> status.text = "正在加载"
                    else -> Unit
                }
            }
            complete.setOnAction {
                engine.executeScript("document.documentElement.outerHTML")?.toString().orEmpty().let { html ->
                    val cookies = engine.executeScript("document.cookie")?.toString().orEmpty()
                    val result = EmbeddedBrowserVerificationResult(
                        sourceUrl = source.bookSourceUrl,
                        finalUrl = engine.location,
                        html = html,
                        cookies = EmbeddedBrowserCookieParser.parse(URI(engine.location), cookies)
                            .map { it.copy(persistent = true) }
                    )
                    val model = EmbeddedBrowserVerificationModel(
                        library = library,
                        httpClient = httpClient,
                        sessionFactory = EmbeddedBrowserVerificationSessionFactory { _, _, _ ->
                            object : EmbeddedBrowserVerificationSession {
                                override fun navigate(request: EmbeddedBrowserVerificationRequest) = Unit
                                override fun complete(): EmbeddedBrowserVerificationResult = result
                                override fun close() = Unit
                            }
                        }
                    )
                    runCatching { model.verify(request) }
                        .onSuccess { status.text = "验证已保存" }
                        .onFailure { status.text = it.message ?: "验证保存失败" }
                }
            }
            close.setOnAction { stage.close() }
            stage.show()
            engine.load(request.url)
        }
    }
}

private object JavaFxRuntime {
    private val started = AtomicBoolean(false)

    fun run(action: () -> Unit) {
        if (Platform.isFxApplicationThread()) {
            action()
            return
        }
        if (started.compareAndSet(false, true)) {
            Platform.startup(action)
        } else {
            Platform.runLater(action)
        }
    }
}

class EmbeddedBrowserVerificationModel(
    private val library: CoreLibrary,
    private val httpClient: CoreHttpClient,
    private val sessionFactory: EmbeddedBrowserVerificationSessionFactory
) {
    fun verify(request: EmbeddedBrowserVerificationRequest): EmbeddedBrowserVerificationResult {
        val source = requireNotNull(library.source(request.sourceUrl)) { "未找到书源: ${request.sourceUrl}" }
        require(request.url.length < 64 * 1024) { "浏览器验证 URL 过长" }
        val uri = URI(request.url)
        require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            "浏览器验证 URL 必须使用 HTTP 或 HTTPS"
        }
        require(!uri.host.isNullOrBlank()) { "浏览器验证 URL 缺少主机名" }
        var result: EmbeddedBrowserVerificationResult? = null
        sessionFactory.create(library, source) { completed -> result = completed }.use { session ->
            session.navigate(request)
            result = session.complete()
        }
        val completed = requireNotNull(result) { "内置浏览器未返回验证结果" }
        completed.cookies.forEach(library::saveCookie)
        if (request.refetchAfterSuccess) {
            val refreshed = refetch(source, request.url)
            return completed.copy(html = refreshed)
        }
        return completed
    }

    private fun refetch(source: CoreBookSource, url: String): String {
        val resolved = CoreUrlRuleSupport.resolve(source, url, ruleField = "browserVerification", library = library)
        val request = CoreHttpRequest(url = resolved.requestUrl, method = resolved.method, headers = resolved.headers, body = resolved.body)
        val response = if (httpClient is CoreSourceAwareHttpClient) {
            httpClient.request(source, request)
        } else {
            httpClient.request(request)
        }
        require(response.statusCode in 200..399) { "验证后重新请求失败：HTTP ${response.statusCode}" }
        return response.body
    }
}

object EmbeddedBrowserCookieParser {
    fun parse(uri: URI, rawCookieHeader: String): List<CoreCookie> {
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        val path = uri.rawPath?.takeIf(String::isNotBlank) ?: "/"
        return rawCookieHeader.split(';').mapNotNull { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val name = pair.substring(0, separator).trim()
            val value = pair.substring(separator + 1).trim()
            if (name.isBlank()) null else CoreCookie(host, defaultPath(path), name, value, persistent = false)
        }
    }

    fun parseSetCookie(uri: URI, raw: String): CoreCookie? {
        val parts = raw.split(';')
        val first = parts.firstOrNull()?.trim().orEmpty()
        val separator = first.indexOf('=')
        if (separator <= 0) return null
        var domain = uri.host.orEmpty().lowercase(Locale.ROOT)
        var path = defaultPath(uri.rawPath.orEmpty())
        var persistent = false
        var expiresAt: Long? = null
        parts.drop(1).forEach { part ->
            val key = part.substringBefore('=').trim().lowercase(Locale.ROOT)
            val value = part.substringAfter('=', "").trim()
            when (key) {
                "domain" -> domain = value.lowercase(Locale.ROOT).removePrefix(".")
                "path" -> path = if (value.startsWith('/')) value else "/$value"
                "max-age" -> value.toLongOrNull()?.let { age ->
                    persistent = true
                    expiresAt = if (age <= 0) 0 else System.currentTimeMillis() + age * 1000
                }
                "expires" -> runCatching {
                    expiresAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli()
                    persistent = true
                }
            }
        }
        return CoreCookie(
            domain = domain,
            path = path,
            name = first.substring(0, separator).trim(),
            value = first.substring(separator + 1).trim(),
            persistent = persistent,
            expiresAt = expiresAt
        )
    }

    private fun defaultPath(raw: String): String {
        val path = if (raw.startsWith('/')) raw else "/$raw"
        return path.substringBeforeLast('/', "/").ifBlank { "/" }
    }
}
