package io.legado.desktop

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.legado.core.library.CoreBook
import io.legado.core.library.CoreChapter
import io.legado.core.library.CoreTxtTocRule
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node
import org.w3c.dom.NodeList

data class ParsedLocalChapter(
    val chapter: CoreChapter,
    val content: String
)

data class ParsedLocalBook(
    val book: CoreBook,
    val chapters: List<ParsedLocalChapter>
)

object LocalBookParser {

    fun parse(path: Path, tocRules: List<CoreTxtTocRule>? = null): ParsedLocalBook {
        val normalizedPath = path.toAbsolutePath().normalize()
        require(Files.isRegularFile(normalizedPath)) { "Local book does not exist: $normalizedPath" }

        return if (normalizedPath.fileName.toString().endsWith(".epub", ignoreCase = true)) {
            parseEpub(normalizedPath)
        } else {
            parseText(normalizedPath, tocRules)
        }
    }

    private fun parseText(path: Path, tocRules: List<CoreTxtTocRule>?): ParsedLocalBook {
        val decoded = decodeText(Files.readAllBytes(path))
        val normalized = normalizeLineEndings(decoded.text, decoded.byteOffsets)
        val bookUrl = path.toString()
        val bookName = path.fileName.toString().substringBeforeLast('.', path.fileName.toString())
        val sections = splitTextIntoChapters(normalized, bookUrl, tocRules)
        return ParsedLocalBook(
            book = CoreBook(
                bookUrl = bookUrl,
                name = bookName,
                origin = "loc_book",
                originName = "本地文件",
                charset = decoded.charset.name(),
                totalChapterNum = sections.size
            ),
            chapters = sections
        )
    }

    private fun parseEpub(path: Path): ParsedLocalBook = ZipFile(path.toFile()).use { zip ->
        val container = zip.readEntry("META-INF/container.xml")
        val rootFile = firstElement(container, "rootfile")?.attributes?.getNamedItem("full-path")?.nodeValue
            ?: error("EPUB container.xml does not define a rootfile")
        val opfPath = rootFile.replace('\\', '/').trimStart('/')
        val opf = zip.readEntry(opfPath)
        val metadata = firstElement(opf, "metadata") ?: opf.documentElement
        val manifest = firstElement(opf, "manifest")
        val manifestItems = childElements(manifest, "item").associateBy(
            keySelector = { it.attributes?.getNamedItem("id")?.nodeValue.orEmpty() },
            valueTransform = { element ->
                ManifestItem(
                    href = element.attributes?.getNamedItem("href")?.nodeValue.orEmpty(),
                    mediaType = element.attributes?.getNamedItem("media-type")?.nodeValue.orEmpty()
                )
            }
        ).filterKeys(String::isNotEmpty)
        val spine = firstElement(opf, "spine")
        val bookUrl = path.toString()
        val baseDirectory = Path.of(opfPath).parent ?: Path.of("")
        val chapters = childElements(spine, "itemref").mapIndexedNotNull { index, itemref ->
            val id = itemref.attributes?.getNamedItem("idref")?.nodeValue ?: return@mapIndexedNotNull null
            val manifestItem = manifestItems[id] ?: return@mapIndexedNotNull null
            if (!manifestItem.isDocument) return@mapIndexedNotNull null

            val entryPath = resolveZipPath(baseDirectory, manifestItem.href)
            val entry = zip.getEntry(entryPath) ?: return@mapIndexedNotNull null
            val document = zip.getInputStream(entry).use { input -> parseXml(input.readBytes()) }
            val title = firstHeadingText(document) ?: Path.of(entryPath).fileName.toString()
                .substringBeforeLast('.', Path.of(entryPath).fileName.toString())
            val content = documentText(document).removeLeadingTitle(title)
            ParsedLocalChapter(
                chapter = CoreChapter(
                    bookUrl = bookUrl,
                    url = "$bookUrl#chapter-$index",
                    title = title,
                    index = index
                ),
                content = content
            )
        }
        val bookName = firstMetadataText(metadata, "title")
            ?: path.fileName.toString().substringBeforeLast('.', path.fileName.toString())
        val author = firstMetadataText(metadata, "creator").orEmpty()
        ParsedLocalBook(
            book = CoreBook(
                bookUrl = bookUrl,
                name = bookName,
                author = author,
                origin = "loc_book",
                originName = "本地文件",
                charset = "UTF-8",
                totalChapterNum = chapters.size
            ),
            chapters = chapters
        )
    }

    private fun splitTextIntoChapters(
        source: TextWithOffsets,
        bookUrl: String,
        tocRules: List<CoreTxtTocRule>?
    ): List<ParsedLocalChapter> {
        val normalizedText = source.text
        val pattern = selectTocPattern(normalizedText, tocRules)
        if (pattern == null) {
            return listOf(
                ParsedLocalChapter(
                    chapter = CoreChapter(
                        bookUrl,
                        "$bookUrl#chapter-0",
                        "正文",
                        0,
                        start = source.byteOffsets.first(),
                        end = source.byteOffsets.last()
                    ),
                    content = normalizedText.trim()
                )
            )
        }

        val matches = pattern.matcher(normalizedText).run {
            buildList {
                while (find()) {
                    if (group().trim().isNotEmpty()) add(Match(start(), end(), group()))
                }
            }
        }
        if (matches.isEmpty()) {
            return listOf(
                ParsedLocalChapter(
                    chapter = CoreChapter(
                        bookUrl,
                        "$bookUrl#chapter-0",
                        "正文",
                        0,
                        start = source.byteOffsets.first(),
                        end = source.byteOffsets.last()
                    ),
                    content = normalizedText.trim()
                )
            )
        }

        val leadingText = normalizedText.substring(0, matches.first().start).trim()
        val chapters = matches.mapIndexed { position, match ->
            val nextHeading = matches.getOrNull(position + 1)?.start ?: normalizedText.length
            val title = match.title.trim()
            val body = normalizedText.substring(match.end, nextHeading).trim()
            val chapterStart = if (position == 0 && leadingText.isNotEmpty()) {
                source.byteOffsets.first()
            } else {
                source.byteOffsets[match.start]
            }
            ParsedLocalChapter(
                chapter = CoreChapter(
                    bookUrl,
                    "$bookUrl#chapter-$position",
                    title,
                    position,
                    start = chapterStart,
                    end = source.byteOffsets[nextHeading]
                ),
                content = body
            )
        }.toMutableList()
        if (leadingText.isNotEmpty()) {
            val first = chapters.first()
            chapters[0] = first.copy(content = "$leadingText\n\n${first.content}".trim())
        }
        return chapters
    }

    /** Reads a local TXT chapter using its Android-compatible byte range. */
    fun readTextChapter(path: Path, chapter: CoreChapter, charsetName: String? = null): String {
        val start = requireNotNull(chapter.start) { "TXT chapter start is missing" }
        val end = requireNotNull(chapter.end) { "TXT chapter end is missing" }
        require(start >= 0L && end >= start) { "Invalid TXT chapter range: $start..$end" }
        require(end - start <= Int.MAX_VALUE) { "TXT chapter is too large to read as a String" }

        val normalizedPath = path.toAbsolutePath().normalize()
        require(Files.isRegularFile(normalizedPath)) { "Local book does not exist: $normalizedPath" }
        val size = Files.size(normalizedPath)
        require(end <= size) { "TXT chapter range exceeds file size: $end > $size" }
        val bytes = ByteArray((end - start).toInt())
        Files.newByteChannel(normalizedPath).use { channel ->
            channel.position(start)
            var offset = 0
            while (offset < bytes.size) {
                val read = channel.read(java.nio.ByteBuffer.wrap(bytes, offset, bytes.size - offset))
                if (read < 0) break
                offset += read
            }
            require(offset == bytes.size) { "Unable to read TXT chapter range" }
        }

        val charset = charsetName?.let { Charset.forName(it) } ?: detectCharset(bytes)
        val text = String(bytes, charset)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (chapter.title == "正文") return text
        val lines = text.lines()
        val titleIndex = lines.indexOfFirst { it.trim() == chapter.title.trim() }
        if (titleIndex < 0) return text
        return lines.mapIndexed { index, line ->
            if (index == titleIndex) "" else line
        }
            .joinToString("\n")
            .trim()
    }

    private fun selectTocPattern(text: String, tocRules: List<CoreTxtTocRule>?): Pattern? {
        val rules = (tocRules ?: defaultTxtTocRules()).filter(CoreTxtTocRule::enable)
        var maxMatches = 1
        var selected: Pattern? = null
        rules.asReversed().forEach { rule ->
            if (rule.rule.isBlank()) return@forEach
            val pattern = try {
                Pattern.compile(rule.rule, Pattern.MULTILINE)
            } catch (_: PatternSyntaxException) {
                return@forEach
            }
            val matcher = pattern.matcher(text)
            var lastMatchEnd = 0
            var matches = 0
            while (matcher.find()) {
                if (lastMatchEnd == 0 || matcher.start() - lastMatchEnd > 1000) {
                    matches++
                    lastMatchEnd = matcher.end()
                }
            }
            if (matches >= maxMatches) {
                maxMatches = matches
                selected = pattern
            }
        }
        return selected
    }

    private fun defaultTxtTocRules(): List<CoreTxtTocRule> = runCatching {
        val type = object : TypeToken<List<CoreTxtTocRule>>() {}.type
        LocalBookParser::class.java.getResourceAsStream("/defaultData/txtTocRule.json")?.use { input ->
            Gson().fromJson<List<CoreTxtTocRule>>(input.reader(Charsets.UTF_8), type)
        } ?: emptyList()
    }.getOrDefault(
        listOf(
            CoreTxtTocRule(
                id = -1L,
                name = "中文章节",
                rule = "^[ \\t　]{0,4}第[0-9零一二两三四五六七八九十百千万]+[章节回集卷部篇].{0,30}$",
                serialNumber = 0
            ),
            CoreTxtTocRule(
                id = -2L,
                name = "英文章节",
                rule = "^[ \\t　]{0,4}(?:[Cc]hapter|[Ss]ection|[Pp]art)\\s{0,4}\\d{1,4}.{0,30}$",
                serialNumber = 1
            ),
            CoreTxtTocRule(
                id = -3L,
                name = "数字分隔符章节",
                rule = "^[ \\t　]{0,4}\\d{1,5}[:：,.， 、_—\\-].{1,30}$",
                serialNumber = 2
            )
        )
    )

    private fun decodeText(bytes: ByteArray): DecodedText {
        if (bytes.startsWith(Constants.UTF8_BOM)) {
            return decodedText(bytes, Constants.UTF8_BOM.size, StandardCharsets.UTF_8)
        }
        if (bytes.startsWith(Constants.UTF16_LE_BOM)) {
            return decodedText(bytes, Constants.UTF16_LE_BOM.size, StandardCharsets.UTF_16LE)
        }
        if (bytes.startsWith(Constants.UTF16_BE_BOM)) {
            return decodedText(bytes, Constants.UTF16_BE_BOM.size, StandardCharsets.UTF_16BE)
        }
        return try {
            decodedText(bytes, 0, StandardCharsets.UTF_8)
        } catch (_: CharacterCodingException) {
            val fallback = Charset.forName("GB18030")
            decodedText(bytes, 0, fallback)
        }
    }

    private fun detectCharset(bytes: ByteArray): Charset = when {
        bytes.startsWith(Constants.UTF8_BOM) -> StandardCharsets.UTF_8
        bytes.startsWith(Constants.UTF16_LE_BOM) -> StandardCharsets.UTF_16LE
        bytes.startsWith(Constants.UTF16_BE_BOM) -> StandardCharsets.UTF_16BE
        else -> try {
            decodeStrict(bytes, StandardCharsets.UTF_8)
            StandardCharsets.UTF_8
        } catch (_: CharacterCodingException) {
            Charset.forName("GB18030")
        }
    }

    private fun decodedText(bytes: ByteArray, contentStart: Int, charset: Charset): DecodedText {
        val text = decodeStrict(
            bytes.copyOfRange(contentStart, bytes.size),
            charset
        )
        val offsets = LongArray(text.length + 1)
        var charIndex = 0
        var byteOffset = contentStart.toLong()
        while (charIndex < text.length) {
            val codePoint = text.codePointAt(charIndex)
            val charCount = Character.charCount(codePoint)
            val encodedLength = String(Character.toChars(codePoint)).toByteArray(charset).size
            repeat(charCount) { offset -> offsets[charIndex + offset] = byteOffset }
            charIndex += charCount
            byteOffset += encodedLength
        }
        offsets[text.length] = byteOffset
        return DecodedText(text, charset, offsets)
    }

    private fun normalizeLineEndings(text: String, byteOffsets: LongArray): TextWithOffsets {
        val normalized = StringBuilder(text.length)
        val offsets = ArrayList<Long>(text.length + 1)
        offsets += byteOffsets[0]
        var index = 0
        while (index < text.length) {
            val current = text[index]
            if (current == '\r') {
                normalized.append('\n')
                if (index + 1 < text.length && text[index + 1] == '\n') {
                    index += 2
                    offsets += byteOffsets[index]
                } else {
                    index++
                    offsets += byteOffsets[index]
                }
            } else {
                normalized.append(current)
                index++
                offsets += byteOffsets[index]
            }
        }
        return TextWithOffsets(normalized.toString(), offsets.toLongArray())
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document = secureDocumentBuilderFactory()
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(bytes))

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }

    private fun ZipFile.readEntry(path: String): org.w3c.dom.Document {
        val entry = getEntry(path) ?: error("EPUB entry is missing: $path")
        return getInputStream(entry).use { input -> parseXml(input.readBytes()) }
    }

    private fun firstElement(node: Node?, name: String): Node? = node?.childNodes?.let { children ->
        (0 until children.length).asSequence()
            .map(children::item)
            .firstOrNull { it.nodeType == Node.ELEMENT_NODE && localName(it) == name }
            ?: (0 until children.length).asSequence()
                .map(children::item)
                .mapNotNull { firstElement(it, name) }
                .firstOrNull()
    }

    private fun childElements(node: Node?, name: String): List<Node> = node?.childNodes?.let { children ->
        (0 until children.length).asSequence()
            .map(children::item)
            .filter { it.nodeType == Node.ELEMENT_NODE && localName(it) == name }
            .toList()
    } ?: emptyList()

    private fun firstMetadataText(node: Node?, name: String): String? = firstElement(node, name)
        ?.textContent
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private fun firstHeadingText(document: org.w3c.dom.Document): String? = (1..6)
        .asSequence()
        .mapNotNull { level -> firstElement(document, "h$level")?.textContent?.cleanInlineText() }
        .firstOrNull { it.isNotEmpty() }

    private fun documentText(document: org.w3c.dom.Document): String {
        val root = firstElement(document, "body") ?: document.documentElement
        val builder = StringBuilder()
        appendNodeText(root, builder)
        return builder.toString().lines()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString("\n\n")
            .trim()
    }

    private fun appendNodeText(node: Node, builder: StringBuilder) {
        if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
            builder.append(node.nodeValue.replace(Regex("\\s+"), " "))
            return
        }
        val isBlock = localName(node) in Constants.BLOCK_ELEMENTS
        if (isBlock && builder.isNotEmpty() && builder.last() != '\n') builder.append('\n')
        val children: NodeList = node.childNodes
        for (index in 0 until children.length) appendNodeText(children.item(index), builder)
        if (isBlock && builder.isNotEmpty() && builder.last() != '\n') builder.append('\n')
    }

    private fun String.removeLeadingTitle(title: String): String {
        val normalizedTitle = title.cleanInlineText()
        val firstLine = lineSequence().firstOrNull()?.trim().orEmpty()
        return if (firstLine == normalizedTitle) lineSequence().drop(1).joinToString("\n").trim() else trim()
    }

    private fun String.cleanInlineText(): String = replace(Regex("\\s+"), " ").trim()

    private fun resolveZipPath(baseDirectory: Path, href: String): String {
        val withoutFragment = href.substringBefore('#').substringBefore('?')
        val decoded = try {
            java.net.URI(withoutFragment).path
        } catch (_: IllegalArgumentException) {
            withoutFragment
        }
        return baseDirectory.resolve(decoded ?: withoutFragment).normalize().toString().replace('\\', '/')
    }

    private data class DecodedText(
        val text: String,
        val charset: Charset,
        val byteOffsets: LongArray
    )

    private data class TextWithOffsets(val text: String, val byteOffsets: LongArray)

    private data class ManifestItem(val href: String, val mediaType: String) {
        val isDocument: Boolean
            get() = mediaType.contains("html", ignoreCase = true) || mediaType.contains("xml", ignoreCase = true)
    }

    private object Constants {
        val BLOCK_ELEMENTS = setOf(
            "address", "article", "aside", "blockquote", "br", "div", "dl", "dt", "dd",
            "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6",
            "header", "hr", "li", "main", "nav", "ol", "p", "pre", "section", "table", "tr", "ul"
        )
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    }

    private fun localName(node: Node): String = node.localName ?: node.nodeName.substringAfter(':')

    private data class Match(val start: Int, val end: Int, val title: String)
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size &&
    prefix.indices.all { index -> this[index] == prefix[index] }
