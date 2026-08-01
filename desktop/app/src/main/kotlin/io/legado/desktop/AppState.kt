package io.legado.desktop

class AppState(
    initialRoute: AppRoute = AppRoute.BOOKSHELF
) {
    var route: AppRoute = initialRoute
        private set

    var isDarkTheme: Boolean = false
        private set

    fun navigate(route: AppRoute) {
        this.route = route
    }

    fun toggleTheme() {
        isDarkTheme = !isDarkTheme
    }
}
