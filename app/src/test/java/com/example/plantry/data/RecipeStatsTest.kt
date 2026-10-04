package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeStatsTest {

    private val recipe = Recipe(1, "Dal", "", null, bookServings = 4, ourServings = 2, cookingTimeMinutes = 30)

    private val ingredients = listOf(
        ingredient(1, "Linsen", PlantPoints.ONE, Nutrition(protein = 24.0, carbs = 50.0)),
        ingredient(2, "Knoblauch", PlantPoints.QUARTER, Nutrition(protein = 6.0, carbs = 33.0)),
        ingredient(3, "Reis, trocken", PlantPoints.ONE, Nutrition(protein = 7.0, carbs = 80.0)),
        ingredient(4, "Reis, gekocht", PlantPoints.ZERO, Nutrition(protein = 2.7, carbs = 28.0), buyAsIngredientId = 3),
        ingredient(5, "Öl", PlantPoints.ZERO, Nutrition()),
    ).associateBy { it.id }

    @Test
    fun of_sumsDistinctPlantsAndDividesNutritionByOurServings() {
        val lines = listOf(
            line(0, 200.0, ingredientId = 1),
            line(1, 10.0, ingredientId = 2),
            // Both rice lines are bought as Reis, trocken: one plant.
            line(2, 100.0, ingredientId = 3),
            line(3, 100.0, ingredientId = 4),
            line(4, 10.0, ingredientId = 5),
            // Missing ingredient: skipped.
            line(5, 50.0, ingredientId = 99),
        )

        val stats = RecipeStats.of(recipe, lines, ingredients)

        assertEquals(2.25, stats.plantPoints, 0.0)
        assertEquals((48.0 + 0.6 + 7.0 + 2.7) / 2, stats.proteinPerPortion, 1e-9)
        assertEquals((100.0 + 3.3 + 80.0 + 28.0) / 2, stats.carbsPerPortion, 1e-9)
    }

    @Test
    fun of_noLines_isZero() {
        assertEquals(RecipeStats(0.0, 0.0, 0.0), RecipeStats.of(recipe, emptyList(), ingredients))
    }

    @Test
    fun rounded_toNearestWholeNumber() {
        val stats = RecipeStats(plantPoints = 5.25, proteinPerPortion = 27.5, carbsPerPortion = 47.49)

        assertEquals(5, stats.roundedPlantPoints)
        assertEquals(28, stats.roundedProtein)
        assertEquals(47, stats.roundedCarbs)
        assertEquals(6, RecipeStats(5.75, 0.0, 0.0).roundedPlantPoints)
        assertEquals(3, RecipeStats(2.5, 0.0, 0.0).roundedPlantPoints)
    }

    @Test
    fun snapshot_takesTitleAndOnlyThatRecipesLines() {
        val lines = listOf(line(0, 100.0, ingredientId = 1), line(0, 100.0, ingredientId = 3).copy(recipeId = 2))

        val snapshot = RecipeSnapshot.of(recipe, lines, ingredients)

        assertEquals("Dal", snapshot.title)
        assertEquals(RecipeStats(1.0, 12.0, 25.0), snapshot.stats)
    }

    private fun line(position: Int, grams: Double, ingredientId: Long) =
        RecipeIngredient(id = position + 1L, recipeId = 1, position = position, originalText = "", grams = grams, ingredientId = ingredientId)

    private fun ingredient(id: Long, name: String, points: PlantPoints, nutrition: Nutrition, buyAsIngredientId: Long? = null) =
        Ingredient(
            id = id,
            name = name,
            fdcId = null,
            usdaDescription = null,
            nutrition = nutrition,
            unitWeights = emptyList(),
            buyUnit = BuyUnit.GRAMS,
            packSizeGrams = null,
            storeSection = StoreSection.OTHER,
            staple = false,
            plantPoints = points,
            buyAsIngredientId = buyAsIngredientId,
            buyAsYieldFactor = null,
            reviewed = true,
        )
}
