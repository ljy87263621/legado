package io.legado.desktop

import io.legado.core.library.CoreLibrary
import io.legado.desktop.persistence.DesktopWebDavConfigStore
import java.nio.file.Path

fun createWebDavSettingsModel(
    library: CoreLibrary,
    dataDirectory: Path,
    store: DesktopWebDavConfigStore,
    client: WebDavClient = JavaNetWebDavClient()
): WebDavSettingsModel = WebDavSettingsModel(
    backupModel = WebDavBackupModel(library, client = client, store = store),
    remoteBookModel = WebDavRemoteBookModel(
        library = library,
        client = client,
        store = store,
        downloadDirectory = dataDirectory.resolve("webdav-downloads")
    )
)
