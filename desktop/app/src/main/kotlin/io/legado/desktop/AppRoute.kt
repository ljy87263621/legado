package io.legado.desktop

enum class AppRoute(val label: String) {
    WELCOME("欢迎"),
    BOOKSHELF("书架"),
    SEARCH("搜索"),
    EXPLORE("发现"),
    BOOK_DETAIL("详情"),
    SUBSCRIPTIONS("订阅"),
    SOURCES("书源"),
    SETTINGS("设置"),
    READER("阅读"),
    BOOKMARKS("书签"),
    READ_RECORDS("阅读记录"),
    REPLACE_RULES("替换规则"),
    DICT_RULES("字典规则"),
    TXT_TOC_RULES("TXT目录规则"),
    SOURCE_FILTER_RULES("书源发现筛选");

    companion object {
        val primary = listOf(
            BOOKSHELF,
            SEARCH,
            EXPLORE,
            SUBSCRIPTIONS,
            SOURCES,
            SETTINGS
        )
    }
}
