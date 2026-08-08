package io.legado.desktop

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer as JdkHttpServer
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreBookGroupIds
import io.legado.core.library.CoreLibrary
import io.legado.core.library.CoreReplaceRule
import io.legado.core.library.CoreReplacementService
import io.legado.core.source.BookSourceJsonCodec
import io.legado.core.source.BookSourceSearchService
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreHttpResponse
import io.legado.core.source.CoreHttpRequest
import io.legado.core.source.CoreSearchResult
import io.legado.core.source.CoreSourceAwareHttpClient
import io.legado.core.source.CoreSourceDebugService
import io.legado.core.source.CoreSourceHttpClient
import io.legado.core.source.CoreUrlRuleSupport
import io.legado.core.source.JavaNetHttpClient
import io.legado.core.source.OnlineBookService
import io.legado.desktop.persistence.DesktopDataDirectory
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.BindException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.concurrent.thread
import kotlin.math.roundToInt

private const val IMAGE_CONNECT_TIMEOUT_MILLIS = 10_000
private const val IMAGE_READ_TIMEOUT_MILLIS = 30_000
private const val COVER_WIDTH = 84
private const val COVER_HEIGHT = 112

private const val DESKTOP_MANAGEMENT_SUPPORTED_ROUTES_DESCRIPTION =
    "只监听本机：GET /health、GET /getBookshelf、GET /getBookSources、GET /getGroups、" +
    "GET /getBookSource、GET /getChapterList、GET /getBookContent、GET /getReadConfig、" +
    "GET /getReplaceRules、" +
        "GET /refreshToc、GET /cover、GET /image、" +
        "POST /saveBook、POST /deleteBook、POST /saveBookProgress、POST /saveReadConfig、" +
    "POST /saveBookSource、POST /saveBookSources、POST /deleteBookSources、" +
    "POST /saveReplaceRule、POST /deleteReplaceRule、POST /testReplaceRule、POST /addLocalBook、" +
    "WebSocket /searchBook、WebSocket /bookSourceDebug"

private data class AndroidSearchBookPayload(
    val name: String,
    val author: String,
    val bookUrl: String,
    val origin: String,
    val originName: String,
    val type: Int,
    val kind: String?,
    val wordCount: String?,
    val variable: String?,
    val coverUrl: String?,
    val intro: String?,
    val latestChapterTitle: String?,
    val tocUrl: String,
    val time: Long,
    val originOrder: Int,
    val chapterWordCountText: String? = null,
    val chapterWordCount: Int = -1,
    val respondTime: Int
)

private fun CoreSearchResult.toAndroidSearchBookPayload(): AndroidSearchBookPayload {
    val responseTime = source.respondTime.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
        .toInt()
    return AndroidSearchBookPayload(
        name = book.name,
        author = book.author,
        bookUrl = book.bookUrl,
        origin = book.origin,
        originName = book.originName,
        type = book.type,
        kind = book.kind,
        wordCount = book.wordCount,
        variable = book.variable,
        coverUrl = book.coverUrl,
        intro = book.intro,
        latestChapterTitle = book.latestChapterTitle,
        tocUrl = book.tocUrl,
        time = System.currentTimeMillis(),
        originOrder = book.originOrder,
        respondTime = responseTime
    )
}

/** A loopback-only bridge for the Android-compatible management contract. */
class DesktopManagementServer(
    private val library: CoreLibrary,
    port: Int = DEFAULT_PORT,
    private val gson: Gson = Gson(),
    private val uploadDirectory: Path = defaultUploadDirectory()
) : Closeable {

    private val requestedPort = port

    @Volatile
    private var httpServer: JdkHttpServer? = null
    @Volatile
    private var webSocketServer: DesktopWebSocketServer? = null
    @Volatile
    private var effectivePort: Int = requestedPort
    private var executor: ExecutorService? = null
    @Volatile
    private var webReadConfig: WebReadConfig = WebReadConfig()

    val isRunning: Boolean
        get() = httpServer != null

    val port: Int
        get() = effectivePort

    val endpoint: String
        get() = "http://127.0.0.1:$effectivePort"

    val webSocketEndpoint: String
        get() = "ws://127.0.0.1:${effectivePort + 1}"

    val supportedRoutesDescription: String = DESKTOP_MANAGEMENT_SUPPORTED_ROUTES_DESCRIPTION

    @Synchronized
    fun start() {
        if (httpServer != null) return
        require(requestedPort in 0 until MAX_PORT) { "端口必须在 0-65534 之间" }

        var lastBindError: BindException? = null
        repeat(if (requestedPort == 0) EPHEMERAL_START_ATTEMPTS else 1) { attempt ->
            val server = JdkHttpServer.create(
                InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), requestedPort),
                0
            )
            val serverExecutor = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "legado-management-http").apply { isDaemon = true }
            }
            var socketServer: DesktopWebSocketServer? = null
            try {
                server.createContext("/") { exchange -> handle(exchange) }
                server.executor = serverExecutor
                server.start()
                effectivePort = server.address.port
                require(effectivePort < MAX_PORT) { "HTTP 端口已占用 WebSocket 所需的下一个端口" }
                socketServer = DesktopWebSocketServer(
                    library = library,
                    port = effectivePort + 1,
                    gson = gson
                )
                socketServer.startServer()
                check(socketServer.boundPort == effectivePort + 1) {
                    "WebSocket 端口分配失败"
                }
                httpServer = server
                webSocketServer = socketServer
                executor = serverExecutor
                return
            } catch (error: Throwable) {
                socketServer?.stopServer()
                server.stop(0)
                serverExecutor.shutdownNow()
                if (requestedPort != 0 || !error.hasBindException() || attempt == EPHEMERAL_START_ATTEMPTS - 1) {
                    throw error
                }
                lastBindError = error.findBindException()
            }
        }
        throw lastBindError ?: BindException("无法分配本地管理服务端口")
    }

    @Synchronized
    override fun close() {
        httpServer?.stop(0)
        httpServer = null
        webSocketServer?.stopServer()
        webSocketServer = null
        executor?.shutdownNow()
        executor = null
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val response = when {
                exchange.requestMethod.equals("OPTIONS", ignoreCase = true) ->
                    Response(204, ByteArray(0), contentType = "text/plain; charset=utf-8")
                exchange.requestMethod.equals("GET", ignoreCase = true) -> {
                    routeGet(exchange.requestURI.path, parseQuery(exchange.requestURI.rawQuery))
                }
                exchange.requestMethod.equals("POST", ignoreCase = true) -> {
                    routePost(exchange.requestURI.path, readRequestBody(exchange))
                }
                else -> Response(405, failure("仅支持 GET、POST、OPTIONS 请求"))
            }
            write(exchange, response, requestOrigin(exchange))
        } catch (error: Throwable) {
            write(
                exchange,
                Response(500, failure(error.message ?: error::class.java.simpleName)),
                requestOrigin(exchange)
            )
        } finally {
            exchange.close()
        }
    }

    private fun routeGet(path: String, query: Map<String, String>): Response = when (path) {
        "/", "/index.html", "/favicon.ico", "/uploadBook/", "/uploadBook/index.html" ->
            webResource(path)
        "/health" -> Response(200, success(mapOf("status" to "running")))
        "/getBookshelf" -> bookshelfResponse(query)
        "/getBookSources" -> {
            val sources = library.sources()
            if (sources.isEmpty()) {
                Response(200, failure("设备源列表为空"))
            } else {
                Response(200, success(sources))
            }
        }
        "/getBookSource" -> getBookSource(query)
        "/getGroups" -> Response(200, success(library.groups()))
        "/getChapterList" -> {
            val bookUrl = query["bookUrl"] ?: query["url"]
            if (bookUrl.isNullOrBlank()) {
                Response(400, failure("缺少 bookUrl 参数"))
            } else {
                val chapters = library.chapters(bookUrl)
                if (chapters.isNotEmpty()) {
                    Response(200, success(chapters))
                } else {
                    refreshToc(query + ("url" to bookUrl))
                }
            }
        }
        "/getBookContent" -> {
            val bookUrl = query["url"] ?: query["bookUrl"]
            val chapterIndex = query["index"]?.toIntOrNull()
            when {
                bookUrl.isNullOrBlank() -> Response(400, failure("缺少 url 参数"))
                chapterIndex == null -> Response(400, failure("缺少有效的 index 参数"))
                else -> {
                    val book = library.book(bookUrl)
                    val chapter = library.chapters(bookUrl)
                        .firstOrNull { it.index == chapterIndex }
                    when {
                        book == null || chapter == null -> Response(404, failure("未找到"))
                        else -> runCatching {
                            OnlineBookService(library, JavaNetHttpClient()).loadContent(book, chapter)
                        }.fold(
                            onSuccess = { content -> Response(200, success(content)) },
                            onFailure = { error -> Response(200, failure(error.message ?: "获取章节正文失败")) }
                        )
                    }
                }
            }
        }
        "/getReadConfig" -> Response(
            200,
            success(library.webReadConfigJson() ?: gson.toJson(webReadConfig))
        )
        "/getReplaceRules" -> Response(200, success(gson.toJson(library.replaceRules())))
        "/refreshToc" -> refreshToc(query)
        "/cover" -> imageResponse(query, isCover = true)
        "/image" -> imageResponse(query, isCover = false)
        else -> Response(404, failure("未找到接口: $path"))
    }

    private fun bookshelfResponse(query: Map<String, String>): Response {
        val groupId = query["groupId"]?.let {
            it.toLongOrNull() ?: return Response(400, failure("groupId 参数无效"))
        }
        val allBooks = library.books().filter { book -> book.type and NOT_SHELF_BOOK_TYPE == 0 }
        val books = when (groupId) {
            null, CoreBookGroupIds.ALL -> allBooks
            CoreBookGroupIds.LOCAL -> allBooks.filter { book ->
                book.origin == "loc_book" || book.origin == "local" || book.type and LOCAL_BOOK_TYPE != 0
            }
            CoreBookGroupIds.UNGROUPED -> {
                val userGroupMask = library.groups()
                    .asSequence()
                    .filter { it.groupId > 0 }
                    .fold(0L) { mask, group -> mask or group.groupId }
                allBooks.filter { book -> book.group and userGroupMask == 0L }
            }
            CoreBookGroupIds.ERROR -> allBooks.filter { book -> book.type and UPDATE_ERROR_BOOK_TYPE != 0 }
            CoreBookGroupIds.ROOT -> {
                val userGroupMask = library.groups()
                    .asSequence()
                    .filter { it.groupId > 0 }
                    .fold(0L) { mask, group -> mask or group.groupId }
                allBooks.filter { book ->
                    book.type and TEXT_BOOK_TYPE != 0 &&
                        book.type and LOCAL_BOOK_TYPE == 0 &&
                        book.group and userGroupMask == 0L
                }
            }
            else -> if (groupId > 0) {
                allBooks.filter { book -> book.group and groupId != 0L }
            } else {
                allBooks
            }
        }
        return if (books.isEmpty()) {
            Response(200, failure("未找到"))
        } else {
            Response(200, success(books))
        }
    }

    private fun webResource(path: String): Response {
        val resourcePath = when (path) {
            "/", "/index.html" -> "web/index.html"
            "/favicon.ico" -> "web/favicon.ico"
            "/uploadBook/", "/uploadBook/index.html" -> "web/uploadBook/index.html"
            else -> return Response(404, failure("未找到资源: $path"))
        }
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: return Response(404, failure("未找到资源: $path"))
        val contentType = if (resourcePath.endsWith(".ico")) {
            "image/x-icon"
        } else {
            "text/html; charset=utf-8"
        }
        return Response(
            status = 200,
            body = stream.use { it.readBytes() },
            contentType = contentType
        )
    }

    private fun routePost(path: String, request: RequestBody): Response = when (path) {
        "/addLocalBook" -> addLocalBook(request)
        else -> {
            val body = request.text()
            when (path) {
                "/saveBook" -> saveBook(body)
                "/deleteBook" -> deleteBook(body)
                "/saveBookProgress" -> saveBookProgress(body)
                "/saveReadConfig" -> saveReadConfig(body)
                "/saveBookSource" -> saveBookSource(body)
                "/saveBookSources" -> saveBookSources(body)
                "/deleteBookSources" -> deleteBookSources(body)
                "/saveReplaceRule" -> saveReplaceRule(body)
                "/deleteReplaceRule" -> deleteReplaceRule(body)
                "/testReplaceRule" -> testReplaceRule(body)
                else -> Response(405, failure("未找到 POST 接口: $path"))
            }
        }
    }

    private fun getBookSource(query: Map<String, String>): Response {
        val url = query["url"]?.takeIf(String::isNotBlank)
            ?: return Response(400, failure("参数url不能为空，请指定源地址"))
        val source = library.source(url)
            ?: return Response(404, failure("未找到源，请检查书源地址"))
        return Response(200, success(source))
    }

    private fun saveBookSource(body: String): Response {
        val source = runCatching { BookSourceJsonCodec.decode(body).single() }
            .getOrElse { return Response(400, failure("转换源失败")) }
        if (source.bookSourceUrl.isBlank() || source.bookSourceName.isBlank()) {
            return Response(400, failure("源名称和URL不能为空"))
        }
        library.saveSource(source)
        return Response(200, success(""))
    }

    private fun saveBookSources(body: String): Response {
        val sources = runCatching { BookSourceJsonCodec.decode(body) }
            .getOrElse { return Response(400, failure("转换源失败")) }
        if (sources.isEmpty()) return Response(400, failure("转换源失败"))
        val validSources = sources.filter {
            it.bookSourceUrl.isNotBlank() && it.bookSourceName.isNotBlank()
        }
        validSources.forEach(library::saveSource)
        return Response(200, success(validSources))
    }

    private fun deleteBookSources(body: String): Response {
        val sources = runCatching { BookSourceJsonCodec.decode(body) }
            .getOrElse { return Response(400, failure("数据格式错误")) }
        sources.forEach { library.deleteSource(it.bookSourceUrl) }
        return Response(200, success("已执行"))
    }

    private fun deleteBook(body: String): Response {
        val book = runCatching { gson.fromJson(body, CoreBook::class.java) }
            .getOrNull()
            ?: return Response(400, failure("格式不对"))
        if (book.bookUrl.isBlank()) return Response(400, failure("格式不对"))
        library.deleteBook(book.bookUrl)
        return Response(200, success(""))
    }

    private fun saveReplaceRule(body: String): Response {
        val rule = runCatching { gson.fromJson(body, CoreReplaceRule::class.java) }
            .getOrNull()
            ?: return Response(400, failure("格式不对"))
        val savedRule = if (rule.order == Int.MIN_VALUE) {
            val nextOrder = library.replaceRules().maxOfOrNull { it.order }?.plus(1) ?: 0
            rule.copy(order = nextOrder)
        } else {
            rule
        }
        library.saveReplaceRule(savedRule)
        return Response(200, success(""))
    }

    private fun deleteReplaceRule(body: String): Response {
        val rule = runCatching { gson.fromJson(body, CoreReplaceRule::class.java) }
            .getOrNull()
            ?: return Response(400, failure("格式不对"))
        library.deleteReplaceRule(rule.id)
        return Response(200, success(""))
    }

    private fun testReplaceRule(body: String): Response {
        val request = runCatching { gson.fromJson(body, TestReplaceRulePayload::class.java) }
            .getOrNull()
            ?: return Response(400, failure("格式不对"))
        val rule = request.rule ?: return Response(400, failure("格式不对"))
        val text = request.text ?: return Response(400, failure("格式不对"))
        val result = runCatching { CoreReplacementService(library).test(rule, text) }
            .getOrElse { return Response(400, failure(it.message ?: "替换规则无效")) }
        return Response(200, success(result.text))
    }

    private fun refreshToc(query: Map<String, String>): Response {
        val bookUrl = query["url"]?.takeIf(String::isNotBlank)
            ?: return Response(400, failure("参数url不能为空"))
        val book = library.book(bookUrl)
            ?: return Response(404, failure("未在数据库找到对应书籍"))
        return runCatching {
            if (book.origin == "loc_book") {
                LocalBookImporter(library).importFile(Path.of(book.bookUrl), replaceExistingChapters = true)
                library.chapters(book.bookUrl)
            } else {
                OnlineBookService(library, JavaNetHttpClient()).refreshChapters(
                    book = book,
                    replaceExistingChapters = true
                )
            }
        }.fold(
            onSuccess = { chapters -> Response(200, success(chapters)) },
            onFailure = { error -> Response(200, failure(error.message ?: "刷新目录失败")) }
        )
    }

    private fun imageResponse(query: Map<String, String>, isCover: Boolean): Response {
        if (!isCover) {
            val bookUrl = query["url"]?.takeIf(String::isNotBlank)
                ?: return Response(400, failure("bookUrl为空"))
            if (library.book(bookUrl) == null) {
                return Response(404, failure("未在数据库找到对应书籍"))
            }
        }
        val path = query["path"]?.takeIf(String::isNotBlank)
            ?: return Response(400, failure("图片路径不能为空"))
        val source = runCatching { readImage(path) }
            .getOrElse { return Response(404, failure("图片读取失败：${it.message ?: "无效图片"}")) }
        val width = query["width"]?.toIntOrNull()?.takeIf { it > 0 }
        val image = if (isCover) resizeCover(source) else resizeToWidth(source, width)
        val output = java.io.ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return Response(200, output.toByteArray(), "image/png")
    }

    private fun addLocalBook(request: RequestBody): Response {
        val multipart = request.multipart()
            ?: return Response(400, failure("请求必须为 multipart/form-data"))
        val fileName = multipart.fields["fileName"]?.takeIf(String::isNotBlank)
            ?: multipart.file?.fileName?.takeIf(String::isNotBlank)
            ?: return Response(400, failure("fileName 不能为空"))
        val fileData = multipart.file?.data
            ?: return Response(400, failure("fileData 不能为空"))
        if (fileData.isEmpty()) return Response(400, failure("fileData 不能为空"))
        val safeName = Path.of(fileName).fileName.toString()
        if (safeName.isBlank() || safeName == "." || safeName == "..") {
            return Response(400, failure("文件名无效"))
        }
        val uploadRoot = uploadDirectory.toAbsolutePath().normalize()
        val target = uploadRoot.resolve(safeName).normalize()
        require(target.parent == uploadRoot) { "文件名无效" }
        Files.createDirectories(uploadRoot)
        Files.write(target, fileData)
        return runCatching {
            LocalBookImporter(library).importFile(target)
            Response(200, success(""))
        }.getOrElse { error ->
            Response(400, failure(error.message ?: "导入本地书籍失败"))
        }
    }

    private fun saveBook(body: String): Response {
        val element = try {
            JsonParser.parseString(body)
        } catch (_: JsonParseException) {
            return Response(400, failure("格式不对"))
        }
        if (!element.isJsonObject) return Response(400, failure("格式不对"))

        val bookUrl = element.asJsonObject.get("bookUrl")
        if (bookUrl == null || !bookUrl.isJsonPrimitive || !bookUrl.asJsonPrimitive.isString) {
            return Response(400, failure("bookUrl 不能为空"))
        }
        if (bookUrl.asString.isBlank()) return Response(400, failure("bookUrl 不能为空"))

        val book = try {
            gson.fromJson(element, CoreBook::class.java)
        } catch (_: JsonSyntaxException) {
            null
        } ?: return Response(400, failure("格式不对"))

        library.saveBook(book)
        return Response(200, success(""))
    }

    private fun saveReadConfig(body: String): Response {
        val config = try {
            gson.fromJson(body, WebReadConfigPayload::class.java)
        } catch (_: JsonSyntaxException) {
            null
        } ?: return Response(400, failure("阅读配置格式不对"))

        val validated = runCatching { config.validated() }
            .getOrElse { return Response(400, failure("阅读配置无效：${it.message}")) }
        webReadConfig = validated
        library.saveWebReadConfigJson(gson.toJson(validated))
        return Response(200, success(""))
    }

    private fun saveBookProgress(body: String): Response {
        val progress = try {
            gson.fromJson(body, BookProgressPayload::class.java)
        } catch (_: JsonSyntaxException) {
            null
        } ?: return Response(400, failure("格式不对"))

        val name = progress.name?.takeIf { it.isNotBlank() }
            ?: return Response(400, failure("缺少 name 或 author 字段"))
        val author = progress.author?.takeIf { it.isNotBlank() }
            ?: return Response(400, failure("缺少 name 或 author 字段"))
        val chapterIndex = progress.durChapterIndex
            ?: return Response(400, failure("缺少阅读进度字段"))
        val chapterPos = progress.durChapterPos
            ?: return Response(400, failure("缺少阅读进度字段"))
        val chapterTime = progress.durChapterTime
            ?: return Response(400, failure("缺少阅读进度字段"))
        val book = library.books().firstOrNull { it.name == name && it.author == author }
            ?: return Response(404, failure("未找到书籍"))

        library.saveBook(
            book.copy(
                durChapterIndex = chapterIndex,
                durChapterPos = chapterPos,
                durChapterTitle = progress.durChapterTitle,
                durChapterTime = chapterTime
            )
        )
        return Response(200, success(""))
    }

    private fun readRequestBody(exchange: HttpExchange): RequestBody = RequestBody(
        contentType = exchange.requestHeaders.getFirst("Content-Type").orEmpty(),
        bytes = exchange.requestBody.use { it.readBytes() }
    )

    private fun write(exchange: HttpExchange, response: Response, origin: String?) {
        exchange.responseHeaders.set("Content-Type", response.contentType)
        exchange.responseHeaders.set("Access-Control-Allow-Origin", origin ?: "*")
        exchange.responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        exchange.responseHeaders.set("Access-Control-Allow-Headers", "content-type")
        exchange.responseHeaders.set("Access-Control-Max-Age", "3600")
        exchange.sendResponseHeaders(response.status, response.body.size.toLong())
        exchange.responseBody.use { output -> output.write(response.body) }
    }

    private fun requestOrigin(exchange: HttpExchange): String? =
        exchange.requestHeaders.getFirst("Origin")?.takeIf { it.isNotBlank() }

    private fun readImage(path: String): BufferedImage {
        val input = if (path.startsWith("http://") || path.startsWith("https://")) {
            val connection = URL(path).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = IMAGE_CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = IMAGE_READ_TIMEOUT_MILLIS
                require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
                connection.inputStream.use { ImageIO.read(it) ?: error("不支持的图片格式") }
            } finally {
                connection.disconnect()
            }
        } else {
            Files.newInputStream(Path.of(path)).use { ImageIO.read(it) ?: error("不支持的图片格式") }
        }
        return input
    }

    private fun resizeCover(source: BufferedImage): BufferedImage = resize(source, COVER_WIDTH, COVER_HEIGHT)

    private fun resizeToWidth(source: BufferedImage, width: Int?): BufferedImage {
        val targetWidth = width ?: source.width
        if (targetWidth >= source.width) return source
        val targetHeight = (source.height.toDouble() * targetWidth / source.width).roundToInt().coerceAtLeast(1)
        return resize(source, targetWidth, targetHeight)
    }

    private fun resize(source: BufferedImage, targetWidth: Int, targetHeight: Int): BufferedImage {
        val result = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = result.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null)
        graphics.dispose()
        return result
    }

    private fun success(data: Any): String = gson.toJson(
        ManagementReturnData(isSuccess = true, errorMsg = "", data = data)
    )

    private fun failure(message: String): String = gson.toJson(
        ManagementReturnData(isSuccess = false, errorMsg = message, data = null)
    )

    private fun parseQuery(rawQuery: String?): Map<String, String> = rawQuery
        ?.split('&')
        ?.mapNotNull { component ->
            val parts = component.split('=', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            URLDecoder.decode(parts[0], StandardCharsets.UTF_8) to
                URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
        }
        ?.toMap()
        ?: emptyMap()

    private data class Response(
        val status: Int,
        val body: ByteArray,
        val contentType: String
    ) {
        constructor(
            status: Int,
            body: String,
            contentType: String = "application/json; charset=utf-8"
        ) : this(status, body.toByteArray(StandardCharsets.UTF_8), contentType)
    }

    private data class ManagementReturnData(
        val isSuccess: Boolean,
        val errorMsg: String,
        val data: Any?
    )

    private data class RequestBody(
        val contentType: String,
        val bytes: ByteArray
    ) {
        fun text(): String = bytes.toString(StandardCharsets.UTF_8)

        fun multipart(): MultipartRequest? {
            val boundary = contentType.parameter("boundary")?.takeIf(String::isNotBlank)
                ?: return null
            val marker = "--$boundary".toByteArray(StandardCharsets.ISO_8859_1)
            val parts = bytes.splitBytes(marker)
            val fields = linkedMapOf<String, String>()
            var file: MultipartFile? = null
            parts.drop(1).forEach { rawPart ->
                val part = rawPart.trimCrlf()
                if (part.isEmpty() || part.contentEquals("--".toByteArray())) return@forEach
                val headerEnd = part.indexOfBytes("\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
                if (headerEnd < 0) return@forEach
                val headers = String(part.copyOfRange(0, headerEnd), StandardCharsets.ISO_8859_1)
                    .lineSequence()
                    .mapNotNull { line ->
                        val separator = line.indexOf(':')
                        if (separator <= 0) null
                        else line.substring(0, separator).lowercase() to line.substring(separator + 1).trim()
                    }.toMap()
                val disposition = headers["content-disposition"].orEmpty()
                val name = disposition.parameter("name") ?: return@forEach
                val data = part.copyOfRange(headerEnd + 4, part.size).trimCrlf()
                val fileName = disposition.parameter("filename")
                if (fileName != null) file = MultipartFile(fileName, data)
                else fields[name] = String(data, StandardCharsets.UTF_8)
            }
            return MultipartRequest(fields, file)
        }
    }

    private data class MultipartRequest(
        val fields: Map<String, String>,
        val file: MultipartFile?
    )

    private data class MultipartFile(val fileName: String, val data: ByteArray)

    private data class TestReplaceRulePayload(
        val rule: CoreReplaceRule?,
        val text: String?
    )

    private data class BookProgressPayload(
        val name: String?,
        val author: String?,
        val durChapterIndex: Int?,
        val durChapterPos: Int?,
        val durChapterTime: Long?,
        val durChapterTitle: String?
    )

    private data class WebReadConfig(
        val theme: Int = 0,
        val font: Int = 0,
        val fontSize: Int = 18,
        val readWidth: Int = 800,
        val infiniteLoading: Boolean = false,
        val customFontName: String = "",
        val jumpDuration: Int = 1000,
        val spacing: WebReadSpacing = WebReadSpacing()
    )

    private data class WebReadSpacing(
        val paragraph: Double = 1.0,
        val line: Double = 0.8,
        val letter: Double = 0.0
    )

    private data class WebReadConfigPayload(
        val theme: Int?,
        val font: Int?,
        val fontSize: Int?,
        val readWidth: Int?,
        val infiniteLoading: Boolean?,
        val customFontName: String?,
        val jumpDuration: Int?,
        val spacing: WebReadSpacingPayload?
    ) {
        fun validated(): WebReadConfig {
            val checkedTheme = requireValue(theme, "theme") { it in 0..6 }
            val checkedFont = requireValue(font, "font") { it in 0..2 }
            val checkedFontSize = requireValue(fontSize, "fontSize") { it in 8..72 }
            val checkedReadWidth = requireValue(readWidth, "readWidth") { it in 0..2_000 }
            val checkedInfiniteLoading = infiniteLoading ?: error("缺少 infiniteLoading")
            val checkedCustomFontName = customFontName ?: error("缺少 customFontName")
            require(checkedCustomFontName.length <= 256) { "customFontName 超出长度限制" }
            val checkedJumpDuration = requireValue(jumpDuration, "jumpDuration") { it in 0..60_000 }
            val checkedSpacing = spacing ?: error("缺少 spacing")
            return WebReadConfig(
                theme = checkedTheme,
                font = checkedFont,
                fontSize = checkedFontSize,
                readWidth = checkedReadWidth,
                infiniteLoading = checkedInfiniteLoading,
                customFontName = checkedCustomFontName,
                jumpDuration = checkedJumpDuration,
                spacing = checkedSpacing.validated()
            )
        }

        private fun <T> requireValue(value: T?, name: String, predicate: (T) -> Boolean): T {
            require(value != null) { "缺少 $name" }
            require(predicate(value)) { "$name 超出范围" }
            return value
        }
    }

    private data class WebReadSpacingPayload(
        val paragraph: Double?,
        val line: Double?,
        val letter: Double?
    ) {
        fun validated(): WebReadSpacing {
            val checkedParagraph = requireValue(paragraph, "spacing.paragraph") { it in 0.0..10.0 }
            val checkedLine = requireValue(line, "spacing.line") { it in 0.0..5.0 }
            val checkedLetter = requireValue(letter, "spacing.letter") { it in -10.0..10.0 }
            return WebReadSpacing(checkedParagraph, checkedLine, checkedLetter)
        }

        private fun <T> requireValue(value: T?, name: String, predicate: (T) -> Boolean): T {
            require(value != null) { "缺少 $name" }
            require(predicate(value)) { "$name 超出范围" }
            return value
        }
    }

    private companion object {
        const val DEFAULT_PORT = 1122
        const val LOOPBACK_HOST = "127.0.0.1"
        const val MAX_PORT = 65535
        const val EPHEMERAL_START_ATTEMPTS = 3
        const val TEXT_BOOK_TYPE = 1 shl 3
        const val UPDATE_ERROR_BOOK_TYPE = 1 shl 4
        const val LOCAL_BOOK_TYPE = 1 shl 8
        const val NOT_SHELF_BOOK_TYPE = 1 shl 10
    }
}

private fun defaultUploadDirectory(): Path = DesktopDataDirectory.resolve().resolve("uploads")

private fun String.parameter(name: String): String? = split(';')
    .asSequence()
    .map { it.trim() }
    .mapNotNull { parameter ->
        val separator = parameter.indexOf('=')
        if (separator <= 0 || !parameter.substring(0, separator).trim().equals(name, ignoreCase = true)) {
            null
        } else {
            parameter.substring(separator + 1).trim().trim('"')
        }
    }
    .firstOrNull()

private fun ByteArray.splitBytes(delimiter: ByteArray): List<ByteArray> {
    require(delimiter.isNotEmpty())
    val result = mutableListOf<ByteArray>()
    var start = 0
    while (start <= size) {
        val delimiterStart = indexOfBytes(delimiter, start)
        if (delimiterStart < 0) {
            result += copyOfRange(start, size)
            break
        }
        result += copyOfRange(start, delimiterStart)
        start = delimiterStart + delimiter.size
    }
    return result
}

private fun ByteArray.indexOfBytes(needle: ByteArray, startIndex: Int = 0): Int {
    if (needle.isEmpty()) return startIndex.coerceIn(0, size)
    val first = needle[0]
    val lastStart = size - needle.size
    for (index in startIndex.coerceAtLeast(0)..lastStart) {
        if (this[index] != first) continue
        var offset = 1
        while (offset < needle.size && this[index + offset] == needle[offset]) offset++
        if (offset == needle.size) return index
    }
    return -1
}

private fun ByteArray.trimCrlf(): ByteArray {
    var start = 0
    var end = size
    if (end - start >= 2 && this[start] == '\r'.code.toByte() && this[start + 1] == '\n'.code.toByte()) {
        start += 2
    }
    if (end - start >= 2 && this[end - 2] == '\r'.code.toByte() && this[end - 1] == '\n'.code.toByte()) {
        end -= 2
    }
    return copyOfRange(start, end)
}

private class DesktopWebSocketServer(
    private val library: CoreLibrary,
    port: Int,
    private val gson: Gson
) : NanoWSD("127.0.0.1", port) {

    private val searchService = BookSourceSearchService(library, JavaNetHttpClient())
    private val debugService = DesktopSourceDebugService(library, gson)

    val boundPort: Int
        get() = boundPortValue

    @Volatile
    private var boundPortValue: Int = port

    fun startServer() {
        super.start()
        boundPortValue = getListeningPort()
    }

    fun stopServer() {
        if (wasStarted()) super.stop()
    }

    override fun openWebSocket(handshake: NanoHTTPD.IHTTPSession): NanoWSD.WebSocket? = when (handshake.uri) {
        "/searchBook" -> SearchSocket(handshake, searchService, gson)
        "/bookSourceDebug" -> DebugSocket(handshake, debugService, gson)
        else -> null
    }

    private class SearchSocket(
        handshake: NanoHTTPD.IHTTPSession,
        private val searchService: BookSourceSearchService,
        private val gson: Gson
    ) : NanoWSD.WebSocket(handshake) {
        override fun onOpen() = Unit

        override fun onClose(
            code: NanoWSD.WebSocketFrame.CloseCode,
            reason: String,
            initiatedByRemote: Boolean
        ) = Unit

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            thread(isDaemon = true, name = "legado-management-search") {
                try {
                    val payload = parsePayload(message.textPayload)
                    val key = payload["key"]?.takeIf(String::isNotBlank)
                        ?: return@thread closeWithMessage("搜索关键字不能为空")
                    send(gson.toJson(searchService.search(key).map(CoreSearchResult::toAndroidSearchBookPayload)))
                    close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "Search finish", false)
                } catch (error: Throwable) {
                    closeWithMessage(error.message ?: "搜索失败")
                }
            }
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame) = Unit

        override fun onException(exception: IOException) = Unit

        private fun closeWithMessage(message: String) {
            runCatching {
                send(message)
                close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "Search finish", false)
            }
        }
    }

    private class DebugSocket(
        handshake: NanoHTTPD.IHTTPSession,
        private val debugService: DesktopSourceDebugService,
        private val gson: Gson
    ) : NanoWSD.WebSocket(handshake) {
        override fun onOpen() = Unit

        override fun onClose(
            code: NanoWSD.WebSocketFrame.CloseCode,
            reason: String,
            initiatedByRemote: Boolean
        ) = Unit

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            thread(isDaemon = true, name = "legado-management-debug") {
                try {
                    val payload = parsePayload(message.textPayload)
                    val tag = payload["tag"]?.takeIf(String::isNotBlank)
                    val key = payload["key"]?.takeIf(String::isNotBlank)
                    if (tag == null || key == null) {
                        return@thread closeWithMessage("书源地址和关键字不能为空")
                    }
                    debugService.execute(tag, key).forEach(::send)
                    close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "调试结束", false)
                } catch (error: Throwable) {
                    closeWithMessage(error.message ?: "调试失败")
                }
            }
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame) = Unit

        override fun onException(exception: IOException) = Unit

        private fun closeWithMessage(message: String) {
            runCatching {
                send(message)
                close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "调试结束", false)
            }
        }
    }

    private companion object {
        fun parsePayload(raw: String): Map<String, String> {
            val element = com.google.gson.JsonParser.parseString(raw)
            require(element.isJsonObject) { "数据必须为Json格式" }
            return element.asJsonObject.entrySet().associate { (key, value) ->
                key to value.takeUnless { it.isJsonNull }?.asString.orEmpty()
            }
        }
    }
}

private fun Throwable.hasBindException(): Boolean = findBindException() != null

private fun Throwable.findBindException(): BindException? {
    var current: Throwable? = this
    while (current != null) {
        if (current is BindException) return current
        current = current.cause
    }
    return null
}

private class DesktopSourceDebugService(
    private val library: CoreLibrary,
    private val gson: Gson,
    private val httpClient: CoreHttpClient = JavaNetHttpClient()
) {
    private val sourceDebugService = CoreSourceDebugService()

    fun execute(sourceUrl: String, keyword: String): List<String> {
        val source = library.source(sourceUrl) ?: error("未找到书源: $sourceUrl")
        val searchUrl = source.searchUrl?.takeIf(String::isNotBlank)
            ?: error("书源未配置搜索地址")
        val resolved = CoreUrlRuleSupport.resolve(
            source = source,
            rawUrl = searchUrl,
            keyword = keyword,
            page = 1,
            ruleField = "searchUrl",
            library = library
        )
        val headers = sourceDebugService.sourceHeaders(
            source = source,
            baseUrl = resolved.url,
            bindings = mapOf("key" to keyword, "keyword" to keyword, "page" to 1),
            library = library
        )
        val request = CoreHttpRequest(
            url = resolved.requestUrl,
            method = resolved.method,
            headers = CoreUrlRuleSupport.mergeHeaders(headers, resolved.headers),
            body = resolved.body,
            charset = resolved.charset
        )
        val response = if (httpClient is CoreSourceAwareHttpClient) {
            httpClient.request(source, request)
        } else {
            CoreSourceHttpClient(library, httpClient).request(source, request)
        }
        val sourceLabel = source.bookSourceName.ifBlank { source.bookSourceUrl }
        return buildList {
            add(gson.toJson(mapOf("type" to "content", "content" to "书源：$sourceLabel")))
            add(gson.toJson(mapOf("type" to "content", "content" to "请求：${request.url}")))
            add(gson.toJson(mapOf("type" to "content", "content" to "请求方法：${request.method}")))
            if (request.headers.isNotEmpty()) {
                add(
                    gson.toJson(
                        mapOf(
                            "type" to "content",
                            "content" to request.headers.entries.joinToString("\n") { (name, value) ->
                                "$name: $value"
                            }
                        )
                    )
                )
            }
            request.body?.let { body ->
                add(gson.toJson(mapOf("type" to "content", "content" to "请求 body：$body")))
            }
            add(gson.toJson(mapOf("type" to "content", "content" to "状态码：${response.statusCode}")))
            if (response.body.isNotBlank()) {
                add(gson.toJson(mapOf("type" to "content", "content" to response.body)))
            }
            if (response.statusCode !in 200..299) {
                add(gson.toJson(mapOf("type" to "error", "msg" to "HTTP ${response.statusCode}")))
            }
        }
    }
}

class DesktopManagementServerModel(
    private val library: CoreLibrary,
    defaultPort: Int = 1122
) {
    var portInput: String = defaultPort.toString()
        private set

    var message: String = ""
        private set

    var endpoint: String = ""
        private set

    var webSocketEndpoint: String = ""
        private set

    val supportedRoutesDescription: String = DESKTOP_MANAGEMENT_SUPPORTED_ROUTES_DESCRIPTION

    val isRunning: Boolean
        get() = server?.isRunning == true

    private var server: DesktopManagementServer? = null

    fun updatePort(value: String) {
        portInput = value
        if (!isRunning) message = ""
    }

    fun start() {
        if (isRunning) return
        val port = portInput.trim().toIntOrNull()
        if (port == null || port !in 0 until 65535) {
            endpoint = ""
            webSocketEndpoint = ""
            message = "端口必须在 0-65534 之间"
            return
        }
        runCatching {
            DesktopManagementServer(library, port).also { it.start() }
        }.onSuccess { started ->
            server = started
            endpoint = started.endpoint
            webSocketEndpoint = started.webSocketEndpoint
            message = "本地管理服务已启动"
        }.onFailure { error ->
            endpoint = ""
            webSocketEndpoint = ""
            message = "本地管理服务启动失败：${error.message ?: error::class.java.simpleName}"
        }
    }

    fun stop() {
        server?.close()
        server = null
        endpoint = ""
        webSocketEndpoint = ""
        message = "本地管理服务已停止"
    }
}
