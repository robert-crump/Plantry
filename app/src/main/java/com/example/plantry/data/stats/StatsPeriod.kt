package com.example.plantry.data.stats

import com.example.plantry.data.planner.plantPointsGoalReached
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** The Statistik screen's period toggle. */
enum class StatsRange { WEEK, MONTH, QUARTER }

/**
 * One period of the Statistik screen: a Monday–Sunday week, a calendar month, or three calendar
 * months. Three-month periods step by three, counted back from the current month, so the current
 * one ends with it.
 */
data class StatsPeriod(val range: StatsRange, val start: LocalDate) {
    val end: LocalDate
        get() = when (range) {
            StatsRange.WEEK -> start.plusDays(6)
            StatsRange.MONTH -> start.plusMonths(1).minusDays(1)
            StatsRange.QUARTER -> start.plusMonths(3).minusDays(1)
        }

    /** The Mondays of the weeks belonging to this period: those starting in it. */
    val weeks: List<LocalDate>
        get() = generateSequence(start.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))) { it.plusWeeks(1) }
            .takeWhile { it <= end }
            .toList()

    operator fun contains(date: LocalDate) = date in start..end

    /** The period [steps] periods later, or earlier when negative. */
    fun plus(steps: Long): StatsPeriod = when (range) {
        StatsRange.WEEK -> copy(start = start.plusWeeks(steps))
        StatsRange.MONTH -> copy(start = start.plusMonths(steps))
        StatsRange.QUARTER -> copy(start = start.plusMonths(3 * steps))
    }

    companion object {
        /** The period of [range] containing [date], three-month periods counted back from [today]'s month. */
        fun containing(range: StatsRange, date: LocalDate, today: LocalDate): StatsPeriod = when (range) {
            StatsRange.WEEK -> StatsPeriod(range, weekStart(date))
            StatsRange.MONTH -> StatsPeriod(range, date.withDayOfMonth(1))
            StatsRange.QUARTER -> {
                val thisMonth = YearMonth.from(today)
                val monthsBack = ChronoUnit.MONTHS.between(YearMonth.from(date), thisMonth)
                val periodsBack = Math.floorDiv(monthsBack, 3L)
                StatsPeriod(range, thisMonth.minusMonths(3 * periodsBack + 2).atDay(1))
            }
        }
    }
}

/** The Monday of [date]'s week. */
internal fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * Which periods the Statistik screen can show, from [today] and the date of the [firstEntry] in the
 * cooking log (null without entries): forward up to the current period, back to the one holding the
 * first entry's week, since a week belongs to the month it starts in.
 */
class StatsCalendar(private val today: LocalDate, private val firstEntry: LocalDate?) {

    fun current(range: StatsRange): StatsPeriod = StatsPeriod.containing(range, today, today)

    fun canStepBack(period: StatsPeriod): Boolean =
        firstEntry != null && period.start > StatsPeriod.containing(period.range, weekStart(firstEntry), today).start

    fun canStepForward(period: StatsPeriod): Boolean = period.start < current(period.range).start
}

/**
 * A week's plant points on the Statistik screen; null for an empty slot: a week before the first
 * entry's, or one still ahead. [current] is the unfinished week of today.
 */
data class WeekPoints(val monday: LocalDate, val points: Double?, val current: Boolean) {
    val reached: Boolean get() = points != null && plantPointsGoalReached(points)

    /** Whether it counts towards the average and the reached count: the unfinished week only once it reached the goal. */
    val counted: Boolean get() = points != null && (!current || reached)
}

/** The plant points of a period's weeks, from each week's points as Speiseplan's week headers show them. */
class PlantPointsStats(val weeks: List<WeekPoints>) {
    private val counted = weeks.filter { it.counted }

    /** The average over the counted weeks; null if none counts yet. */
    val average: Double? = counted.mapNotNull { it.points }.takeIf { it.isNotEmpty() }?.average()

    val countedWeeks: Int get() = counted.size

    val reachedWeeks: Int = counted.count { it.reached }

    /** The highest week's points, 0 without any. */
    val max: Double = weeks.mapNotNull { it.points }.maxOrNull() ?: 0.0

    companion object {
        /**
         * [weekly] has the points of each week with entries, keyed by its Monday; weeks from the
         * [firstEntry]'s up to [today]'s without entries count as 0.
         */
        fun of(period: StatsPeriod, today: LocalDate, firstEntry: LocalDate?, weekly: Map<LocalDate, Double>): PlantPointsStats {
            val firstWeek = firstEntry?.let(::weekStart)
            val thisWeek = weekStart(today)
            return PlantPointsStats(
                period.weeks.map { monday ->
                    val points = if (firstWeek == null || monday < firstWeek || monday > thisWeek) null else weekly[monday] ?: 0.0
                    WeekPoints(monday, points, current = monday == thisWeek)
                },
            )
        }
    }
}
