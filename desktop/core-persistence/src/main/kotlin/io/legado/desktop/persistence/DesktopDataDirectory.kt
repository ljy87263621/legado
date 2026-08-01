package io.legado.desktop.persistence

import java.nio.file.Path

object DesktopDataDirectory {

    fun resolve(
        systemProperty: String? = System.getProperty(DATA_DIR_PROPERTY),
        environmentVariable: String? = System.getenv(DATA_DIR_ENVIRONMENT),
        localAppData: Path? = System.getenv(LOCAL_APP_DATA_ENVIRONMENT)?.let(::toPath),
        userHome: Path = Path.of(System.getProperty("user.home"))
    ): Path = systemProperty.pathOrNull()
        ?: environmentVariable.pathOrNull()
        ?: localAppData?.resolve(APPLICATION_DIRECTORY)
        ?: userHome.resolve("AppData").resolve("Local")
        .resolve(APPLICATION_DIRECTORY)

    private fun String?.pathOrNull(): Path? =
        this?.trim()?.takeIf(String::isNotEmpty)?.let(::toPath)

    private fun toPath(value: String): Path = Path.of(value)

    private const val APPLICATION_DIRECTORY = "Legado"
    private const val DATA_DIR_PROPERTY = "legado.dataDir"
    private const val DATA_DIR_ENVIRONMENT = "LEGADO_DATA_DIR"
    private const val LOCAL_APP_DATA_ENVIRONMENT = "LOCALAPPDATA"
}
