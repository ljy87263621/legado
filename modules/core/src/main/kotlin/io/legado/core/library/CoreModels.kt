package io.legado.core.library

data class CoreBook(
    val bookUrl: String,
    val name: String = "",
    val author: String = "",
    val origin: String = "local",
    val originName: String = "",
    val tocUrl: String = "",
    val coverUrl: String? = null,
    val intro: String? = null,
    val type: Int = 0,
    val group: Long = 0,
    val totalChapterNum: Int = 0,
    val durChapterIndex: Int = 0,
    val durChapterPos: Int = 0,
    val durChapterTitle: String? = null,
    val durChapterTime: Long = 0,
    val canUpdate: Boolean = true,
    val order: Int = 0,
    val originOrder: Int = 0,
    val variable: String? = null,
    val readConfigJson: String? = null
)

data class CoreBookSource(
    val bookSourceUrl: String,
    val bookSourceName: String = "",
    val bookSourceGroup: String? = null,
    val bookSourceType: Int = 0,
    val bookUrlPattern: String? = null,
    val customOrder: Int = 0,
    val enabled: Boolean = true,
    val enabledExplore: Boolean = true,
    val enabledReview: Boolean = true,
    val enabledCookieJar: Boolean = true,
    val enableDangerousApi: Boolean = false,
    val concurrentRate: String? = null,
    val header: String? = null,
    val loginUrl: String? = null,
    val loginUi: String? = null,
    val searchUrl: String? = null,
    val ruleSearch: String? = null,
    val ruleBookInfo: String? = null,
    val ruleToc: String? = null,
    val ruleContent: String? = null,
    val ruleExplore: String? = null,
    val ruleReview: String? = null,
    val jsLib: String? = null,
    val bookSourceComment: String? = null
)

data class CoreChapter(
    val bookUrl: String,
    val url: String,
    val title: String = "",
    val index: Int = 0,
    val isVolume: Boolean = false,
    val isVip: Boolean = false,
    val isPay: Boolean = false,
    val resourceUrl: String? = null,
    val tag: String? = null,
    val wordCount: String? = null,
    val variable: String? = null
)
