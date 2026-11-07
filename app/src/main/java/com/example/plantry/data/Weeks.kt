package com.example.plantry.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The Monday of [date]'s week; weeks run Monday–Sunday. */
internal fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Groups [items] (newest first) into Monday–Sunday weeks, keyed by each week's Monday, keeping their order. */
internal fun <T> groupByWeek(items: List<T>, date: (T) -> LocalDate): Map<LocalDate, List<T>> =
    items.groupBy { weekStart(date(it)) }

/**
 * Per Monday–Sunday week, keyed by its Monday: the plant points of the distinct plants the week's
 * entries snapshotted (see [CookLogPlant], [distinctPlantPoints]), so later changes to recipes or
 * ingredients don't change past weeks.
 */
internal fun weeklyPlantPoints(entries: List<CookLog>, plants: List<CookLogPlant>): Map<LocalDate, Double> {
    val plantsByEntry = plants.groupBy { it.cookLogId }
    return groupByWeek(entries) { it.cookedOn }.mapValues { (_, week) ->
        distinctPlantPoints(week.flatMap { plantsByEntry[it.id].orEmpty() })
    }
}
