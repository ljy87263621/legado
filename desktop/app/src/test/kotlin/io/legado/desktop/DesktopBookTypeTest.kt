package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopBookTypeTest {

    @Test
    fun onlyLocalImageBooksUseTheDesktopImageReader() {
        val localImageBook = CoreBook(
            bookUrl = "D:/books/comic.cbz",
            name = "comic",
            origin = "loc_book",
            type = DesktopBookType.IMAGE
        )
        val remoteImageBook = CoreBook(
            bookUrl = "https://example.test/comic",
            name = "comic",
            origin = "https://example.test/source.json",
            type = DesktopBookType.IMAGE
        )

        assertTrue(DesktopBookType.isLocalImageBook(localImageBook))
        assertFalse(DesktopBookType.isLocalImageBook(remoteImageBook))
    }

    @Test
    fun onlyLocalAudioBooksUseTheDesktopAudioReader() {
        val localAudioBook = CoreBook(
            bookUrl = "D:/books/chapter.wav",
            name = "chapter",
            origin = "loc_book",
            type = DesktopBookType.AUDIO
        )
        val remoteAudioBook = CoreBook(
            bookUrl = "https://example.test/audio",
            name = "chapter",
            origin = "https://example.test/source.json",
            type = DesktopBookType.AUDIO
        )

        assertTrue(DesktopBookType.isLocalAudioBook(localAudioBook))
        assertFalse(DesktopBookType.isLocalAudioBook(remoteAudioBook))
    }

    @Test
    fun routesBooksFromImageSourcesToTheOnlineImageReader() {
        val book = CoreBook(
            bookUrl = "https://example.test/comic",
            origin = "https://example.test/source.json",
            type = CoreBookType.fromSourceType(2)
        )

        assertTrue(DesktopBookType.isOnlineImageBook(book))
    }
}
