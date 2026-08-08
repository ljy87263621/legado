package io.legado.desktop

class AppState(
    initialRoute: AppRoute = AppRoute.BOOKSHELF
) {
    var route: AppRoute = initialRoute
        private set

    var readerReturnRoute: AppRoute = AppRoute.BOOKSHELF
        private set

    val readerReturnLabel: String
        get() = "返回${readerReturnRoute.label}"

    var isDarkTheme: Boolean = false
        private set

    fun navigate(route: AppRoute) {
        this.route = route
    }

    fun openReader(returnRoute: AppRoute = AppRoute.BOOKSHELF) {
        readerReturnRoute = returnRoute.takeUnless { it == AppRoute.READER } ?: AppRoute.BOOKSHELF
        route = AppRoute.READER
    }

    fun closeReader(): AppRoute {
        route = readerReturnRoute
        return route
    }

    fun toggleTheme() {
        isDarkTheme = !isDarkTheme
    }
}
