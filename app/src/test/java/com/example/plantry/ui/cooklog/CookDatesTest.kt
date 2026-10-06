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
    fun plannedDate_isShortWeekdayDayAndMonth_withTheYearOnlyWhenNotCurrent() {
        assertEquals("Do, 8. Okt", formatPlannedDate(LocalDate.of(2026, 10, 8), today, Locale.GERMANY))
        assertEquals("Fr, 1. Jan 2027", formatPlannedDate(LocalDate.of(2027, 1, 1), today, Locale.GERMANY))
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

    private fun weekLabel(monday: LocalDate, today: LocalDate = this.today) =
        cookWeekLabel(monday, today, Locale.GERMANY, thisWeek = "Diese Woche", lastWeek = "Letzte Woche")

    @Test
    fun weekLabel_thisAndLastWeek_relativeToToday() {
        // today (Mon 5 Oct 2026) starts this week.
        assertEquals("Diese Woche", weekLabel(LocalDate.of(2026, 10, 5)))
        assertEquals("Letzte Woche", weekLabel(LocalDate.of(2026, 9, 28)))
        assertEquals("Diese Woche", weekLabel(LocalDate.of(2026, 10, 5), today = LocalDate.of(2026, 10, 11)))
    }

    @Test
    fun weekLabel_olderWeeks_areTheRange() {
        assertEquals("21.–27. Sep", weekLabel(LocalDate.of(2026, 9, 21)))
        assertEquals("27. Jul – 2. Aug", weekLabel(LocalDate.of(2026, 7, 27)))
    }

    @Test
    fun weekLabel_acrossYears_hasBothYears() {
        assertEquals("29. Dez 2025 – 4. Jan 2026", weekLabel(LocalDate.of(2025, 12, 29)))
        assertEquals("29. Dez 2025 – 4. Jan 2026", weekLabel(LocalDate.of(2025, 12, 29), today = LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun weekLabel_inAnEarlierYear_hasTheYear() {
        assertEquals("6.–12. Okt 2025", weekLabel(LocalDate.of(2025, 10, 6)))
        assertEquals("29. Sep – 5. Okt 2025", weekLabel(LocalDate.of(2025, 9, 29)))
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
