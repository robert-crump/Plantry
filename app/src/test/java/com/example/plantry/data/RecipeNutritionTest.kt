package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeNutritionTest {

    private val tofu = Nutrition(kcal = 144.0, protein = 17.3, carbs = 2.8, sugar = 0.6, fat = 8.7, fibre = 2.3)
    private val lentils = Nutrition(kcal = 352.0, protein = 24.6, carbs = 63.4, sugar = 2.0, fat = 1.1, fibre = 10.7)
    private val oil = Nutrition(kcal = 884.0, fat = 100.0)

    private fun line(id: Long, name: String, grams: Double, per100g: Nutrition) = NutritionLine(id, name, grams, per100g)

    private fun assertNutrition(expected: Nutrition, actual: Nutrition) {
        Nutrient.entries.forEach { assertEquals("$it", expected[it], actual[it], 1e-9) }
    }

    @Test
    fun perPortion_isSumOfGramsTimesPer100gDividedByOurServings() {
        val nutrition = RecipeNutrition.calculate(
            listOf(line(1, "Tofu", 400.0, tofu), line(2, "Linsen", 200.0, lentils)),
            servings = 4,
        )

        assertNutrition(
            Nutrition(
                kcal = (4 * 144.0 + 2 * 352.0) / 4,
                protein = (4 * 17.3 + 2 * 24.6) / 4,
                carbs = (4 * 2.8 + 2 * 63.4) / 4,
                sugar = (4 * 0.6 + 2 * 2.0) / 4,
                fat = (4 * 8.7 + 2 * 1.1) / 4,
                fibre = (4 * 2.3 + 2 * 10.7) / 4,
            ),
            nutrition.perPortion,
        )
    }

    @Test
    fun noLines_isAllZeroWithoutContributors() {
        val nutrition = RecipeNutrition.calculate(emptyList(), servings = 2)

        assertNutrition(Nutrition(), nutrition.perPortion)
        assertTrue(nutrition.topContributors.values.all { it.isEmpty() })
        assertEquals(ProteinRating.RED, nutrition.proteinRating)
    }

    @Test
    fun nonPositiveServings_areRejected() {
        assertThrows(IllegalArgumentException::class.java) { RecipeNutrition.calculate(emptyList(), servings = 0) }
    }

    @Test
    fun topContributors_areLargestFirstPerPortionAndSkipZero() {
        val nutrition = RecipeNutrition.calculate(
            listOf(line(1, "Tofu", 200.0, tofu), line(2, "Linsen", 200.0, lentils), line(3, "Öl", 20.0, oil)),
            servings = 2,
        )

        assertEquals(
            listOf(Contributor("Linsen", 24.6), Contributor("Tofu", 17.3)),
            nutrition.topContributors.getValue(Nutrient.PROTEIN),
        )
        assertEquals(listOf("Öl", "Tofu", "Linsen"), nutrition.topContributors.getValue(Nutrient.FAT).map { it.name })
    }

    @Test
    fun topContributors_areLimitedToThree() {
        val lines = (1L..5L).map { line(it, "Zutat $it", it * 100.0, tofu) }

        val protein = RecipeNutrition.calculate(lines, servings = 1).topContributors.getValue(Nutrient.PROTEIN)

        assertEquals(listOf("Zutat 5", "Zutat 4", "Zutat 3"), protein.map { it.name })
    }

    @Test
    fun topContributors_mergeLinesOfTheSameIngredient() {
        val nutrition = RecipeNutrition.calculate(
            listOf(line(1, "Tofu", 100.0, tofu), line(2, "Linsen", 100.0, lentils), line(1, "Tofu", 100.0, tofu)),
            servings = 1,
        )

        val protein = nutrition.topContributors.getValue(Nutrient.PROTEIN)
        assertEquals(listOf("Tofu", "Linsen"), protein.map { it.name })
        assertEquals(34.6, protein.first().amount, 1e-9)
    }

    @Test
    fun proteinRating_isGreenFromThirtyGrams() {
        assertEquals(ProteinRating.GREEN, ProteinRating.of(30.0))
        assertEquals(ProteinRating.GREEN, ProteinRating.of(45.0))
        assertEquals(ProteinRating.YELLOW, ProteinRating.of(29.9))
        assertEquals(ProteinRating.YELLOW, ProteinRating.of(20.0))
        assertEquals(ProteinRating.RED, ProteinRating.of(19.9))
    }

    @Test
    fun proteinRating_followsPerPortionValue() {
        // 600 g tofu = 103.8 g protein: 3 portions are green, 4 yellow, 6 red.
        val lines = listOf(line(1, "Tofu", 600.0, tofu))

        assertEquals(ProteinRating.GREEN, RecipeNutrition.calculate(lines, servings = 3).proteinRating)
        assertEquals(ProteinRating.YELLOW, RecipeNutrition.calculate(lines, servings = 4).proteinRating)
        assertEquals(ProteinRating.RED, RecipeNutrition.calculate(lines, servings = 6).proteinRating)
    }

    @Test
    fun nutritionLines_lookUpIngredientsAndSkipMissingOnes() {
        val ingredient = ingredient(id = 1, name = "Tofu", nutrition = tofu)

        val lines = nutritionLines(
            listOf(RecipeIngredientDraft("200 g Tofu", 200.0, 1), RecipeIngredientDraft("x", 50.0, 99)),
            mapOf(1L to ingredient),
        )

        assertEquals(listOf(NutritionLine(1, "Tofu", 200.0, tofu)), lines)
    }
}

internal fun ingredient(id: Long, name: String, nutrition: Nutrition = Nutrition()) = Ingredient(
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
    plantPoints = PlantPoints.ZERO,
    buyAsIngredientId = null,
    buyAsYieldFactor = null,
    reviewed = true,
)
