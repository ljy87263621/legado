package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreChapter
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
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

    fun parse(path: Path): ParsedLocalBook {
        val normalizedPath = path.toAbsolutePath().normalize()
        require(Files.isRegularFile(normalizedPath)) { "Local book does not exist: $normalizedPath" }

        return if (normalizedPath.fileName.toString().endsWith(".epub", ignoreCase = true)) {
            parseEpub(normalizedPath)
        } else {
            parseText(normalizedPath)
        }
    }

    private fun parseText(path: Path): ParsedLocalBook {
        val decoded = decodeText(Files.readAllBytes(path))
        val bookUrl = path.toString()
        val bookName = path.fileName.toString().substringBeforeLast('.', path.fileName.toString())
        val sections = splitTextIntoChapters(decoded.text, bookUrl)
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

    private fun splitTextIntoChapters(text: String, bookUrl: String): List<ParsedLocalChapter> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        val headingIndexes = lines.mapIndexedNotNull { index, line ->
            if (Constants.CHAPTER_HEADING.matches(line.trim())) index else null
        }
        if (headingIndexes.isEmpty()) {
            return listOf(
                ParsedLocalChapter(
                    chapter = CoreChapter(bookUrl, "$bookUrl#chapter-0", "正文", 0),
                    content = text.trim()
                )
            )
        }

        val leadingText = lines.subList(0, headingIndexes.first()).joinToString("\n").trim()
        val chapters = headingIndexes.mapIndexed { position, headingIndex ->
            val nextHeading = headingIndexes.getOrNull(position + 1) ?: lines.size
            val title = lines[headingIndex].trim()
            val body = lines.subList(headingIndex + 1, nextHeading).joinToString("\n").trim()
            ParsedLocalChapter(
                chapter = CoreChapter(bookUrl, "$bookUrl#chapter-$position", title, position),
                content = body
            )
        }.toMutableList()
        if (leadingText.isNotEmpty()) {
            val first = chapters.first()
            chapters[0] = first.copy(content = "$leadingText\n\n${first.content}".trim())
        }
        return chapters
    }

    private fun decodeText(bytes: ByteArray): DecodedText {
        if (bytes.startsWith(Constants.UTF8_BOM)) {
            return DecodedText(String(bytes, Constants.UTF8_BOM.size, bytes.size - Constants.UTF8_BOM.size, StandardCharsets.UTF_8), StandardCharsets.UTF_8)
        }
        if (bytes.startsWith(Constants.UTF16_LE_BOM)) {
            return DecodedText(String(bytes, Constants.UTF16_LE_BOM.size, bytes.size - Constants.UTF16_LE_BOM.size, StandardCharsets.UTF_16LE), StandardCharsets.UTF_16LE)
        }
        if (bytes.startsWith(Constants.UTF16_BE_BOM)) {
            return DecodedText(String(bytes, Constants.UTF16_BE_BOM.size, bytes.size - Constants.UTF16_BE_BOM.size, StandardCharsets.UTF_16BE), StandardCharsets.UTF_16BE)
        }
        return try {
            DecodedText(decodeStrict(bytes, StandardCharsets.UTF_8), StandardCharsets.UTF_8)
        } catch (_: CharacterCodingException) {
            val fallback = Charset.forName("GB18030")
            DecodedText(decodeStrict(bytes, fallback), fallback)
        }
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

    private data class DecodedText(val text: String, val charset: Charset)

    private data class ManifestItem(val href: String, val mediaType: String) {
        val isDocument: Boolean
            get() = mediaType.contains("html", ignoreCase = true) || mediaType.contains("xml", ignoreCase = true)
    }

    private object Constants {
        val CHAPTER_HEADING = Regex(
            "^(第[0-9零一二三四五六七八九十百千万两]+[章节回集卷部篇].*|(?:chapter|chapter\\s+)[0-9零一二三四五六七八九十百千万两]+.*)$",
            RegexOption.IGNORE_CASE
        )
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
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size &&
    prefix.indices.all { index -> this[index] == prefix[index] }
