package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CookingStatsTest {

    private val today = LocalDate.of(2026, 10, 2)

    @Test
    fun daysSinceLastCooked_neverCooked_isNull() {
        assertNull(CookingStats.daysSinceLastCooked(emptyList(), today))
    }

    @Test
    fun daysSinceLastCooked_cookedToday_isZero() {
        assertEquals(0L, CookingStats.daysSinceLastCooked(listOf(today), today))
    }

    @Test
    fun daysSinceLastCooked_cookedYesterday_isOne() {
        assertEquals(1L, CookingStats.daysSinceLastCooked(listOf(today.minusDays(1)), today))
    }

    @Test
    fun daysSinceLastCooked_usesLatestDateRegardlessOfOrder() {
        val dates = listOf(today.minusDays(30), today.minusDays(3), today.minusDays(10))

        assertEquals(3L, CookingStats.daysSinceLastCooked(dates, today))
    }

    @Test
    fun daysSinceLastCooked_acrossMonthAndYear() {
        assertEquals(3L, CookingStats.daysSinceLastCooked(listOf(LocalDate.of(2026, 9, 29)), today))
        assertEquals(
            2L,
            CookingStats.daysSinceLastCooked(listOf(LocalDate.of(2025, 12, 31)), LocalDate.of(2026, 1, 2)),
        )
    }

    @Test
    fun daysSinceLastCooked_futureDate_countsAsToday() {
        assertEquals(0L, CookingStats.daysSinceLastCooked(listOf(today.plusDays(2)), today))
    }

    @Test
    fun from_countsEveryEntryIncludingSameDay() {
        val stats = CookingStats.from(listOf(today.minusDays(5), today.minusDays(5), today.minusDays(9)), today)

        assertEquals(CookingStats(timesCooked = 3, daysSinceLastCooked = 5), stats)
    }

    @Test
    fun from_neverCooked() {
        assertEquals(CookingStats(timesCooked = 0, daysSinceLastCooked = null), CookingStats.from(emptyList(), today))
    }
}
