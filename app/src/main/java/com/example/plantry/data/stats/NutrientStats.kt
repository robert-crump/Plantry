package com.example.plantry.data.stats

import com.example.plantry.data.CookLog
import com.example.plantry.data.RecipeStats
import java.time.LocalDate

/**
 * Each day's logged amount of a nutrient: the sum of [value] (per portion, from the entry's stats
 * snapshot) over that day's entries. Entries without a value are left out; a day with only such
 * entries is missing.
 */
fun dailyNutrient(entries: List<CookLog>, value: (RecipeStats) -> Double?): Map<LocalDate, Double> =
    entries.mapNotNull { entry -> value(entry.stats)?.let { entry.cookedOn to it } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.sum() }

/** One bar of a nutrient chart: a day, or for 3 Monate the week of [start]; [value] null without entries. */
data class NutrientBar(val start: LocalDate, val value: Double?)

/**
 * A nutrient's logged amounts in a period, counting only days with entries: Woche and Monat have a
 * bar per day, 3 Monate a bar per week with the average over its days with entries.
 */
class NutrientStats(val bars: List<NutrientBar>, private val logged: List<Double>) {
    /** The average per day with entries; null without any. */
    val average: Double? = logged.takeIf { it.isNotEmpty() }?.average()

    val days: Int get() = logged.size

    val total: Double = logged.sum()

    companion object {
        /** [daily] as from [dailyNutrient]; 3 Monate take each week's seven days, as its weeks belong to the month they start in. */
        fun of(period: StatsPeriod, daily: Map<LocalDate, Double>): NutrientStats = when (period.range) {
            StatsRange.QUARTER -> {
                val weeks = period.weeks.map { monday -> monday to (0L..6L).mapNotNull { daily[monday.plusDays(it)] } }
                NutrientStats(
                    weeks.map { (monday, values) -> NutrientBar(monday, values.takeIf { it.isNotEmpty() }?.average()) },
                    weeks.flatMap { it.second },
                )
            }
            else -> {
                val days = generateSequence(period.start) { it.plusDays(1) }.takeWhile { it <= period.end }.toList()
                NutrientStats(days.map { NutrientBar(it, daily[it]) }, days.mapNotNull { daily[it] })
            }
        }
    }
}
