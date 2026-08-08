package io.legado.desktop

import io.legado.core.library.CoreLibrary
import io.legado.core.library.CoreReaderPageMode
import io.legado.core.library.CoreReaderSettings
import io.legado.core.library.CoreReaderTheme

data class ReaderPalette(
    val backgroundArgb: Long,
    val contentArgb: Long
)

class ReaderSettingsModel(
    private val library: CoreLibrary
) {
    var settings: CoreReaderSettings = library.readerSettings()
        private set

    val palette: ReaderPalette
        get() = settings.theme.palette()

    fun reload() {
        settings = library.readerSettings()
    }

    fun update(
        textSize: Int = settings.textSize,
        lineSpacingExtra: Int = settings.lineSpacingExtra,
        theme: CoreReaderTheme = settings.theme,
        pageMode: CoreReaderPageMode = settings.pageMode,
        autoRead: Boolean = settings.autoRead,
        autoReadSpeedSeconds: Int = settings.autoReadSpeedSeconds
    ) {
        settings = settings.copy(
            textSize = textSize.coerceIn(8, 72),
            lineSpacingExtra = lineSpacingExtra.coerceIn(0, 48),
            theme = theme,
            pageMode = pageMode,
            autoRead = autoRead,
            autoReadSpeedSeconds = autoReadSpeedSeconds.coerceIn(1, 120)
        )
        library.saveReaderSettings(settings)
    }

    private fun CoreReaderTheme.palette(): ReaderPalette = when (this) {
        CoreReaderTheme.DAY -> ReaderPalette(0xFFFFFBFEL, 0xFF1C1B1FL)
        CoreReaderTheme.NIGHT -> ReaderPalette(0xFF121212L, 0xFFFFFFFFL)
        CoreReaderTheme.SEPIA -> ReaderPalette(0xFFF4ECD8L, 0xFF4A3F35L)
        CoreReaderTheme.GREEN -> ReaderPalette(0xFFE9F5E1L, 0xFF1F3521L)
    }
}
