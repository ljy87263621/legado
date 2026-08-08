package io.legado.desktop.persistence

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

data class DesktopDataMigrationResult(
    val sourceDirectory: Path,
    val targetDirectory: Path,
    val sourceBackup: Path,
    val targetDatabase: Path
)

/** Moves a live desktop database to a user-selected data directory without overwriting data. */
class DesktopDataDirectoryMigration {

    fun migrate(
        library: SqliteCoreLibrary,
        targetDirectory: Path
    ): DesktopDataMigrationResult {
        val sourceDatabase = library.databasePath
        val sourceDirectory = sourceDatabase.parent
            ?: error("当前数据库没有可用的数据目录")
        val normalizedSource = sourceDirectory.toAbsolutePath().normalize()
        val normalizedTarget = targetDirectory.toAbsolutePath().normalize()
        val targetDatabase = normalizedTarget.resolve(DATABASE_FILE_NAME)

        require(normalizedTarget != normalizedSource) { "迁移目标不能是当前数据目录" }
        require(!normalizedTarget.startsWith(normalizedSource)) {
            "迁移目标不能位于当前数据目录内"
        }
        require(!normalizedSource.startsWith(normalizedTarget)) {
            "迁移目标不能包含当前数据目录"
        }
        require(!Files.exists(targetDatabase)) {
            "目标目录已存在数据库: $targetDatabase"
        }

        Files.createDirectories(normalizedSource)
        Files.createDirectories(normalizedTarget)
        val sourceBackup = normalizedSource.resolve(
            "legado-migration-backup-${System.currentTimeMillis()}-${UUID.randomUUID()}.db"
        )
        val targetTemporary = normalizedTarget.resolve(
            ".legado-migration-${UUID.randomUUID()}.db"
        )

        try {
            library.snapshotTo(sourceBackup)
            Files.copy(sourceBackup, targetTemporary, StandardCopyOption.COPY_ATTRIBUTES)
            try {
                Files.move(targetTemporary, targetDatabase, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(targetTemporary, targetDatabase)
            }
        } finally {
            Files.deleteIfExists(targetTemporary)
        }

        return DesktopDataMigrationResult(
            sourceDirectory = normalizedSource,
            targetDirectory = normalizedTarget,
            sourceBackup = sourceBackup,
            targetDatabase = targetDatabase
        )
    }

    private companion object {
        const val DATABASE_FILE_NAME = "legado.db"
    }
}
