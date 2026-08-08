package io.legado.desktop

import io.legado.core.library.CoreTxtTocRule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBookParserTest {

    @Test
    fun plainTextFileBecomesAReadableChapter() {
        withTempFile("plain.txt", "普通内容第一行\n普通内容第二行") { file ->
            val imported = LocalBookParser.parse(file)

            assertEquals("plain", imported.book.name)
            assertEquals(1, imported.chapters.size)
            assertEquals("普通内容第一行\n普通内容第二行", imported.chapters.single().content)
        }
    }

    @Test
    fun textHeadingsBecomeOrderedChapters() {
        withTempFile(
            "chapters.txt",
            "第一章 开始\n${"主角醒来。".repeat(250)}\n\n第二章 出发\n走出房门"
        ) { file ->
            val imported = LocalBookParser.parse(file)

            assertEquals(listOf("第一章 开始", "第二章 出发"), imported.chapters.map { it.chapter.title })
            assertTrue(imported.chapters[0].content.startsWith("主角醒来。"))
            assertTrue(imported.chapters[0].content.length > 1000)
            assertEquals("走出房门", imported.chapters[1].content)
            assertEquals(listOf(0, 1), imported.chapters.map { it.chapter.index })
        }
    }

    @Test
    fun builtInTxtTocRulesIncludeChineseSeparatorHeadings() {
        withTempFile(
            "separator-headings.txt",
            "一、开端\n主角醒来\n\n二、继续\n走出房门"
        ) { file ->
            val imported = LocalBookParser.parse(file)

            assertEquals(listOf("一、开端", "二、继续"), imported.chapters.map { it.chapter.title })
            assertEquals(listOf("主角醒来", "走出房门"), imported.chapters.map { it.content })
        }
    }

    @Test
    fun configuredTxtTocRuleOverridesTheBuiltInHeadingPattern() {
        withTempFile(
            "custom-rule.txt",
            "Section 1: Arrival\nThe ship docks\n\nSection 2: Departure\nThe ship leaves"
        ) { file ->
            val imported = LocalBookParser.parse(
                file,
                listOf(
                    CoreTxtTocRule(
                        id = 1L,
                        name = "English sections",
                        rule = "^Section \\d+:.*$",
                        serialNumber = 0,
                        enable = true
                    )
                )
            )

            assertEquals(listOf("Section 1: Arrival", "Section 2: Departure"), imported.chapters.map { it.chapter.title })
            assertEquals("The ship docks", imported.chapters[0].content)
            assertEquals("The ship leaves", imported.chapters[1].content)
        }
    }

    @Test
    fun textChaptersKeepOriginalUtf8ByteRangesIncludingMultibyteCharacters() {
        withTempBytes(
            "utf8-offsets.txt",
            "序言\n第一章 开始\n多字节正文\n第二章 终章\n结尾".toByteArray(StandardCharsets.UTF_8)
        ) { file ->
            val imported = LocalBookParser.parse(file)
            val bytes = Files.readAllBytes(file)
            val chapters = imported.chapters.map { it.chapter }

            assertEquals(2, chapters.size)
            assertEquals(
                0L,
                chapters[0].start
            )
            assertEquals(
                "序言\n第一章 开始\n多字节正文\n".toByteArray(StandardCharsets.UTF_8).size.toLong(),
                chapters[0].end
            )
            assertEquals(chapters[0].end, chapters[1].start)
            assertEquals(bytes.size.toLong(), chapters[1].end)
            assertEquals(
                "序言\n\n多字节正文",
                LocalBookParser.readTextChapter(file, chapters[0], imported.book.charset)
            )
        }
    }

    @Test
    fun delayedPlainTextReadKeepsBodyThatContainsTheFallbackTitle() {
        withTempFile(
            "fallback-title.txt",
            "正文不是章节标题\n这里仍然是正文"
        ) { file ->
            val imported = LocalBookParser.parse(
                file,
                listOf(
                    CoreTxtTocRule(
                        id = 1L,
                        name = "disabled",
                        rule = "^this-rule-does-not-match$",
                        serialNumber = 0,
                        enable = false
                    )
                )
            )

            assertEquals(
                "正文不是章节标题\n这里仍然是正文",
                LocalBookParser.readTextChapter(
                    file,
                    imported.chapters.single().chapter,
                    imported.book.charset
                )
            )
        }
    }

    @Test
    fun textChapterRangesIncludeUtf16BomAndCanBeReadWithoutCachedContent() {
        withTempBytes(
            "utf16-offsets.txt",
            "第一章\n正文\n第二章\n结尾".toByteArray(StandardCharsets.UTF_16LE).let { byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + it }
        ) { file ->
            val imported = LocalBookParser.parse(
                file,
                listOf(
                    CoreTxtTocRule(
                        id = 1L,
                        name = "UTF-16 headings",
                        rule = "^[ \\t　]{0,4}第[一二三四五六七八九十]+章$",
                        serialNumber = 0
                    )
                )
            )
            val chapters = imported.chapters.map { it.chapter }

            assertEquals(2, chapters.size)
            assertEquals(2L, chapters[0].start)
            assertEquals(chapters[0].end, chapters[1].start)
            assertEquals(Files.size(file), chapters[1].end)
            assertEquals(
                "正文",
                LocalBookParser.readTextChapter(file, chapters[0], imported.book.charset)
            )
        }
    }

    @Test
    fun epubMetadataAndSpineContentAreImportedInOrder() {
        withTempEpub { file ->
            val imported = LocalBookParser.parse(file)

            assertEquals("示例 EPUB", imported.book.name)
            assertEquals("测试作者", imported.book.author)
            assertEquals(listOf("第一章", "第二章"), imported.chapters.map { it.chapter.title })
            assertTrue(imported.chapters[0].content.contains("第一章正文"))
            assertTrue(imported.chapters[1].content.contains("第二章正文"))
        }
    }

    private fun withTempFile(name: String, content: String, block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("legado-parser-test")
        val file = directory.resolve(name)
        Files.writeString(file, content)
        try {
            block(file)
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    private fun withTempBytes(name: String, content: ByteArray, block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("legado-parser-test")
        val file = directory.resolve(name)
        Files.write(file, content)
        try {
            block(file)
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    private fun withTempEpub(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("legado-epub-test")
        val file = directory.resolve("sample.epub")
        ZipOutputStream(Files.newOutputStream(file)).use { zip ->
            zipEntry(zip, "META-INF/container.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>
            """.trimIndent())
            zipEntry(zip, "OEBPS/content.opf", """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>示例 EPUB</dc:title><dc:creator>测试作者</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="one" href="text/one.xhtml" media-type="application/xhtml+xml"/>
                    <item id="two" href="text/two.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="one"/><itemref idref="two"/></spine>
                </package>
            """.trimIndent())
            zipEntry(zip, "OEBPS/text/one.xhtml", "<html><body><h1>第一章</h1><p>第一章正文</p></body></html>")
            zipEntry(zip, "OEBPS/text/two.xhtml", "<html><body><h1>第二章</h1><p>第二章正文</p></body></html>")
        }
        try {
            block(file)
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    private fun zipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray())
        zip.closeEntry()
    }
}
