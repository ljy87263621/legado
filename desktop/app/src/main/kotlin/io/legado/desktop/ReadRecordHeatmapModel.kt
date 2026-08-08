package io.legado.desktop

import io.legado.core.library.CoreLibrary
import java.time.YearMonth

data class ReadRecordHeatmapDay(
    val day: Int,
    val seconds: Long,
    val level: Int
)

data class ReadRecordHeatmapMonth(
    val year: Int,
    val month: Int,
    val firstWeekdayMonday: Int,
    val days: List<ReadRecordHeatmapDay>,
    val totalSeconds: Long
)

object ReadRecordHeatmapLayout {
    const val CELL_HEIGHT_DP = 30
    const val CELL_SPACING_DP = 4

    fun rowCount(firstWeekdayMonday: Int, dayCount: Int): Int {
        require(firstWeekdayMonday in 0..6)
        require(dayCount in 1..31)
        return (firstWeekdayMonday + dayCount + 6) / 7
    }

    fun gridHeightDp(firstWeekdayMonday: Int, dayCount: Int): Int {
        val rows = rowCount(firstWeekdayMonday, dayCount)
        return rows * CELL_HEIGHT_DP + (rows - 1) * CELL_SPACING_DP
    }
}

class ReadRecordHeatmapModel(
    private val library: CoreLibrary
) {
    fun month(year: Int, month: Int): ReadRecordHeatmapMonth {
        val yearMonth = YearMonth.of(year, month)
        val secondsByDay = LongArray(yearMonth.lengthOfMonth() + 1)

        library.readRecords().forEach { record ->
            if (record.day / 100 == year * 100 + month) {
                val day = record.day % 100
                if (day in 1..yearMonth.lengthOfMonth()) {
                    secondsByDay[day] += (record.endSec - record.startSec).coerceAtLeast(0L)
                }
            }
        }

        val days = (1..yearMonth.lengthOfMonth()).map { day ->
            val seconds = secondsByDay[day]
            ReadRecordHeatmapDay(day, seconds, levelFor(seconds))
        }
        return ReadRecordHeatmapMonth(
            year = year,
            month = month,
            firstWeekdayMonday = yearMonth.atDay(1).dayOfWeek.value - 1,
            days = days,
            totalSeconds = days.sumOf(ReadRecordHeatmapDay::seconds)
        )
    }

    private fun levelFor(seconds: Long): Int {
        if (seconds <= 0L) return 0
        val ratio = (seconds.toDouble() / MAX_READ_SECONDS).coerceAtMost(1.0)
        return when {
            ratio <= 0.2 -> 1
            ratio <= 0.4 -> 2
            ratio <= 0.6 -> 3
            ratio <= 0.8 -> 4
            else -> 5
        }
    }

    private companion object {
        const val MAX_READ_SECONDS = 12L * 60L * 60L
    }
}
