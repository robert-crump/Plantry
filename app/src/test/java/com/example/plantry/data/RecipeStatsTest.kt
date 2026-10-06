package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeStatsTest {

    private val recipe = Recipe(1, "Dal", "", null, bookServings = 4, ourServings = 2, cookingTimeMinutes = 30)

    private val ingredients = listOf(
        ingredient(1, "Linsen", PlantPoints.ONE, Nutrition(kcal = 340.0, protein = 24.0, carbs = 50.0, fibre = 11.0)),
        ingredient(2, "Knoblauch", PlantPoints.QUARTER, Nutrition(kcal = 150.0, protein = 6.0, carbs = 33.0, fibre = 2.0)),
        ingredient(3, "Reis, trocken", PlantPoints.ONE, Nutrition(kcal = 360.0, protein = 7.0, carbs = 80.0, fibre = 1.0)),
        ingredient(4, "Reis, gekocht", PlantPoints.ONE, Nutrition(kcal = 130.0, protein = 2.7, carbs = 28.0, fibre = 0.4)),
        ingredient(5, "Öl", PlantPoints.ZERO, Nutrition()),
    ).associateBy { it.id }

    @Test
    fun of_sumsDistinctPlantsAndDividesNutritionByOurServings() {
        val lines = listOf(
            line(0, 200.0, ingredientId = 1),
            line(1, 10.0, ingredientId = 2),
            // Two different ingredients: two plants, even though both are rice.
            line(2, 100.0, ingredientId = 3),
            line(3, 100.0, ingredientId = 4),
            // The same ingredient again: counted once.
            line(4, 0.0, ingredientId = 1),
            line(5, 10.0, ingredientId = 5),
            // Missing ingredient: skipped.
            line(6, 50.0, ingredientId = 99),
        )

        val stats = RecipeStats.of(recipe, lines, ingredients)

        assertEquals(3.25, stats.plantPoints, 0.0)
        assertEquals((48.0 + 0.6 + 7.0 + 2.7) / 2, stats.proteinPerPortion, 1e-9)
        assertEquals((100.0 + 3.3 + 80.0 + 28.0) / 2, stats.carbsPerPortion, 1e-9)
        assertEquals((680.0 + 15.0 + 360.0 + 130.0) / 2, stats.kcalPerPortion!!, 1e-9)
        assertEquals((22.0 + 0.2 + 1.0 + 0.4) / 2, stats.fibrePerPortion!!, 1e-9)
    }

    @Test
    fun of_noLines_isZero() {
        assertEquals(RecipeStats(0.0, 0.0, 0.0, 0.0, 0.0), RecipeStats.of(recipe, emptyList(), ingredients))
    }

    @Test
    fun rounded_toNearestWholeNumber() {
        val stats = RecipeStats(
            plantPoints = 5.25,
            proteinPerPortion = 27.5,
            carbsPerPortion = 47.49,
            kcalPerPortion = 519.5,
            fibrePerPortion = 8.49,
        )

        assertEquals(5, stats.roundedPlantPoints)
        assertEquals(28, stats.roundedProtein)
        assertEquals(520, stats.roundedKcal)
        assertEquals(8, stats.roundedFibre)
        assertEquals(6, RecipeStats(5.75, 0.0, 0.0).roundedPlantPoints)
        assertEquals(3, RecipeStats(2.5, 0.0, 0.0).roundedPlantPoints)
    }

    @Test
    fun rounded_kcalAndFibreMissingInOldEntries_isNull() {
        val stats = RecipeStats(plantPoints = 1.0, proteinPerPortion = 20.0, carbsPerPortion = 40.0)

        assertEquals(null, stats.roundedKcal)
        assertEquals(null, stats.roundedFibre)
    }

    @Test
    fun snapshot_takesTitleAndOnlyThatRecipesLines() {
        val lines = listOf(line(0, 100.0, ingredientId = 1), line(0, 100.0, ingredientId = 3).copy(recipeId = 2))

        val snapshot = RecipeSnapshot.of(recipe, lines, ingredients)

        assertEquals("Dal", snapshot.title)
        assertEquals(RecipeStats(1.0, 12.0, 25.0, 170.0, 5.5), snapshot.stats)
    }

    private fun line(position: Int, grams: Double, ingredientId: Long) =
        RecipeIngredient(id = position + 1L, recipeId = 1, position = position, originalText = "", grams = grams, ingredientId = ingredientId)

    private fun ingredient(id: Long, name: String, points: PlantPoints, nutrition: Nutrition) =
        Ingredient(
            id = id,
            name = name,
            fdcId = null,
            usdaDescription = null,
            nutrition = nutrition,
            storeSection = StoreSection.OTHER,
            plantPoints = points,
            reviewed = true,
        )
}
