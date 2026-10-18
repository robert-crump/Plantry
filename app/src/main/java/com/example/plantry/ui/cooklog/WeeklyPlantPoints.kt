package com.example.plantry.ui.cooklog

import com.example.plantry.data.CookLog
import com.example.plantry.data.CookLogPlant
import com.example.plantry.data.distinctPlantPoints
import java.time.LocalDate

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
