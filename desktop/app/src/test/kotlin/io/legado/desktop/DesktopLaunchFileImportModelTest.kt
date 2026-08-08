package io.legado.desktop

import io.legado.core.library.CoreBook
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLaunchFileImportModelTest {

    @Test
    fun successfulStartupFileImportExposesTheImportedBook() {
        val expectedBook = CoreBook(
            bookUrl = "D:\\Books\\story.txt",
            name = "story"
        )
        val model = DesktopLaunchFileImportModel { path ->
            assertEquals(Path.of("D:\\Books\\story.txt"), path)
            expectedBook
        }

        val state = model.importFile("D:\\Books\\story.txt")

        assertEquals(DesktopLaunchFileImportStatus.IMPORTED, state.status)
        assertEquals(expectedBook, state.book)
        assertTrue(state.error.isNullOrBlank())
    }

    @Test
    fun failedStartupFileImportKeepsTheErrorAndCanBeRetried() {
        var attempts = 0
        val model = DesktopLaunchFileImportModel {
            attempts++
            error("文件内容损坏")
        }

        val state = model.importFile("D:\\Books\\broken.txt")

        assertEquals(DesktopLaunchFileImportStatus.FAILED, state.status)
        assertEquals("文件内容损坏", state.error)
        assertTrue(model.canRetry)
        assertEquals(1, attempts)
    }
}
