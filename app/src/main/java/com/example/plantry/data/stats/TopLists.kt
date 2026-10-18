package com.example.plantry.data.stats

import com.example.plantry.data.CookLog
import com.example.plantry.data.CookLogPlant
import java.time.LocalDate

/** A row of "Meistgekochte Rezepte": [recipeId] null once the recipe was deleted. */
data class TopRecipe(val recipeId: Long?, val title: String, val count: Int)

/** A row of "Top-Pflanzen": [ingredientId] null once the ingredient was deleted; [plantPoints] 1 or ¼. */
data class TopPlant(val ingredientId: Long?, val name: String, val plantPoints: Double, val count: Int)

/** How many rows each top list shows. */
const val TOP_LIST_SIZE = 5

/**
 * The days a period's lists count, as its nutrient charts do: Woche and Monat their own days,
 * 3 Monate the seven days of each week starting in it.
 */
internal fun StatsPeriod.countedDays(): ClosedRange<LocalDate> = when (range) {
    StatsRange.QUARTER -> weeks.first()..weeks.last().plusDays(6)
    else -> start..end
}

/**
 * The most-cooked recipes in [period] by number of entries, grouped by recipe so a rename doesn't
 * split one (titled as its newest entry), entries of deleted recipes by their snapshot title. Ties go
 * to the most recently cooked, then by title.
 */
fun topRecipes(period: StatsPeriod, entries: List<CookLog>, limit: Int = TOP_LIST_SIZE): List<TopRecipe> {
    val days = period.countedDays()
    return entries.filter { it.cookedOn in days }
        .groupBy { entry -> entry.recipeId?.let { RecipeKey.Saved(it) } ?: RecipeKey.Deleted(entry.title) }
        .map { (key, group) ->
            val newest = group.maxWith(byRecency)
            Ranked(TopRecipe((key as? RecipeKey.Saved)?.id, newest.title, group.size), group.size, newest)
        }
        .ranked(limit) { it.title }
}

/**
 * The plants appearing in the most of [period]'s entries, from each entry's [plants] snapshot (zero
 * points already left out). A plant matches by ingredient, by name once the ingredient is gone, as in
 * [com.example.plantry.data.distinctPlantPoints]; it is named as in its newest entry and counts its
 * highest points. Ties go to the most recently used, then by name.
 */
fun topPlants(period: StatsPeriod, entries: List<CookLog>, plants: List<CookLogPlant>, limit: Int = TOP_LIST_SIZE): List<TopPlant> {
    val days = period.countedDays()
    val inPeriod = entries.filter { it.cookedOn in days }.associateBy { it.id }
    return plants.mapNotNull { plant -> inPeriod[plant.cookLogId]?.let { plant to it } }
        .groupBy { (plant, _) -> plant.ingredientId ?: plant.name }
        .map { (_, uses) ->
            val (newestPlant, newestEntry) = uses.maxWith { a, b -> byRecency.compare(a.second, b.second) }
            val count = uses.map { it.second.id }.distinct().size
            Ranked(TopPlant(newestPlant.ingredientId, newestPlant.name, uses.maxOf { it.first.plantPoints }, count), count, newestEntry)
        }
        .ranked(limit) { it.name }
}

private sealed interface RecipeKey {
    data class Saved(val id: Long) : RecipeKey
    data class Deleted(val title: String) : RecipeKey
}

/** Older entries first: by day, then by when they were logged on the same day. */
private val byRecency: Comparator<CookLog> = compareBy<CookLog> { it.cookedOn }.thenBy { it.id }

private class Ranked<T>(val row: T, val count: Int, val newest: CookLog)

/** Most entries first, then the most recently used, then by name; the first [limit]. */
private fun <T> List<Ranked<T>>.ranked(limit: Int, name: (T) -> String): List<T> =
    sortedWith(
        compareByDescending<Ranked<T>> { it.count }
            .then { a, b -> byRecency.compare(b.newest, a.newest) }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { name(it.row) },
    ).take(limit).map { it.row }
