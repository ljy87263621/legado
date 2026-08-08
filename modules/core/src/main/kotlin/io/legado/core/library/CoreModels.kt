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
    val kind: String? = null,
    val customTag: String? = null,
    val customCoverUrl: String? = null,
    val customIntro: String? = null,
    val charset: String? = null,
    val type: Int = 0,
    val group: Long = 0,
    val latestChapterTitle: String? = null,
    val latestChapterTime: Long = 0,
    val lastCheckTime: Long = 0,
    val lastCheckCount: Int = 0,
    val totalChapterNum: Int = 0,
    val durChapterIndex: Int = 0,
    val durChapterPos: Int = 0,
    val durChapterTitle: String? = null,
    val durChapterTime: Long = 0,
    val wordCount: String? = null,
    val canUpdate: Boolean = true,
    val order: Int = 0,
    val originOrder: Int = 0,
    val variable: String? = null,
    val readConfigJson: String? = null,
    val syncTime: Long = 0
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
    val enabledCookieJar: Boolean? = true,
    val enableDangerousApi: Boolean? = false,
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
    val loginCheckJs: String? = null,
    val coverDecodeJs: String? = null,
    val bookSourceComment: String? = null,
    val variableComment: String? = null,
    val lastUpdateTime: Long = 0,
    val respondTime: Long = 180000,
    val weight: Int = 0,
    val exploreUrl: String? = null,
    val exploreScreen: String? = null,
    val exploreStyle: Int = 0
)

data class CoreCookie(
    val domain: String,
    val path: String,
    val name: String,
    val value: String,
    val persistent: Boolean = false,
    val expiresAt: Long? = null
)

data class CoreSourceVariable(
    val sourceUrl: String,
    val value: String
)

data class CoreSubscriptionPage(
    val url: String,
    val title: String,
    val iconUrl: String? = null,
    val category: String = "订阅页面",
    val lastUpdatedAt: Long = 0L
)

data class CoreUpdateSchedule(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 360,
    val nextRunAt: Long = 0L,
    val lastRunAt: Long = 0L,
    val lastSummary: String? = null
) {
    fun normalized(): CoreUpdateSchedule = copy(
        intervalMinutes = intervalMinutes.coerceIn(15, 1440)
    )
}

object CoreBookSourceType {
    const val AUDIO = 1
    const val IMAGE = 2
    const val FILE = 3
    const val VIDEO = 4
    const val RSS = 5
}

/** Bit flags persisted on [CoreBook.type], aligned with Android BookType values. */
object CoreBookType {
    const val TEXT = 1 shl 3
    const val AUDIO = 1 shl 5
    const val IMAGE = 1 shl 6
    const val WEB_FILE = 1 shl 7
    const val VIDEO = 1 shl 11
    const val RSS = 1 shl 12

    fun fromSourceType(sourceType: Int): Int = when (sourceType) {
        CoreBookSourceType.AUDIO -> AUDIO
        CoreBookSourceType.IMAGE -> IMAGE
        CoreBookSourceType.FILE -> TEXT or WEB_FILE
        CoreBookSourceType.VIDEO -> VIDEO
        CoreBookSourceType.RSS -> RSS
        else -> TEXT
    }
}

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
    val variable: String? = null,
    val start: Long? = null,
    val end: Long? = null,
    val startFragmentId: String? = null,
    val endFragmentId: String? = null
)

data class CoreChapterDownloadItem(
    val chapter: CoreChapter,
    val status: String,
    val error: String? = null
)

data class CoreChapterDownloadTask(
    val taskId: String,
    val bookUrl: String,
    val status: String,
    val total: Int,
    val completed: Int,
    val skipped: Int,
    val downloaded: Int,
    val failed: Int,
    val items: List<CoreChapterDownloadItem>,
    val error: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
