package com.example.plantry.ui.cooklog

import com.example.plantry.data.CookLog
import com.example.plantry.data.Ingredient
import com.example.plantry.data.MixContents
import com.example.plantry.data.Mixes
import com.example.plantry.data.RecipeLine
import java.time.LocalDate

/**
 * Per Monday–Sunday week, keyed by its Monday: the plant points of the distinct plants of the
 * week's entries, by their recipes as they are now, each mix resolved to its components (see
 * [Mixes.plants]). Entries of deleted recipes are skipped.
 */
internal fun weeklyPlantPoints(
    entries: List<CookLog>,
    lines: List<RecipeLine>,
    ingredients: Map<Long, Ingredient>,
    contents: MixContents = emptyMap(),
): Map<LocalDate, Double> {
    val ingredientIds = lines.groupBy({ it.recipeId }, { it.ingredientId })
    return groupByWeek(entries) { it.cookedOn }.mapValues { (_, week) ->
        Mixes.plantPoints(week.flatMap { entry -> entry.recipeId?.let { ingredientIds[it] }.orEmpty() }, ingredients, contents)
    }
}
