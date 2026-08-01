package io.legado.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBookParserTest {

    @Test
    fun plainTextFileBecomesAReadableChapter() {
        withTempFile("plain.txt", "正文第一行\n正文第二行") { file ->
            val imported = LocalBookParser.parse(file)

            assertEquals("plain", imported.book.name)
            assertEquals(1, imported.chapters.size)
            assertEquals("正文第一行\n正文第二行", imported.chapters.single().content)
        }
    }

    @Test
    fun textHeadingsBecomeOrderedChapters() {
        withTempFile(
            "chapters.txt",
            "第一章 开始\n主角醒来\n\n第二章 出发\n走出房门"
        ) { file ->
            val imported = LocalBookParser.parse(file)

            assertEquals(listOf("第一章 开始", "第二章 出发"), imported.chapters.map { it.chapter.title })
            assertEquals("主角醒来", imported.chapters[0].content)
            assertEquals("走出房门", imported.chapters[1].content)
            assertEquals(listOf(0, 1), imported.chapters.map { it.chapter.index })
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
