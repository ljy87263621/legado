package io.legado.desktop

import io.legado.core.library.CoreBook
import java.nio.file.Path

enum class DesktopLaunchFileImportStatus {
    IMPORTED,
    FAILED
}

data class DesktopLaunchFileImportState(
    val status: DesktopLaunchFileImportStatus? = null,
    val book: CoreBook? = null,
    val error: String? = null
)

class DesktopLaunchFileImportModel(
    private val importAction: (Path) -> CoreBook
) {
    var state: DesktopLaunchFileImportState = DesktopLaunchFileImportState()
        private set

    val canRetry: Boolean
        get() = state.status == DesktopLaunchFileImportStatus.FAILED

    fun importFile(path: String): DesktopLaunchFileImportState {
        state = runCatching { importAction(Path.of(path)) }
            .fold(
                onSuccess = { book ->
                    DesktopLaunchFileImportState(
                        status = DesktopLaunchFileImportStatus.IMPORTED,
                        book = book
                    )
                },
                onFailure = { error ->
                    DesktopLaunchFileImportState(
                        status = DesktopLaunchFileImportStatus.FAILED,
                        error = error.message ?: "无法导入文件"
                    )
                }
            )
        return state
    }
}
