package com.example.plantry.data

import java.time.LocalDate
import kotlin.math.roundToLong

/** An ingredient line reduced to what the price needs; [grams] are null when the ingredient lacks the line's unit. */
data class PriceLine(val ingredientId: Long, val name: String, val grams: Double?, val price: Price?)

/** What one ingredient costs for the whole recipe, in €. */
data class IngredientCost(val ingredientId: Long, val name: String, val cost: Double)

/** An ingredient the estimate leaves out or relies on an old price for; [priceDate] is set for an outdated price. */
data class PriceGap(val ingredientId: Long, val name: String, val priceDate: LocalDate? = null)

/**
 * The rough grocery cost of one portion of a recipe: the sum over the priced lines of grams × €/kg,
 * divided by our servings. Recipes are never scaled.
 */
data class RecipePrice(
    /** In €; null when no line has a price. */
    val perPortion: Double?,
    /** Per priced ingredient for the whole recipe, most expensive first. */
    val costs: List<IngredientCost>,
    /**
     * Ingredients without a price that matter: at least [NEGLIGIBLE_GRAMS] over the whole recipe, or on
     * a line without a weight (also when priced, as that line can't be costed). In recipe order.
     */
    val missing: List<PriceGap>,
    /** Priced ingredients of at least [NEGLIGIBLE_GRAMS] whose price is outdated, see [Price.isOutdated]. In recipe order. */
    val outdated: List<PriceGap>,
) {
    /** In € for the whole recipe; 0 without a price. */
    val total: Double get() = costs.sumOf { it.cost }

    /** Whether the estimate leaves something out or relies on an old price. */
    val hasGaps: Boolean get() = missing.isNotEmpty() || outdated.isNotEmpty()

    /** [perPortion] rounded to 10 cents, as the estimate shows it; null without a price. */
    val roundedPerPortion: Double? get() = perPortion?.let(::roundToTenCents)

    companion object {
        /** Below this many grams over the whole recipe, a missing or old price doesn't matter. */
        const val NEGLIGIBLE_GRAMS = 5.0

        fun roundToTenCents(euros: Double): Double = (euros * 10).roundToLong() / 10.0

        fun calculate(lines: List<PriceLine>, servings: Int, today: LocalDate): RecipePrice {
            require(servings > 0) { "servings must be positive, was $servings" }
            val costs = mutableListOf<IngredientCost>()
            val missing = mutableListOf<PriceGap>()
            val outdated = mutableListOf<PriceGap>()
            // An ingredient used on several lines counts once, with their grams summed.
            lines.groupBy { it.ingredientId }.values.forEach { group ->
                val first = group.first()
                val grams = group.sumOf { it.grams ?: 0.0 }
                val unweighed = group.any { it.grams == null }
                val price = first.price
                if (price != null && group.any { it.grams != null }) {
                    costs += IngredientCost(first.ingredientId, first.name, grams * price.pricePerKg / 1000)
                    if (grams >= NEGLIGIBLE_GRAMS && price.isOutdated(today)) {
                        outdated += PriceGap(first.ingredientId, first.name, price.priceDate)
                    }
                }
                if (unweighed || (price == null && grams >= NEGLIGIBLE_GRAMS)) missing += PriceGap(first.ingredientId, first.name)
            }
            return RecipePrice(
                perPortion = if (costs.isEmpty()) null else costs.sumOf { it.cost } / servings,
                costs = costs.sortedByDescending { it.cost },
                missing = missing,
                outdated = outdated,
            )
        }
    }
}

/**
 * Builds the price lines for [lines], looking up each ingredient in [ingredients] by id. Lines whose
 * ingredient is missing are skipped.
 */
fun priceLines(lines: List<RecipeLine>, ingredients: Map<Long, Ingredient>): List<PriceLine> =
    lines.mapNotNull { line ->
        ingredients[line.ingredientId]?.let { PriceLine(it.id, it.name, line.grams, it.price) }
    }
