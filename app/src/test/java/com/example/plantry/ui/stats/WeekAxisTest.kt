package com.example.plantry.ui.stats

import com.example.plantry.data.stats.StatsPeriod
import com.example.plantry.data.stats.StatsRange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class WeekAxisTest {

    private val german = Locale.GERMAN

    @Test
    fun aMonth_spansItsDays_eachWeekOverItsDaysInTheMonth() {
        val axis = weekAxis(StatsPeriod(StatsRange.MONTH, LocalDate.of(2026, 10, 1)), german)

        assertEquals(31f, axis.length)
        assertEquals(listOf(4f to 11f, 11f to 18f, 18f to 25f, 25f to 31f), axis.spans)
        assertEquals(listOf("1", "5", "10", "15", "20", "25", "31"), axis.labels.map { it.text })
        assertEquals(0.5f, axis.labels.first().at)
        assertEquals(AxisMark.TICK, axis.labels.first().mark)
    }

    @Test
    fun threeMonths_haveASlotPerWeek_namingEachMonthAtItsFirstWeek() {
        val axis = weekAxis(StatsPeriod(StatsRange.QUARTER, LocalDate.of(2026, 8, 1)), german)

        assertEquals(13f, axis.length)
        assertEquals(0f to 1f, axis.spans.first())
        assertEquals(listOf("Aug", "Sep", "Okt"), axis.labels.mapNotNull { it.text })
        assertEquals(listOf(0, 5, 9), axis.labels.withIndex().filter { it.value.text != null }.map { it.index })
        assertEquals(AxisMark.DOT, axis.labels.last().mark)
    }

    @Test
    fun periodTitles() {
        assertEquals("Oktober 2026", formatPeriodTitle(StatsPeriod(StatsRange.MONTH, LocalDate.of(2026, 10, 1)), german))
        assertEquals("Aug–Okt 2026", formatPeriodTitle(StatsPeriod(StatsRange.QUARTER, LocalDate.of(2026, 8, 1)), german))
        assertEquals("Nov 2025 – Jan 2026", formatPeriodTitle(StatsPeriod(StatsRange.QUARTER, LocalDate.of(2025, 11, 1)), german))
    }
}
