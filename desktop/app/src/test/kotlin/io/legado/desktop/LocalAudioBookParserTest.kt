package io.legado.desktop

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAudioBookParserTest {

    @Test
    fun parsesSupportedLocalAudioAsOnePlayableChapter() {
        val directory = Files.createTempDirectory("legado-audio-parser-test")
        val file = directory.resolve("chapter.wav")
        try {
            Files.write(file, byteArrayOf(1, 2, 3))

            val parsed = LocalAudioBookParser.parse(file)

            assertEquals(DesktopBookType.AUDIO, parsed.book.type)
            assertEquals("loc_book", parsed.book.origin)
            assertEquals(1, parsed.chapters.size)
            assertEquals(file.toAbsolutePath().normalize().toString(), parsed.chapters.single().resourceUrl)
            assertTrue(LocalAudioBookParser.canParse(file))
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }
}
