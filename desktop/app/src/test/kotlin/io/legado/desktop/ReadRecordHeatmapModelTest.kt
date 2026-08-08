package io.legado.desktop

import io.legado.core.library.CoreReadRecord
import io.legado.core.library.InMemoryCoreLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadRecordHeatmapModelTest {

    @Test
    fun calculatesEnoughGridHeightForA31DayMonthStartingOnSaturday() {
        assertEquals(6, ReadRecordHeatmapLayout.rowCount(5, 31))
        assertEquals(200, ReadRecordHeatmapLayout.gridHeightDp(5, 31))
    }

    @Test
    fun usesLeapYearLengthForFebruary() {
        val month = ReadRecordHeatmapModel(InMemoryCoreLibrary()).month(2024, 2)

        assertEquals(29, month.days.size)
    }

    @Test
    fun aggregatesOnlyTheSelectedMonthAndUsesAndroidCompatibleLevels() {
        val library = InMemoryCoreLibrary()
        library.saveReadRecord(CoreReadRecord("星河", 20260731, 0L, 99L))
        library.saveReadRecord(CoreReadRecord("星河", 20260801, 100L, 3_700L))
        library.saveReadRecord(CoreReadRecord("山海", 20260801, 4_000L, 13_000L))
        library.saveReadRecord(CoreReadRecord("星河", 20260802, 0L, 43_200L))
        library.saveReadRecord(CoreReadRecord("星河", 20260803, 100L, 0L))

        val month = ReadRecordHeatmapModel(library).month(2026, 8)

        assertEquals(2026, month.year)
        assertEquals(8, month.month)
        assertEquals(5, month.firstWeekdayMonday)
        assertEquals(31, month.days.size)
        assertEquals(55_800L, month.totalSeconds)
        assertEquals(12_600L, month.days[0].seconds)
        assertEquals(2, month.days[0].level)
        assertEquals(43_200L, month.days[1].seconds)
        assertEquals(5, month.days[1].level)
        assertEquals(0L, month.days[2].seconds)
        assertEquals(0, month.days[2].level)
        assertEquals(0L, month.days[30].seconds)
    }
}
