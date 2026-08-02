package io.legado.desktop

import io.legado.core.library.CoreReaderPageMode
import io.legado.core.library.CoreReaderTheme
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSettingsModelTest {

    @Test
    fun settingsModelLoadsDefaultsAndPersistsUpdates() {
        val library = InMemoryCoreLibrary()
        val model = ReaderSettingsModel(library)

        assertEquals(20, model.settings.textSize)

        model.update(
            textSize = 24,
            lineSpacingExtra = 16,
            theme = CoreReaderTheme.NIGHT,
            pageMode = CoreReaderPageMode.PAGED,
            autoRead = true
        )

        assertEquals(24, model.settings.textSize)
        assertEquals(16, library.readerSettings().lineSpacingExtra)
        assertEquals(CoreReaderTheme.NIGHT, library.readerSettings().theme)
        assertEquals(CoreReaderPageMode.PAGED, library.readerSettings().pageMode)
        assertEquals(true, library.readerSettings().autoRead)
    }

    @Test
    fun readerPaletteChangesWithTheSelectedTheme() {
        val model = ReaderSettingsModel(InMemoryCoreLibrary())

        val day = model.palette
        model.update(theme = CoreReaderTheme.NIGHT)

        assertEquals(0xFFFFFBFE, day.backgroundArgb)
        assertEquals(0xFF121212, model.palette.backgroundArgb)
        assertEquals(0xFFFFFFFF, model.palette.contentArgb)
    }

    @Test
    fun reloadReadsSettingsWrittenAfterModelCreation() {
        val library = InMemoryCoreLibrary()
        val model = ReaderSettingsModel(library)

        library.saveReaderSettings(
            model.settings.copy(
                textSize = 30,
                theme = CoreReaderTheme.SEPIA
            )
        )

        model.reload()

        assertEquals(30, model.settings.textSize)
        assertEquals(CoreReaderTheme.SEPIA, model.settings.theme)
    }
}
