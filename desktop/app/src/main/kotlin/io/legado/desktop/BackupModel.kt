package io.legado.desktop

import io.legado.core.library.CoreBackupService
import io.legado.core.library.CoreBackupSummary
import io.legado.core.library.CoreLibrary
import java.nio.file.Path

data class BackupOperationResult(
    val summary: CoreBackupSummary? = null,
    val error: String? = null
) {
    val isSuccess: Boolean
        get() = error == null
}

class BackupModel(
    private val library: CoreLibrary,
    private val service: CoreBackupService = CoreBackupService()
) {

    fun export(path: Path): BackupOperationResult = runOperation {
        service.export(library, path)
    }

    fun import(path: Path): BackupOperationResult = runOperation {
        service.import(library, path)
    }

    private fun runOperation(operation: () -> CoreBackupSummary): BackupOperationResult =
        runCatching { operation() }
            .fold(
                onSuccess = { BackupOperationResult(summary = it) },
                onFailure = { error ->
                    BackupOperationResult(error = error.message ?: "备份操作失败")
                }
            )
}
