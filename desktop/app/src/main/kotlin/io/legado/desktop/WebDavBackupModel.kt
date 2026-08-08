package io.legado.desktop

import io.legado.desktop.persistence.DesktopWebDavConfig
import io.legado.desktop.persistence.DesktopWebDavConfigStore
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class WebDavBackupFile(
    val name: String,
    val size: Long
)

data class WebDavOperationResult(
    val message: String? = null,
    val error: String? = null
) {
    val isSuccess: Boolean
        get() = error == null
}

interface WebDavClient {
    fun list(config: DesktopWebDavConfig): List<WebDavBackupFile>

    fun listDirectory(config: DesktopWebDavConfig): List<WebDavBackupFile> = list(config)

    fun put(config: DesktopWebDavConfig, name: String, content: ByteArray)

    fun get(config: DesktopWebDavConfig, name: String): ByteArray

    fun delete(config: DesktopWebDavConfig, name: String)
}

class JavaNetWebDavClient(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 30_000
) : WebDavClient {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(connectTimeoutMillis.toLong()))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun list(config: DesktopWebDavConfig): List<WebDavBackupFile> {
        return listDirectory(config)
            .filter { it.name.startsWith("backup") && it.name.endsWith(".zip", ignoreCase = true) }
            .sortedByDescending(WebDavBackupFile::name)
    }

    override fun listDirectory(config: DesktopWebDavConfig): List<WebDavBackupFile> {
        val response = request(config, "PROPFIND", body = "".toByteArray(StandardCharsets.UTF_8))
        require(response.status in 200..299) { "WebDAV 列出备份失败：HTTP ${response.status}" }
        return parseListing(response.body)
    }

    override fun put(config: DesktopWebDavConfig, name: String, content: ByteArray) {
        val response = request(config, "PUT", name, content)
        require(response.status in 200..299) { "WebDAV 上传失败：HTTP ${response.status}" }
    }

    override fun get(config: DesktopWebDavConfig, name: String): ByteArray {
        val response = request(config, "GET", name)
        require(response.status in 200..299) { "WebDAV 下载失败：HTTP ${response.status}" }
        return response.body
    }

    override fun delete(config: DesktopWebDavConfig, name: String) {
        val response = request(config, "DELETE", name)
        require(response.status in 200..299) { "WebDAV 删除失败：HTTP ${response.status}" }
    }

    private fun request(
        config: DesktopWebDavConfig,
        method: String,
        name: String? = null,
        body: ByteArray? = null
    ): Response {
        val base = URI(config.url)
        val path = base.path.trimEnd('/') + if (name == null) "/" else "/$name"
        val uri = URI(base.scheme, base.rawAuthority, path, null, null)
        val requestBuilder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMillis(readTimeoutMillis.toLong()))
            .header("Authorization", basicAuth(config))
        if (method == "PROPFIND") {
            requestBuilder.header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
        }
        val request = requestBuilder.method(
            method,
            body?.let(HttpRequest.BodyPublishers::ofByteArray)
                ?: HttpRequest.BodyPublishers.noBody()
        ).build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        return Response(response.statusCode(), response.body())
    }

    private fun basicAuth(config: DesktopWebDavConfig): String {
        val value = "${config.username}:${config.password}"
        return "Basic " + Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun parseListing(body: ByteArray): List<WebDavBackupFile> {
        val builder = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
            .newDocumentBuilder()
        val document = builder.parse(ByteArrayInputStream(body))
        val responses = document.getElementsByTagNameNS("DAV:", "response")
        return buildList {
            for (index in 0 until responses.length) {
                val response = responses.item(index) as Element
                val name = response.getElementsByTagNameNS("DAV:", "href")
                    .item(0)
                    ?.textContent
                    ?.substringAfterLast('/')
                    ?.takeIf { it.isNotBlank() }
                    ?: continue
                val size = response.getElementsByTagNameNS("DAV:", "getcontentlength")
                    .item(0)
                    ?.textContent
                    ?.toLongOrNull()
                    ?: 0L
                add(WebDavBackupFile(name, size))
            }
        }
    }

    private data class Response(val status: Int, val body: ByteArray)
}

class WebDavBackupModel(
    private val library: io.legado.core.library.CoreLibrary,
    private val client: WebDavClient = JavaNetWebDavClient(),
    private val store: DesktopWebDavConfigStore
) {
    var config: DesktopWebDavConfig? = store.webDavConfig()
        private set

    fun configure(url: String, username: String, password: String): WebDavOperationResult =
        runCatching {
            val normalized = normalizeConfig(url, username, password)
            client.list(normalized)
            store.saveWebDavConfig(normalized)
            config = normalized
            WebDavOperationResult(message = "WebDAV 连接成功")
        }.getOrElse { WebDavOperationResult(error = it.message ?: "WebDAV 配置失败") }

    fun listBackups(): Result<List<WebDavBackupFile>> = runCatching {
        client.list(requireConfig())
    }

    fun upload(archive: Path, name: String): WebDavOperationResult = runCatching {
        require(Files.isRegularFile(archive)) { "本地备份不存在" }
        require(isSafeBackupName(name)) { "远端备份文件名非法" }
        client.put(requireConfig(), name, Files.readAllBytes(archive))
        WebDavOperationResult(message = "备份已上传：$name")
    }.getOrElse { WebDavOperationResult(error = it.message ?: "WebDAV 上传失败") }

    fun restore(name: String): WebDavOperationResult {
        val temporary = Files.createTempFile("legado-webdav-", ".zip")
        return try {
            require(isSafeBackupName(name)) { "远端备份文件名非法" }
            Files.write(temporary, client.get(requireConfig(), name))
            val result = BackupModel(library).import(temporary)
            if (result.isSuccess) {
                WebDavOperationResult(message = "备份已恢复：$name")
            } else {
                WebDavOperationResult(error = result.error ?: "WebDAV 恢复失败")
            }
        } catch (error: Throwable) {
            WebDavOperationResult(error = error.message ?: "WebDAV 恢复失败")
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun requireConfig(): DesktopWebDavConfig = config ?: error("请先配置 WebDAV")

    private fun normalizeConfig(url: String, username: String, password: String): DesktopWebDavConfig {
        val normalizedUrl = url.trim().let { if (it.endsWith('/')) it else "$it/" }
        val uri = URI(normalizedUrl)
        require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            "WebDAV 地址必须使用 HTTP 或 HTTPS"
        }
        require(!uri.host.isNullOrBlank()) { "WebDAV 地址缺少主机名" }
        require(username.isNotBlank()) { "WebDAV 用户名不能为空" }
        require(password.isNotEmpty()) { "WebDAV 密码不能为空" }
        return DesktopWebDavConfig(normalizedUrl, username, password)
    }

    private fun isSafeBackupName(name: String): Boolean =
        name.startsWith("backup") &&
            name.endsWith(".zip", ignoreCase = true) &&
            name.length <= 180 &&
            name == name.substringAfterLast('/') &&
            name == name.substringAfterLast('\\') &&
            name.none { it.isISOControl() || it == '?' || it == '#' }
}

data class WebDavRemoteBook(
    val name: String,
    val path: String,
    val size: Long
)

class WebDavRemoteBookModel(
    private val library: io.legado.core.library.CoreLibrary,
    private val client: WebDavClient = JavaNetWebDavClient(),
    private val store: DesktopWebDavConfigStore,
    private val downloadDirectory: Path
) {
    private var config: DesktopWebDavConfig? = store.webDavConfig()

    fun configure(
        url: String,
        username: String,
        password: String,
        bookDirectory: String = "books"
    ): WebDavOperationResult = runCatching {
        val normalized = normalizeConfig(url, username, password, bookDirectory)
        client.listDirectory(directoryConfig(normalized))
        store.saveWebDavConfig(normalized)
        config = normalized
        WebDavOperationResult(message = "WebDAV 书库连接成功")
    }.getOrElse { WebDavOperationResult(error = it.message ?: "WebDAV 书库配置失败") }

    fun listBooks(): Result<List<WebDavRemoteBook>> = runCatching {
        val current = requireConfig()
        client.listDirectory(directoryConfig(current))
            .filter { it.name.isSupportedBookName() && !it.name.startsWith("backup", ignoreCase = true) }
            .map { file ->
                WebDavRemoteBook(
                    name = file.name,
                    path = remotePath(current, file.name),
                    size = file.size
                )
            }
            .sortedBy(WebDavRemoteBook::name)
    }

    fun upload(localFile: Path): WebDavOperationResult = runCatching {
        require(Files.isRegularFile(localFile)) { "本地书籍不存在" }
        val name = localFile.fileName.toString()
        require(name.isSupportedBookName()) { "不支持的书籍格式" }
        val current = requireConfig()
        client.put(directoryConfig(current), name, Files.readAllBytes(localFile))
        WebDavOperationResult(message = "书籍已上传：$name")
    }.getOrElse { WebDavOperationResult(error = it.message ?: "WebDAV 书籍上传失败") }

    fun download(book: WebDavRemoteBook): Result<Path> = runCatching {
        require(book.name == book.name.substringAfterLast('/') && book.name == book.name.substringAfterLast('\\')) {
            "远端书籍路径非法"
        }
        require(book.name.isSupportedBookName()) { "不支持的书籍格式" }
        val current = requireConfig()
        val destination = downloadDirectory.resolve(book.name).normalize()
        require(destination.parent == downloadDirectory.toAbsolutePath().normalize()) { "本地下载路径非法" }
        Files.createDirectories(downloadDirectory)
        Files.write(destination, client.get(directoryConfig(current), book.name))
        LocalBookImporter(library).importFile(destination)
        destination
    }

    fun delete(book: WebDavRemoteBook): WebDavOperationResult = runCatching {
        require(book.name == book.name.substringAfterLast('/') && book.name == book.name.substringAfterLast('\\')) {
            "远端书籍路径非法"
        }
        require(book.name.isSupportedBookName()) { "不支持的书籍格式" }
        client.delete(directoryConfig(requireConfig()), book.name)
        WebDavOperationResult(message = "远端书籍已删除：${book.name}")
    }.getOrElse { error ->
        WebDavOperationResult(error = error.message ?: "WebDAV 书籍删除失败")
    }

    private fun requireConfig(): DesktopWebDavConfig = config ?: error("请先配置 WebDAV 书库")

    private fun normalizeConfig(
        url: String,
        username: String,
        password: String,
        bookDirectory: String
    ): DesktopWebDavConfig {
        val base = url.trim().let { if (it.endsWith('/')) it else "$it/" }
        val uri = URI(base)
        require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            "WebDAV 地址必须使用 HTTP 或 HTTPS"
        }
        require(!uri.host.isNullOrBlank()) { "WebDAV 地址缺少主机名" }
        require(username.isNotBlank()) { "WebDAV 用户名不能为空" }
        require(password.isNotEmpty()) { "WebDAV 密码不能为空" }
        val directory = bookDirectory.trim().trim('/','\\')
        require(directory.isNotBlank() && directory != "." && directory != "..") { "书库目录不能为空" }
        require(directory.split('/', '\\').none { it == ".." || it.isBlank() }) { "书库目录非法" }
        return DesktopWebDavConfig(base, username, password, directory)
    }

    private fun directoryConfig(config: DesktopWebDavConfig): DesktopWebDavConfig =
        config.copy(url = "${config.url.trimEnd('/')}/${config.bookDirectory}/")

    private fun remotePath(config: DesktopWebDavConfig, name: String): String =
        "${config.url.trimEnd('/')}/${config.bookDirectory}/$name"

    private fun String.isSupportedBookName(): Boolean =
        substringAfterLast('.', "").lowercase() in SUPPORTED_BOOK_EXTENSIONS

    private companion object {
        val SUPPORTED_BOOK_EXTENSIONS = setOf(
            "txt", "epub", "cbz", "zip", "bmp", "gif", "jpeg", "jpg", "png", "webp",
            "wav", "aif", "aiff", "au", "snd"
        )
    }
}
