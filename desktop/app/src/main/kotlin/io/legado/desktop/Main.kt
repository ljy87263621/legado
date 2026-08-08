package io.legado.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.shape.RoundedCornerShape
import io.legado.core.library.CoreBook
import io.legado.core.library.CoreBookGroup
import io.legado.core.library.CoreLibrary
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreDictRule
import io.legado.core.library.CoreReplaceRule
import io.legado.core.library.CoreReadRecord
import io.legado.core.library.CoreReaderPageMode
import io.legado.core.library.CoreReaderTheme
import io.legado.core.library.CoreSourceFilterRule
import io.legado.core.library.CoreSubscriptionPage
import io.legado.core.library.CoreTxtTocRule
import io.legado.core.source.BookSourceSearchService
import io.legado.core.source.BookSourceJsonCodec
import io.legado.core.source.CoreHttpClient
import io.legado.core.source.CoreSearchResult
import io.legado.core.source.CoreSourceHttpClient
import io.legado.core.source.CoreSourceSessionStatus
import io.legado.core.source.JavaNetHttpClient
import io.legado.core.source.OnlineBookService
import io.legado.desktop.persistence.DesktopDataDirectory
import io.legado.desktop.persistence.DesktopDataMigrationResult
import io.legado.desktop.persistence.SqliteCoreLibrary
import io.legado.desktop.persistence.DesktopSetupStore
import io.legado.desktop.persistence.DesktopWebDavConfigStore
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Desktop
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView
import org.jetbrains.skia.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

fun main(args: Array<String>) = application {
    WindowsProtocolRegistration.registerCurrentExecutable()
    WindowsFileAssociationRegistration.registerCurrentExecutable()
    val launchRequest = remember { DesktopLaunchRequest.fromArgs(args) }
    val applicationDirectory = remember {
        DesktopDataDirectory.resolveApplicationDirectory(
            processCommand = ProcessHandle.current().info().command().orElse(null),
            javaHome = System.getProperty("java.home"),
            userDir = Path.of(System.getProperty("user.dir"))
        )
    }
    val dataDirectory = remember(launchRequest?.dataDirectory, launchRequest?.portable, applicationDirectory) {
        DesktopDataDirectory.resolve(
            systemProperty = launchRequest?.dataDirectory ?: System.getProperty("legado.dataDir"),
            applicationDirectory = applicationDirectory,
            forcePortable = launchRequest?.portable == true
        ).toAbsolutePath().normalize()
    }
    val library = remember(dataDirectory) {
        SqliteCoreLibrary(dataDirectory.resolve("legado.db"))
    }
    val libraryClosed = remember(library) { AtomicBoolean(false) }
    val closeLibrary = remember(library) {
        { if (libraryClosed.compareAndSet(false, true)) library.close() }
    }
    val restartExecutable = remember {
        WindowsProtocolRegistration.resolveExecutable(
            processCommand = ProcessHandle.current().info().command().orElse(null),
            javaHome = System.getProperty("java.home")
        )
    }
    val closeApplicationHandler = remember {
        AtomicReference<() -> Unit>({ exitApplication() })
    }
    val windowState = rememberWindowState(
        size = DpSize(1180.dp, 760.dp),
        position = WindowPosition.Aligned(Alignment.Center)
    )
    Window(
        onCloseRequest = {
            closeApplicationHandler.get().invoke()
        },
        title = "Legado",
        state = windowState
    ) {
        LegadoApp(
            library = library,
            dataDirectory = dataDirectory,
            launchRequest = launchRequest,
            onStartupValidationComplete = {
                exitApplication()
            },
            onWindowCloseHandlerChanged = { handler ->
                closeApplicationHandler.set(handler)
            },
            onExitApplication = {
                exitApplication()
            },
            onRestartApplication = { targetDirectory, reportError ->
                runCatching {
                    val executable = restartExecutable
                        ?: error("当前运行环境无法定位 Legado.exe，请手动重启应用")
                    DesktopRestart.restart(executable, targetDirectory)
                    exitApplication()
                }.onFailure { error -> reportError(error.message ?: "无法重启应用") }
            },
            onApplicationDisposed = closeLibrary
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LegadoApp(
    library: CoreLibrary,
    dataDirectory: Path? = null,
    launchRequest: DesktopLaunchRequest? = null,
    onStartupValidationComplete: () -> Unit = {},
    onWindowCloseHandlerChanged: (((() -> Unit)) -> Unit) = {},
    onExitApplication: () -> Unit = {},
    onRestartApplication: (Path, (String) -> Unit) -> Unit = { _, _ -> },
    onApplicationDisposed: () -> Unit = {}
) {
    LaunchedEffect(launchRequest?.startupValidation) {
        if (launchRequest?.startupValidation == true) {
            delay(500)
            onStartupValidationComplete()
        }
    }
    val httpClient = remember { CoreSourceHttpClient(library, JavaNetHttpClient()) }
    val onlineService = remember { OnlineBookService(library, httpClient) }
    val bookshelfModel = remember { BookshelfModel(library) }
    val bookUpdateModel = remember { BookUpdateModel(library, onlineService) }
    val sqliteLibrary = library as? SqliteCoreLibrary
    val chapterDownloadModel = remember(sqliteLibrary) {
        ChapterDownloadModel(library, onlineService, sqliteLibrary)
    }
    val updateScheduler = remember { BookUpdateScheduler(library, bookUpdateModel) }
    val searchService = remember { BookSourceSearchService(library, httpClient) }
    val searchModel = remember { SearchModel(library, searchService) }
    val exploreModel = remember { ExploreModel(library, searchService) }
    val sourceModel = remember { SourceModel(library, httpClient = httpClient) }
    val subscriptionModel = remember { SubscriptionModel(library, searchService) }
    val subscriptionPageModel = remember { SubscriptionPageModel(library) }
    val detailModel = remember { BookDetailModel(library, onlineService, searchService) }
    val readerSettingsModel = remember { ReaderSettingsModel(library) }
    val bookmarkModel = remember { BookmarkModel(library) }
    val readRecordModel = remember { ReadRecordModel(library) }
    val readRecordHeatmapModel = remember { ReadRecordHeatmapModel(library) }
    val replaceRuleModel = remember { ReplaceRuleModel(library) }
    val dictRuleModel = remember { DictRuleModel(library, httpClient) }
    val txtTocRuleModel = remember { TxtTocRuleModel(library) }
    val sourceFilterRuleModel = remember { SourceFilterRuleModel(library) }
    val setupStore = library as? DesktopSetupStore
    val welcomeModel = remember(setupStore) {
        setupStore?.let(::WelcomeModel)
    }
    val managementServerModel = remember(library) { DesktopManagementServerModel(library) }
    val protocolRegistrationModel = remember { WindowsProtocolRegistrationModel() }
    val fileAssociationRegistrationModel = remember { WindowsFileAssociationRegistrationModel() }
    val webDavSettingsModel = remember(library) {
        val store = library as? DesktopWebDavConfigStore
        if (store != null && dataDirectory != null) {
            createWebDavSettingsModel(
                library = library,
                dataDirectory = dataDirectory,
                store = store
            )
        } else {
            null
        }
    }
    val dataDirectoryMigrationModel = remember(sqliteLibrary) {
        sqliteLibrary?.let(::DataDirectoryMigrationModel)
    }
    val appState = remember {
        AppState(
            initialRoute = DesktopStartupRoute.resolve(
                setupComplete = setupStore?.isSetupComplete(),
                hasLaunchRequest = launchRequest != null,
                setupStoreAvailable = setupStore != null,
                hasLocalFileLaunch = launchRequest?.localFilePath != null
            )
        )
    }
    var route by remember { mutableStateOf(appState.route) }
    var darkTheme by remember { mutableStateOf(appState.isDarkTheme) }
    var selectedBookUrl by remember { mutableStateOf<String?>(null) }
    var selectedChapterIndex by remember { mutableStateOf<Int?>(null) }
    var selectedChapterPos by remember { mutableStateOf<Int?>(null) }
    var selectedBook by remember { mutableStateOf<CoreBook?>(null) }
    var bookshelfRefreshToken by remember { mutableStateOf(0) }
    var importError by remember { mutableStateOf<String?>(null) }
    var backupFeedback by remember { mutableStateOf<String?>(null) }
    var pendingDataMigration by remember { mutableStateOf<PendingDataMigration?>(null) }
    val currentPendingDataMigration = rememberUpdatedState(pendingDataMigration)
    val applicationLifecycle = remember(updateScheduler, chapterDownloadModel) {
        DesktopApplicationLifecycle(
            stopUpdates = { timeoutMillis ->
                updateScheduler.stopForMigration(timeoutMillis)
            },
            stopDownloads = { timeoutMillis ->
                chapterDownloadModel.stopForMigration(timeoutMillis)
            },
            resumeUpdates = updateScheduler::resumeAfterMigration,
            resumeDownloads = chapterDownloadModel::resumeAfterMigration,
            closeUpdates = updateScheduler::close,
            closeDownloads = { chapterDownloadModel.shutdown() },
            onDisposed = {
                managementServerModel.stop()
                onApplicationDisposed()
            }
        )
    }

    DisposableEffect(applicationLifecycle) {
        updateScheduler.start()
        onWindowCloseHandlerChanged {
            if (currentPendingDataMigration.value == null) {
                if (applicationLifecycle.close()) {
                    onExitApplication()
                } else {
                    backupFeedback = "后台任务未能及时停止，应用保持打开状态"
                }
            }
        }
        onDispose {
            applicationLifecycle.close()
        }
    }
    LaunchedEffect(updateScheduler) {
        updateScheduler.state.collect { state ->
            if (
                state.schedule.lastRunAt > 0L &&
                state.status in setOf(BookUpdateScheduleStatus.COMPLETED, BookUpdateScheduleStatus.FAILED)
            ) {
                bookshelfModel.refresh()
                bookshelfRefreshToken++
            }
        }
    }
    LaunchedEffect(launchRequest?.localFilePath) {
        val path = launchRequest?.localFilePath ?: return@LaunchedEffect
        val importModel = DesktopLaunchFileImportModel { file ->
            LocalBookImporter(library).importFile(file)
        }
        val state = importModel.importFile(path)
        state.book?.let { book ->
            selectedBook = book
            selectedBookUrl = book.bookUrl
            selectedChapterIndex = null
            selectedChapterPos = null
            appState.openReader()
            route = appState.route
            bookshelfRefreshToken++
        } ?: run {
            importError = state.error ?: "无法导入文件"
        }
    }

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppShell(
                route = route,
                welcomeModel = welcomeModel,
                launchRequest = launchRequest,
                onRouteChange = {
                    appState.navigate(it)
                    route = appState.route
                },
                darkTheme = darkTheme,
                onToggleTheme = {
                    appState.toggleTheme()
                    darkTheme = appState.isDarkTheme
                },
                bookshelfModel = bookshelfModel,
                bookUpdateModel = bookUpdateModel,
                updateScheduler = updateScheduler,
                bookshelfRefreshToken = bookshelfRefreshToken,
                importError = importError,
                onDismissImportError = { importError = null },
                onImport = {
                    selectLocalBook()?.let { path ->
                        runCatching { LocalBookImporter(library).importFile(path) }
                            .onSuccess {
                                importError = null
                                bookshelfRefreshToken++
                            }
                            .onFailure { error ->
                                importError = error.message ?: "无法导入文件"
                            }
                    }
                },
                onOpenBook = { bookUrl ->
                    selectedBookUrl = bookUrl
                    selectedChapterIndex = null
                    selectedChapterPos = null
                    selectedBook = library.book(bookUrl)
                    appState.openReader()
                    route = appState.route
                },
                onOpenDetail = { book ->
                    selectedBook = book
                    selectedBookUrl = book.bookUrl
                    selectedChapterIndex = null
                    selectedChapterPos = null
                    appState.navigate(AppRoute.BOOK_DETAIL)
                    route = appState.route
                },
                onBookChanged = { switchedBook ->
                    selectedBook = switchedBook
                    selectedBookUrl = switchedBook.bookUrl
                    selectedChapterIndex = null
                    selectedChapterPos = null
                },
                onOpenBookmark = { target ->
                    selectedBookUrl = target.bookUrl
                    selectedChapterIndex = target.chapterIndex
                    selectedChapterPos = target.chapterPos
                    selectedBook = library.book(target.bookUrl)
                    appState.openReader(AppRoute.BOOKMARKS)
                    route = appState.route
                },
                searchModel = searchModel,
                exploreModel = exploreModel,
                sourceModel = sourceModel,
                subscriptionModel = subscriptionModel,
                subscriptionPageModel = subscriptionPageModel,
                httpClient = httpClient,
                detailModel = detailModel,
                chapterDownloadModel = chapterDownloadModel,
                readerSettingsModel = readerSettingsModel,
                dataDirectory = dataDirectory,
                bookmarkModel = bookmarkModel,
                readRecordModel = readRecordModel,
                readRecordHeatmapModel = readRecordHeatmapModel,
                replaceRuleModel = replaceRuleModel,
                dictRuleModel = dictRuleModel,
                txtTocRuleModel = txtTocRuleModel,
                sourceFilterRuleModel = sourceFilterRuleModel,
                managementServerModel = managementServerModel,
                protocolRegistrationModel = protocolRegistrationModel,
                fileAssociationRegistrationModel = fileAssociationRegistrationModel,
                webDavSettingsModel = webDavSettingsModel,
                dataDirectoryMigrationModel = dataDirectoryMigrationModel,
                pendingDataMigration = pendingDataMigration,
                onMigrateDataDirectory = { targetDirectory ->
                    val migrationModel = dataDirectoryMigrationModel
                    if (migrationModel == null) {
                        backupFeedback = "当前数据存储不支持目录迁移"
                    } else {
                        val stopped = runCatching {
                            applicationLifecycle.stopForMigration(timeoutMillis = 35_000L)
                        }.getOrElse { false }
                        if (!stopped) {
                            backupFeedback = "后台任务未能及时停止，未执行数据目录迁移"
                        } else {
                            migrationModel.migrate(targetDirectory)
                                .onSuccess { result ->
                                    backupFeedback =
                                        "数据目录迁移完成，重启后生效。源数据快照：${result.sourceBackup}"
                                    pendingDataMigration = PendingDataMigration(result)
                                }
                                .onFailure { error ->
                                    applicationLifecycle.resumeAfterMigration()
                                    backupFeedback = error.message ?: "数据目录迁移失败"
                                }
                        }
                    }
                },
                onDataMigrationRestartFailed = { message ->
                    pendingDataMigration = pendingDataMigration?.restartFailed(message)
                },
                onRestartApplication = onRestartApplication,
                backupFeedback = backupFeedback,
                onDismissBackupFeedback = { backupFeedback = null },
                onBackupFeedback = { backupFeedback = it },
                onExportBackup = {
                    selectBackupFile("导出本地备份", FileDialog.SAVE)?.let { path ->
                        val result = BackupModel(library).export(path)
                        backupFeedback = result.summary?.let { summary ->
                            "备份已导出：${summary.books} 本书，${summary.chapters} 个章节"
                        } ?: result.error ?: "备份导出失败"
                    }
                },
                onImportBackup = {
                    selectBackupFile("导入本地备份", FileDialog.LOAD)?.let { path ->
                        val result = BackupModel(library).import(path)
                        if (result.isSuccess) {
                            refreshAfterBackupImport(
                                bookshelfModel = bookshelfModel,
                                sourceModel = sourceModel,
                                subscriptionModel = subscriptionModel,
                                subscriptionPageModel = subscriptionPageModel,
                                readerSettingsModel = readerSettingsModel
                            )
                            updateScheduler.reload()
                            bookshelfRefreshToken++
                        }
                        backupFeedback = result.summary?.let { summary ->
                            "备份已导入：${summary.books} 本书，${summary.chapters} 个章节"
                        } ?: result.error ?: "备份导入失败"
                    }
                },
                onWebDavRestoreSuccess = {
                    refreshAfterBackupImport(
                        bookshelfModel = bookshelfModel,
                        sourceModel = sourceModel,
                        subscriptionModel = subscriptionModel,
                        subscriptionPageModel = subscriptionPageModel,
                        readerSettingsModel = readerSettingsModel
                    )
                    updateScheduler.reload()
                    bookshelfRefreshToken++
                },
                onlineService = onlineService,
                onBookshelfChanged = { bookshelfRefreshToken++ },
                selectedBookUrl = selectedBookUrl,
                selectedChapterIndex = selectedChapterIndex,
                selectedChapterPos = selectedChapterPos,
                onSelectChapter = {
                    selectedChapterIndex = it
                    selectedChapterPos = null
                },
                selectedBook = selectedBook,
                library = library,
                readerReturnLabel = appState.readerReturnLabel,
                onOpenReader = { bookUrl, returnRoute, chapterIndex, chapterPos ->
                    selectedBookUrl = bookUrl
                    selectedChapterIndex = chapterIndex
                    selectedChapterPos = chapterPos
                    selectedBook = library.book(bookUrl)
                    appState.openReader(returnRoute)
                    route = appState.route
                },
                onCloseReader = {
                    route = appState.closeReader()
                }
            )
        }
    }
}

@Composable
private fun AppShell(
    route: AppRoute,
    welcomeModel: WelcomeModel?,
    launchRequest: DesktopLaunchRequest?,
    onRouteChange: (AppRoute) -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    bookshelfModel: BookshelfModel,
    bookUpdateModel: BookUpdateModel,
    updateScheduler: BookUpdateScheduler,
    bookshelfRefreshToken: Int,
    importError: String?,
    onDismissImportError: () -> Unit,
    onImport: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenDetail: (CoreBook) -> Unit,
    onBookChanged: (CoreBook) -> Unit,
    onOpenBookmark: (BookmarkOpenTarget) -> Unit,
    searchModel: SearchModel,
    exploreModel: ExploreModel,
    sourceModel: SourceModel,
    subscriptionModel: SubscriptionModel,
    subscriptionPageModel: SubscriptionPageModel,
    httpClient: CoreHttpClient,
    detailModel: BookDetailModel,
    chapterDownloadModel: ChapterDownloadModel,
    readerSettingsModel: ReaderSettingsModel,
    dataDirectory: Path?,
    bookmarkModel: BookmarkModel,
    readRecordModel: ReadRecordModel,
    readRecordHeatmapModel: ReadRecordHeatmapModel,
    replaceRuleModel: ReplaceRuleModel,
    dictRuleModel: DictRuleModel,
    txtTocRuleModel: TxtTocRuleModel,
    sourceFilterRuleModel: SourceFilterRuleModel,
    managementServerModel: DesktopManagementServerModel,
    protocolRegistrationModel: WindowsProtocolRegistrationModel,
    fileAssociationRegistrationModel: WindowsFileAssociationRegistrationModel,
    webDavSettingsModel: WebDavSettingsModel?,
    dataDirectoryMigrationModel: DataDirectoryMigrationModel?,
    pendingDataMigration: PendingDataMigration?,
    onMigrateDataDirectory: (Path) -> Unit,
    onDataMigrationRestartFailed: (String) -> Unit,
    onRestartApplication: (Path, (String) -> Unit) -> Unit,
    backupFeedback: String?,
    onDismissBackupFeedback: () -> Unit,
    onBackupFeedback: (String) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onWebDavRestoreSuccess: () -> Unit,
    onlineService: OnlineBookService,
    onBookshelfChanged: () -> Unit,
    selectedBookUrl: String?,
    selectedChapterIndex: Int?,
    selectedChapterPos: Int?,
    onSelectChapter: (Int) -> Unit,
    selectedBook: CoreBook?,
    library: CoreLibrary,
    readerReturnLabel: String,
    onOpenReader: (String, AppRoute, Int?, Int?) -> Unit,
    onCloseReader: () -> Unit
) {
    bookshelfRefreshToken
    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            header = {
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (darkTheme) Icons.Default.WbSunny else Icons.Default.DarkMode,
                        contentDescription = if (darkTheme) "切换浅色主题" else "切换深色主题"
                    )
                }
            }
        ) {
            AppRoute.primary.forEach { item ->
                NavigationRailItem(
                    selected = route == item,
                    onClick = { onRouteChange(item) },
                    icon = { Icon(item.icon(), contentDescription = item.label) },
                    label = { Text(item.label) }
                )
            }
        }
        when (route) {
            AppRoute.WELCOME -> WelcomeScreen(
                model = welcomeModel,
                onComplete = {
                    welcomeModel?.completeSetup()
                    onRouteChange(AppRoute.BOOKSHELF)
                }
            )
            AppRoute.BOOKSHELF -> BookshelfScreen(
                model = bookshelfModel,
                updateModel = bookUpdateModel,
                onNavigate = onRouteChange,
                onImport = onImport,
                importError = importError,
                onDismissImportError = onDismissImportError,
                onOpenBook = onOpenBook
            )
            AppRoute.SEARCH -> SearchScreen(
                model = searchModel,
                sourceModel = sourceModel,
                onBookshelfChanged = onBookshelfChanged,
                onOpenDetail = onOpenDetail
            )
            AppRoute.EXPLORE -> ExploreScreen(
                model = exploreModel,
                sourceModel = sourceModel,
                onBookshelfChanged = onBookshelfChanged,
                onOpenDetail = onOpenDetail
            )
            AppRoute.BOOK_DETAIL -> selectedBook?.let { book ->
                BookDetailScreen(
                    model = detailModel,
                    sourceModel = sourceModel,
                    initialBook = book,
                    library = library,
                    downloadModel = chapterDownloadModel,
                    onBack = { onRouteChange(AppRoute.SEARCH) },
                    onOpenChapter = { chapter ->
                        onOpenReader(book.bookUrl, AppRoute.BOOK_DETAIL, chapter.index, null)
                    },
                    onBookChanged = onBookChanged,
                    onBookshelfChanged = onBookshelfChanged
                )
            } ?: PlaceholderScreen(AppRoute.BOOK_DETAIL)
            AppRoute.SOURCES -> SourcesScreen(
                model = sourceModel,
                httpClient = httpClient,
                library = library
            )
            AppRoute.SUBSCRIPTIONS -> SubscriptionScreen(
                model = subscriptionModel,
                sourceModel = sourceModel,
                pageModel = subscriptionPageModel,
                httpClient = httpClient,
                initialOnlineImportUrl = launchRequest?.onlineImportUrl,
                initialSubscriptionPageUrl = launchRequest?.subscriptionPageUrl,
                onOpenArticle = { result ->
                    val book = subscriptionModel.openArticle(result)
                    onOpenReader(book.bookUrl, AppRoute.SUBSCRIPTIONS, null, null)
                }
            )
            AppRoute.SETTINGS -> SettingsScreen(
                model = readerSettingsModel,
                updateScheduler = updateScheduler,
                onOpenBookmarks = { onRouteChange(AppRoute.BOOKMARKS) },
                onOpenReadRecords = { onRouteChange(AppRoute.READ_RECORDS) },
                onOpenReplaceRules = { onRouteChange(AppRoute.REPLACE_RULES) },
                onOpenDictRules = { onRouteChange(AppRoute.DICT_RULES) },
                onOpenTxtTocRules = { onRouteChange(AppRoute.TXT_TOC_RULES) },
                onOpenSourceFilterRules = { onRouteChange(AppRoute.SOURCE_FILTER_RULES) },
                managementServerModel = managementServerModel,
                protocolRegistrationModel = protocolRegistrationModel,
                fileAssociationRegistrationModel = fileAssociationRegistrationModel,
                dataDirectory = dataDirectory,
                onOpenDataDirectory = {
                    runCatching { openDataDirectory(dataDirectory) }
                        .onSuccess { onBackupFeedback("已打开数据目录") }
                        .onFailure { error ->
                            onBackupFeedback(error.message ?: "无法打开数据目录")
                        }
                },
                dataDirectoryMigrationModel = dataDirectoryMigrationModel,
                pendingDataMigration = pendingDataMigration,
                onMigrateDataDirectory = onMigrateDataDirectory,
                onDataMigrationRestartFailed = onDataMigrationRestartFailed,
                onRestartApplication = onRestartApplication,
                onBackupFeedback = onBackupFeedback,
                backupFeedback = backupFeedback,
                onDismissBackupFeedback = onDismissBackupFeedback,
                onExportBackup = onExportBackup,
                onImportBackup = onImportBackup,
                webDavSettingsModel = webDavSettingsModel,
                onWebDavRestoreSuccess = onWebDavRestoreSuccess
            )
            AppRoute.BOOKMARKS -> BookmarksScreen(
                model = bookmarkModel,
                onBack = { onRouteChange(AppRoute.SETTINGS) },
                onOpenBookmark = onOpenBookmark
            )
            AppRoute.READ_RECORDS -> ReadRecordsScreen(
                model = readRecordModel,
                heatmapModel = readRecordHeatmapModel,
                onBack = { onRouteChange(AppRoute.SETTINGS) },
                onOpenBook = { bookUrl -> onOpenReader(bookUrl, AppRoute.READ_RECORDS, null, null) }
            )
            AppRoute.REPLACE_RULES -> ReplaceRulesScreen(
                model = replaceRuleModel,
                onBack = { onRouteChange(AppRoute.SETTINGS) }
            )
            AppRoute.DICT_RULES -> DictRulesScreen(
                model = dictRuleModel,
                onBack = { onRouteChange(AppRoute.SETTINGS) }
            )
            AppRoute.TXT_TOC_RULES -> TxtTocRulesScreen(
                model = txtTocRuleModel,
                onBack = { onRouteChange(AppRoute.SETTINGS) }
            )
            AppRoute.SOURCE_FILTER_RULES -> SourceFilterRulesScreen(
                model = sourceFilterRuleModel,
                onBack = { onRouteChange(AppRoute.SETTINGS) }
            )
            AppRoute.READER -> selectedBookUrl?.let { bookUrl ->
                val book = library.book(bookUrl)
                if (book != null && DesktopBookType.isLocalAudioBook(book)) {
                    AudioReaderScreen(
                        model = remember(bookUrl, selectedChapterIndex) {
                            AudioReaderModel(
                                library,
                                bookUrl,
                                startChapterIndex = selectedChapterIndex
                            )
                        },
                        onBack = onCloseReader,
                        onBackLabel = readerReturnLabel
                    )
                } else if (book != null && DesktopBookType.isLocalImageBook(book)) {
                    ImageReaderScreen(
                        model = remember(bookUrl, selectedChapterIndex, selectedChapterPos) {
                            ImageReaderModel(
                                library,
                                bookUrl,
                                startChapterIndex = selectedChapterIndex,
                                startChapterPageIndex = selectedChapterPos
                            )
                        },
                        onBack = onCloseReader,
                        onBackLabel = readerReturnLabel
                    )
                } else if (book != null && DesktopBookType.isOnlineImageBook(book)) {
                    OnlineImageReaderScreen(
                        model = remember(bookUrl, selectedChapterIndex, selectedChapterPos) {
                            OnlineImageReaderModel(
                                library = library,
                                bookUrl = bookUrl,
                                onlineService = onlineService,
                                httpClient = httpClient
                            )
                        },
                        sourceModel = sourceModel,
                        onBack = onCloseReader,
                        onBackLabel = readerReturnLabel
                    )
                } else {
                    ReaderScreen(
                        model = remember(bookUrl, selectedChapterIndex, selectedChapterPos) {
                            ReaderModel(
                                library,
                                bookUrl,
                                onlineService,
                                startChapterIndex = selectedChapterIndex,
                                startPosition = selectedChapterPos
                            )
                        },
                        settingsModel = readerSettingsModel,
                        dictionaryModel = remember { ReaderDictionaryModel(library, httpClient) },
                        sourceModel = sourceModel,
                        onBack = onCloseReader,
                        onBackLabel = readerReturnLabel
                    )
                }
            } ?: PlaceholderScreen(AppRoute.READER)
        }
    }
}

@Composable
private fun WelcomeScreen(
    model: WelcomeModel?,
    onComplete: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Book,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text("欢迎使用 Legado", style = MaterialTheme.typography.headlineMedium)
            Text(
                "你的书架、书源和阅读数据会保存在当前数据目录中。完成初始化后即可开始使用桌面阅读器。",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onComplete,
                enabled = model != null
            ) {
                Text("开始使用")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BookshelfScreen(
    model: BookshelfModel,
    updateModel: BookUpdateModel,
    onNavigate: (AppRoute) -> Unit,
    onImport: () -> Unit,
    importError: String?,
    onDismissImportError: () -> Unit,
    onOpenBook: (String) -> Unit
) {
    var query by remember { mutableStateOf(model.query) }
    var groupMenuExpanded by remember { mutableStateOf(false) }
    var batchGroupMenuExpanded by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedBooks by remember { mutableStateOf(emptySet<String>()) }
    var groupEditorVisible by remember { mutableStateOf(false) }
    var groupEditorId by remember { mutableStateOf<Long?>(null) }
    var groupEditorName by remember { mutableStateOf("") }
    var groupDeleteTarget by remember { mutableStateOf<CoreBookGroup?>(null) }
    var batchDeleteConfirmVisible by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    var updateResults by remember { mutableStateOf(emptyMap<String, BookUpdateResult>()) }
    var updateTask by remember { mutableStateOf<BookUpdateTask?>(null) }
    var updateTaskState by remember { mutableStateOf(BookUpdateTaskState()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(updateTask) {
        val task = updateTask ?: return@LaunchedEffect
        task.state.collect { state ->
            updateTaskState = state
            updateResults = state.results.associateBy { it.book.bookUrl }
            if (state.status in setOf(
                    BookUpdateTaskStatus.COMPLETED,
                    BookUpdateTaskStatus.CANCELLED,
                    BookUpdateTaskStatus.FAILED
                )
            ) {
                feedback = updateSummary(state.results, state.status)
                revision++
            }
        }
    }
    revision
    val books = model.visibleBooks()
    val groups = modelGroups(model)
    val userGroups = model.availableGroups().filter { it.groupId > 0 }
    val updateTaskActive = updateTaskState.status in setOf(
        BookUpdateTaskStatus.RUNNING,
        BookUpdateTaskStatus.PAUSED
    )

    fun closeSelectionMode() {
        selectionMode = false
        selectedBooks = emptySet()
        batchGroupMenuExpanded = false
    }

    fun openGroupEditor(group: CoreBookGroup?) {
        groupEditorId = group?.groupId
        groupEditorName = group?.groupName.orEmpty()
        groupEditorVisible = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书架") },
                actions = {
                    IconButton(
                        enabled = !updateTaskActive,
                        onClick = {
                            val task = updateModel.createUpdateTask(maxRetries = 2)
                            updateTask = task
                            updateTaskState = task.state.value
                            updateResults = emptyMap()
                            feedback = "正在检查书籍更新..."
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { task.run() }
                                } catch (error: Throwable) {
                                    if (task.state.value.status != BookUpdateTaskStatus.FAILED) {
                                        feedback = error.message ?: "更新检查失败"
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "检查书籍更新")
                    }
                    if (updateTaskActive) {
                        IconButton(
                            onClick = {
                                if (updateTaskState.status == BookUpdateTaskStatus.PAUSED) {
                                    updateTask?.resume()
                                } else {
                                    updateTask?.pause()
                                }
                            }
                        ) {
                            Icon(
                                if (updateTaskState.status == BookUpdateTaskStatus.PAUSED) {
                                    Icons.Default.PlayArrow
                                } else {
                                    Icons.Default.Pause
                                },
                                contentDescription = if (updateTaskState.status == BookUpdateTaskStatus.PAUSED) {
                                    "继续检查更新"
                                } else {
                                    "暂停检查更新"
                                }
                            )
                        }
                        IconButton(onClick = { updateTask?.cancel() }) {
                            Icon(Icons.Default.Stop, contentDescription = "取消检查更新")
                        }
                    }
                    IconButton(onClick = onImport) {
                        Icon(Icons.Default.FileOpen, contentDescription = "导入本地书籍")
                    }
                    IconButton(
                        onClick = {
                            selectionMode = !selectionMode
                            if (!selectionMode) selectedBooks = emptySet()
                        }
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = if (selectionMode) "退出批量选择" else "批量管理")
                    }
                    IconButton(onClick = { model.setQuery(query) }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索书架")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    model.setQuery(it)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索书名或作者") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    Button(onClick = { groupMenuExpanded = true }) {
                        Text(model.selectedGroup?.groupName ?: "全部")
                    }
                    DropdownMenu(
                        expanded = groupMenuExpanded,
                        onDismissRequest = { groupMenuExpanded = false }
                    ) {
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.second) },
                                onClick = {
                                    model.selectGroup(group.first)
                                    selectedBooks = emptySet()
                                    groupMenuExpanded = false
                                    revision++
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = { openGroupEditor(null) }) {
                    Icon(Icons.Default.Add, contentDescription = "新建分组")
                }
                if (model.selectedGroupId > 0) {
                    IconButton(onClick = { openGroupEditor(model.selectedGroup) }) {
                        Icon(Icons.Default.Edit, contentDescription = "重命名分组")
                    }
                    IconButton(onClick = { groupDeleteTarget = model.selectedGroup }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除分组")
                    }
                }
            }
            if (selectionMode) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("已选择 ${selectedBooks.size} 本", modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            selectedBooks = if (selectedBooks.size == books.size) {
                                emptySet()
                            } else {
                                books.map(CoreBook::bookUrl).toSet()
                            }
                        },
                        enabled = books.isNotEmpty()
                    ) {
                        Text(if (selectedBooks.size == books.size && books.isNotEmpty()) "清空选择" else "全选")
                    }
                    Box {
                        Button(
                            onClick = { batchGroupMenuExpanded = true },
                            enabled = selectedBooks.isNotEmpty() && userGroups.isNotEmpty()
                        ) {
                            Text("分组")
                        }
                        DropdownMenu(
                            expanded = batchGroupMenuExpanded,
                            onDismissRequest = { batchGroupMenuExpanded = false }
                        ) {
                            userGroups.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text("移至 ${group.groupName}") },
                                    onClick = {
                                        model.moveBooksToGroup(selectedBooks, group.groupId)
                                        feedback = "已移至分组：${group.groupName}"
                                        batchGroupMenuExpanded = false
                                        revision++
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("添加到 ${group.groupName}") },
                                    onClick = {
                                        model.addBooksToGroup(selectedBooks, group.groupId)
                                        feedback = "已添加到分组：${group.groupName}"
                                        batchGroupMenuExpanded = false
                                        revision++
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("移出 ${group.groupName}") },
                                    onClick = {
                                        model.removeBooksFromGroup(selectedBooks, group.groupId)
                                        feedback = "已移出分组：${group.groupName}"
                                        batchGroupMenuExpanded = false
                                        revision++
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { batchDeleteConfirmVisible = true },
                        enabled = selectedBooks.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "删除所选书籍")
                    }
                }
            }
            feedback?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(message, modifier = Modifier.fillMaxWidth())
            }
            if (updateTaskActive) {
                Spacer(Modifier.height(10.dp))
                val progress = if (updateTaskState.total == 0) {
                    0f
                } else {
                    updateTaskState.completed.toFloat() / updateTaskState.total
                }
                Text(
                    text = buildString {
                        append("更新进度：${updateTaskState.completed}/${updateTaskState.total}")
                        updateTaskState.currentBook?.let {
                            append(" · ${it.name.ifBlank { "未命名书籍" }}")
                        }
                        if (updateTaskState.currentAttempt > 1) {
                            append(" · 第 ${updateTaskState.currentAttempt} 次尝试")
                        }
                        if (updateTaskState.status == BookUpdateTaskStatus.PAUSED) {
                            append(" · 已暂停")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            importError?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onDismissImportError)
                )
            }
            Spacer(Modifier.height(24.dp))
            if (books.isEmpty()) {
                EmptyBookshelf(onSearch = { onNavigate(AppRoute.SEARCH) })
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(books, key = CoreBook::bookUrl) { book ->
                        BookTile(
                            book = book,
                            selected = book.bookUrl in selectedBooks,
                            selectionMode = selectionMode,
                            updateResult = updateResults[book.bookUrl],
                            updateItem = updateTaskState.items.firstOrNull { it.book.bookUrl == book.bookUrl },
                            onClick = {
                                if (selectionMode) {
                                    selectedBooks = if (book.bookUrl in selectedBooks) {
                                        selectedBooks - book.bookUrl
                                    } else {
                                        selectedBooks + book.bookUrl
                                    }
                                } else {
                                    onOpenBook(book.bookUrl)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (groupEditorVisible) {
        AlertDialog(
            onDismissRequest = { groupEditorVisible = false },
            title = { Text(if (groupEditorId == null) "新建分组" else "重命名分组") },
            text = {
                OutlinedTextField(
                    value = groupEditorName,
                    onValueChange = { groupEditorName = it },
                    singleLine = true,
                    label = { Text("分组名称") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            if (groupEditorId == null) {
                                model.createGroup(groupEditorName)
                            } else {
                                check(model.renameGroup(groupEditorId!!, groupEditorName)) { "分组不存在" }
                            }
                        }.onSuccess {
                            groupEditorVisible = false
                            feedback = if (groupEditorId == null) "分组已创建" else "分组已重命名"
                            revision++
                        }.onFailure { error ->
                            feedback = error.message ?: "分组操作失败"
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { groupEditorVisible = false }) { Text("取消") }
            }
        )
    }

    groupDeleteTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { groupDeleteTarget = null },
            title = { Text("删除分组") },
            text = { Text("删除“${group.groupName}”后，书籍会保留但移出该分组。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.deleteGroup(group.groupId)
                        groupDeleteTarget = null
                        feedback = "分组已删除"
                        revision++
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { groupDeleteTarget = null }) { Text("取消") }
            }
        )
    }

    if (batchDeleteConfirmVisible) {
        AlertDialog(
            onDismissRequest = { batchDeleteConfirmVisible = false },
            title = { Text("删除所选书籍") },
            text = { Text("确定删除已选择的 ${selectedBooks.size} 本书及其章节吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val count = model.deleteBooks(selectedBooks)
                        feedback = "已删除 $count 本书"
                        batchDeleteConfirmVisible = false
                        closeSelectionMode()
                        revision++
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { batchDeleteConfirmVisible = false }) { Text("取消") }
            }
        )
    }
}

private fun modelGroups(model: BookshelfModel): List<Pair<Long, String>> = buildList {
    add(-1L to "全部")
    model.availableGroups().forEach { group ->
        if (group.groupId != -1L) add(group.groupId to group.groupName)
    }
}

private fun updateSummary(
    results: List<BookUpdateResult>,
    status: BookUpdateTaskStatus
): String {
    if (status == BookUpdateTaskStatus.CANCELLED) {
        return "更新已取消：已完成 ${results.size} 本书"
    }
    if (status == BookUpdateTaskStatus.FAILED) {
        return "更新任务失败：${results.lastOrNull()?.error ?: "未知错误"}"
    }
    if (results.isEmpty()) return "没有可检查的在线书籍"
    val updated = results.filter { it.status == BookUpdateStatus.UPDATED }
    val failed = results.filter { it.status == BookUpdateStatus.FAILED }
    val noUpdateCount = results.count { it.status == BookUpdateStatus.NO_UPDATE }
    return buildString {
        append("检查完成：${updated.size} 本书有更新，共新增 ${updated.sumOf { it.newChapterCount }} 章")
        if (noUpdateCount > 0) append("，$noUpdateCount 本书无更新")
        if (failed.isNotEmpty()) {
            append("\n失败：")
            append(
                failed.joinToString("；") {
                    "${it.book.name.ifBlank { "未命名书籍" }}：${it.error ?: "更新检查失败"}"
                }
            )
        }
    }
}

@Composable
private fun EmptyBookshelf(onSearch: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Book,
                contentDescription = null,
                modifier = Modifier.width(56.dp).height(56.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("书架还是空的", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "从搜索或导入开始添加你的第一本书",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSearch) { Text("去搜索") }
        }
    }
}

@Composable
private fun BookTile(
    book: CoreBook,
    selected: Boolean,
    selectionMode: Boolean,
    updateResult: BookUpdateResult?,
    updateItem: BookUpdateItemState?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        tonalElevation = if (selected) 6.dp else 2.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    book.name.ifBlank { "未命名书籍" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onClick() })
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(book.author.ifBlank { "未知作者" }, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                book.durChapterTitle ?: "尚未开始阅读",
                style = MaterialTheme.typography.bodySmall
            )
            updateResult?.let { result ->
                Spacer(Modifier.height(6.dp))
                val (message, color) = when (result.status) {
                    BookUpdateStatus.UPDATED -> "新增 ${result.newChapterCount} 章" to MaterialTheme.colorScheme.primary
                    BookUpdateStatus.NO_UPDATE -> "暂无更新" to MaterialTheme.colorScheme.onSurfaceVariant
                    BookUpdateStatus.FAILED -> "更新失败：${result.error ?: "未知错误"}" to MaterialTheme.colorScheme.error
                }
                Text(message, style = MaterialTheme.typography.bodySmall, color = color)
            }
            updateItem?.let { item ->
                if (item.result == null) {
                    Spacer(Modifier.height(6.dp))
                    val (message, color) = when (item.status) {
                        BookUpdateItemStatus.PENDING -> "等待更新" to MaterialTheme.colorScheme.onSurfaceVariant
                        BookUpdateItemStatus.RUNNING -> {
                            val attempt = if (item.attempt > 1) " · 第 ${item.attempt} 次尝试" else ""
                            "正在更新$attempt" to MaterialTheme.colorScheme.primary
                        }
                        BookUpdateItemStatus.CANCELLED -> "已取消" to MaterialTheme.colorScheme.onSurfaceVariant
                        else -> "" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    if (message.isNotBlank()) {
                        Text(message, style = MaterialTheme.typography.bodySmall, color = color)
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SearchScreen(
    model: SearchModel,
    sourceModel: SourceModel,
    onBookshelfChanged: () -> Unit,
    onOpenDetail: (CoreBook) -> Unit
) {
    var query by remember { mutableStateOf(model.query) }
    var revision by remember { mutableStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    revision
    val results = model.results

    Scaffold(
        topBar = { TopAppBar(title = { Text("搜索") }) }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        model.setQuery(it)
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("搜索书名或作者") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                Button(
                    onClick = {
                        model.setQuery(query)
                        searching = true
                        feedback = null
                        scope.launch {
                            withContext(Dispatchers.IO) { model.search() }
                            searching = false
                            revision++
                        }
                    },
                    enabled = !searching && query.isNotBlank()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (searching) "搜索中" else "搜索")
                }
            }
            feedback?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(18.dp))
            when {
                searching -> SearchEmptyState("正在搜索书源")
                model.error != null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(model.error!!, style = MaterialTheme.typography.bodyLarge)
                    if (model.sessionStatus == io.legado.core.source.CoreSourceSessionStatus.LOGIN_REQUIRED &&
                        model.loginUrl != null && model.loginSourceUrl != null
                    ) {
                        Button(onClick = {
                            runCatching {
                                sourceModel.openEmbeddedBrowserVerification(
                                    url = model.loginUrl!!,
                                    sourceUrl = model.loginSourceUrl!!,
                                    title = "书源登录"
                                )
                            }.onSuccess {
                                feedback = "已打开内置浏览器，请完成登录后重新搜索"
                            }.onFailure { error ->
                                feedback = error.message ?: "打开重新登录失败"
                            }
                        }) {
                            Text("重新登录")
                        }
                    }
                }
                results.isEmpty() -> SearchEmptyState(
                    if (query.isBlank()) "输入关键词开始搜索" else "没有找到匹配的书籍"
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(results, key = { result -> result.book.bookUrl }) { result ->
                        SearchResultRow(
                            result = result,
                            onOpenDetail = { onOpenDetail(result.book) },
                            onAdd = {
                                runCatching {
                                    model.addToBookshelf(result)
                                }.onSuccess {
                                    feedback = "已加入书架：${result.book.name}"
                                    onBookshelfChanged()
                                }.onFailure {
                                    feedback = it.message ?: "加入书架失败"
                                }
                                revision++
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExploreScreen(
    model: ExploreModel,
    sourceModel: SourceModel,
    onBookshelfChanged: () -> Unit,
    onOpenDetail: (CoreBook) -> Unit
) {
    var revision by remember { mutableStateOf(0) }
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    revision

    LaunchedEffect(Unit) {
        model.refreshSources()
        if (model.selectedSource != null && model.results.isEmpty()) {
            loading = true
            withContext(Dispatchers.IO) { model.load() }
            loading = false
            revision++
        }
    }

    fun loadPage(next: Boolean) {
        if (model.selectedSource == null || loading) return
        loading = true
        feedback = null
        scope.launch {
            withContext(Dispatchers.IO) {
                if (next) model.loadNextPage() else model.load()
            }
            loading = false
            revision++
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("发现") }) }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Button(onClick = { sourceMenuExpanded = true }, enabled = model.sources.isNotEmpty()) {
                        Icon(Icons.Default.Source, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(model.selectedSource?.bookSourceName ?: "选择书源")
                    }
                    DropdownMenu(
                        expanded = sourceMenuExpanded,
                        onDismissRequest = { sourceMenuExpanded = false }
                    ) {
                        model.sources.forEach { source ->
                            DropdownMenuItem(
                                text = { Text(source.bookSourceName.ifBlank { source.bookSourceUrl }) },
                                onClick = {
                                    sourceMenuExpanded = false
                                    model.selectSource(source)
                                    loadPage(next = false)
                                }
                            )
                        }
                    }
                }
                model.options.forEach { option ->
                    var optionMenuExpanded by remember(option.name) { mutableStateOf(false) }
                    Box {
                        Button(onClick = { optionMenuExpanded = true }) {
                            Text("${option.name}: ${option.options.firstOrNull { it.second == option.selectedValue }?.first ?: option.selectedValue}")
                        }
                        DropdownMenu(
                            expanded = optionMenuExpanded,
                            onDismissRequest = { optionMenuExpanded = false }
                        ) {
                            option.options.forEach { (label, value) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        optionMenuExpanded = false
                                        model.setOption(option.name, value)
                                        loadPage(next = false)
                                    }
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = { loadPage(next = false) }, enabled = !loading && model.selectedSource != null) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新发现内容")
                }
            }
            model.error?.let { message ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    if (model.sessionStatus == CoreSourceSessionStatus.LOGIN_REQUIRED &&
                        !model.loginUrl.isNullOrBlank() && !model.loginSourceUrl.isNullOrBlank()
                    ) {
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = {
                            runCatching {
                                sourceModel.openEmbeddedBrowserVerification(
                                    url = model.loginUrl!!,
                                    sourceUrl = model.loginSourceUrl!!,
                                    title = "书源登录"
                                )
                            }.onSuccess {
                                feedback = "已打开内置浏览器，请完成登录后重试"
                            }.onFailure { throwable ->
                                feedback = throwable.message ?: "打开重新登录失败"
                            }
                        }) { Text("重新登录") }
                    }
                }
            }
            if (model.filteredCount > 0 || model.invalidRuleCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "已按书源发现筛选规则隐藏 ${model.filteredCount} 条结果",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (model.invalidRuleCount > 0) {
                    Text(
                        text = "有 ${model.invalidRuleCount} 条已启用筛选规则无效，未参与过滤",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            feedback?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(message, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(18.dp))
            when {
                loading -> SearchEmptyState("正在加载发现内容")
                model.sources.isEmpty() -> SearchEmptyState("没有可用的发现书源")
                model.results.isEmpty() -> SearchEmptyState("暂无发现内容")
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(model.results, key = { result -> result.book.bookUrl }) { result ->
                        SearchResultRow(
                            result = result,
                            onOpenDetail = { onOpenDetail(result.book) },
                            onAdd = {
                                runCatching { model.addToBookshelf(result) }
                                    .onSuccess {
                                        feedback = "已加入书架：${result.book.name}"
                                        onBookshelfChanged()
                                    }
                                    .onFailure { feedback = it.message ?: "加入书架失败" }
                                revision++
                            }
                        )
                    }
                }
            }
            if (model.results.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("第 ${model.page} 页")
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { loadPage(next = true) }, enabled = !loading) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("加载下一页")
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: CoreSearchResult,
    onOpenDetail: () -> Unit,
    onAdd: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(result.book.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${result.book.author.ifBlank { "未知作者" }} · ${result.source.bookSourceName}",
                    style = MaterialTheme.typography.bodySmall
                )
                result.book.intro?.takeIf(String::isNotBlank)?.let { intro ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        intro,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "加入书架")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SubscriptionScreen(
    model: SubscriptionModel,
    sourceModel: SourceModel,
    pageModel: SubscriptionPageModel,
    httpClient: CoreHttpClient,
    initialOnlineImportUrl: String? = null,
    initialSubscriptionPageUrl: String? = null,
    onOpenArticle: (CoreSearchResult) -> Unit
) {
    var revision by remember { mutableStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    var onlineImportDialogVisible by remember { mutableStateOf(false) }
    var onlineImportUrl by remember { mutableStateOf("") }
    var onlineImporting by remember { mutableStateOf(false) }
    var onlineImportError by remember { mutableStateOf<String?>(null) }
    var importingDirectoryUrl by remember { mutableStateOf<String?>(null) }
    var importedDirectoryUrls by remember { mutableStateOf(emptySet<String>()) }
    var directoryUrl by remember {
        mutableStateOf(initialSubscriptionPageUrl ?: SubscriptionDirectoryModel.DEFAULT_DIRECTORY_URL)
    }
    var directoryPage by remember { mutableStateOf<SubscriptionDirectoryPage?>(null) }
    var directoryLoading by remember { mutableStateOf(false) }
    var directoryError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    revision

    fun startOnlineImport(url: String, directoryEntry: SubscriptionDirectoryEntry? = null) {
        val importTarget = url.trim()
        if (importTarget.isBlank()) return
        onlineImporting = true
        onlineImportError = null
        importingDirectoryUrl = directoryEntry?.importUrl
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { sourceModel.importOnline(importTarget) }
            }
            result.onSuccess { count ->
                model.refreshSources()
                feedback = "已导入 $count 个书源"
                directoryEntry?.importUrl?.let { importedDirectoryUrls = importedDirectoryUrls + it }
                onlineImportUrl = ""
                onlineImportDialogVisible = false
                revision++
            }.onFailure { error ->
                val message = error.message ?: "订阅源导入失败"
                if (directoryEntry != null) {
                    directoryError = message
                } else {
                    onlineImportError = message
                }
            }
            onlineImporting = false
            importingDirectoryUrl = null
        }
    }

    fun loadDirectory(url: String = directoryUrl) {
        val target = url.trim()
        if (target.isBlank()) {
            directoryError = "订阅目录地址不能为空"
            return
        }
        directoryLoading = true
        directoryError = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { SubscriptionDirectoryModel.loadPage(httpClient, target) }
            }.onSuccess { page ->
                directoryPage = page
                directoryUrl = page.url
                pageModel.savePage(page)
                feedback = "已保存订阅页：${page.title}"
                revision++
            }.onFailure { error ->
                directoryError = error.message ?: "订阅目录加载失败"
            }
            directoryLoading = false
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            model.refreshSources()
            if (model.selectedSource != null) model.refresh()
        }
        revision++
        loadDirectory(initialSubscriptionPageUrl ?: directoryUrl)
    }

    LaunchedEffect(initialOnlineImportUrl) {
        initialOnlineImportUrl?.let { url ->
            onlineImportUrl = url
            onlineImportDialogVisible = true
            startOnlineImport(url)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("订阅") },
                actions = {
                    IconButton(
                        onClick = {
                            onlineImportDialogVisible = true
                            onlineImportError = null
                        },
                        enabled = !onlineImporting
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "在线导入订阅源")
                    }
                    IconButton(onClick = {
                        val path = selectJsonFile("导入订阅源", FileDialog.LOAD) ?: return@IconButton
                        runCatching {
                            sourceModel.importJson(Files.readString(path, StandardCharsets.UTF_8))
                        }.onSuccess { count ->
                            model.refreshSources()
                            feedback = "已导入 $count 个书源"
                            revision++
                        }.onFailure { error ->
                            feedback = error.message ?: "订阅源导入失败"
                            revision++
                        }
                    }) {
                        Icon(Icons.Default.Upload, contentDescription = "导入订阅源")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (selectedTab == 0) {
                                    loadDirectory()
                                } else {
                                    withContext(Dispatchers.IO) {
                                        model.refreshSources()
                                        model.refresh()
                                    }
                                    revision++
                                }
                            }
                        },
                        enabled = !model.isLoading && !directoryLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = if (selectedTab == 0) "刷新订阅目录" else "刷新订阅文章")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            feedback?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
            }
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("订阅目录") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("我的文章") }
                )
            }
            Spacer(Modifier.height(14.dp))
            if (selectedTab == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = directoryUrl,
                        onValueChange = {
                            directoryUrl = it
                            directoryError = null
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("订阅目录地址") },
                        singleLine = true,
                        enabled = !directoryLoading
                    )
                    IconButton(onClick = { loadDirectory() }, enabled = !directoryLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "加载订阅目录")
                    }
                }
                Spacer(Modifier.height(12.dp))
                directoryError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                }
                if (directoryLoading) {
                    Text("正在加载订阅页", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                }
                val savedPages = pageModel.pages
                val currentPage = directoryPage
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("已保存订阅页", style = MaterialTheme.typography.titleMedium)
                    if (savedPages.isEmpty()) {
                        Text("加载网页后会显示在这里", style = MaterialTheme.typography.bodySmall)
                    } else {
                        savedPages.forEach { page ->
                            SubscriptionPageRow(
                                page = page,
                                refreshing = directoryLoading && directoryUrl == page.url,
                                onOpen = { openExternalUrl(page.url) },
                                onRefresh = { loadDirectory(page.url) },
                                onDelete = {
                                    pageModel.deletePage(page.url)
                                    if (directoryPage?.url == page.url) directoryPage = null
                                    feedback = "已删除订阅页：${page.title}"
                                    revision++
                                }
                            )
                        }
                    }

                    currentPage?.let { page ->
                        Spacer(Modifier.height(8.dp))
                        Text("${page.title} · 页面资源", style = MaterialTheme.typography.titleMedium)
                        if (page.navigationEntries.isNotEmpty()) {
                            Text("订阅页", style = MaterialTheme.typography.titleSmall)
                            page.navigationEntries.forEach { navigation ->
                                SubscriptionDirectoryRow(
                                    entry = navigation,
                                    importing = false,
                                    imported = false,
                                    onImport = {},
                                    onOpenExternal = { navigation.externalUrl?.let(::openExternalUrl) }
                                )
                            }
                        }
                        val sections = page.resourceSections()
                        val bookSources = sections.bookSources
                        val rules = sections.rules
                        val tts = sections.tts
                        val resources = sections.resources
                        val webPages = sections.webPages
                        val resourceEntries = bookSources + rules + tts + resources + webPages

                        @Composable
                        fun renderEntry(entry: SubscriptionDirectoryEntry) {
                            SubscriptionDirectoryRow(
                                entry = entry,
                                importing = onlineImporting && importingDirectoryUrl == entry.importUrl,
                                imported = entry.importUrl in importedDirectoryUrls,
                                onImport = { startOnlineImport(entry.importUrl.orEmpty(), entry) },
                                onOpenExternal = { entry.externalUrl?.let(::openExternalUrl) }
                            )
                        }

                        if (bookSources.isNotEmpty()) {
                            Text("书源", style = MaterialTheme.typography.titleSmall)
                            bookSources.forEach { entry -> renderEntry(entry) }
                        }
                        if (rules.isNotEmpty()) {
                            Text("规则", style = MaterialTheme.typography.titleSmall)
                            rules.forEach { entry -> renderEntry(entry) }
                        }
                        if (tts.isNotEmpty()) {
                            Text("朗读与 TTS", style = MaterialTheme.typography.titleSmall)
                            tts.forEach { entry -> renderEntry(entry) }
                        }
                        if (resources.isNotEmpty()) {
                            Text("其他资源", style = MaterialTheme.typography.titleSmall)
                            resources.forEach { entry -> renderEntry(entry) }
                        }
                        if (webPages.isNotEmpty()) {
                            Text("网页入口", style = MaterialTheme.typography.titleSmall)
                            webPages.forEach { entry -> renderEntry(entry) }
                        }
                        if (resourceEntries.isEmpty()) {
                            Text("该订阅页没有解析到可用资源", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (currentPage == null && savedPages.isEmpty() && !directoryLoading) {
                        Text("暂无可用订阅页")
                    }
                }
            } else {
                model.error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                }
                if (model.sources.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("还没有订阅源，请先在订阅目录中一键导入")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Button(onClick = { sourceMenuExpanded = true }) {
                                Text(model.selectedSource?.bookSourceName?.ifBlank { model.selectedSource?.bookSourceUrl.orEmpty() } ?: "选择订阅源")
                            }
                            DropdownMenu(
                                expanded = sourceMenuExpanded,
                                onDismissRequest = { sourceMenuExpanded = false }
                            ) {
                                model.sources.forEach { source ->
                                    DropdownMenuItem(
                                        text = { Text(source.bookSourceName.ifBlank { source.bookSourceUrl }) },
                                        onClick = {
                                            model.selectSource(source.bookSourceUrl)
                                            sourceMenuExpanded = false
                                            scope.launch {
                                                withContext(Dispatchers.IO) { model.refresh() }
                                                revision++
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        Text("${model.articles.size} 篇文章", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(18.dp))
                    when {
                        model.isLoading -> Text("正在刷新订阅")
                        model.articles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("当前订阅源暂无文章")
                        }
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(model.articles, key = { result -> result.book.bookUrl }) { result ->
                                SubscriptionArticleRow(
                                    result = result,
                                    onOpen = { onOpenArticle(result) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (onlineImportDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!onlineImporting) onlineImportDialogVisible = false
            },
            title = { Text("在线导入订阅源") },
            text = {
                Column {
                    OutlinedTextField(
                        value = onlineImportUrl,
                        onValueChange = {
                            onlineImportUrl = it
                            onlineImportError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("订阅源链接") },
                        placeholder = { Text("yuedu:// 或 http(s)://") },
                        enabled = !onlineImporting,
                        singleLine = false,
                        maxLines = 4
                    )
                    onlineImportError?.let { message ->
                        Spacer(Modifier.height(8.dp))
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        startOnlineImport(onlineImportUrl)
                    },
                    enabled = onlineImportUrl.isNotBlank() && !onlineImporting
                ) {
                    Text(if (onlineImporting) "导入中..." else "导入")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onlineImportDialogVisible = false },
                    enabled = !onlineImporting
                ) {
                    Text("取消")
                }
            }
        )
    }
}

internal fun refreshAfterBackupImport(
    bookshelfModel: BookshelfModel,
    sourceModel: SourceModel,
    subscriptionModel: SubscriptionModel,
    subscriptionPageModel: SubscriptionPageModel,
    readerSettingsModel: ReaderSettingsModel
) {
    bookshelfModel.refresh()
    sourceModel.refresh()
    subscriptionModel.refreshSources()
    subscriptionPageModel.refresh()
    readerSettingsModel.reload()
}

@Composable
private fun SubscriptionPageRow(
    page: CoreSubscriptionPage,
    refreshing: Boolean,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubscriptionPageIcon(page)
            Column(modifier = Modifier.weight(1f)) {
                Text(page.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(
                    page.url,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.Default.FileOpen, contentDescription = "打开订阅页")
            }
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新订阅页")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除订阅页")
            }
        }
    }
}

@Composable
private fun SubscriptionPageIcon(page: CoreSubscriptionPage) {
    var icon by remember(page.url, page.iconUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(page.url, page.iconUrl) {
        icon = withContext(Dispatchers.IO) {
            SubscriptionPageIconModel.candidateUrls(page).firstNotNullOfOrNull(::downloadSubscriptionIcon)
        }
    }
    if (icon == null) {
        Icon(
            Icons.Default.Subscriptions,
            contentDescription = "订阅页图标",
            modifier = Modifier.size(40.dp)
        )
    } else {
        Image(
            bitmap = icon!!,
            contentDescription = "${page.title}图标",
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

private fun downloadSubscriptionIcon(url: String): ImageBitmap? = runCatching {
    val connection = URL(url).openConnection() as? HttpURLConnection ?: return null
    try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "image/avif,image/webp,image/png,image/jpeg,image/*;q=0.8")
        connection.setRequestProperty("User-Agent", "Legado Desktop/0.1")
        if (connection.responseCode !in 200..299) return null
        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_ICON_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_ICON_BYTES) return null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } finally {
        connection.disconnect()
    }
}.getOrNull()

private const val MAX_ICON_BYTES = 512 * 1024
private const val DEFAULT_ICON_BUFFER_SIZE = 8 * 1024

private fun openExternalUrl(url: String) {
    runCatching {
        require(Desktop.isDesktopSupported()) { "系统不支持打开外部链接" }
        Desktop.getDesktop().browse(URI(url))
    }
}

private fun openDataDirectory(dataDirectory: Path?) {
    val directory = dataDirectory ?: error("数据目录尚未配置")
    Files.createDirectories(directory)
    require(Desktop.isDesktopSupported()) { "系统不支持打开数据目录" }
    require(Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) { "系统不支持打开数据目录" }
    Desktop.getDesktop().open(directory.toFile())
}

private fun selectDataDirectory(): Path? {
    val chooser = JFileChooser(FileSystemView.getFileSystemView()).apply {
        dialogTitle = "选择新的数据目录"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.toPath()
    } else {
        null
    }
}

@Composable
private fun SubscriptionDirectoryRow(
    entry: SubscriptionDirectoryEntry,
    importing: Boolean,
    imported: Boolean,
    onImport: () -> Unit,
    onOpenExternal: () -> Unit
) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                if (entry.tags.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Text(entry.tags.joinToString("  "), style = MaterialTheme.typography.labelMedium)
                }
                if (entry.description.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        entry.description,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (entry.updatedAt.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text("更新：${entry.updatedAt}", style = MaterialTheme.typography.bodySmall)
                }
            }
            when {
                entry.isImportable -> Button(onClick = onImport, enabled = !importing) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (importing) "导入中..." else if (imported) "再次导入" else "一键导入")
                }
                entry.externalUrl != null -> TextButton(onClick = onOpenExternal) {
                    Text("打开网页")
                }
            }
        }
    }
}

@Composable
private fun SubscriptionArticleRow(
    result: CoreSearchResult,
    onOpen: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(result.book.name.ifBlank { "未命名文章" }, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                result.book.author.ifBlank { result.source.bookSourceName },
                style = MaterialTheme.typography.bodySmall
            )
            result.book.intro?.takeIf(String::isNotBlank)?.let { intro ->
                Spacer(Modifier.height(8.dp))
                Text(
                    intro,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SourcesScreen(
    model: SourceModel,
    httpClient: CoreHttpClient,
    library: CoreLibrary
) {
    var revision by remember { mutableStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var editorState by remember { mutableStateOf<SourceEditorState?>(null) }
    revision
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书源") },
                actions = {
                    IconButton(onClick = {
                        editorState = SourceEditorState(null, """{
  "bookSourceUrl": "https://example.com",
  "bookSourceName": "新书源",
  "searchUrl": "",
  "ruleSearch": ""
}""")
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "新建书源")
                    }
                    IconButton(onClick = {
                        val path = selectJsonFile("导入书源", FileDialog.LOAD) ?: return@IconButton
                        runCatching {
                            model.importJson(Files.readString(path, StandardCharsets.UTF_8))
                        }.onSuccess { count ->
                            feedback = "已导入 $count 个书源"
                            revision++
                        }.onFailure { error ->
                            feedback = error.message ?: "书源导入失败"
                            revision++
                        }
                    }) {
                        Icon(Icons.Default.Upload, contentDescription = "导入书源")
                    }
                    IconButton(onClick = {
                        val path = selectJsonFile("导出书源", FileDialog.SAVE) ?: return@IconButton
                        runCatching {
                            Files.writeString(path, model.exportJson(), StandardCharsets.UTF_8)
                        }.onSuccess {
                            feedback = "书源已导出"
                            revision++
                        }.onFailure { error ->
                            feedback = error.message ?: "书源导出失败"
                            revision++
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "导出书源")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            model.refresh()
                            revision++
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新书源")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            feedback?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
            }
            if (model.sources.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有书源，请先导入 JSON 书源")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(model.sources, key = CoreBookSource::bookSourceUrl) { source ->
                        SourceRow(
                            source = source,
                            onEdit = { editorState = SourceEditorState(source.bookSourceUrl, BookSourceJsonCodec.encode(source)) },
                            onEnabledChange = { enabled ->
                                model.setEnabled(source.bookSourceUrl, enabled)
                                revision++
                            },
                            onDelete = {
                                model.delete(source.bookSourceUrl)
                                feedback = "已删除：${source.bookSourceName}"
                                revision++
                            }
                        )
                    }
                }
            }
        }
    }
    editorState?.let { state ->
        SourceEditorDialog(
            initialJson = state.json,
            originalBookSourceUrl = state.originalBookSourceUrl,
            model = model,
            httpClient = httpClient,
            library = library,
            onDismiss = { editorState = null },
            onSaved = { source ->
                editorState = null
                feedback = "已保存：${source.bookSourceName.ifBlank { source.bookSourceUrl }}"
                revision++
            }
        )
    }
}

private data class SourceEditorState(
    val originalBookSourceUrl: String?,
    val json: String
)

@Composable
private fun SourceRow(
    source: CoreBookSource,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        source.bookSourceName.ifBlank { "未命名书源" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        source.bookSourceUrl,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = source.enabled, onCheckedChange = onEnabledChange)
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑书源")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除书源")
                }
            }
            source.bookSourceComment?.takeIf(String::isNotBlank)?.let { comment ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(comment, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SourceEditorDialog(
    initialJson: String,
    originalBookSourceUrl: String?,
    model: SourceModel,
    httpClient: CoreHttpClient,
    library: CoreLibrary,
    onDismiss: () -> Unit,
    onSaved: (CoreBookSource) -> Unit
) {
    var json by remember(initialJson) { mutableStateOf(initialJson) }
    var ruleField by remember { mutableStateOf("ruleSearch.searchUrl") }
    var script by remember { mutableStateOf("return result;") }
    var input by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var debugUrl by remember(initialJson) { mutableStateOf(sourceUrlFromJson(initialJson)) }
    var debugHeaders by remember { mutableStateOf("") }
    var debugResult by remember { mutableStateOf<SourceDebugResult?>(null) }
    var debugging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑书源") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("完整 JSON", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = json,
                    onValueChange = { json = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                    minLines = 10,
                    label = { Text("书源 JSON") }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text("脚本测试", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = ruleField,
                    onValueChange = { ruleField = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("规则字段") }
                )
                OutlinedTextField(
                    value = script,
                    onValueChange = { script = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("JavaScript") }
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("输入内容") }
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("基础 URL，可选") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                testing = true
                                feedback = withContext(Dispatchers.IO) {
                                    runCatching {
                                        "结果：${model.testScript(json, ruleField, script, input, baseUrl)}"
                                    }.getOrElse { error -> error.message ?: "脚本测试失败" }
                                }
                                testing = false
                            }
                        },
                        enabled = !testing
                    ) {
                        Text(if (testing) "测试中" else "测试脚本")
                    }
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
                feedback?.let { message ->
                    Text(
                        text = message,
                        color = if (message.startsWith("结果：")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text("请求检查", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = debugUrl,
                    onValueChange = { debugUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("请求 URL") }
                )
                OutlinedTextField(
                    value = debugHeaders,
                    onValueChange = { debugHeaders = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("请求 headers（JSON 或每行 name: value）") }
                )
                Button(
                    onClick = {
                        scope.launch {
                            debugging = true
                            debugResult = withContext(Dispatchers.IO) {
                                SourceDebugModel(httpClient = httpClient, library = library).execute(
                                    SourceDebugRequest(
                                        url = debugUrl,
                                        headersText = debugHeaders,
                                        sourceJson = json
                                    )
                                )
                            }
                            debugging = false
                        }
                    },
                    enabled = !debugging && debugUrl.isNotBlank()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (debugging) "请求中" else "检查请求")
                }
                Button(
                    onClick = {
                        runCatching {
                            model.openEmbeddedBrowserVerification(
                                url = debugResult?.loginUrl ?: debugUrl,
                                sourceUrl = sourceUrlFromJson(json),
                                title = sourceNameFromJson(json)
                            )
                        }.onSuccess { request ->
                            feedback = "已在内置浏览器打开验证：${request.title.ifBlank { request.url }}"
                        }.onFailure { error ->
                            feedback = error.message ?: "打开浏览器验证失败"
                        }
                    },
                    enabled = !debugging && debugUrl.isNotBlank()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (debugResult?.sessionStatus == SourceSessionStatus.LOGIN_REQUIRED) "重新登录" else "内置浏览器验证")
                }
                debugResult?.let { result ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (result.sessionStatus == SourceSessionStatus.LOGIN_REQUIRED) {
                            Text("当前书源需要重新登录", color = MaterialTheme.colorScheme.error)
                        }
                        Text(
                            when {
                                result.error != null && result.statusCode != null -> "状态：${result.error}"
                                result.error != null -> "失败：${result.error}"
                                else -> "状态：HTTP ${result.statusCode}"
                            },
                            color = if (result.error == null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        result.requestUrl?.let {
                            Text("请求 URL：$it", style = MaterialTheme.typography.bodySmall)
                        }
                        result.requestMethod?.let {
                            Text("请求方法：$it", style = MaterialTheme.typography.bodySmall)
                        }
                        if (result.requestHeaders.isNotEmpty()) {
                            Text("请求 headers", style = MaterialTheme.typography.labelLarge)
                            SelectionContainer {
                                Text(
                                    result.requestHeaders.entries.joinToString("\n") { (name, value) ->
                                        "$name: $value"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        result.requestBody?.let { body ->
                            Text("请求 body", style = MaterialTheme.typography.labelLarge)
                            Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                                SelectionContainer {
                                    Text(
                                        body,
                                        modifier = Modifier.padding(8.dp),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        result.finalUrl?.let { Text("最终 URL：$it", style = MaterialTheme.typography.bodySmall) }
                        if (result.responseHeaders.isNotEmpty()) {
                            Text("响应 headers", style = MaterialTheme.typography.labelLarge)
                            SelectionContainer {
                                Text(
                                    result.responseHeaders.entries.joinToString("\n") { (name, values) ->
                                        "$name: ${values.joinToString(", ")}"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Text("响应正文", style = MaterialTheme.typography.labelLarge)
                        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                            SelectionContainer {
                                Text(
                                    result.body.ifBlank { "（空响应）" },
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                runCatching { model.saveJson(json, originalBookSourceUrl) }
                    .onSuccess(onSaved)
                    .onFailure { error -> feedback = error.message ?: "书源保存失败" }
            }) {
                Text("保存")
            }
        }
    )
}

private fun sourceUrlFromJson(json: String): String = runCatching {
    BookSourceJsonCodec.decode(json).singleOrNull()?.bookSourceUrl.orEmpty()
}.getOrDefault("")

private fun sourceNameFromJson(json: String): String = runCatching {
    BookSourceJsonCodec.decode(json).singleOrNull()?.bookSourceName.orEmpty()
}.getOrDefault("")

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BookDetailScreen(
    model: BookDetailModel,
    sourceModel: SourceModel,
    initialBook: CoreBook,
    library: CoreLibrary,
    downloadModel: ChapterDownloadModel,
    onBack: () -> Unit,
    onOpenChapter: (io.legado.core.library.CoreChapter) -> Unit,
    onBookChanged: (CoreBook) -> Unit,
    onBookshelfChanged: () -> Unit
) {
    var revision by remember(initialBook.bookUrl) { mutableStateOf(0) }
    var feedback by remember(initialBook.bookUrl) { mutableStateOf<String?>(null) }
    var hasOpened by remember(initialBook.bookUrl) { mutableStateOf(false) }
    var sourceDialogVisible by remember(initialBook.bookUrl) { mutableStateOf(false) }
    var sourceLoading by remember(initialBook.bookUrl) { mutableStateOf(false) }
    var switchingSource by remember(initialBook.bookUrl) { mutableStateOf(false) }
    var selectedChapterUrls by remember(initialBook.bookUrl) { mutableStateOf(emptySet<String>()) }
    var downloadTask by remember(initialBook.bookUrl) { mutableStateOf<ChapterDownloadTask?>(null) }
    var downloadState by remember(initialBook.bookUrl) { mutableStateOf<ChapterDownloadTaskState?>(null) }
    val scope = rememberCoroutineScope()
    revision

    LaunchedEffect(downloadTask) {
        downloadTask?.state?.collect { state ->
            downloadState = state
        }
    }

    fun startDownload(chapters: List<io.legado.core.library.CoreChapter>) {
        val currentBook = model.book ?: initialBook
        if (chapters.isEmpty()) {
            feedback = "没有可下载的章节"
            return
        }
        if (downloadState?.status in setOf(
                ChapterDownloadTaskStatus.RUNNING,
                ChapterDownloadTaskStatus.PAUSED
            )
        ) {
            feedback = "已有章节下载任务正在执行"
            return
        }
        val task = downloadModel.createTask(currentBook, chapters)
        downloadTask = task
        selectedChapterUrls = emptySet()
        feedback = "已加入 ${chapters.size} 个章节的下载任务"
        scope.launch(Dispatchers.IO) { task.run() }
    }

    LaunchedEffect(initialBook.bookUrl) {
        if (!hasOpened) {
            withContext(Dispatchers.IO) { model.open(initialBook) }
            downloadTask = downloadModel.taskForBook(model.book ?: initialBook)
            hasOpened = true
            revision++
        }
    }

    val book = model.book ?: initialBook
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book.name.ifBlank { "书籍详情" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { model.refreshChapters() }
                                revision++
                            }
                        },
                        enabled = !model.isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新目录")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(book.name.ifBlank { "未命名书籍" }, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(book.author.ifBlank { "未知作者" }, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${book.originName.ifBlank { book.origin }} · ${book.totalChapterNum} 章",
                        style = MaterialTheme.typography.bodySmall
                    )
                    book.intro?.takeIf(String::isNotBlank)?.let { intro ->
                        Spacer(Modifier.height(12.dp))
                        Text(intro, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            sourceDialogVisible = true
                            sourceLoading = true
                            scope.launch {
                                withContext(Dispatchers.IO) { model.loadSourceCandidates() }
                                sourceLoading = false
                                revision++
                            }
                        },
                        enabled = !sourceLoading && !switchingSource
                    ) {
                        Icon(Icons.Default.Source, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("切换书源")
                    }
                    Button(onClick = {
                        model.book?.let { libraryBook ->
                            library.saveBook(libraryBook)
                            onBookshelfChanged()
                            feedback = "已加入书架：${libraryBook.name}"
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("加入书架")
                    }
                }
            }
            feedback?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(message, color = MaterialTheme.colorScheme.primary)
            }
            if (model.isLoading) {
                Spacer(Modifier.height(16.dp))
                Text("正在加载详情和目录")
            }
            model.error?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { model.open(initialBook) }
                        revision++
                    }
                }) { Text("重试") }
                if (model.sessionStatus == io.legado.core.source.CoreSourceSessionStatus.LOGIN_REQUIRED &&
                    model.loginUrl != null && model.loginSourceUrl != null
                ) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        runCatching {
                            sourceModel.openEmbeddedBrowserVerification(
                                url = model.loginUrl!!,
                                sourceUrl = model.loginSourceUrl!!,
                                title = book.originName.ifBlank { "书源登录" }
                            )
                        }.onSuccess {
                            feedback = "已打开内置浏览器，请完成登录后重试"
                        }.onFailure { error ->
                            feedback = error.message ?: "打开重新登录失败"
                        }
                    }) { Text("重新登录") }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("目录", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            if (model.chapters.isEmpty()) {
                Text("暂无目录")
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            startDownload(model.chapters.filter { library.content(it) == null })
                        },
                        enabled = downloadState?.status !in setOf(
                            ChapterDownloadTaskStatus.RUNNING,
                            ChapterDownloadTaskStatus.PAUSED
                        )
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("下载未缓存")
                    }
                    Button(
                        onClick = {
                            startDownload(model.chapters.filter { it.url in selectedChapterUrls })
                        },
                        enabled = selectedChapterUrls.isNotEmpty() && downloadState?.status !in setOf(
                            ChapterDownloadTaskStatus.RUNNING,
                            ChapterDownloadTaskStatus.PAUSED
                        )
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("下载选中 (${selectedChapterUrls.size})")
                    }
                    Text(
                        "已缓存 ${model.chapters.count { library.content(it) != null }} / ${model.chapters.size}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                downloadState?.let { state ->
                    Spacer(Modifier.height(10.dp))
                    val progress = if (state.total == 0) 0f else state.completed.toFloat() / state.total
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "下载 ${state.completed}/${state.total}，成功 ${state.downloaded}，跳过 ${state.skipped}，失败 ${state.failed}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        when (state.status) {
                            ChapterDownloadTaskStatus.RUNNING -> IconButton(onClick = { downloadTask?.pause() }) {
                                Icon(Icons.Default.Pause, contentDescription = "暂停章节下载")
                            }
                            ChapterDownloadTaskStatus.PAUSED -> IconButton(onClick = { downloadTask?.resume() }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "继续章节下载")
                            }
                            else -> Unit
                        }
                        if (state.status in setOf(
                                ChapterDownloadTaskStatus.RUNNING,
                                ChapterDownloadTaskStatus.PAUSED
                            )
                        ) {
                            IconButton(onClick = { downloadTask?.cancel() }) {
                                Icon(Icons.Default.Stop, contentDescription = "取消章节下载")
                            }
                        }
                    }
                    state.currentChapter?.let { current ->
                        Text("正在下载：${current.title}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(model.chapters, key = { chapter -> chapter.url }) { chapter ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = chapter.url in selectedChapterUrls,
                                    onCheckedChange = { checked ->
                                        selectedChapterUrls = if (checked) {
                                            selectedChapterUrls + chapter.url
                                        } else {
                                            selectedChapterUrls - chapter.url
                                        }
                                    }
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onOpenChapter(chapter) }
                                        .padding(vertical = 12.dp)
                                ) {
                                    Text(chapter.title)
                                    if (library.content(chapter) != null) {
                                        Text("已缓存", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                downloadState?.items
                                    ?.firstOrNull { it.chapter.url == chapter.url }
                                    ?.let { item ->
                                        Text(
                                            when (item.status) {
                                                ChapterDownloadItemStatus.CACHED -> "已缓存"
                                                ChapterDownloadItemStatus.DOWNLOADED -> "已下载"
                                                ChapterDownloadItemStatus.FAILED -> "失败"
                                                ChapterDownloadItemStatus.CANCELLED -> "已取消"
                                                ChapterDownloadItemStatus.RUNNING -> "下载中"
                                                ChapterDownloadItemStatus.PENDING -> "等待中"
                                            },
                                            modifier = Modifier.padding(end = 14.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (item.status == ChapterDownloadItemStatus.FAILED) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
    if (sourceDialogVisible) {
        AlertDialog(
            onDismissRequest = { if (!switchingSource) sourceDialogVisible = false },
            title = { Text("选择书源") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when {
                        sourceLoading -> Text("正在搜索可用书源")
                        model.sourceCandidates.isEmpty() -> Text(
                            model.sourceError ?: "没有找到同名同作者的可用书源"
                        )
                        else -> model.sourceCandidates.forEach { candidate ->
                            val isCurrent = candidate.source.bookSourceUrl == book.origin
                            Surface(
                                tonalElevation = if (isCurrent) 2.dp else 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !switchingSource && !isCurrent) {
                                        switchingSource = true
                                        feedback = null
                                        scope.launch {
                                            val switched = withContext(Dispatchers.IO) {
                                                model.switchSource(candidate)
                                            }
                                            switchingSource = false
                                            if (switched != null) {
                                                sourceDialogVisible = false
                                                onBookChanged(switched)
                                                onBookshelfChanged()
                                                feedback = "已切换到：${candidate.source.bookSourceName}"
                                                revision++
                                            } else {
                                                revision++
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(candidate.source.bookSourceName.ifBlank { candidate.source.bookSourceUrl })
                                        Text(
                                            "${candidate.book.name} · ${candidate.book.author.ifBlank { "未知作者" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isCurrent) Text("当前")
                                }
                            }
                        }
                    }
                    if (switchingSource) {
                        Text("正在加载新书源目录")
                    }
                    model.sourceError?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { sourceDialogVisible = false },
                    enabled = !switchingSource
                ) { Text("关闭") }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SettingsScreen(
    model: ReaderSettingsModel,
    updateScheduler: BookUpdateScheduler,
    managementServerModel: DesktopManagementServerModel,
    protocolRegistrationModel: WindowsProtocolRegistrationModel,
    onOpenBookmarks: () -> Unit,
    onOpenReadRecords: () -> Unit,
    onOpenReplaceRules: () -> Unit,
    onOpenDictRules: () -> Unit,
    onOpenTxtTocRules: () -> Unit,
    onOpenSourceFilterRules: () -> Unit,
    dataDirectory: Path?,
    fileAssociationRegistrationModel: WindowsFileAssociationRegistrationModel,
    onOpenDataDirectory: () -> Unit,
    dataDirectoryMigrationModel: DataDirectoryMigrationModel?,
    pendingDataMigration: PendingDataMigration?,
    onMigrateDataDirectory: (Path) -> Unit,
    onDataMigrationRestartFailed: (String) -> Unit,
    onRestartApplication: (Path, (String) -> Unit) -> Unit,
    onBackupFeedback: (String) -> Unit,
    backupFeedback: String?,
    onDismissBackupFeedback: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    webDavSettingsModel: WebDavSettingsModel?,
    onWebDavRestoreSuccess: () -> Unit
) {
    var revision by remember { mutableStateOf(0) }
    revision
    val settings = model.settings
    val updateState by updateScheduler.state.collectAsState()
    val webDavScope = rememberCoroutineScope()
    val webDavState = webDavSettingsModel?.state
    Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Text("字号：${settings.textSize}")
            Slider(
                value = settings.textSize.toFloat(),
                onValueChange = {
                    model.update(textSize = it.toInt())
                    revision++
                },
                valueRange = 12f..40f,
                steps = 13
            )
            Text("行距：${settings.lineSpacingExtra}")
            Slider(
                value = settings.lineSpacingExtra.toFloat(),
                onValueChange = {
                    model.update(lineSpacingExtra = it.toInt())
                    revision++
                },
                valueRange = 0f..32f,
                steps = 15
            )
            Spacer(Modifier.height(8.dp))
            Text("主题", style = MaterialTheme.typography.titleMedium)
            CoreReaderTheme.entries.forEach { theme ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.theme == theme,
                        onClick = {
                            model.update(theme = theme)
                            revision++
                        }
                    )
                    Text(theme.displayName())
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("翻页模式", style = MaterialTheme.typography.titleMedium)
            CoreReaderPageMode.entries.forEach { mode ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.pageMode == mode,
                        onClick = {
                            model.update(pageMode = mode)
                            revision++
                        }
                    )
                    Text(if (mode == CoreReaderPageMode.SCROLL) "滚动" else "分页")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = settings.autoRead,
                    onCheckedChange = {
                        model.update(autoRead = it)
                        revision++
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text("自动阅读")
            }
            Text("自动阅读间隔：${settings.autoReadSpeedSeconds} 秒")
            Slider(
                value = settings.autoReadSpeedSeconds.toFloat(),
                onValueChange = {
                    model.update(autoReadSpeedSeconds = it.toInt())
                    revision++
                },
                valueRange = 1f..120f,
                steps = 118
            )
            Spacer(Modifier.height(20.dp))
            Text("后台更新", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = updateState.schedule.enabled,
                    onCheckedChange = {
                        updateScheduler.configure(it, updateState.schedule.intervalMinutes)
                        revision++
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text("定时检查书籍更新")
            }
            Text("更新间隔：${updateState.schedule.intervalMinutes} 分钟")
            Slider(
                value = updateState.schedule.intervalMinutes.toFloat(),
                onValueChange = {
                    updateScheduler.configure(updateState.schedule.enabled, it.toInt())
                    revision++
                },
                valueRange = 15f..1440f,
                steps = 94
            )
            Text(
                text = when (updateState.status) {
                    BookUpdateScheduleStatus.DISABLED -> "状态：已关闭"
                    BookUpdateScheduleStatus.WAITING -> "状态：等待下一次检查"
                    BookUpdateScheduleStatus.RUNNING -> "状态：正在检查"
                    BookUpdateScheduleStatus.COMPLETED -> "状态：最近一次检查已完成"
                    BookUpdateScheduleStatus.FAILED -> "状态：最近一次检查失败"
                },
                color = if (updateState.status == BookUpdateScheduleStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            updateState.schedule.lastSummary?.let { summary ->
                Text(
                    summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenReadRecords) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("阅读记录")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenBookmarks) {
                Icon(Icons.Default.Bookmark, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("书签")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenReplaceRules) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("替换规则")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenDictRules) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("字典规则")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenTxtTocRules) {
                Icon(Icons.Default.Book, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("TXT目录规则")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenSourceFilterRules) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("书源发现筛选")
            }
            Spacer(Modifier.height(24.dp))
            Text("数据管理", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text("当前数据目录", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = dataDirectory?.toString() ?: "未配置",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = onOpenDataDirectory,
                    enabled = dataDirectory != null
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "打开数据目录")
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    selectDataDirectory()?.let(onMigrateDataDirectory)
                },
                enabled = dataDirectoryMigrationModel != null
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("迁移数据目录")
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onExportBackup) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("导出本地备份")
                }
                Button(onClick = onImportBackup) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("导入本地备份")
                }
            }
            webDavSettingsModel?.let { webDavModel ->
                val current = webDavModel.state
                Spacer(Modifier.height(24.dp))
                Text("WebDAV 远程备份", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = current.url,
                    onValueChange = {
                        webDavModel.updateUrl(it)
                        revision++
                    },
                    label = { Text("WebDAV 地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = current.username,
                        onValueChange = {
                            webDavModel.updateUsername(it)
                            revision++
                        },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = current.password,
                        onValueChange = {
                            webDavModel.updatePassword(it)
                            revision++
                        },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            webDavScope.launch {
                                val result = withContext(Dispatchers.IO) { webDavModel.configure() }
                                revision++
                                onBackupFeedback(result.message ?: result.error ?: "WebDAV 配置失败")
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("保存并测试连接")
                    }
                    Button(
                        onClick = {
                            webDavScope.launch {
                                val result = withContext(Dispatchers.IO) { webDavModel.refresh() }
                                revision++
                                onBackupFeedback(result.message ?: result.error ?: "远端备份刷新失败")
                            }
                        },
                        enabled = webDavModel.isConfigured()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("刷新远端列表")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = current.remoteName,
                        onValueChange = {
                            webDavModel.updateRemoteName(it)
                            revision++
                        },
                        label = { Text("上传文件名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            selectBackupFile("上传 WebDAV 备份", FileDialog.LOAD)?.let { archive ->
                                webDavScope.launch {
                                    val result = withContext(Dispatchers.IO) { webDavModel.upload(archive) }
                                    revision++
                                    onBackupFeedback(result.message ?: result.error ?: "WebDAV 上传失败")
                                }
                            }
                        },
                        enabled = webDavModel.isConfigured()
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("上传")
                    }
                }
                if (current.backups.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("远端备份", style = MaterialTheme.typography.titleMedium)
                    current.backups.forEach { backup ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    webDavModel.selectBackup(backup.name)
                                    revision++
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = current.selectedBackupName == backup.name,
                                onClick = {
                                    webDavModel.selectBackup(backup.name)
                                    revision++
                                }
                            )
                            Text(
                                text = "${backup.name} (${backup.size} B)",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        webDavScope.launch {
                            val result = withContext(Dispatchers.IO) { webDavModel.restore() }
                            revision++
                            if (result.isSuccess) onWebDavRestoreSuccess()
                            onBackupFeedback(result.message ?: result.error ?: "WebDAV 恢复失败")
                        }
                    },
                    enabled = webDavModel.isConfigured() &&
                        current.selectedBackupName != null
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("恢复选中备份")
                }
                Spacer(Modifier.height(24.dp))
                Text("WebDAV 远端书库", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = current.bookDirectory,
                    onValueChange = {
                        webDavModel.updateBookDirectory(it)
                        revision++
                    },
                    label = { Text("书库目录") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            webDavScope.launch {
                                val result = withContext(Dispatchers.IO) { webDavModel.configure() }
                                revision++
                                onBackupFeedback(result.message ?: result.error ?: "WebDAV 书库配置失败")
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("保存书库目录")
                    }
                    Button(
                        onClick = {
                            webDavScope.launch {
                                val result = withContext(Dispatchers.IO) { webDavModel.refreshRemoteBooks() }
                                revision++
                                onBackupFeedback(result.message ?: result.error ?: "远端书库刷新失败")
                            }
                        },
                        enabled = webDavModel.isConfigured()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("刷新书库")
                    }
                    Button(
                        onClick = {
                            selectBackupFile("上传到 WebDAV 书库", FileDialog.LOAD)?.let { bookFile ->
                                webDavScope.launch {
                                    val result = withContext(Dispatchers.IO) { webDavModel.uploadBook(bookFile) }
                                    revision++
                                    onBackupFeedback(result.message ?: result.error ?: "WebDAV 书籍上传失败")
                                }
                            }
                        },
                        enabled = webDavModel.isConfigured()
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("上传书籍")
                    }
                }
                if (current.remoteBooks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    current.remoteBooks.forEach { remoteBook ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    webDavModel.selectRemoteBook(remoteBook.name)
                                    revision++
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = current.selectedRemoteBookName == remoteBook.name,
                                onClick = {
                                    webDavModel.selectRemoteBook(remoteBook.name)
                                    revision++
                                }
                            )
                            Text(
                                text = "${remoteBook.name} (${remoteBook.size} B)",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            webDavScope.launch {
                                val result = withContext(Dispatchers.IO) { webDavModel.downloadSelectedRemoteBook() }
                                revision++
                                if (result.isSuccess) onWebDavRestoreSuccess()
                                onBackupFeedback(result.message ?: result.error ?: "远端书籍下载失败")
                            }
                        },
                        enabled = webDavModel.isConfigured() && current.selectedRemoteBookName != null
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("下载并导入")
                    }
                    Button(
                        onClick = {
                            webDavScope.launch {
                                val result = withContext(Dispatchers.IO) { webDavModel.deleteSelectedRemoteBook() }
                                revision++
                                onBackupFeedback(result.message ?: result.error ?: "远端书籍删除失败")
                            }
                        },
                        enabled = webDavModel.isConfigured() && current.selectedRemoteBookName != null
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("删除远端书籍")
                    }
                }
                current.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Windows 协议关联", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "yuedu:// 和 legado:// 链接将在本地桌面应用中打开",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        protocolRegistrationModel.register()
                        revision++
                    },
                    enabled = protocolRegistrationModel.canRegister
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("注册协议关联")
                }
                Text(
                    protocolRegistrationModel.state.message,
                    modifier = Modifier.weight(1f),
                    color = if (protocolRegistrationModel.state.status == WindowsProtocolRegistrationStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            protocolRegistrationModel.state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
            Text("Windows 文件关联", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "打开已关联的 TXT、EPUB、图片、CBZ/ZIP 和本地音频文件时导入到书架",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        fileAssociationRegistrationModel.register()
                        revision++
                    },
                    enabled = fileAssociationRegistrationModel.canRegister
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("注册文件关联")
                }
                Text(
                    fileAssociationRegistrationModel.state.message,
                    modifier = Modifier.weight(1f),
                    color = if (fileAssociationRegistrationModel.state.status == WindowsFileAssociationStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            fileAssociationRegistrationModel.state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
            Text("本地管理服务", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                managementServerModel.supportedRoutesDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = managementServerModel.portInput,
                    onValueChange = {
                        managementServerModel.updatePort(it)
                        revision++
                    },
                    enabled = !managementServerModel.isRunning,
                    label = { Text("端口") },
                    singleLine = true,
                    modifier = Modifier.width(180.dp)
                )
                Button(
                    onClick = {
                        if (managementServerModel.isRunning) {
                            managementServerModel.stop()
                        } else {
                            managementServerModel.start()
                        }
                        revision++
                    }
                ) {
                    Icon(
                        if (managementServerModel.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (managementServerModel.isRunning) "停止" else "启动")
                }
            }
            Text(
                text = if (managementServerModel.isRunning) {
                    "状态：运行中"
                } else {
                    "状态：已停止"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (managementServerModel.endpoint.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        managementServerModel.endpoint,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val clipboardManager = LocalClipboardManager.current
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(managementServerModel.endpoint))
                        revision++
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "复制管理服务地址")
                    }
                }
            }
            if (managementServerModel.webSocketEndpoint.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        managementServerModel.webSocketEndpoint,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val clipboardManager = LocalClipboardManager.current
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(managementServerModel.webSocketEndpoint))
                        revision++
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "复制 WebSocket 地址")
                    }
                }
            }
            if (managementServerModel.message.isNotBlank()) {
                Text(
                    managementServerModel.message,
                    color = if (managementServerModel.message.contains("失败") ||
                        managementServerModel.message.contains("必须")
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            backupFeedback?.let { message ->
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(message, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismissBackupFeedback) {
                        Icon(Icons.Default.Delete, contentDescription = "关闭备份提示")
                    }
                }
            }
        }
    }
    pendingDataMigration?.let { pending ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("数据目录已迁移") },
            text = {
                Column {
                    Text(
                        "新目录：${pending.result.targetDirectory}\n" +
                            "旧数据快照：${pending.result.sourceBackup}\n\n" +
                            "必须立即重启应用后才能继续使用。"
                    )
                    pending.restartError?.let { error ->
                        Spacer(Modifier.height(12.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestartApplication(pending.result.targetDirectory) { message ->
                            onDataMigrationRestartFailed(message)
                        }
                    }
                ) { Text("立即重启") }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BookmarksScreen(
    model: BookmarkModel,
    onBack: () -> Unit,
    onOpenBookmark: (BookmarkOpenTarget) -> Unit
) {
    var query by remember { mutableStateOf(model.query) }
    var revision by remember { mutableStateOf(0) }
    revision
    val bookmarks = model.visibleBookmarks()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书签") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "返回设置")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 28.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    model.setQuery(it)
                    revision++
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                singleLine = true,
                label = { Text("搜索书签") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (query.isBlank()) "还没有书签" else "没有匹配的书签")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bookmarks, key = { it.bookmark.time }) { item ->
                        Surface(
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                model.open(item.bookmark)?.let(onOpenBookmark)
                            }
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            item.bookmark.bookName,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "${item.bookmark.chapterName} · ${item.bookmark.bookAuthor.ifBlank { "未知作者" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = {
                                        model.delete(item.bookmark.time)
                                        revision++
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除书签")
                                    }
                                }
                                Text(
                                    item.bookmark.content.ifBlank { item.bookmark.bookText },
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Text(
                                    "点击打开到书签位置",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
            }
        }
    }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReadRecordsScreen(
    model: ReadRecordModel,
    heatmapModel: ReadRecordHeatmapModel,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit
) {
    var query by remember { mutableStateOf(model.query) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    revision
    val summary = model.summary
    val records = model.visibleRecords()
    val heatmap = heatmapModel.month(displayedMonth.year, displayedMonth.monthValue)
    val currentMonth = YearMonth.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阅读记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "返回设置")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val path = selectMarkdownFile("导出阅读记录", FileDialog.SAVE) ?: return@IconButton
                        feedback = runCatching {
                            Files.writeString(path, model.exportMarkdown(), StandardCharsets.UTF_8)
                            "阅读记录已导出"
                        }.getOrElse { error -> error.message ?: "阅读记录导出失败" }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "导出阅读记录")
                    }
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.Default.History, contentDescription = "排序阅读记录")
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            ReadRecordSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.displayName()) },
                                    onClick = {
                                        model.setSort(sort)
                                        sortMenuExpanded = false
                                        revision++
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                ReadRecordStat("总时长", formatDuration(summary.totalSeconds))
                ReadRecordStat("书籍", "${summary.bookCount} 本")
                ReadRecordStat("会话", "${summary.sessionCount} 次")
            }
            feedback?.let { message ->
                Text(
                    message,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    model.setQuery(it)
                    revision++
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                singleLine = true,
                label = { Text("搜索书名") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            ReadRecordHeatmap(
                month = heatmap,
                canGoNext = displayedMonth < currentMonth,
                selectedDay = selectedDay,
                onPreviousMonth = {
                    displayedMonth = displayedMonth.minusMonths(1)
                    selectedDay = null
                },
                onNextMonth = {
                    if (displayedMonth < currentMonth) {
                        displayedMonth = displayedMonth.plusMonths(1)
                        selectedDay = null
                    }
                },
                onDaySelected = { day ->
                    selectedDay = if (selectedDay == day) null else day
                }
            )
            if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(if (query.isBlank()) "还没有阅读记录" else "没有匹配的阅读记录")
            }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(records, key = { it.bookName }) { item ->
                        Surface(
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                item.book?.let { onOpenBook(it.bookUrl) }
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.bookName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "累计 ${formatDuration(item.totalSeconds)} · ${item.recordCount} 次会话",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "最近阅读：${formatTimestamp(item.lastReadSec)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        model.deleteBookRecords(item.bookName)
                                        revision++
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除阅读记录")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadRecordHeatmap(
    month: ReadRecordHeatmapMonth,
    canGoNext: Boolean,
    selectedDay: Int?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (Int) -> Unit
) {
    val selected = selectedDay?.let { day -> month.days.getOrNull(day - 1) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一个月")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${month.year}年${month.month}月", style = MaterialTheme.typography.titleMedium)
                Text(
                    "本月 ${formatDuration(month.totalSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onNextMonth, enabled = canGoNext) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一个月")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val cells: List<ReadRecordHeatmapDay?> = buildList {
            repeat(month.firstWeekdayMonday) { add(null) }
            addAll(month.days)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().height(
                ReadRecordHeatmapLayout.gridHeightDp(
                    month.firstWeekdayMonday,
                    month.days.size
                ).dp
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            userScrollEnabled = false
        ) {
            items(cells) { day ->
                if (day == null) {
                    Spacer(Modifier.height(30.dp))
                } else {
                    val isSelected = selectedDay == day.day
                    Surface(
                        color = readRecordHeatmapColor(day.level),
                        shape = RoundedCornerShape(4.dp),
                        border = if (isSelected) {
                            androidx.compose.foundation.BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .clickable { onDaySelected(day.day) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                day.day.toString(),
                                color = readRecordHeatmapTextColor(day.level),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
        Text(
            selected?.let { "${month.year}年${month.month}月${it.day}日：${formatDuration(it.seconds)}" }
                ?: "点击日期查看当日阅读时长",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun readRecordHeatmapColor(level: Int): Color = when (level) {
    0 -> Color(0xFFE6E8EB)
    1 -> Color(0xFFD8F3DC)
    2 -> Color(0xFFB7E4C7)
    3 -> Color(0xFF74C69D)
    4 -> Color(0xFF40916C)
    else -> Color(0xFF1B4332)
}

private fun readRecordHeatmapTextColor(level: Int): Color = when (level) {
    0, 1, 2 -> Color(0xFF1B4332)
    else -> Color.White
}

@Composable
private fun ReadRecordStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun ReadRecordSort.displayName(): String = when (this) {
    ReadRecordSort.LAST_READ -> "按最近阅读"
    ReadRecordSort.TOTAL_SECONDS -> "按总时长"
    ReadRecordSort.BOOK_NAME -> "按书名"
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分"
        minutes > 0 -> "${minutes}分${seconds}秒"
        else -> "${seconds}秒"
    }
}

private fun formatTimestamp(seconds: Long): String {
    if (seconds <= 0L) return "未记录"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(seconds * 1000L))
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DictRulesScreen(
    model: DictRuleModel,
    onBack: () -> Unit
) {
    var revision by remember { mutableStateOf(0) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<CoreDictRule?>(null) }
    var testingRule by remember { mutableStateOf<CoreDictRule?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    revision

    fun openEditor(rule: CoreDictRule?) {
        editingRule = rule ?: CoreDictRule(name = "")
        editorOpen = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("字典规则") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "返回设置")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        model.refresh()
                        revision++
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新字典规则")
                    }
                    IconButton(onClick = {
                        selectJsonFile("导入字典规则", FileDialog.LOAD, "dict-rules.json")?.let { path ->
                            runCatching {
                                model.importJson(Files.readString(path, StandardCharsets.UTF_8))
                            }.onSuccess { count ->
                                feedback = "已导入 $count 条字典规则"
                                revision++
                            }.onFailure { error ->
                                feedback = error.message ?: "字典规则导入失败"
                            }
                        }
                    }) {
                        Icon(Icons.Default.Upload, contentDescription = "导入字典规则")
                    }
                    IconButton(onClick = {
                        selectJsonFile("导出字典规则", FileDialog.SAVE, "dict-rules.json")?.let { path ->
                            runCatching {
                                Files.writeString(path, model.exportJson(), StandardCharsets.UTF_8)
                            }.onSuccess {
                                feedback = "字典规则已导出"
                            }.onFailure { error ->
                                feedback = error.message ?: "字典规则导出失败"
                            }
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "导出字典规则")
                    }
                    IconButton(onClick = { openEditor(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "新增字典规则")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            feedback?.let { message ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(message, modifier = Modifier.weight(1f))
                    IconButton(onClick = { feedback = null }) {
                        Icon(Icons.Default.Delete, contentDescription = "关闭提示")
                    }
                }
            }
            if (model.rules.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("还没有字典规则")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(model.rules, key = { rule -> rule.name }) { rule ->
                        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        rule.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = {
                                            model.setEnabled(rule.name, it)
                                            revision++
                                        }
                                    )
                                }
                                Text(
                                    "请求：${rule.urlRule}",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "提取：${rule.showRule.ifBlank { "响应正文" }}",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = { testingRule = rule }) {
                                        Icon(Icons.Default.Search, contentDescription = "测试字典规则")
                                    }
                                    IconButton(onClick = { openEditor(rule) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "编辑字典规则")
                                    }
                                    IconButton(onClick = {
                                        model.delete(rule.name)
                                        revision++
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除字典规则")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        DictRuleEditorDialog(
            initialRule = requireNotNull(editingRule),
            onDismiss = { editorOpen = false },
            onSave = { rule ->
                runCatching {
                    model.save(rule, previousName = editingRule?.name)
                }.onSuccess {
                    editorOpen = false
                    feedback = "字典规则已保存"
                    revision++
                }.onFailure { error ->
                    feedback = error.message ?: "字典规则保存失败"
                }
            }
        )
    }
    testingRule?.let { rule ->
        DictRuleTestDialog(
            rule = rule,
            model = model,
            onDismiss = { testingRule = null }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DictRuleEditorDialog(
    initialRule: CoreDictRule,
    onDismiss: () -> Unit,
    onSave: (CoreDictRule) -> Unit
) {
    var name by remember(initialRule.name) { mutableStateOf(initialRule.name) }
    var urlRule by remember(initialRule.name) { mutableStateOf(initialRule.urlRule) }
    var showRule by remember(initialRule.name) { mutableStateOf(initialRule.showRule) }
    var enabled by remember(initialRule.name) { mutableStateOf(initialRule.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialRule.name.isBlank()) "新增字典规则" else "编辑字典规则")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = urlRule,
                    onValueChange = { urlRule = it },
                    label = { Text("请求地址，可使用 {{key}}") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = showRule,
                    onValueChange = { showRule = it },
                    label = { Text("提取规则，可留空") },
                    supportingText = { Text("支持 CSS、JSONPath 和简单正则") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Spacer(Modifier.width(8.dp))
                    Text("启用")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initialRule.copy(
                            name = name.trim(),
                            urlRule = urlRule.trim(),
                            showRule = showRule.trim(),
                            enabled = enabled
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DictRuleTestDialog(
    rule: CoreDictRule,
    model: DictRuleModel,
    onDismiss: () -> Unit
) {
    var input by remember(rule.name) { mutableStateOf("") }
    var result by remember(rule.name) { mutableStateOf<DictRuleTestResult?>(null) }
    var testing by remember(rule.name) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("测试字典规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("查询词") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                result?.let { testResult ->
                    Text(
                        text = testResult.text ?: testResult.error.orEmpty(),
                        color = if (testResult.isSuccess) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !testing,
                onClick = {
                    testing = true
                    scope.launch {
                        result = withContext(Dispatchers.IO) { model.test(rule, input) }
                        testing = false
                    }
                }
            ) { Text(if (testing) "测试中" else "测试") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TxtTocRulesScreen(
    model: TxtTocRuleModel,
    onBack: () -> Unit
) {
    var revision by remember { mutableStateOf(0) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<CoreTxtTocRule?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    revision

    fun openEditor(rule: CoreTxtTocRule?) {
        editingRule = rule ?: CoreTxtTocRule()
        editorOpen = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TXT目录规则") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "返回设置")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        model.refresh()
                        revision++
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新TXT目录规则")
                    }
                    IconButton(onClick = {
                        selectJsonFile("导入TXT目录规则", FileDialog.LOAD, "txt-toc-rules.json")?.let { path ->
                            runCatching {
                                model.importJson(Files.readString(path, StandardCharsets.UTF_8))
                            }.onSuccess { count ->
                                feedback = "已导入 $count 条TXT目录规则"
                                revision++
                            }.onFailure { error ->
                                feedback = error.message ?: "TXT目录规则导入失败"
                            }
                        }
                    }) {
                        Icon(Icons.Default.Upload, contentDescription = "导入TXT目录规则")
                    }
                    IconButton(onClick = {
                        selectJsonFile("导出TXT目录规则", FileDialog.SAVE, "txt-toc-rules.json")?.let { path ->
                            runCatching {
                                Files.writeString(path, model.exportJson(), StandardCharsets.UTF_8)
                            }.onSuccess {
                                feedback = "TXT目录规则已导出"
                            }.onFailure { error ->
                                feedback = error.message ?: "TXT目录规则导出失败"
                            }
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "导出TXT目录规则")
                    }
                    IconButton(onClick = { openEditor(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "新增TXT目录规则")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            feedback?.let { message ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(message, modifier = Modifier.weight(1f))
                    IconButton(onClick = { feedback = null }) {
                        Icon(Icons.Default.Delete, contentDescription = "关闭提示")
                    }
                }
            }
            if (model.rules.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("还没有TXT目录规则")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(model.rules, key = { rule -> rule.id }) { rule ->
                        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        rule.name.ifBlank { "未命名规则" },
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = rule.enable,
                                        onCheckedChange = {
                                            model.setEnabled(rule.id, it)
                                            revision++
                                        }
                                    )
                                }
                                Text(
                                    "规则：${rule.rule}",
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                rule.example?.takeIf(String::isNotBlank)?.let { example ->
                                    Text(
                                        "示例：$example",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    "顺序：${rule.serialNumber} · ${if (rule.enable) "已启用" else "已停用"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = { openEditor(rule) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "编辑TXT目录规则")
                                    }
                                    IconButton(onClick = {
                                        model.delete(rule.id)
                                        revision++
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除TXT目录规则")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        TxtTocRuleEditorDialog(
            initialRule = requireNotNull(editingRule),
            onDismiss = { editorOpen = false },
            onSave = { rule ->
                runCatching { model.save(rule) }
                    .onSuccess {
                        editorOpen = false
                        feedback = "TXT目录规则已保存"
                        revision++
                    }
                    .onFailure { error ->
                        feedback = error.message ?: "TXT目录规则保存失败"
                    }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TxtTocRuleEditorDialog(
    initialRule: CoreTxtTocRule,
    onDismiss: () -> Unit,
    onSave: (CoreTxtTocRule) -> Unit
) {
    var name by remember(initialRule.id) { mutableStateOf(initialRule.name) }
    var rule by remember(initialRule.id) { mutableStateOf(initialRule.rule) }
    var example by remember(initialRule.id) { mutableStateOf(initialRule.example.orEmpty()) }
    var enabled by remember(initialRule.id) { mutableStateOf(initialRule.enable) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialRule.name.isBlank()) "新增TXT目录规则" else "编辑TXT目录规则")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rule,
                    onValueChange = { rule = it },
                    label = { Text("目录正则规则") },
                    supportingText = { Text("每个匹配应包含章节标题捕获组") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = example,
                    onValueChange = { example = it },
                    label = { Text("示例，可留空") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Spacer(Modifier.width(8.dp))
                    Text("启用")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initialRule.copy(
                            name = name,
                            rule = rule,
                            example = example,
                            enable = enabled
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SourceFilterRulesScreen(
    model: SourceFilterRuleModel,
    onBack: () -> Unit
) {
    var revision by remember { mutableStateOf(0) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<CoreSourceFilterRule?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    revision

    fun openEditor(rule: CoreSourceFilterRule?) {
        editingRule = rule ?: CoreSourceFilterRule(id = "")
        editorOpen = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书源发现筛选") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "返回设置")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        model.refresh()
                        revision++
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新书源发现筛选")
                    }
                    IconButton(onClick = {
                        selectJsonFile("导入书源发现筛选", FileDialog.LOAD, "source-filter-rules.json")?.let { path ->
                            runCatching {
                                model.importJson(Files.readString(path, StandardCharsets.UTF_8))
                            }.onSuccess { count ->
                                feedback = "已导入 $count 条书源发现筛选"
                                revision++
                            }.onFailure { error ->
                                feedback = error.message ?: "书源发现筛选导入失败"
                            }
                        }
                    }) {
                        Icon(Icons.Default.Upload, contentDescription = "导入书源发现筛选")
                    }
                    IconButton(onClick = {
                        selectJsonFile("导出书源发现筛选", FileDialog.SAVE, "source-filter-rules.json")?.let { path ->
                            runCatching {
                                Files.writeString(path, model.exportJson(), StandardCharsets.UTF_8)
                            }.onSuccess {
                                feedback = "书源发现筛选已导出"
                            }.onFailure { error ->
                                feedback = error.message ?: "书源发现筛选导出失败"
                            }
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "导出书源发现筛选")
                    }
                    IconButton(onClick = { openEditor(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "新增书源发现筛选")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            feedback?.let { message ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(message, modifier = Modifier.weight(1f))
                    IconButton(onClick = { feedback = null }) {
                        Icon(Icons.Default.Delete, contentDescription = "关闭提示")
                    }
                }
            }
            if (model.rules.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("还没有书源发现筛选")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(model.rules, key = { rule -> rule.id }) { rule ->
                        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        rule.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = {
                                            model.setEnabled(rule.id, it)
                                            revision++
                                        }
                                    )
                                }
                                Text(
                                    "正则：${rule.pattern}",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("字段：${rule.fields}")
                                Text(
                                    "作用域：${rule.scope.ifBlank { "全部书源" }} · " +
                                        "顺序：${rule.order} · ${if (rule.enabled) "已启用" else "已停用"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = { openEditor(rule) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "编辑书源发现筛选")
                                    }
                                    IconButton(onClick = {
                                        model.delete(rule.id)
                                        revision++
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除书源发现筛选")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        SourceFilterRuleEditorDialog(
            initialRule = requireNotNull(editingRule),
            onDismiss = { editorOpen = false },
            onSave = { rule ->
                runCatching { model.save(rule) }
                    .onSuccess {
                        editorOpen = false
                        feedback = "书源发现筛选已保存"
                        revision++
                    }
                    .onFailure { error ->
                        feedback = error.message ?: "书源发现筛选保存失败"
                    }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SourceFilterRuleEditorDialog(
    initialRule: CoreSourceFilterRule,
    onDismiss: () -> Unit,
    onSave: (CoreSourceFilterRule) -> Unit
) {
    var name by remember(initialRule.id) { mutableStateOf(initialRule.name) }
    var pattern by remember(initialRule.id) { mutableStateOf(initialRule.pattern) }
    var fields by remember(initialRule.id) { mutableStateOf(initialRule.fields) }
    var scope by remember(initialRule.id) { mutableStateOf(initialRule.scope) }
    var enabled by remember(initialRule.id) { mutableStateOf(initialRule.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRule.id.isBlank()) "新增书源发现筛选" else "编辑书源发现筛选") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("正则") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fields,
                    onValueChange = { fields = it },
                    label = { Text("字段") },
                    supportingText = { Text("可选：NAME, AUTHOR, INTRO, KIND, WORD_COUNT；同一规则内为或") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scope,
                    onValueChange = { scope = it },
                    label = { Text("作用域") },
                    supportingText = { Text("留空表示全部书源；source::URL 表示指定书源；多个分组用逗号分隔") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Spacer(Modifier.width(8.dp))
                    Text("启用")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initialRule.copy(
                            name = name,
                            pattern = pattern,
                            fields = fields,
                            scope = scope,
                            enabled = enabled
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReplaceRulesScreen(
    model: ReplaceRuleModel,
    onBack: () -> Unit
) {
    var revision by remember { mutableStateOf(0) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<CoreReplaceRule?>(null) }
    var testingRule by remember { mutableStateOf<CoreReplaceRule?>(null) }
    revision

    fun openEditor(rule: CoreReplaceRule?) {
        editingRule = rule ?: CoreReplaceRule()
        editorOpen = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("替换规则") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "返回设置")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        model.refresh()
                        revision++
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新替换规则")
                    }
                    IconButton(onClick = { openEditor(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "新增替换规则")
                    }
                }
            )
        }
    ) { contentPadding ->
        if (model.rules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("还没有替换规则")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(model.rules, key = { rule -> rule.id }) { rule ->
                    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    rule.name.ifBlank { "未命名规则" },
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = {
                                        model.setEnabled(rule.id, it)
                                        revision++
                                    }
                                )
                            }
                            Text(
                                "${if (rule.isRegex) "正则" else "文本"}：${rule.pattern}",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "替换为：${rule.replacement}",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "作用域：${rule.scope?.ifBlank { "全部书籍" } ?: "全部书籍"} · " +
                                    "${if (rule.scopeTitle) "标题" else "不处理标题"} · " +
                                    "${if (rule.scopeContent) "正文" else "不处理正文"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = { testingRule = rule }) {
                                    Icon(Icons.Default.Search, contentDescription = "测试替换规则")
                                }
                                IconButton(onClick = { openEditor(rule) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "编辑替换规则")
                                }
                                IconButton(onClick = {
                                    model.delete(rule.id)
                                    revision++
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除替换规则")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        ReplaceRuleEditorDialog(
            initialRule = requireNotNull(editingRule),
            onDismiss = { editorOpen = false },
            onSave = { rule ->
                model.save(rule)
                editorOpen = false
                revision++
            }
        )
    }
    testingRule?.let { rule ->
        ReplaceRuleTestDialog(
            rule = rule,
            model = model,
            onDismiss = { testingRule = null }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReplaceRuleEditorDialog(
    initialRule: CoreReplaceRule,
    onDismiss: () -> Unit,
    onSave: (CoreReplaceRule) -> Unit
) {
    var name by remember(initialRule.id) { mutableStateOf(initialRule.name) }
    var pattern by remember(initialRule.id) { mutableStateOf(initialRule.pattern) }
    var replacement by remember(initialRule.id) { mutableStateOf(initialRule.replacement) }
    var scope by remember(initialRule.id) { mutableStateOf(initialRule.scope.orEmpty()) }
    var excludeScope by remember(initialRule.id) { mutableStateOf(initialRule.excludeScope.orEmpty()) }
    var enabled by remember(initialRule.id) { mutableStateOf(initialRule.enabled) }
    var isRegex by remember(initialRule.id) { mutableStateOf(initialRule.isRegex) }
    var scopeTitle by remember(initialRule.id) { mutableStateOf(initialRule.scopeTitle) }
    var scopeContent by remember(initialRule.id) { mutableStateOf(initialRule.scopeContent) }
    var timeout by remember(initialRule.id) { mutableStateOf(initialRule.timeoutMillisecond.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRule.name.isBlank() && initialRule.pattern.isBlank()) "新增替换规则" else "编辑替换规则") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("匹配内容") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("替换内容") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scope,
                    onValueChange = { scope = it },
                    label = { Text("书籍范围，可留空") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = excludeScope,
                    onValueChange = { excludeScope = it },
                    label = { Text("排除范围，可留空") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timeout,
                    onValueChange = { timeout = it.filter(Char::isDigit) },
                    label = { Text("正则超时（毫秒）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Spacer(Modifier.width(8.dp))
                    Text("启用")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isRegex, onCheckedChange = { isRegex = it })
                    Spacer(Modifier.width(8.dp))
                    Text("使用正则表达式")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = scopeTitle, onCheckedChange = { scopeTitle = it })
                    Spacer(Modifier.width(8.dp))
                    Text("处理章节标题")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = scopeContent, onCheckedChange = { scopeContent = it })
                    Spacer(Modifier.width(8.dp))
                    Text("处理章节正文")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initialRule.copy(
                            name = name,
                            pattern = pattern,
                            replacement = replacement,
                            scope = scope.trim().ifBlank { null },
                            excludeScope = excludeScope.trim().ifBlank { null },
                            enabled = enabled,
                            isRegex = isRegex,
                            scopeTitle = scopeTitle,
                            scopeContent = scopeContent,
                            timeoutMillisecond = timeout.toLongOrNull() ?: 3000L
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReplaceRuleTestDialog(
    rule: CoreReplaceRule,
    model: ReplaceRuleModel,
    onDismiss: () -> Unit
) {
    var input by remember(rule.id) { mutableStateOf("") }
    var result by remember(rule.id) { mutableStateOf<ReplaceRuleTestResult?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("测试替换规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("测试文本") },
                    modifier = Modifier.fillMaxWidth()
                )
                result?.let { testResult ->
                    Text(
                        text = testResult.text ?: testResult.error.orEmpty(),
                        color = if (testResult.isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { result = model.test(rule, input) }) { Text("测试") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReaderScreen(
    model: ReaderModel,
    settingsModel: ReaderSettingsModel,
    dictionaryModel: ReaderDictionaryModel,
    sourceModel: SourceModel,
    onBack: () -> Unit,
    onBackLabel: String = "返回书架"
) {
    var revision by remember { mutableStateOf(0) }
    var bookmarkMenuExpanded by remember { mutableStateOf(false) }
    var loadedChapterUrl by remember { mutableStateOf<String?>(null) }
    var readAloudModel by remember { mutableStateOf<WindowsReadAloudModel?>(null) }
    var readAloudState by remember { mutableStateOf(WindowsReadAloudState.STOPPED) }
    var readAloudError by remember { mutableStateOf<String?>(null) }
    var dictionaryDialogOpen by remember { mutableStateOf(false) }
    var dictionaryInitialQuery by remember { mutableStateOf("") }
    val readAloudController = remember { ReaderReadAloudController() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val pageMode = settingsModel.settings.pageMode
    revision
    val palette = settingsModel.palette
    val bookmarks = model.bookmarks()

    LaunchedEffect(model.currentChapter.url) {
        val targetChapter = model.currentChapter
        readAloudController.clear()
        readAloudModel = null
        readAloudState = WindowsReadAloudState.STOPPED
        readAloudError = null
        loadedChapterUrl = null
        scrollState.scrollTo(model.currentPosition)
        val loaded = withContext(Dispatchers.IO) { model.loadContent(targetChapter) }
        if (loaded && model.currentChapter.url == targetChapter.url) {
            loadedChapterUrl = targetChapter.url
        }
        revision++
    }

    LaunchedEffect(loadedChapterUrl, model.currentChapter.url) {
        if (loadedChapterUrl == model.currentChapter.url) {
            scrollState.scrollTo(model.currentPosition)
            readAloudModel = readAloudController.sync(model.currentChapter.url, model.currentContent)
            readAloudState = readAloudModel?.state ?: WindowsReadAloudState.STOPPED
            readAloudError = readAloudModel?.error
        } else {
            readAloudController.clear()
            readAloudModel = null
            readAloudState = WindowsReadAloudState.STOPPED
            readAloudError = null
        }
    }

    LaunchedEffect(readAloudModel, model.currentChapter.url) {
        val current = readAloudModel ?: return@LaunchedEffect
        while (readAloudModel === current) {
            readAloudState = current.state
            readAloudError = current.error
            delay(100)
        }
    }

    DisposableEffect(readAloudController) {
        onDispose { readAloudController.close() }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun navigateChapter(next: Boolean) {
        model.savePosition(model.currentPosition)
        val changed = if (next) model.nextChapter() else model.previousChapter()
        if (changed) {
            model.savePosition(model.currentPosition)
            revision++
        }
    }

    Scaffold(
        containerColor = Color(palette.backgroundArgb.toInt()),
        contentColor = Color(palette.contentArgb.toInt()),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(palette.backgroundArgb.toInt()),
                    titleContentColor = Color(palette.contentArgb.toInt()),
                    navigationIconContentColor = Color(palette.contentArgb.toInt()),
                    actionIconContentColor = Color(palette.contentArgb.toInt())
                ),
                title = { Text(model.currentChapterTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = onBackLabel)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        dictionaryInitialQuery = ReaderDictionaryQuery.fromClipboard(clipboardManager.getText()?.text).orEmpty()
                        dictionaryDialogOpen = true
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "查词")
                    }
                    IconButton(onClick = {
                        model.addBookmark()
                        revision++
                    }) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "添加书签")
                    }
                    IconButton(
                        enabled = readAloudModel != null,
                        onClick = {
                            readAloudModel?.let { reader ->
                                when (readAloudState) {
                                    WindowsReadAloudState.PLAYING -> reader.pause()
                                    WindowsReadAloudState.PAUSED,
                                    WindowsReadAloudState.STOPPED -> reader.play()
                                }
                                readAloudState = reader.state
                                readAloudError = reader.error
                            }
                        }
                    ) {
                        Icon(
                            if (readAloudState == WindowsReadAloudState.PLAYING) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = if (readAloudState == WindowsReadAloudState.PLAYING) {
                                "暂停朗读"
                            } else {
                                "开始朗读"
                            }
                        )
                    }
                    IconButton(
                        enabled = readAloudModel != null && readAloudState != WindowsReadAloudState.STOPPED,
                        onClick = {
                            readAloudModel?.stop()
                            readAloudState = readAloudModel?.state ?: WindowsReadAloudState.STOPPED
                            readAloudError = readAloudModel?.error
                        }
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "停止朗读")
                    }
                    Box {
                        IconButton(onClick = { bookmarkMenuExpanded = true }) {
                            Icon(Icons.Default.Bookmark, contentDescription = "查看书签")
                        }
                        DropdownMenu(
                            expanded = bookmarkMenuExpanded,
                            onDismissRequest = { bookmarkMenuExpanded = false }
                        ) {
                            if (bookmarks.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("暂无书签") },
                                    onClick = { bookmarkMenuExpanded = false }
                                )
                            } else {
                                bookmarks.forEach { bookmark ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(bookmark.chapterName)
                                                Text(
                                                    bookmark.content.ifBlank { bookmark.bookText }.take(48),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        },
                                        onClick = {
                                            if (model.openBookmark(bookmark)) {
                                                bookmarkMenuExpanded = false
                                                scope.launch { scrollState.scrollTo(model.currentPosition) }
                                                revision++
                                            }
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                model.removeBookmark(bookmark.time)
                                                revision++
                                            }) {
                                                Icon(Icons.Default.Delete, contentDescription = "删除书签")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            navigateChapter(next = false)
                        },
                        enabled = model.hasPrevious
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一章")
                    }
                    IconButton(
                        onClick = {
                            navigateChapter(next = true)
                        },
                        enabled = model.hasNext
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一章")
                    }
                }
            )
        }
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .then(if (pageMode == CoreReaderPageMode.SCROLL) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(horizontal = 48.dp, vertical = 28.dp)
        ) {
            val textSize = settingsModel.settings.textSize
            val lineHeight = textSize + settingsModel.settings.lineSpacingExtra
            val pageSize = if (pageMode == CoreReaderPageMode.PAGED) {
                (
                    (maxWidth.value / (textSize * 0.6f).coerceAtLeast(1f)) *
                        (maxHeight.value / lineHeight.coerceAtLeast(1).toFloat())
                    ).toInt().coerceAtLeast(1)
            } else {
                1
            }
            val pages = if (pageMode == CoreReaderPageMode.PAGED) model.pages(pageSize) else emptyList()
            val currentPage = if (pages.isNotEmpty()) {
                pages[model.currentPageIndex(pageSize)]
            } else {
                ReaderPage("", 0, 0)
            }

            LaunchedEffect(
                settingsModel.settings.autoRead,
                settingsModel.settings.autoReadSpeedSeconds,
                pageMode,
                pageSize,
                model.currentChapter.url
            ) {
                if (!settingsModel.settings.autoRead) return@LaunchedEffect
                while (true) {
                    delay(settingsModel.settings.autoReadSpeedSeconds * 1000L)
                    if (pageMode == CoreReaderPageMode.PAGED) {
                        model.savePosition(model.currentPosition)
                        when (model.autoReadTick(pageSize)) {
                            ReaderAutoReadResult.PAGE_ADVANCED -> {
                                model.savePosition(model.currentPosition)
                                revision++
                            }
                            ReaderAutoReadResult.CHAPTER_ADVANCED -> {
                                model.savePosition(model.currentPosition)
                                revision++
                                break
                            }
                            ReaderAutoReadResult.END -> break
                        }
                    } else {
                        val step = scrollState.viewportSize.coerceAtLeast(1)
                        val target = (scrollState.value + step).coerceAtMost(scrollState.maxValue)
                        if (target > scrollState.value) {
                            scrollState.animateScrollTo(target)
                            model.savePosition(target)
                        } else if (model.nextChapter()) {
                            model.savePosition(model.currentPosition)
                            revision++
                            break
                        } else {
                            break
                        }
                    }
                }
            }

            val readerModifier = Modifier
                .then(if (pageMode == CoreReaderPageMode.PAGED) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        val readerKey = when (event.key) {
                            Key.DirectionLeft -> ReaderKeyboardKey.LEFT
                            Key.DirectionRight -> ReaderKeyboardKey.RIGHT
                            Key.DirectionUp -> ReaderKeyboardKey.UP
                            Key.DirectionDown -> ReaderKeyboardKey.DOWN
                            Key.Spacebar -> ReaderKeyboardKey.SPACE
                            Key.S -> ReaderKeyboardKey.S
                            else -> null
                        }
                        when (ReaderKeyboardCommand.from(readerKey ?: return@onPreviewKeyEvent false, event.isCtrlPressed)) {
                            ReaderKeyboardCommand.PREVIOUS_PAGE -> {
                                scope.launch {
                                    if (pageMode == CoreReaderPageMode.PAGED) {
                                        if (model.previousPage(pageSize)) revision++
                                    } else {
                                        scrollState.animateScrollTo(
                                            (scrollState.value - scrollState.viewportSize.coerceAtLeast(1))
                                                .coerceAtLeast(0)
                                        )
                                    }
                                }
                                true
                            }
                            ReaderKeyboardCommand.NEXT_PAGE -> {
                                scope.launch {
                                    if (pageMode == CoreReaderPageMode.PAGED) {
                                        if (model.nextPage(pageSize)) revision++
                                    } else {
                                        scrollState.animateScrollTo(
                                            (scrollState.value + scrollState.viewportSize.coerceAtLeast(1))
                                                .coerceAtMost(scrollState.maxValue)
                                        )
                                    }
                                }
                                true
                            }
                            ReaderKeyboardCommand.PREVIOUS_CHAPTER -> {
                                navigateChapter(next = false)
                                true
                            }
                            ReaderKeyboardCommand.NEXT_CHAPTER -> {
                                navigateChapter(next = true)
                                true
                            }
                            ReaderKeyboardCommand.SAVE_POSITION -> {
                                model.savePosition(
                                    if (pageMode == CoreReaderPageMode.PAGED) currentPage.startPosition else scrollState.value
                                )
                                true
                            }
                            null -> false
                        }
                    }
                }
                .focusRequester(focusRequester)
                .focusable()

            Column(readerModifier) {
                Box(
                    modifier = if (pageMode == CoreReaderPageMode.PAGED) {
                        Modifier.fillMaxWidth().weight(1f)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                ) {
                    when {
                        model.isLoading -> Text("正在加载正文")
                        model.error != null -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(model.error!!, color = MaterialTheme.colorScheme.error)
                                Button(onClick = {
                                    val targetChapter = model.currentChapter
                                    scope.launch {
                                        val loaded = withContext(Dispatchers.IO) {
                                            model.loadContent(targetChapter)
                                        }
                                        if (loaded && model.currentChapter.url == targetChapter.url) {
                                            loadedChapterUrl = targetChapter.url
                                        }
                                        revision++
                                    }
                                }) { Text("重试") }
                                if (model.sessionStatus == io.legado.core.source.CoreSourceSessionStatus.LOGIN_REQUIRED &&
                                    model.loginUrl != null && model.loginSourceUrl != null
                                ) {
                                    Button(onClick = {
                                        runCatching {
                                            sourceModel.openEmbeddedBrowserVerification(
                                                url = model.loginUrl!!,
                                                sourceUrl = model.loginSourceUrl!!,
                                                title = "书源登录"
                                            )
                                        }.onFailure { error ->
                                            readAloudError = error.message ?: "打开重新登录失败"
                                        }
                                    }) { Text("重新登录") }
                                }
                            }
                        }
                        else -> SelectionContainer {
                            Text(
                                text = if (pageMode == CoreReaderPageMode.PAGED) currentPage.text else model.currentContent,
                                color = Color(palette.contentArgb.toInt()),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = textSize.sp,
                                    lineHeight = lineHeight.sp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                if (readAloudModel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when (readAloudState) {
                                WindowsReadAloudState.PLAYING -> "正在朗读"
                                WindowsReadAloudState.PAUSED -> "朗读已暂停"
                                WindowsReadAloudState.STOPPED -> "朗读已停止"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    readAloudError?.let { message ->
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (pageMode == CoreReaderPageMode.PAGED) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (model.previousPage(pageSize)) revision++
                        }, enabled = model.currentPageIndex(pageSize) > 0) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一页")
                        }
                        Text("${model.currentPageIndex(pageSize) + 1}/${pages.size}")
                        IconButton(onClick = {
                            if (model.nextPage(pageSize)) revision++
                        }, enabled = model.currentPageIndex(pageSize) < pages.lastIndex) {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一页")
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { navigateChapter(next = false) }, enabled = model.hasPrevious) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一章")
                    }
                    Button(onClick = {
                        model.savePosition(if (pageMode == CoreReaderPageMode.PAGED) currentPage.startPosition else scrollState.value)
                    }) {
                        Text("保存阅读位置")
                    }
                    IconButton(onClick = { navigateChapter(next = true) }, enabled = model.hasNext) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一章")
                    }
                }
            }
        }
    }

    if (dictionaryDialogOpen) {
        ReaderDictionaryDialog(
            model = dictionaryModel,
            initialQuery = dictionaryInitialQuery,
            onDismiss = { dictionaryDialogOpen = false }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ImageReaderScreen(
    model: ImageReaderModel,
    onBack: () -> Unit,
    onBackLabel: String = "返回书架"
) {
    var revision by remember { mutableStateOf(0) }
    var image by remember(model.currentPageIndex) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(model.currentPageIndex) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    revision

    LaunchedEffect(model.currentPageIndex) {
        image = null
        error = null
        val decoded = withContext(Dispatchers.IO) {
            runCatching { Image.makeFromEncoded(model.currentPageBytes()).toComposeImageBitmap() }
        }
        decoded
            .onSuccess { image = it }
            .onFailure { throwable -> error = throwable.message ?: "图片加载失败" }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun changePage(next: Boolean) {
        model.savePosition()
        val changed = if (next) model.nextPage() else model.previousPage()
        if (changed) {
            model.savePosition()
            revision++
        }
    }

    fun changeChapter(next: Boolean) {
        model.savePosition()
        val changed = if (next) model.nextChapter() else model.previousChapter()
        if (changed) {
            model.savePosition()
            revision++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(model.currentChapterTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${model.currentPageIndex + 1}/${model.pageCount} · ${model.currentPageLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = onBackLabel)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        model.zoomOut()
                        revision++
                    }) {
                        Icon(Icons.Default.Remove, contentDescription = "缩小")
                    }
                    Text("${(model.zoom * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = {
                        model.zoomIn()
                        revision++
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "放大")
                    }
                    IconButton(onClick = {
                        model.resetZoom()
                        revision++
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "重置缩放")
                    }
                    IconButton(onClick = { changeChapter(next = false) }, enabled = model.hasPreviousChapter) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一章")
                    }
                    IconButton(onClick = { changeChapter(next = true) }, enabled = model.hasNextChapter) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一章")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (event.isCtrlPressed) changeChapter(next = false) else changePage(next = false)
                                true
                            }
                            Key.DirectionRight, Key.Spacebar -> {
                                if (event.isCtrlPressed) changeChapter(next = true) else changePage(next = true)
                                true
                            }
                            Key.S -> {
                                model.savePosition()
                                true
                            }
                            else -> false
                        }
                    }
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(horizontalScrollState)
                    .verticalScroll(verticalScrollState)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    image == null -> Text("正在加载图片")
                    else -> Image(
                        bitmap = image!!,
                        contentDescription = model.currentPageLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer(
                                scaleX = model.zoom,
                                scaleY = model.zoom
                            ),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { changePage(next = false) }, enabled = model.hasPrevious) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一页")
                }
                Text("${model.currentPageIndex + 1}/${model.pageCount}")
                IconButton(onClick = { changePage(next = true) }, enabled = model.hasNext) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一页")
                }
                Button(onClick = { model.savePosition() }) {
                    Text("保存阅读位置")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun OnlineImageReaderScreen(
    model: OnlineImageReaderModel,
    sourceModel: SourceModel,
    onBack: () -> Unit,
    onBackLabel: String = "返回书架"
) {
    var revision by remember { mutableStateOf(0) }
    var image by remember(model.currentPageIndex, revision) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(model.currentPageIndex, revision) { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(model.currentChapter.index, model.currentPageIndex, revision) {
        image = null
        error = null
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                if (model.currentChapterPages.isEmpty()) check(model.loadCurrentChapter()) { "章节没有可用图片" }
                Image.makeFromEncoded(model.currentPageBytes()).toComposeImageBitmap()
            }
        }
        decoded.onSuccess { image = it }
            .onFailure { throwable -> error = throwable.message ?: "图片加载失败" }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun changePage(next: Boolean) {
        model.savePosition()
        val changed = if (next) model.nextPage() else model.previousPage()
        if (changed) {
            model.savePosition()
            revision++
        }
    }

    fun changeChapter(next: Boolean) {
        model.savePosition()
        val changed = if (next) model.nextChapter() else model.previousChapter()
        if (changed) {
            model.savePosition()
            revision++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(model.currentChapter.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${model.currentPageIndex + 1}/${model.currentChapterPages.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = onBackLabel)
                    }
                },
                actions = {
                    IconButton(onClick = { changeChapter(false) }, enabled = model.hasPreviousChapter) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一章")
                    }
                    IconButton(onClick = { changeChapter(true) }, enabled = model.hasNextChapter) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一章")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                        Key.DirectionLeft -> {
                            if (event.isCtrlPressed) changeChapter(false) else changePage(false)
                            true
                        }
                        Key.DirectionRight, Key.Spacebar -> {
                            if (event.isCtrlPressed) changeChapter(true) else changePage(true)
                            true
                        }
                        Key.S -> {
                            model.savePosition()
                            true
                        }
                        else -> false
                    }
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(horizontalScrollState)
                    .verticalScroll(verticalScrollState)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        if (model.sessionStatus == CoreSourceSessionStatus.LOGIN_REQUIRED &&
                            !model.loginUrl.isNullOrBlank() && !model.loginSourceUrl.isNullOrBlank()
                        ) {
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                runCatching {
                                    sourceModel.openEmbeddedBrowserVerification(
                                        url = model.loginUrl!!,
                                        sourceUrl = model.loginSourceUrl!!,
                                        title = "书源登录"
                                    )
                                }.onSuccess {
                                    feedback = "已打开内置浏览器，请完成登录后重试"
                                }.onFailure { throwable ->
                                    feedback = throwable.message ?: "打开重新登录失败"
                                }
                            }) { Text("重新登录") }
                        }
                    }
                    image == null -> Text("正在加载图片")
                    else -> Image(
                        bitmap = image!!,
                        contentDescription = "漫画图片 ${model.currentPageIndex + 1}",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            feedback?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { changePage(false) }, enabled = model.hasPrevious) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一页")
                }
                Text("${model.currentPageIndex + 1}/${model.currentChapterPages.size}")
                IconButton(onClick = { changePage(true) }, enabled = model.hasNext) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一页")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AudioReaderScreen(
    model: AudioReaderModel,
    onBack: () -> Unit,
    onBackLabel: String = "返回书架"
) {
    var revision by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf(model.state) }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    revision

    DisposableEffect(model) {
        onDispose { model.close() }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun refresh() {
        state = model.state
        revision++
    }

    fun runPlayback(action: () -> Boolean) {
        runCatching { action() }
            .onSuccess {
                error = null
                refresh()
            }
            .onFailure { throwable ->
                error = throwable.message ?: "音频播放失败"
                refresh()
            }
    }

    fun changeChapter(next: Boolean) {
        model.savePosition()
        val changed = if (next) model.nextChapter() else model.previousChapter()
        if (changed) {
            error = null
            model.savePosition()
            refresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(model.currentChapterTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${model.currentChapterIndex + 1}/${model.chapterCount}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = onBackLabel)
                    }
                },
                actions = {
                    IconButton(onClick = { changeChapter(next = false) }, enabled = model.hasPreviousChapter) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一段音频")
                    }
                    IconButton(onClick = { changeChapter(next = true) }, enabled = model.hasNextChapter) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一段音频")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(28.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                changeChapter(next = false)
                                true
                            }
                            Key.DirectionRight, Key.Spacebar -> {
                                changeChapter(next = true)
                                true
                            }
                            Key.S -> {
                                model.savePosition()
                                true
                            }
                            else -> false
                        }
                    }
                }
                .focusRequester(focusRequester)
                .focusable(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("本地音频", style = MaterialTheme.typography.headlineSmall)
            Text(model.currentResource, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                when (state) {
                    AudioPlaybackState.PLAYING -> "正在播放"
                    AudioPlaybackState.PAUSED -> "已暂停"
                    AudioPlaybackState.STOPPED -> "已停止"
                }
            )
            error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    runPlayback {
                        if (state == AudioPlaybackState.PLAYING) model.pause() else model.play()
                    }
                }) {
                    Text(if (state == AudioPlaybackState.PLAYING) "暂停" else "播放")
                }
                Button(onClick = { runPlayback { model.stop() } }) {
                    Text("停止")
                }
                Button(onClick = {
                    model.savePosition()
                    refresh()
                }) {
                    Text("保存进度")
                }
            }
            HorizontalDivider()
            Text("章节", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items((0 until model.chapterCount).toList()) { index ->
                    TextButton(
                        onClick = {
                            model.savePosition()
                            if (model.selectChapter(index)) {
                                error = null
                                model.savePosition()
                                refresh()
                            }
                        }
                    ) {
                        Text(
                            if (index == model.currentChapterIndex) {
                                "▶ ${model.chapterTitle(index)}"
                            } else {
                                model.chapterTitle(index)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReaderDictionaryDialog(
    model: ReaderDictionaryModel,
    initialQuery: String = "",
    onDismiss: () -> Unit
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var lookup by remember { mutableStateOf<ReaderDictionaryLookup?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    fun readClipboard() {
        val clipboardQuery = ReaderDictionaryQuery.fromClipboard(clipboardManager.getText()?.text)
        if (clipboardQuery != null) {
            query = clipboardQuery
            error = null
            lookup = null
        } else {
            error = "剪贴板没有可查词的文本"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("查词") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        error = null
                        lookup = null
                    },
                    label = { Text("查询内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    trailingIcon = {
                        IconButton(onClick = ::readClipboard) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "从剪贴板填入")
                        }
                    }
                )
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                lookup?.let { result ->
                    if (result.items.isEmpty()) {
                        Text("没有启用的字典规则")
                    } else {
                        result.items.forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.rule.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    item.text ?: item.error.orEmpty(),
                                    color = if (item.isSuccess) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading,
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { model.lookup(query) }
                        }.onSuccess {
                            lookup = it
                        }.onFailure {
                            error = it.message ?: "查词失败"
                            lookup = null
                        }
                        loading = false
                    }
                }
            ) { Text(if (loading) "查询中" else "查询") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun PlaceholderScreen(route: AppRoute) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("${route.label}功能正在迁移", style = MaterialTheme.typography.headlineSmall)
    }
}

private fun AppRoute.icon() = when (this) {
    AppRoute.WELCOME -> Icons.Default.Book
    AppRoute.BOOKSHELF -> Icons.Default.Book
    AppRoute.SEARCH -> Icons.Default.Search
    AppRoute.EXPLORE -> Icons.Default.Explore
    AppRoute.BOOK_DETAIL -> Icons.Default.Book
    AppRoute.SUBSCRIPTIONS -> Icons.Default.Subscriptions
    AppRoute.SOURCES -> Icons.Default.Source
    AppRoute.SETTINGS -> Icons.Default.Settings
    AppRoute.READER -> Icons.Default.Book
    AppRoute.BOOKMARKS -> Icons.Default.Bookmark
    AppRoute.READ_RECORDS -> Icons.Default.History
    AppRoute.REPLACE_RULES -> Icons.Default.Edit
    AppRoute.DICT_RULES -> Icons.Default.Search
    AppRoute.TXT_TOC_RULES -> Icons.Default.Book
    AppRoute.SOURCE_FILTER_RULES -> Icons.Default.Search
}

private fun CoreReaderTheme.displayName(): String = when (this) {
    CoreReaderTheme.DAY -> "日间"
    CoreReaderTheme.NIGHT -> "夜间"
    CoreReaderTheme.SEPIA -> "护眼"
    CoreReaderTheme.GREEN -> "绿色"
}

private fun selectLocalBook(): Path? {
    val dialog = FileDialog(null as Frame?, "导入本地书籍", FileDialog.LOAD).apply {
        isMultipleMode = false
        filenameFilter = java.io.FilenameFilter { _, name ->
            name.endsWith(".txt", ignoreCase = true) ||
                name.endsWith(".epub", ignoreCase = true) ||
                name.endsWith(".wav", ignoreCase = true) ||
                name.endsWith(".aif", ignoreCase = true) ||
                name.endsWith(".aiff", ignoreCase = true) ||
                name.endsWith(".au", ignoreCase = true) ||
                name.endsWith(".snd", ignoreCase = true) ||
                name.endsWith(".cbz", ignoreCase = true) ||
                name.endsWith(".zip", ignoreCase = true) ||
                name.endsWith(".png", ignoreCase = true) ||
                name.endsWith(".jpg", ignoreCase = true) ||
                name.endsWith(".jpeg", ignoreCase = true) ||
                name.endsWith(".gif", ignoreCase = true) ||
                name.endsWith(".webp", ignoreCase = true) ||
                name.endsWith(".bmp", ignoreCase = true)
        }
    }
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val filename = dialog.file ?: return null
    return Path.of(directory, filename)
}

private fun selectJsonFile(
    title: String,
    mode: Int,
    defaultFileName: String = "book-sources.json"
): Path? {
    val dialog = FileDialog(null as Frame?, title, mode).apply {
        isMultipleMode = false
        filenameFilter = java.io.FilenameFilter { _, name ->
            name.endsWith(".json", ignoreCase = true) || mode == FileDialog.SAVE
        }
        if (mode == FileDialog.SAVE) file = defaultFileName
    }
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val filename = dialog.file ?: return null
    return Path.of(directory, filename)
}

private fun selectMarkdownFile(title: String, mode: Int): Path? {
    val dialog = FileDialog(null as Frame?, title, mode).apply {
        isMultipleMode = false
        filenameFilter = java.io.FilenameFilter { _, name ->
            name.endsWith(".md", ignoreCase = true) || mode == FileDialog.SAVE
        }
        if (mode == FileDialog.SAVE) file = "legado-read-records.md"
    }
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val filename = dialog.file ?: return null
    val normalized = if (mode == FileDialog.SAVE && !filename.endsWith(".md", ignoreCase = true)) {
        "$filename.md"
    } else {
        filename
    }
    return Path.of(directory, normalized)
}

private fun selectBackupFile(title: String, mode: Int): Path? {
    val dialog = FileDialog(null as Frame?, title, mode).apply {
        isMultipleMode = false
        filenameFilter = java.io.FilenameFilter { _, name ->
            name.endsWith(".zip", ignoreCase = true) || mode == FileDialog.SAVE
        }
        if (mode == FileDialog.SAVE) file = "legado-backup.zip"
    }
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val filename = dialog.file ?: return null
    val normalized = if (mode == FileDialog.SAVE && !filename.endsWith(".zip", ignoreCase = true)) {
        "$filename.zip"
    } else {
        filename
    }
    return Path.of(directory, normalized)
}
