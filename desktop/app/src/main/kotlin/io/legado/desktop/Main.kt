package io.legado.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.unit.DpSize
import io.legado.core.library.CoreBook
import io.legado.core.library.InMemoryCoreLibrary

fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(1180.dp, 760.dp),
        position = WindowPosition.Aligned(Alignment.Center)
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "Legado",
        state = windowState
    ) {
        LegadoApp()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LegadoApp() {
    val library = remember { InMemoryCoreLibrary() }
    val bookshelfModel = remember { BookshelfModel(library) }
    val appState = remember { AppState() }
    var route by remember { mutableStateOf(appState.route) }
    var darkTheme by remember { mutableStateOf(appState.isDarkTheme) }

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
                bookshelfModel = bookshelfModel
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
    bookshelfModel: BookshelfModel
) {
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
                onNavigate = onRouteChange
            )
            else -> PlaceholderScreen(route)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BookshelfScreen(
    model: BookshelfModel,
    onNavigate: (AppRoute) -> Unit
) {
    var query by remember { mutableStateOf(model.query) }
    val books = model.visibleBooks()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书架") },
                actions = {
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
                        BookTile(book)
                    }
                }
            }
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
private fun BookTile(book: CoreBook) {
    Surface(tonalElevation = 2.dp) {
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
    AppRoute.SUBSCRIPTIONS -> Icons.Default.Subscriptions
    AppRoute.SOURCES -> Icons.Default.Source
    AppRoute.SETTINGS -> Icons.Default.Settings
    AppRoute.READER -> Icons.Default.Book
}
