package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekSummaryTest {

    private val riceDry = ingredient(1, "Reis, trocken").copy(plantPoints = PlantPoints.ONE)
    private val riceCooked = ingredient(2, "Reis, gekocht").copy(plantPoints = PlantPoints.ONE, buyAsIngredientId = 1)
    private val lentils = ingredient(3, "Linsen", Nutrition(protein = 25.0)).copy(plantPoints = PlantPoints.ONE)
    private val cumin = ingredient(4, "Kreuzkümmel").copy(plantPoints = PlantPoints.QUARTER)
    private val paprika = ingredient(5, "Paprikapulver").copy(plantPoints = PlantPoints.QUARTER)
    private val tofu = ingredient(6, "Tofu", Nutrition(protein = 15.0))

    private val ingredients = listOf(riceDry, riceCooked, lentils, cumin, paprika, tofu).associateBy { it.id }

    private fun recipe(id: Long, servings: Int = 2) = Recipe(
        id = id, title = "Rezept $id", source = "", page = null,
        bookServings = servings, ourServings = servings, cookingTimeMinutes = 20,
    )

    private fun line(recipeId: Long, ingredient: Ingredient, grams: Double = 100.0) =
        RecipeIngredient(recipeId = recipeId, position = 0, originalText = "", grams = grams, ingredientId = ingredient.id)

    private fun summary(recipes: List<Recipe>, lines: List<RecipeIngredient>) = WeekSummary.of(recipes, lines, ingredients)

    @Test
    fun plantPoints_buyAsLinkCountsAsItsTarget() {
        val result = summary(
            listOf(recipe(1), recipe(2)),
            listOf(line(1, riceCooked), line(2, riceDry), line(2, lentils)),
        )

        assertEquals(2.0, result.plantPoints, 0.0)
        assertEquals(2, result.distinctIngredients)
    }

    @Test
    fun plantPoints_sameIngredientInSeveralRecipesCountsOnce() {
        val result = summary(
            listOf(recipe(1), recipe(2)),
            listOf(line(1, lentils), line(2, lentils), line(2, lentils, grams = 50.0)),
        )

        assertEquals(1.0, result.plantPoints, 0.0)
        assertEquals(1, result.distinctIngredients)
    }

    @Test
    fun plantPoints_herbsAndSpicesCountAQuarter_nonPlantsZero() {
        val result = summary(
            listOf(recipe(1)),
            listOf(line(1, cumin), line(1, paprika), line(1, cumin), line(1, tofu), line(1, lentils)),
        )

        assertEquals(1.5, result.plantPoints, 0.0)
        assertEquals(4, result.distinctIngredients)
    }

    @Test
    fun plantPoints_countTheTargetsPoints() {
        // An unreviewed cooked variant without points still counts as the dry plant it is bought as.
        val cookedNoPoints = riceCooked.copy(id = 7, plantPoints = PlantPoints.ZERO)
        val result = WeekSummary.of(
            listOf(recipe(1)),
            listOf(line(1, cookedNoPoints)),
            ingredients + (cookedNoPoints.id to cookedNoPoints),
        )

        assertEquals(1.0, result.plantPoints, 0.0)
    }

    @Test
    fun plantPoints_followTheWholeBuyAsChain() {
        val riceLeftover = ingredient(8, "Reis, Rest").copy(buyAsIngredientId = riceCooked.id)
        val result = WeekSummary.of(
            listOf(recipe(1)),
            listOf(line(1, riceLeftover), line(1, riceCooked), line(1, riceDry)),
            ingredients + (riceLeftover.id to riceLeftover),
        )

        assertEquals(1.0, result.plantPoints, 0.0)
        assertEquals(1, result.distinctIngredients)
    }

    @Test
    fun buyAsRoot_stopsOnCycle() {
        val a = ingredient(10, "A").copy(buyAsIngredientId = 11)
        val b = ingredient(11, "B").copy(buyAsIngredientId = 10)

        assertEquals(b, WeekSummary.buyAsRoot(a, mapOf(a.id to a, b.id to b)))
    }

    @Test
    fun linesOfRecipesNotOnTheMenuAreIgnored() {
        val result = summary(listOf(recipe(1)), listOf(line(1, lentils), line(2, riceDry)))

        assertEquals(1, result.distinctIngredients)
        assertEquals(1.0, result.plantPoints, 0.0)
    }

    @Test
    fun averageProtein_isTheMeanOfThePerPortionValues() {
        val result = summary(
            // 400 g lentils / 2 = 50 g; 200 g tofu / 1 = 30 g; no lines = 0 g.
            listOf(recipe(1, servings = 2), recipe(2, servings = 1), recipe(3)),
            listOf(line(1, lentils, grams = 400.0), line(2, tofu, grams = 200.0)),
        )

        assertEquals(80.0 / 3, result.averageProteinPerPortion!!, 1e-9)
    }

    @Test
    fun emptyMenu() {
        val result = summary(emptyList(), listOf(line(1, lentils)))

        assertEquals(WeekSummary(0, 0.0, null), result)
        assertNull(result.averageProteinPerPortion)
    }
}
