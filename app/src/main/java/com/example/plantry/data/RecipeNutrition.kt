package com.example.plantry.data

/** An ingredient line reduced to what the nutrition calculation needs. */
data class NutritionLine(val ingredientId: Long, val name: String, val grams: Double, val per100g: Nutrition)

/** How much one ingredient contributes to a nutrient, per portion. */
data class Contributor(val name: String, val amount: Double)

/**
 * Nutrition of one portion of a recipe: the sum over all lines of grams × per-100 g values,
 * divided by our servings. Recipes are never scaled.
 */
data class RecipeNutrition(
    val perPortion: Nutrition,
    /** Per nutrient, the ingredients contributing most, largest first; zero contributions are left out. */
    val topContributors: Map<Nutrient, List<Contributor>>,
) {
    val proteinRating: ProteinRating get() = ProteinRating.of(perPortion.protein)

    companion object {
        const val TOP_CONTRIBUTOR_COUNT = 3

        fun calculate(lines: List<NutritionLine>, servings: Int): RecipeNutrition {
            require(servings > 0) { "servings must be positive, was $servings" }
            // An ingredient used on several lines counts as one contributor.
            val perIngredient = lines.groupBy { it.ingredientId }.values.map { group ->
                val first = group.first()
                first.name to first.per100g.scaled(group.sumOf { it.grams } / 100.0 / servings)
            }
            val perPortion = Nutrition.of(
                Nutrient.entries.associateWith { nutrient -> perIngredient.sumOf { (_, n) -> n[nutrient] } },
            )
            val topContributors = Nutrient.entries.associateWith { nutrient ->
                perIngredient
                    .map { (name, n) -> Contributor(name, n[nutrient]) }
                    .filter { it.amount > 0 }
                    .sortedByDescending { it.amount }
                    .take(TOP_CONTRIBUTOR_COUNT)
            }
            return RecipeNutrition(perPortion, topContributors)
        }
    }
}

/** Only protein is rated; the other nutrients are shown without judgement. */
enum class ProteinRating {
    GREEN,
    YELLOW,
    RED,
    ;

    companion object {
        /** Grams of protein per portion the plan aims for. */
        const val GREEN_MIN = 30.0
        const val YELLOW_MIN = 20.0

        fun of(proteinPerPortion: Double) = when {
            proteinPerPortion >= GREEN_MIN -> GREEN
            proteinPerPortion >= YELLOW_MIN -> YELLOW
            else -> RED
        }
    }
}

fun Nutrition.scaled(factor: Double) = Nutrition.of(Nutrient.entries.associateWith { this[it] * factor })

/**
 * Builds the nutrition lines for [lines], looking up each ingredient in [ingredients] by id.
 * Lines whose ingredient is missing are skipped.
 */
fun nutritionLines(lines: List<RecipeIngredientDraft>, ingredients: Map<Long, Ingredient>): List<NutritionLine> =
    lines.mapNotNull { line ->
        ingredients[line.ingredientId]?.let { NutritionLine(it.id, it.name, line.grams, it.nutrition) }
    }
