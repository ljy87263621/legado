package io.legado.desktop

import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopRestartTest {

    @Test
    fun restartCommandStartsTheInstalledExecutableWithTheSelectedDataDirectory() {
        val executable = Path.of("D:\\Apps\\Legado\\Legado.exe")
        val dataDirectory = Path.of("D:\\Apps\\Legado\\data-new")

        assertEquals(
            listOf(
                executable.toString(),
                "--data-dir",
                dataDirectory.toAbsolutePath().normalize().toString()
            ),
            DesktopRestart.command(executable, dataDirectory)
        )
    }
}
