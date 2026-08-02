package io.legado.desktop

enum class AppRoute(val label: String) {
    BOOKSHELF("书架"),
    SEARCH("搜索"),
    BOOK_DETAIL("详情"),
    SUBSCRIPTIONS("订阅"),
    SOURCES("书源"),
    SETTINGS("设置"),
    READER("阅读"),
    READ_RECORDS("阅读记录");

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
