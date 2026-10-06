package com.example.plantry.data.usda

import com.example.plantry.data.Nutrient
import com.example.plantry.data.Nutrition

/** An entry of the bundled USDA FoodData Central (SR Legacy) dataset. */
data class UsdaFood(
    val fdcId: Long,
    val description: String,
    /** Per 100 g; nutrients USDA has no value for are 0. */
    val nutrition: Nutrition,
)

/** Offline search over the bundled USDA foods (English descriptions). */
class UsdaCatalog(foods: List<UsdaFood>) {

    private val entries = foods.map { Entry(it, words(it.description)) }

    /**
     * Returns foods with a word starting with each word of [query]. Foods whose description starts
     * with the first query word come first, then shorter descriptions.
     */
    fun search(query: String, limit: Int = 50): List<UsdaFood> {
        val terms = words(query)
        if (terms.isEmpty()) return emptyList()
        return entries
            .filter { entry -> terms.all { term -> entry.words.any { it.startsWith(term) } } }
            .sortedWith(
                compareBy<Entry> { !it.words.first().startsWith(terms.first()) }
                    .thenBy { it.words.size }
                    .thenBy { it.food.description.lowercase() },
            )
            .take(limit)
            .map { it.food }
    }

    /**
     * Candidates for Claude to pick from: the best [perTerm] hits of each term, in term order,
     * without duplicates, at most [limit].
     */
    fun candidates(terms: List<String>, perTerm: Int = 8, limit: Int = 15): List<UsdaFood> =
        terms.flatMap { search(it, perTerm) }.distinctBy { it.fdcId }.take(limit)

    private class Entry(val food: UsdaFood, val words: List<String>)

    companion object {
        const val ASSET_NAME = "usda_sr_legacy.tsv"

        /** Nutrient column order of the asset, see tools/usda_to_asset.py. */
        private val NUTRIENT_COLUMNS = listOf(
            Nutrient.KCAL, Nutrient.PROTEIN, Nutrient.CARBS, Nutrient.SUGAR, Nutrient.FAT, Nutrient.FIBRE,
        )

        private val WORD_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")

        private fun words(text: String) = text.lowercase().split(WORD_SEPARATOR).filter { it.isNotEmpty() }

        fun parse(lines: Sequence<String>) = UsdaCatalog(lines.filter { it.isNotBlank() }.map(::parseLine).toList())

        private fun parseLine(line: String): UsdaFood {
            val columns = line.split('\t')
            val nutrients = NUTRIENT_COLUMNS.withIndex().associate { (i, nutrient) ->
                nutrient to (columns[2 + i].toDoubleOrNull() ?: 0.0)
            }
            // Column 8, the portion weights, is no longer read.
            return UsdaFood(columns[0].toLong(), columns[1], Nutrition.of(nutrients))
        }
    }
}
