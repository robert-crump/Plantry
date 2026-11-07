package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeeksTest {
    @Test
    fun weekStart_isTheMonday() {
        assertEquals(LocalDate.of(2026, 10, 5), weekStart(LocalDate.of(2026, 10, 5)))
        assertEquals(LocalDate.of(2026, 10, 5), weekStart(LocalDate.of(2026, 10, 11)))
    }

    @Test
    fun groupByWeek_splitsAtMondays_newestFirst() {
        val dates = listOf(
            LocalDate.of(2026, 10, 5), // Mon
            LocalDate.of(2026, 10, 4), // Sun
            LocalDate.of(2026, 9, 28), // Mon
            LocalDate.of(2026, 9, 27), // Sun
        )
        val weeks = groupByWeek(dates) { it }
        assertEquals(
            listOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 21)),
            weeks.keys.toList(),
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 9, 28)), weeks.getValue(LocalDate.of(2026, 9, 28)))
    }
}
