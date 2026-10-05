package com.example.plantry.ui.cooklog

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class CookDatesTest {

    private val today = LocalDate.of(2026, 10, 5)

    @Test
    fun currentYear_inGerman_hasNoYear() {
        assertEquals("Mittwoch, 30. September", formatCookDate(LocalDate.of(2026, 9, 30), today, Locale.GERMANY))
    }

    @Test
    fun earlierYear_inGerman_hasTheYear() {
        assertEquals("Dienstag, 30. September 2025", formatCookDate(LocalDate.of(2025, 9, 30), today, Locale.GERMANY))
    }

    @Test
    fun currentYear_inEnglish_hasNoYear() {
        assertEquals("Wednesday, September 30", formatCookDate(LocalDate.of(2026, 9, 30), today, Locale.US))
    }

    @Test
    fun earlierYear_inEnglish_hasTheYear() {
        assertEquals("Tuesday, September 30, 2025", formatCookDate(LocalDate.of(2025, 9, 30), today, Locale.US))
    }

    @Test
    fun monthLabel_isTheLocaleAbbreviationWithoutDot() {
        assertEquals("Okt", cookMonthLabel(LocalDate.of(2026, 10, 23), Locale.GERMANY))
        assertEquals("Mär", cookMonthLabel(LocalDate.of(2026, 3, 1), Locale.GERMANY))
        assertEquals("Oct", cookMonthLabel(LocalDate.of(2026, 10, 23), Locale.US))
    }

    @Test
    fun monthYearLabel_isTheMonthAbbreviationAndTheYear() {
        assertEquals("Okt 2025", cookMonthYearLabel(LocalDate.of(2025, 10, 23), Locale.GERMANY))
        assertEquals("Dec 2024", cookMonthYearLabel(LocalDate.of(2024, 12, 24), Locale.US))
    }
}
