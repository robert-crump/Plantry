package com.example.plantry.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsPeriodTest {

    /** A Sunday. */
    private val today = LocalDate.of(2026, 10, 18)

    private fun date(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test
    fun aWeek_runsMondayToSunday() {
        val week = StatsPeriod.containing(StatsRange.WEEK, today, today)

        assertEquals(date(10, 12), week.start)
        assertEquals(date(10, 18), week.end)
        assertEquals(listOf(date(10, 12)), week.weeks)
    }

    @Test
    fun aMonth_isTheCalendarMonth() {
        val february = StatsPeriod.containing(StatsRange.MONTH, date(2, 14), today)

        assertEquals(date(2, 1), february.start)
        assertEquals(date(2, 28), february.end)
    }

    @Test
    fun aMonthsWeeks_areThoseStartingInIt() {
        // 1 Oct 2026 is a Thursday: its week belongs to September; the week of Mon 26 Oct runs into November.
        val october = StatsPeriod.containing(StatsRange.MONTH, today, today)

        assertEquals(listOf(date(10, 5), date(10, 12), date(10, 19), date(10, 26)), october.weeks)
        assertEquals(date(9, 28), StatsPeriod.containing(StatsRange.MONTH, date(9, 1), today).weeks.last())
    }

    @Test
    fun theCurrentQuarter_endsWithThisMonth() {
        val quarter = StatsPeriod.containing(StatsRange.QUARTER, today, today)

        assertEquals(date(8, 1), quarter.start)
        assertEquals(date(10, 31), quarter.end)
        assertEquals(13, quarter.weeks.size)
        assertEquals(date(8, 3), quarter.weeks.first())
    }

    @Test
    fun quarters_stepByThreeMonthsBackFromThisMonth() {
        assertEquals(date(5, 1), StatsPeriod.containing(StatsRange.QUARTER, date(7, 31), today).start)
        assertEquals(date(5, 1), StatsPeriod.containing(StatsRange.QUARTER, date(5, 1), today).start)
        assertEquals(date(2, 1), StatsPeriod.containing(StatsRange.QUARTER, date(4, 30), today).start)
        assertEquals(date(11, 1, 2025), StatsPeriod.containing(StatsRange.QUARTER, date(1, 2), today).start)
    }

    @Test
    fun plus_stepsByOnePeriod() {
        assertEquals(date(10, 5), StatsPeriod(StatsRange.WEEK, date(10, 12)).plus(-1).start)
        assertEquals(date(11, 1, 2025), StatsPeriod(StatsRange.MONTH, date(1, 1)).plus(-2).start)
        assertEquals(date(5, 1), StatsPeriod(StatsRange.QUARTER, date(8, 1)).plus(-1).start)
        assertEquals(date(11, 1), StatsPeriod(StatsRange.QUARTER, date(8, 1)).plus(1).start)
    }

    @Test
    fun contains_includesBothEnds() {
        val october = StatsPeriod(StatsRange.MONTH, date(10, 1))

        assertTrue(date(10, 1) in october)
        assertTrue(date(10, 31) in october)
        assertFalse(date(11, 1) in october)
    }

    @Test
    fun forward_stopsAtTheCurrentPeriod() {
        val calendar = StatsCalendar(today, firstEntry = date(1, 5))
        StatsRange.entries.forEach { range ->
            val current = calendar.current(range)
            assertFalse(calendar.canStepForward(current))
            assertTrue(calendar.canStepForward(current.plus(-1)))
        }
    }

    @Test
    fun back_stopsAtThePeriodHoldingTheFirstEntry() {
        val calendar = StatsCalendar(today, firstEntry = date(9, 16))

        assertTrue(calendar.canStepBack(StatsPeriod(StatsRange.WEEK, date(9, 21))))
        assertFalse(calendar.canStepBack(StatsPeriod(StatsRange.WEEK, date(9, 14))))
        assertTrue(calendar.canStepBack(StatsPeriod(StatsRange.MONTH, date(10, 1))))
        assertFalse(calendar.canStepBack(StatsPeriod(StatsRange.MONTH, date(9, 1))))
        assertFalse(calendar.canStepBack(calendar.current(StatsRange.QUARTER)))
    }

    @Test
    fun back_reachesTheMonthTheFirstEntrysWeekStartsIn() {
        // Thu 1 Oct belongs to the week of Mon 28 Sep, which September shows.
        val calendar = StatsCalendar(today, firstEntry = date(10, 1))

        assertTrue(calendar.canStepBack(StatsPeriod(StatsRange.MONTH, date(10, 1))))
        assertFalse(calendar.canStepBack(StatsPeriod(StatsRange.MONTH, date(9, 1))))
    }

    @Test
    fun weekStepsTo_countsWeeksBackFromTheCurrentOne() {
        val calendar = StatsCalendar(today, firstEntry = date(9, 16))

        assertEquals(0, calendar.weekStepsTo(date(10, 12)))
        assertEquals(0, calendar.weekStepsTo(today))
        assertEquals(-1, calendar.weekStepsTo(date(10, 5)))
        // Thu 17 Sep belongs to the week of Mon 14 Sep.
        assertEquals(-4, calendar.weekStepsTo(date(9, 17)))
        assertEquals(date(9, 14), calendar.current(StatsRange.WEEK).plus(-4).start)
    }

    @Test
    fun weekStepsTo_staysBetweenTheFirstEntrysWeekAndToday() {
        val calendar = StatsCalendar(today, firstEntry = date(9, 16))

        assertEquals(-4, calendar.weekStepsTo(date(8, 3)))
        assertEquals(0, calendar.weekStepsTo(date(11, 2)))
        assertEquals(0, StatsCalendar(today, firstEntry = null).weekStepsTo(date(10, 5)))
    }

    @Test
    fun withoutEntries_thereIsNothingToStepBackTo() {
        assertFalse(StatsCalendar(today, firstEntry = null).canStepBack(StatsPeriod(StatsRange.WEEK, date(10, 12))))
    }

    @Test
    fun plantPoints_weeksAfterTheFirstEntryWithoutEntriesCountAsZero_earlierOnesAreEmpty() {
        val stats = PlantPointsStats.of(
            StatsPeriod(StatsRange.MONTH, date(9, 1)),
            today,
            firstEntry = date(9, 16),
            weekly = mapOf(date(9, 14) to 20.0, date(9, 28) to 31.0),
        )

        assertEquals(listOf(null, 20.0, 0.0, 31.0), stats.weeks.map { it.points })
        assertEquals(3, stats.countedWeeks)
        assertEquals(51.0 / 3, stats.average!!, 1e-9)
        assertEquals(1, stats.reachedWeeks)
        assertEquals(31.0, stats.max, 1e-9)
    }

    @Test
    fun plantPoints_weeksStillAheadAreEmpty() {
        val stats = PlantPointsStats.of(StatsPeriod(StatsRange.MONTH, date(10, 1)), date(10, 14), date(1, 1), emptyMap())

        assertEquals(listOf(0.0, 0.0, null, null), stats.weeks.map { it.points })
    }

    @Test
    fun plantPoints_theUnfinishedWeekIsLeftOutUntilItReachesTheGoal() {
        val period = StatsPeriod(StatsRange.MONTH, date(10, 1))
        val below = PlantPointsStats.of(period, today, date(1, 1), mapOf(date(10, 5) to 30.0, date(10, 12) to 12.0))

        assertTrue(below.weeks.last { it.points != null }.current)
        assertEquals(1, below.countedWeeks)
        assertEquals(30.0, below.average!!, 1e-9)
        assertEquals(1, below.reachedWeeks)
        assertEquals(30.0, below.max, 1e-9)

        val reached = PlantPointsStats.of(period, today, date(1, 1), mapOf(date(10, 5) to 30.0, date(10, 12) to 29.6))
        assertEquals(2, reached.countedWeeks)
        assertEquals(2, reached.reachedWeeks)
    }

    @Test
    fun plantPoints_withOnlyTheUnfinishedWeek_thereIsNoAverage() {
        val stats = PlantPointsStats.of(StatsPeriod(StatsRange.WEEK, date(10, 12)), today, date(10, 13), mapOf(date(10, 12) to 18.0))

        assertNull(stats.average)
        assertEquals(0, stats.countedWeeks)
        assertEquals(18.0, stats.weeks.single().points!!, 1e-9)
    }
}
