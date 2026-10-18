package com.example.plantry.data.stats

import com.example.plantry.data.CookLog
import com.example.plantry.data.RecipeStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NutrientStatsTest {

    private fun date(month: Int, day: Int) = LocalDate.of(2026, month, day)

    private fun entry(on: LocalDate, protein: Double, fibre: Double? = null) =
        CookLog(recipeId = 1, cookedOn = on, title = "Dal", stats = RecipeStats(0.0, protein, 0.0, fibrePerPortion = fibre))

    @Test
    fun aDaysValue_isTheSumOfItsEntries() {
        val daily = dailyNutrient(
            listOf(entry(date(10, 12), 20.0), entry(date(10, 12), 15.5), entry(date(10, 13), 30.0)),
        ) { it.proteinPerPortion }

        assertEquals(mapOf(date(10, 12) to 35.5, date(10, 13) to 30.0), daily)
    }

    @Test
    fun entriesWithoutFibre_areLeftOutOfFibreOnly() {
        val entries = listOf(
            entry(date(10, 12), 20.0, fibre = 6.0),
            entry(date(10, 12), 10.0, fibre = null),
            entry(date(10, 13), 30.0, fibre = null),
        )

        assertEquals(mapOf(date(10, 12) to 6.0), dailyNutrient(entries) { it.fibrePerPortion })
        assertEquals(setOf(date(10, 12), date(10, 13)), dailyNutrient(entries) { it.proteinPerPortion }.keys)
    }

    @Test
    fun aWeek_hasABarPerDay_averagingOnlyDaysWithEntries() {
        val week = StatsPeriod(StatsRange.WEEK, date(10, 12))
        val stats = NutrientStats.of(week, mapOf(date(10, 12) to 60.0, date(10, 14) to 40.0, date(10, 20) to 99.0))

        assertEquals((12..18).map { date(10, it) }, stats.bars.map { it.start })
        assertEquals(listOf(60.0, null, 40.0, null, null, null, null), stats.bars.map { it.value })
        assertEquals(50.0, stats.average!!, 0.0)
        assertEquals(2, stats.days)
        assertEquals(100.0, stats.total, 0.0)
    }

    @Test
    fun aMonth_hasABarPerDayOfTheMonth() {
        val stats = NutrientStats.of(StatsPeriod(StatsRange.MONTH, date(2, 1)), mapOf(date(2, 28) to 10.0, date(3, 1) to 5.0))

        assertEquals(28, stats.bars.size)
        assertEquals(10.0, stats.bars.last().value)
        assertEquals(1, stats.days)
    }

    @Test
    fun threeMonths_haveABarPerWeek_withTheAverageOverItsDaysWithEntries() {
        // Aug–Okt 2026; the last week starts Mon 26 Oct and runs into November, where it still counts.
        val quarter = StatsPeriod(StatsRange.QUARTER, date(8, 1))
        val daily = mapOf(
            date(8, 3) to 30.0,
            date(8, 5) to 50.0,
            date(10, 26) to 20.0,
            date(11, 1) to 40.0,
            // Belongs to the week of 27 Jul, which starts in July.
            date(8, 1) to 99.0,
        )
        val stats = NutrientStats.of(quarter, daily)

        assertEquals(13, stats.bars.size)
        assertEquals(date(8, 3), stats.bars.first().start)
        assertEquals(40.0, stats.bars.first().value!!, 0.0)
        assertNull(stats.bars[1].value)
        assertEquals(30.0, stats.bars.last().value!!, 0.0)
        assertEquals(4, stats.days)
        assertEquals(35.0, stats.average!!, 0.0)
        assertEquals(140.0, stats.total, 0.0)
    }

    @Test
    fun aPeriodWithoutEntries_hasNoAverage() {
        val stats = NutrientStats.of(StatsPeriod(StatsRange.WEEK, date(10, 12)), emptyMap())

        assertNull(stats.average)
        assertEquals(0, stats.days)
        assertEquals(7, stats.bars.size)
    }
}
