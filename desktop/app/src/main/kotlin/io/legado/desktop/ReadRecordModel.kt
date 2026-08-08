package io.legado.desktop

import io.legado.core.library.CoreBook
import io.legado.core.library.CoreLibrary
import io.legado.core.library.CoreReadRecord
import java.text.Collator
import java.util.Locale

enum class ReadRecordSort {
    LAST_READ,
    TOTAL_SECONDS,
    BOOK_NAME
}

data class ReadRecordListItem(
    val bookName: String,
    val totalSeconds: Long,
    val lastReadSec: Long,
    val recordCount: Int,
    val book: CoreBook?
)

data class ReadRecordSummary(
    val totalSeconds: Long,
    val bookCount: Int,
    val sessionCount: Int
)

class ReadRecordModel(
    private val library: CoreLibrary
) {
    var query: String = ""
        private set

    var sort: ReadRecordSort = ReadRecordSort.LAST_READ
        private set

    val summary: ReadRecordSummary
        get() {
            val records = library.readRecords()
            return ReadRecordSummary(
                totalSeconds = records.sumOf(::durationSeconds),
                bookCount = records.map(CoreReadRecord::bookName).distinct().size,
                sessionCount = records.size
            )
        }

    fun setQuery(value: String) {
        query = value.trim()
    }

    fun setSort(value: ReadRecordSort) {
        sort = value
    }

    fun visibleRecords(): List<ReadRecordListItem> {
        val normalizedQuery = query.trim()
        val items = library.readRecords()
            .groupBy(CoreReadRecord::bookName)
            .asSequence()
            .map { (bookName, records) ->
                ReadRecordListItem(
                    bookName = bookName,
                    totalSeconds = records.sumOf(::durationSeconds),
                    lastReadSec = records.maxOfOrNull(CoreReadRecord::endSec) ?: 0L,
                    recordCount = records.size,
                    book = library.books().firstOrNull { it.name == bookName }
                )
            }
            .filter { it.bookName.contains(normalizedQuery, ignoreCase = true) }
            .toList()
        return when (sort) {
            ReadRecordSort.LAST_READ -> items.sortedWith(
                compareByDescending<ReadRecordListItem> { it.lastReadSec }
                    .thenBy { it.bookName }
            )
            ReadRecordSort.TOTAL_SECONDS -> items.sortedWith(
                compareByDescending<ReadRecordListItem> { it.totalSeconds }
                    .thenBy { it.bookName }
            )
            ReadRecordSort.BOOK_NAME -> items.sortedWith { left, right ->
                Collator.getInstance(Locale.CHINA).compare(left.bookName, right.bookName)
            }
        }
    }

    fun open(item: ReadRecordListItem): CoreBook? = item.book

    fun deleteBookRecords(bookName: String): Boolean {
        if (library.readRecords().none { it.bookName == bookName }) return false
        library.deleteReadRecords(bookName)
        return true
    }

    fun exportMarkdown(): String {
        val records = library.readRecords()
        val byBook = records.groupBy(CoreReadRecord::bookName).toSortedMap()
        val builder = StringBuilder()
            .appendLine("# Legado 阅读记录")
            .appendLine()
            .appendLine("## 总览")
            .appendLine("- 总阅读时长：${records.sumOf(::durationSeconds)} 秒")
            .appendLine("- 书籍：${byBook.size} 本")
            .appendLine("- 会话：${records.size} 次")
            .appendLine()
            .appendLine("## 按书汇总")

        byBook.forEach { (bookName, bookRecords) ->
            builder
                .appendLine()
                .appendLine("### ${markdownText(bookName)}")
                .appendLine("- 总时长：${bookRecords.sumOf(::durationSeconds)} 秒")
                .appendLine("- 会话：${bookRecords.size} 次")
                .appendLine("- 最近阅读：${bookRecords.maxOfOrNull(CoreReadRecord::endSec) ?: 0L}")
        }

        builder
            .appendLine()
            .appendLine("## 会话明细")
            .appendLine()
            .appendLine("| 书名 | 日期 | 开始 | 结束 | 时长（秒） |")
            .appendLine("| --- | ---: | ---: | ---: | ---: |")

        records.sortedWith(
            compareBy<CoreReadRecord>(CoreReadRecord::bookName)
                .thenBy(CoreReadRecord::day)
                .thenBy(CoreReadRecord::startSec)
                .thenBy(CoreReadRecord::endSec)
        ).forEach { record ->
            builder.appendLine(
                "| ${markdownCell(record.bookName)} | ${record.day} | " +
                    "${record.startSec} | ${record.endSec} | ${durationSeconds(record)} |"
            )
        }
        return builder.toString()
    }

    private fun durationSeconds(record: CoreReadRecord): Long =
        (record.endSec - record.startSec).coerceAtLeast(0L)

    private fun markdownText(value: String): String =
        value.replace("\r", " ").replace("\n", " ").trim()

    private fun markdownCell(value: String): String =
        markdownText(value).replace("|", "\\|")
}
