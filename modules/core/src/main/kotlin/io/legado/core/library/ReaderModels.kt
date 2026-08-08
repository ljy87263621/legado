package io.legado.core.library

import java.util.Calendar

data class CoreBookGroup(
    val groupId: Long = 1L,
    val groupName: String = "",
    val cover: String? = null,
    val order: Int = 0,
    val enableRefresh: Boolean = true,
    val show: Boolean = true,
    val bookSort: Int = -1
)

object CoreBookGroupIds {
    const val ROOT = -100L
    const val ALL = -1L
    const val LOCAL = -2L
    const val UNGROUPED = -4L
    const val ERROR = -11L
}

data class CoreBookmark(
    val time: Long = System.currentTimeMillis(),
    val bookName: String = "",
    val bookAuthor: String = "",
    val chapterIndex: Int = 0,
    val chapterPos: Int = 0,
    val chapterName: String = "",
    val bookText: String = "",
    val content: String = ""
)

data class CoreReadRecord(
    val bookName: String = "",
    val day: Int = 0,
    val startSec: Long = 0L,
    val endSec: Long = 0L
) {
    companion object {
        fun dayKey(timeSec: Long = System.currentTimeMillis() / 1000): Int {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = timeSec * 1000L
            return calendar.get(Calendar.YEAR) * 10000 +
                (calendar.get(Calendar.MONTH) + 1) * 100 +
                calendar.get(Calendar.DAY_OF_MONTH)
        }
    }
}

enum class CoreReaderTheme {
    DAY,
    NIGHT,
    SEPIA,
    GREEN
}

enum class CoreReaderPageMode {
    SCROLL,
    PAGED
}

data class CoreReaderSettings(
    val textSize: Int = 20,
    val lineSpacingExtra: Int = 12,
    val theme: CoreReaderTheme = CoreReaderTheme.DAY,
    val pageMode: CoreReaderPageMode = CoreReaderPageMode.SCROLL,
    val autoRead: Boolean = false,
    val autoReadSpeedSeconds: Int = 10
)
