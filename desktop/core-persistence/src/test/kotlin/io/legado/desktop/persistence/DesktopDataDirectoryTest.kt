package io.legado.desktop.persistence

import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopDataDirectoryTest {

    @Test
    fun systemPropertyTakesPrecedenceOverEnvironmentAndDefaults() {
        val resolved = DesktopDataDirectory.resolve(
            systemProperty = "D:\\Portable\\LegadoData",
            environmentVariable = "D:\\Environment\\LegadoData",
            localAppData = Paths.get("C:\\Users\\reader\\AppData\\Local"),
            userHome = Paths.get("C:\\Users\\reader")
        )

        assertEquals(Paths.get("D:\\Portable\\LegadoData"), resolved)
    }

    @Test
    fun environmentDirectoryIsUsedBeforeLocalAppData() {
        val resolved = DesktopDataDirectory.resolve(
            systemProperty = null,
            environmentVariable = "D:\\Environment\\LegadoData",
            localAppData = Paths.get("C:\\Users\\reader\\AppData\\Local"),
            userHome = Paths.get("C:\\Users\\reader")
        )

        assertEquals(Paths.get("D:\\Environment\\LegadoData"), resolved)
    }

    @Test
    fun localAppDataUsesTheLegadoApplicationSubdirectory() {
        val resolved = DesktopDataDirectory.resolve(
            systemProperty = null,
            environmentVariable = null,
            localAppData = Paths.get("C:\\Users\\reader\\AppData\\Local"),
            userHome = Paths.get("C:\\Users\\reader")
        )

        assertEquals(
            Paths.get("C:\\Users\\reader\\AppData\\Local\\Legado"),
            resolved
        )
    }

    @Test
    fun localAppDataFallsBackToUserHomeOnNonWindowsEnvironments() {
        val userHome = Paths.get("C:\\Users\\reader")
        val resolved: Path = DesktopDataDirectory.resolve(
            systemProperty = null,
            environmentVariable = null,
            localAppData = null,
            userHome = userHome
        )

        assertEquals(userHome.resolve("AppData").resolve("Local").resolve("Legado"), resolved)
    }

    @Test
    fun portableMarkerUsesDataDirectoryNextToTheApplication() {
        val applicationDirectory = Files.createTempDirectory("legado-portable-app-")
        try {
            Files.createFile(applicationDirectory.resolve("portable.flag"))

            val resolved = DesktopDataDirectory.resolve(
                systemProperty = null,
                environmentVariable = null,
                localAppData = Paths.get("C:\\Users\\reader\\AppData\\Local"),
                userHome = Paths.get("C:\\Users\\reader"),
                applicationDirectory = applicationDirectory
            )

            assertEquals(applicationDirectory.resolve("data"), resolved)
        } finally {
            Files.deleteIfExists(applicationDirectory.resolve("portable.flag"))
            Files.deleteIfExists(applicationDirectory)
        }
    }

    @Test
    fun explicitPortableModeDoesNotRequireAMarkerFile() {
        val applicationDirectory = Files.createTempDirectory("legado-portable-app-")
        try {
            val resolved = DesktopDataDirectory.resolve(
                systemProperty = null,
                environmentVariable = null,
                localAppData = Paths.get("C:\\Users\\reader\\AppData\\Local"),
                userHome = Paths.get("C:\\Users\\reader"),
                applicationDirectory = applicationDirectory,
                forcePortable = true
            )

            assertEquals(applicationDirectory.resolve("data"), resolved)
        } finally {
            Files.deleteIfExists(applicationDirectory)
        }
    }
}
