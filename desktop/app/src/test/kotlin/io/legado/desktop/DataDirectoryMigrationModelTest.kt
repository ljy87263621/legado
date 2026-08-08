package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.desktop.persistence.DesktopDataMigrationResult
import io.legado.desktop.persistence.SqliteCoreLibrary
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataDirectoryMigrationModelTest {

    private lateinit var tempDirectory: Path

    @Before
    fun setUp() {
        tempDirectory = Files.createTempDirectory("legado-data-migration-model-test")
    }

    @After
    fun tearDown() {
        Files.walk(tempDirectory)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::deleteIfExists)
    }

    @Test
    fun migratesTheCurrentLibraryAndReturnsAUserVisibleResult() {
        val sourceDirectory = tempDirectory.resolve("source")
        val targetDirectory = tempDirectory.resolve("target")
        val sourceDatabase = sourceDirectory.resolve("legado.db")
        val book = CoreBook("model-book", name = "迁移模型测试")

        SqliteCoreLibrary(sourceDatabase).use { library ->
            library.saveBook(book)

            val result = DataDirectoryMigrationModel(library).migrate(targetDirectory)

            assertTrue(result.isSuccess)
            assertEquals(targetDirectory.toAbsolutePath().normalize(), result.getOrThrow().targetDirectory)
            assertTrue(Files.isRegularFile(result.getOrThrow().sourceBackup))
        }
    }

    @Test
    fun turnsMigrationErrorsIntoFailureResultsWithoutThrowingToTheUi() {
        val sourceDirectory = tempDirectory.resolve("source")
        val sourceDatabase = sourceDirectory.resolve("legado.db")

        SqliteCoreLibrary(sourceDatabase).use { library ->
            val result = DataDirectoryMigrationModel(library).migrate(sourceDirectory)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("当前数据目录"))
        }
    }

    @Test
    fun completedMigrationRequiresImmediateRestartAndCannotBeDismissed() {
        val result = DesktopDataMigrationResult(
            sourceDirectory = tempDirectory.resolve("source"),
            targetDirectory = tempDirectory.resolve("target"),
            sourceBackup = tempDirectory.resolve("source-backup.db"),
            targetDatabase = tempDirectory.resolve("target").resolve("legado.db")
        )

        val pending = PendingDataMigration(result)

        assertTrue(pending.requiresImmediateRestart)
        assertTrue(!pending.canDismiss)
        assertEquals(
            "重启失败",
            pending.restartFailed("重启失败").restartError
        )
        assertEquals(result, pending.restartFailed("重启失败").result)
    }
}
