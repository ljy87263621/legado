package io.legado.desktop

import java.nio.file.Path

data class WebDavSettingsState(
    val url: String,
    val username: String,
    val password: String,
    val remoteName: String,
    val backups: List<WebDavBackupFile> = emptyList(),
    val selectedBackupName: String? = null,
    val bookDirectory: String = "books",
    val remoteBooks: List<WebDavRemoteBook> = emptyList(),
    val selectedRemoteBookName: String? = null,
    val message: String? = null,
    val error: String? = null
)

class WebDavSettingsModel(
    private val backupModel: WebDavBackupModel,
    private val remoteBookModel: WebDavRemoteBookModel? = null
) {
    fun isConfigured(): Boolean = backupModel.config != null

    var state: WebDavSettingsState = backupModel.config?.let { config ->
        WebDavSettingsState(
            url = config.url,
            username = config.username,
            password = config.password,
            remoteName = defaultRemoteName(),
            bookDirectory = config.bookDirectory
        )
    } ?: WebDavSettingsState(
        url = "",
        username = "",
        password = "",
        remoteName = defaultRemoteName()
    )
        private set

    fun updateUrl(value: String) {
        state = state.copy(url = value, error = null)
    }

    fun updateUsername(value: String) {
        state = state.copy(username = value, error = null)
    }

    fun updatePassword(value: String) {
        state = state.copy(password = value, error = null)
    }

    fun updateRemoteName(value: String) {
        state = state.copy(remoteName = value, error = null)
    }

    fun updateBookDirectory(value: String) {
        state = state.copy(bookDirectory = value, error = null)
    }

    fun selectRemoteBook(name: String?) {
        state = state.copy(selectedRemoteBookName = name, error = null)
    }

    fun selectBackup(name: String?) {
        state = state.copy(selectedBackupName = name, error = null)
    }

    fun configure(): WebDavOperationResult {
        val result = backupModel.configure(state.url, state.username, state.password)
        val remoteResult = remoteBookModel?.configure(
            state.url, state.username, state.password, state.bookDirectory
        )
        return if (result.isSuccess && (remoteResult == null || remoteResult.isSuccess)) {
            refreshBackups(result.message)
        } else {
            val error = result.error ?: remoteResult?.error ?: "WebDAV 配置失败"
            state = state.copy(message = null, error = error)
            WebDavOperationResult(error = error)
        }
    }

    fun refreshRemoteBooks(): WebDavOperationResult {
        val remote = remoteBookModel ?: return WebDavOperationResult(error = "当前数据存储不支持 WebDAV 书库")
        return remote.listBooks().fold(
            onSuccess = { books ->
                state = state.copy(
                    remoteBooks = books,
                    selectedRemoteBookName = state.selectedRemoteBookName?.takeIf { selected ->
                        books.any { it.name == selected }
                    },
                    message = "远端书库已刷新",
                    error = null
                )
                WebDavOperationResult(message = "远端书库已刷新")
            },
            onFailure = { error ->
                val message = error.message ?: "远端书库刷新失败"
                state = state.copy(message = null, error = message)
                WebDavOperationResult(error = message)
            }
        )
    }

    fun downloadSelectedRemoteBook(): WebDavOperationResult {
        val remote = remoteBookModel ?: return WebDavOperationResult(error = "当前数据存储不支持 WebDAV 书库")
        val selected = state.selectedRemoteBookName ?: return WebDavOperationResult(error = "请先选择远端书籍")
        val book = state.remoteBooks.firstOrNull { it.name == selected }
            ?: return WebDavOperationResult(error = "远端书籍不存在")
        return remote.download(book).fold(
            onSuccess = { path -> WebDavOperationResult(message = "书籍已导入：${path.fileName}") },
            onFailure = { error -> WebDavOperationResult(error = error.message ?: "远端书籍下载失败") }
        ).also { result -> state = state.copy(message = result.message, error = result.error) }
    }

    fun uploadBook(localFile: Path): WebDavOperationResult {
        val remote = remoteBookModel ?: return WebDavOperationResult(error = "当前数据存储不支持 WebDAV 书库")
        val result = remote.upload(localFile)
        if (result.isSuccess) refreshRemoteBooks()
        state = state.copy(message = result.message, error = result.error)
        return result
    }

    fun deleteSelectedRemoteBook(): WebDavOperationResult {
        val remote = remoteBookModel ?: return WebDavOperationResult(error = "当前数据存储不支持 WebDAV 书库")
        val selected = state.selectedRemoteBookName ?: return WebDavOperationResult(error = "请先选择远端书籍")
        val book = state.remoteBooks.firstOrNull { it.name == selected }
            ?: return WebDavOperationResult(error = "远端书籍不存在")
        val result = remote.delete(book)
        if (result.isSuccess) {
            state = state.copy(selectedRemoteBookName = null)
            refreshRemoteBooks()
        }
        state = state.copy(message = result.message, error = result.error)
        return result
    }

    fun refresh(): WebDavOperationResult {
        val result = backupModel.listBackups()
            .fold(
                onSuccess = { backups ->
                    state = state.copy(
                        backups = backups,
                        selectedBackupName = state.selectedBackupName?.takeIf { name ->
                            backups.any { it.name == name }
                        },
                        message = "远端备份已刷新",
                        error = null
                    )
                    WebDavOperationResult(message = "远端备份已刷新")
                },
                onFailure = { error ->
                    state = state.copy(message = null, error = error.message ?: "远端备份刷新失败")
                    WebDavOperationResult(error = error.message ?: "远端备份刷新失败")
                }
            )
        return result
    }

    fun upload(archive: Path): WebDavOperationResult {
        val result = backupModel.upload(archive, state.remoteName)
        return if (result.isSuccess) refreshBackups(result.message) else {
            state = state.copy(message = null, error = result.error)
            result
        }
    }

    fun restore(): WebDavOperationResult {
        val name = state.selectedBackupName
        if (name == null) {
            val result = WebDavOperationResult(error = "请先选择远端备份")
            state = state.copy(message = null, error = result.error)
            return result
        }
        val result = backupModel.restore(name)
        state = state.copy(
            message = result.message,
            error = result.error
        )
        return result
    }

    private fun refreshBackups(message: String?): WebDavOperationResult {
        val refreshed = refresh()
        if (refreshed.isSuccess && message != null) {
            state = state.copy(message = message)
        }
        return refreshed.copy(message = if (refreshed.isSuccess) message else refreshed.message)
    }

    private fun defaultRemoteName(): String = "backup-${java.time.LocalDate.now()}.zip"
}
