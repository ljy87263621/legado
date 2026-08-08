package io.legado.desktop

import io.legado.desktop.persistence.DesktopDataDirectoryMigration
import io.legado.desktop.persistence.DesktopDataMigrationResult
import io.legado.desktop.persistence.SqliteCoreLibrary
import java.nio.file.Path

data class PendingDataMigration(
    val result: DesktopDataMigrationResult,
    val restartError: String? = null
) {
    val requiresImmediateRestart: Boolean
        get() = true

    val canDismiss: Boolean
        get() = false

    fun restartFailed(message: String): PendingDataMigration = copy(restartError = message)
}

class DataDirectoryMigrationModel(
    private val library: SqliteCoreLibrary,
    private val migration: DesktopDataDirectoryMigration = DesktopDataDirectoryMigration()
) {

    fun migrate(targetDirectory: Path): Result<DesktopDataMigrationResult> = runCatching {
        migration.migrate(library, targetDirectory)
    }
}
