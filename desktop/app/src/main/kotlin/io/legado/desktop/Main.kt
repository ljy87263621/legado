package io.legado.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.unit.DpSize
import io.legado.core.library.CoreBook
import io.legado.core.library.CoreLibrary
import io.legado.core.library.CoreBookSource
import io.legado.core.library.CoreReadRecord
import io.legado.core.library.CoreReaderPageMode
import io.legado.core.library.CoreReaderTheme
import io.legado.core.source.BookSourceSearchService
import io.legado.core.source.CoreSearchResult
import io.legado.core.source.JavaNetHttpClient
import io.legado.core.source.OnlineBookService
import io.legado.desktop.persistence.DesktopDataDirectory
import io.legado.desktop.persistence.SqliteCoreLibrary
import java.awt.FileDialog
import java.awt.Frame
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun main() = application {
    val library = remember {
        SqliteCoreLibrary(DesktopDataDirectory.resolve().resolve("legado.db"))
    }
    val windowState = rememberWindowState(
        size = DpSize(1180.dp, 760.dp),
        position = WindowPosition.Aligned(Alignment.Center)
    )
    Window(
        onCloseRequest = {
            library.close()
            exitApplication()
        },
        title = "Legado",
        state = windowState
    ) {
        LegadoApp(library)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LegadoApp(library: CoreLibrary) {
    val httpClient = remember { JavaNetHttpClient() }
    val onlineService = remember { OnlineBookService(library, httpClient) }
    val bookshelfModel = remember { BookshelfModel(library) }
    val searchModel = remember {
        SearchModel(library, BookSourceSearchService(library, httpClient))
    }
    val sourceModel = remember { SourceModel(library) }
    val subscriptionModel = remember {
        SubscriptionModel(library, BookSourceSearchService(library, httpClient))
    }
    val detailModel = remember { BookDetailModel(library, onlineService) }
    val readerSettingsModel = remember { ReaderSettingsModel(library) }
    val appState = remember { AppState() }
    var route by remember { mutableStateOf(appState.route) }
    var darkTheme by remember { mutableStateOf(appState.isDarkTheme) }
    var selectedBookUrl by remember { mutableStateOf<String?>(null) }
    var selectedChapterIndex by remember { mutableStateOf<Int?>(null) }
    var selectedBook by remember { mutableStateOf<CoreBook?>(null) }
    var bookshelfRefreshToken by remember { mutableStateOf(0) }
    var importError by remember { mutableStateOf<String?>(null) }
    var backupFeedback by remember { mutableStateOf<String?>(null) }

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppShell(
                route = route,
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
                    selectedBook = library.book(bookUrl)
                    appState.navigate(AppRoute.READER)
                    route = appState.route
                },
                onOpenDetail = { book ->
                    selectedBook = book
                    selectedBookUrl = book.bookUrl
                    selectedChapterIndex = null
                    appState.navigate(AppRoute.BOOK_DETAIL)
                    route = appState.route
                },
                searchModel = searchModel,
                sourceModel = sourceModel,
                subscriptionModel = subscriptionModel,
                detailModel = detailModel,
                readerSettingsModel = readerSettingsModel,
                backupFeedback = backupFeedback,
                onDismissBackupFeedback = { backupFeedback = null },
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
                            bookshelfModel.refresh()
                            sourceModel.refresh()
                            subscriptionModel.refreshSources()
                            readerSettingsModel.reload()
                            bookshelfRefreshToken++
                        }
                        backupFeedback = result.summary?.let { summary ->
                            "备份已导入：${summary.books} 本书，${summary.chapters} 个章节"
                        } ?: result.error ?: "备份导入失败"
                    }
                },
                onlineService = onlineService,
                onBookshelfChanged = { bookshelfRefreshToken++ },
                selectedBookUrl = selectedBookUrl,
                selectedChapterIndex = selectedChapterIndex,
                onSelectChapter = { chapterIndex -> selectedChapterIndex = chapterIndex },
                selectedBook = selectedBook,
                library = library
            )
        }
    }
}

@Composable
private fun AppShell(
    route: AppRoute,
    onRouteChange: (AppRoute) -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    bookshelfModel: BookshelfModel,
    bookshelfRefreshToken: Int,
    importError: String?,
    onDismissImportError: () -> Unit,
    onImport: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenDetail: (CoreBook) -> Unit,
    searchModel: SearchModel,
    sourceModel: SourceModel,
    subscriptionModel: SubscriptionModel,
    detailModel: BookDetailModel,
    readerSettingsModel: ReaderSettingsModel,
    backupFeedback: String?,
    onDismissBackupFeedback: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onlineService: OnlineBookService,
    onBookshelfChanged: () -> Unit,
    selectedBookUrl: String?,
    selectedChapterIndex: Int?,
    onSelectChapter: (Int) -> Unit,
    selectedBook: CoreBook?,
    library: CoreLibrary
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
            AppRoute.BOOKSHELF -> BookshelfScreen(
                model = bookshelfModel,
                onNavigate = onRouteChange,
                onImport = onImport,
                importError = importError,
                onDismissImportError = onDismissImportError,
                onOpenBook = onOpenBook
            )
            AppRoute.SEARCH -> SearchScreen(
                model = searchModel,
                onBookshelfChanged = onBookshelfChanged,
                onOpenDetail = onOpenDetail
            )
            AppRoute.BOOK_DETAIL -> selectedBook?.let { book ->
                BookDetailScreen(
                    model = detailModel,
                    initialBook = book,
                    library = library,
                    onBack = { onRouteChange(AppRoute.SEARCH) },
                    onOpenChapter = { chapter ->
                        onSelectChapter(chapter.index)
                        onRouteChange(AppRoute.READER)
                    },
                    onBookshelfChanged = onBookshelfChanged
                )
            } ?: PlaceholderScreen(AppRoute.BOOK_DETAIL)
            AppRoute.SOURCES -> SourcesScreen(model = sourceModel)
            AppRoute.SUBSCRIPTIONS -> SubscriptionScreen(
                model = subscriptionModel,
                sourceModel = sourceModel,
                onOpenArticle = { result ->
                    val book = subscriptionModel.openArticle(result)
                    onOpenBook(book.bookUrl)
                }
            )
            AppRoute.SETTINGS -> SettingsScreen(
                model = readerSettingsModel,
                onOpenReadRecords = { onRouteChange(AppRoute.READ_RECORDS) },
                backupFeedback = backupFeedback,
                onDismissBackupFeedback = onDismissBackupFeedback,
                onExportBackup = onExportBackup,
                onImportBackup = onImportBackup
            )
            AppRoute.READ_RECORDS -> ReadRecordsScreen(
                records = library.readRecords(),
                onBack = { onRouteChange(AppRoute.SETTINGS) }
            )
            AppRoute.READER -> selectedBookUrl?.let { bookUrl ->
                ReaderScreen(
                    model = remember(bookUrl, selectedChapterIndex) {
                        ReaderModel(
                            library,
                            bookUrl,
                            onlineService,
                            startChapterIndex = selectedChapterIndex
                        )
                    },
                    settingsModel = readerSettingsModel,
                    onBack = { onRouteChange(AppRoute.BOOKSHELF) }
                )
            } ?: PlaceholderScreen(AppRoute.READER)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BookshelfScreen(
    model: BookshelfModel,
    onNavigate: (AppRoute) -> Unit,
    onImport: () -> Unit,
    importError: String?,
    onDismissImportError: () -> Unit,
    onOpenBook: (String) -> Unit
) {
    var query by remember { mutableStateOf(model.query) }
    var groupMenuExpanded by remember { mutableStateOf(false) }
    val books = model.visibleBooks()
    val groups = modelGroups(model)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书架") },
                actions = {
                    IconButton(onClick = onImport) {
                        Icon(Icons.Default.FileOpen, contentDescription = "导入本地书籍")
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
                                groupMenuExpanded = false
                            }
                        )
                    }
                }
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
                        BookTile(book, onClick = { onOpenBook(book.bookUrl) })
                    }
                }
            }
        }
    }
}

private fun modelGroups(model: BookshelfModel): List<Pair<Long, String>> = buildList {
    add(-1L to "全部")
    model.availableGroups().forEach { group ->
        if (group.groupId != -1L) add(group.groupId to group.groupName)
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
private fun BookTile(book: CoreBook, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(book.name.ifBlank { "未命名书籍" }, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(book.author.ifBlank { "未知作者" }, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                book.durChapterTitle ?: "尚未开始阅读",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SearchScreen(
    model: SearchModel,
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
                model.error != null -> SearchEmptyState(model.error!!)
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
    onOpenArticle: (CoreSearchResult) -> Unit
) {
    var revision by remember { mutableStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    revision

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            model.refreshSources()
            if (model.selectedSource != null) model.refresh()
        }
        revision++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("订阅") },
                actions = {
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
                                withContext(Dispatchers.IO) {
                                    model.refreshSources()
                                    model.refresh()
                                }
                                revision++
                            }
                        },
                        enabled = !model.isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新订阅")
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
            model.error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(10.dp))
            }
            if (model.sources.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有订阅源，请导入 bookSourceType=5 的 RSS 书源")
                }
                return@Column
            }

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
private fun SourcesScreen(model: SourceModel) {
    var revision by remember { mutableStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    revision
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书源") },
                actions = {
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
}

@Composable
private fun SourceRow(
    source: CoreBookSource,
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
@OptIn(ExperimentalMaterial3Api::class)
private fun BookDetailScreen(
    model: BookDetailModel,
    initialBook: CoreBook,
    library: CoreLibrary,
    onBack: () -> Unit,
    onOpenChapter: (io.legado.core.library.CoreChapter) -> Unit,
    onBookshelfChanged: () -> Unit
) {
    var revision by remember(initialBook.bookUrl) { mutableStateOf(0) }
    var feedback by remember(initialBook.bookUrl) { mutableStateOf<String?>(null) }
    var hasOpened by remember(initialBook.bookUrl) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    revision

    LaunchedEffect(initialBook.bookUrl) {
        if (!hasOpened) {
            withContext(Dispatchers.IO) { model.open(initialBook) }
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
            }
            Spacer(Modifier.height(18.dp))
            Text("目录", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            if (model.chapters.isEmpty()) {
                Text("暂无目录")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(model.chapters, key = { chapter -> chapter.url }) { chapter ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenChapter(chapter) },
                            tonalElevation = 1.dp
                        ) {
                            Text(
                                chapter.title,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SettingsScreen(
    model: ReaderSettingsModel,
    onOpenReadRecords: () -> Unit,
    backupFeedback: String?,
    onDismissBackupFeedback: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    var revision by remember { mutableStateOf(0) }
    revision
    val settings = model.settings
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
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenReadRecords) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("阅读记录")
            }
            Spacer(Modifier.height(24.dp))
            Text("数据管理", style = MaterialTheme.typography.titleLarge)
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
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReadRecordsScreen(
    records: List<CoreReadRecord>,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阅读记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "返回设置")
                    }
                }
            )
        }
    ) { contentPadding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("还没有阅读记录")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(records, key = { record -> "${record.bookName}-${record.day}-${record.startSec}" }) { record ->
                    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(record.bookName, style = MaterialTheme.typography.titleMedium)
                            Text("${record.day} · 阅读 ${record.endSec - record.startSec} 秒")
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReaderScreen(
    model: ReaderModel,
    settingsModel: ReaderSettingsModel,
    onBack: () -> Unit
) {
    var revision by remember { mutableStateOf(0) }
    var hasEnteredChapter by remember { mutableStateOf(false) }
    var bookmarkMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState(model.currentPosition)
    revision
    val palette = settingsModel.palette
    val bookmarks = model.bookmarks()

    LaunchedEffect(model.currentChapter.url) {
        if (hasEnteredChapter) {
            scrollState.scrollTo(model.currentPosition)
        } else {
            hasEnteredChapter = true
        }
        withContext(Dispatchers.IO) { model.loadCurrentContent() }
        revision++
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
                title = { Text(model.currentChapter.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "返回书架")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        model.addBookmark()
                        revision++
                    }) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "添加书签")
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
                            if (model.previousChapter()) {
                                scope.launch {
                                    withContext(Dispatchers.IO) { model.loadCurrentContent() }
                                    revision++
                                }
                            }
                        },
                        enabled = model.hasPrevious
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一章")
                    }
                    IconButton(
                        onClick = {
                            if (model.nextChapter()) {
                                scope.launch {
                                    withContext(Dispatchers.IO) { model.loadCurrentContent() }
                                    revision++
                                }
                            }
                        },
                        enabled = model.hasNext
                    ) {
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
                .verticalScroll(scrollState)
                .padding(horizontal = 48.dp, vertical = 28.dp)
        ) {
            when {
                model.isLoading -> Text("正在加载正文")
                model.error != null -> {
                    Text(model.error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { model.loadCurrentContent() }
                            revision++
                        }
                    }) { Text("重试") }
                }
                else -> Text(
                    text = model.currentContent,
                    color = Color(palette.contentArgb.toInt()),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = settingsModel.settings.textSize.sp,
                        lineHeight = (settingsModel.settings.textSize + settingsModel.settings.lineSpacingExtra).sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        if (model.previousChapter()) {
                            scope.launch {
                                withContext(Dispatchers.IO) { model.loadCurrentContent() }
                                revision++
                            }
                        }
                    },
                    enabled = model.hasPrevious
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "上一章")
                }
                Button(onClick = { model.savePosition(scrollState.value) }) {
                    Text("保存阅读位置")
                }
                IconButton(
                    onClick = {
                        if (model.nextChapter()) {
                            scope.launch {
                                withContext(Dispatchers.IO) { model.loadCurrentContent() }
                                revision++
                            }
                        }
                    },
                    enabled = model.hasNext
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "下一章")
                }
            }
        }
    }
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
    AppRoute.BOOKSHELF -> Icons.Default.Book
    AppRoute.SEARCH -> Icons.Default.Search
    AppRoute.BOOK_DETAIL -> Icons.Default.Book
    AppRoute.SUBSCRIPTIONS -> Icons.Default.Subscriptions
    AppRoute.SOURCES -> Icons.Default.Source
    AppRoute.SETTINGS -> Icons.Default.Settings
    AppRoute.READER -> Icons.Default.Book
    AppRoute.READ_RECORDS -> Icons.Default.History
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
            name.endsWith(".txt", ignoreCase = true) || name.endsWith(".epub", ignoreCase = true)
        }
    }
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val filename = dialog.file ?: return null
    return Path.of(directory, filename)
}

private fun selectJsonFile(title: String, mode: Int): Path? {
    val dialog = FileDialog(null as Frame?, title, mode).apply {
        isMultipleMode = false
        filenameFilter = java.io.FilenameFilter { _, name ->
            name.endsWith(".json", ignoreCase = true) || mode == FileDialog.SAVE
        }
        if (mode == FileDialog.SAVE) file = "book-sources.json"
    }
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val filename = dialog.file ?: return null
    return Path.of(directory, filename)
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
