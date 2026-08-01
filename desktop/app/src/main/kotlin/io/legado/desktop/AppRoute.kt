package io.legado.desktop

enum class AppRoute(val label: String) {
    BOOKSHELF("书架"),
    SEARCH("搜索"),
    SUBSCRIPTIONS("订阅"),
    SOURCES("书源"),
    SETTINGS("设置"),
    READER("阅读");

    companion object {
        val primary = listOf(
            BOOKSHELF,
            SEARCH,
            SUBSCRIPTIONS,
            SOURCES,
            SETTINGS
        )
    }
}
