package io.legado.desktop.persistence

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object DesktopDataDirectory {

    fun resolve(
        systemProperty: String? = System.getProperty(DATA_DIR_PROPERTY),
        environmentVariable: String? = System.getenv(DATA_DIR_ENVIRONMENT),
        localAppData: Path? = System.getenv(LOCAL_APP_DATA_ENVIRONMENT)?.let(::toPath),
        userHome: Path = Path.of(System.getProperty("user.home")),
        applicationDirectory: Path = resolveApplicationDirectory(
            processCommand = null,
            javaHome = System.getProperty("java.home"),
            userDir = Paths.get(System.getProperty("user.dir"))
        ),
        forcePortable: Boolean = false
    ): Path = systemProperty.pathOrNull()
        ?: environmentVariable.pathOrNull()
        ?: portableDirectory(applicationDirectory, forcePortable)
        ?: localAppData?.resolve(APPLICATION_DIRECTORY)
        ?: userHome.resolve("AppData").resolve("Local")
        .resolve(APPLICATION_DIRECTORY)

    fun resolveApplicationDirectory(
        processCommand: String?,
        javaHome: String?,
        userDir: Path
    ): Path {
        val commandPath = processCommand
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(::toPath)
        if (commandPath?.fileName?.toString()?.equals("Legado.exe", ignoreCase = true) == true) {
            return commandPath.toAbsolutePath().normalize().parent ?: userDir
        }

        val runtimeHome = javaHome
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(::toPath)
        val packagedExecutable = runtimeHome
            ?.parent
            ?.resolve("Legado.exe")
            ?.toAbsolutePath()
            ?.normalize()
        if (packagedExecutable != null && Files.isRegularFile(packagedExecutable)) {
            return packagedExecutable.parent
        }
        return userDir.toAbsolutePath().normalize()
    }

    private fun portableDirectory(applicationDirectory: Path, forcePortable: Boolean): Path? {
        val appDirectory = applicationDirectory.toAbsolutePath().normalize()
        return if (forcePortable || Files.isRegularFile(appDirectory.resolve(PORTABLE_MARKER))) {
            appDirectory.resolve(PORTABLE_DATA_DIRECTORY)
        } else {
            null
        }
    }

    private fun String?.pathOrNull(): Path? =
        this?.trim()?.takeIf(String::isNotEmpty)?.let(::toPath)

    private fun toPath(value: String): Path = Path.of(value)

    private const val APPLICATION_DIRECTORY = "Legado"
    private const val PORTABLE_MARKER = "portable.flag"
    private const val PORTABLE_DATA_DIRECTORY = "data"
    private const val DATA_DIR_PROPERTY = "legado.dataDir"
    private const val DATA_DIR_ENVIRONMENT = "LEGADO_DATA_DIR"
    private const val LOCAL_APP_DATA_ENVIRONMENT = "LOCALAPPDATA"
}
