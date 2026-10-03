package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecipeQueryTest {

    private val riceDry = ingredient(1, "Reis").copy(plantPoints = PlantPoints.ONE)
    private val riceCooked = ingredient(2, "Reis, gekocht").copy(plantPoints = PlantPoints.ONE, buyAsIngredientId = 1)
    private val lentils = ingredient(3, "Linsen", Nutrition(protein = 25.0)).copy(plantPoints = PlantPoints.ONE)
    private val tofu = ingredient(4, "Tofu", Nutrition(protein = 15.0))
    private val cumin = ingredient(5, "Kreuzkümmel").copy(plantPoints = PlantPoints.QUARTER)
    private val salt = ingredient(6, "Salz").copy(staple = true)
    private val oil = ingredient(7, "Olivenöl").copy(staple = true)

    private val ingredients = listOf(riceDry, riceCooked, lentils, tofu, cumin, salt, oil).associateBy { it.id }

    private fun recipe(id: Long, title: String, minutes: Int = 30, source: String = "") = Recipe(
        id = id, title = title, source = source, page = null,
        bookServings = 2, ourServings = 2, cookingTimeMinutes = minutes,
    )

    private fun line(recipeId: Long, ingredient: Ingredient, grams: Double = 100.0) =
        RecipeIngredient(recipeId = recipeId, position = 0, originalText = "", grams = grams, ingredientId = ingredient.id)

    private fun run(
        recipes: List<Recipe>,
        lines: List<RecipeIngredient> = emptyList(),
        filter: RecipeFilter = RecipeFilter(),
        sort: RecipeSort = RecipeSort.TITLE,
        lastCooked: Map<Long, LocalDate> = emptyMap(),
    ) = RecipeQuery.run(recipes, lines, ingredients, lastCooked, filter, sort)

    private fun List<RecipeListItem>.titles() = map { it.recipe.title }

    @Test
    fun title_isDefaultAndIgnoresCaseAndUmlauts() {
        val result = run(listOf(recipe(1, "Zucchini"), recipe(2, "äpfel"), recipe(3, "Bohnen"), recipe(4, "Apfelkuchen")))

        assertEquals(listOf("äpfel", "Apfelkuchen", "Bohnen", "Zucchini"), result.titles())
    }

    @Test
    fun protein_mostFirst() {
        val result = run(
            listOf(recipe(1, "Reis"), recipe(2, "Dal"), recipe(3, "Tofu")),
            listOf(line(1, riceDry), line(2, lentils), line(3, tofu)),
            sort = RecipeSort.PROTEIN,
        )

        assertEquals(listOf("Dal", "Tofu", "Reis"), result.titles())
        assertEquals(12.5, result.first().proteinPerPortion, 1e-9)
    }

    @Test
    fun cookingTime_quickestFirst() {
        val result = run(listOf(recipe(1, "A", 45), recipe(2, "B", 15), recipe(3, "C", 30)), sort = RecipeSort.COOKING_TIME)

        assertEquals(listOf("B", "C", "A"), result.titles())
    }

    @Test
    fun plantPoints_mostFirst_buyAsTargetCountsOnce() {
        val result = run(
            listOf(recipe(1, "Reis doppelt"), recipe(2, "Dal"), recipe(3, "Leer")),
            listOf(
                line(1, riceDry), line(1, riceCooked),
                line(2, lentils), line(2, riceCooked), line(2, cumin),
            ),
            sort = RecipeSort.PLANT_POINTS,
        )

        assertEquals(listOf("Dal", "Reis doppelt", "Leer"), result.titles())
        assertEquals(listOf(2.25, 1.0, 0.0), result.map { it.plantPoints })
    }

    @Test
    fun lastCooked_neverFirstThenLongestAgo() {
        val result = run(
            listOf(recipe(1, "Gestern"), recipe(2, "Nie"), recipe(3, "Lange her")),
            sort = RecipeSort.LAST_COOKED,
            lastCooked = mapOf(1L to LocalDate.of(2026, 10, 2), 3L to LocalDate.of(2026, 8, 1)),
        )

        assertEquals(listOf("Nie", "Lange her", "Gestern"), result.titles())
    }

    @Test
    fun sortTies_brokenByTitle() {
        val result = run(listOf(recipe(1, "B", 20), recipe(2, "A", 20)), sort = RecipeSort.COOKING_TIME)

        assertEquals(listOf("A", "B"), result.titles())
    }

    @Test
    fun ingredientFilter_rankedByMatchCount_sortBreaksTies() {
        val result = run(
            listOf(recipe(1, "Nur Linsen", 40), recipe(2, "Beides", 50), recipe(3, "Nur Tofu", 10), recipe(4, "Keins")),
            listOf(
                line(1, lentils),
                line(2, lentils), line(2, tofu),
                line(3, tofu),
                line(4, riceDry),
            ),
            filter = RecipeFilter(ingredientIds = setOf(lentils.id, tofu.id)),
            sort = RecipeSort.COOKING_TIME,
        )

        assertEquals(listOf("Beides", "Nur Tofu", "Nur Linsen"), result.titles())
        assertEquals(listOf(2, 1, 1), result.map { it.matchedIngredients })
    }

    @Test
    fun ingredientFilter_followsBuyAsLinksBothWays() {
        val recipes = listOf(recipe(1, "Mit gekochtem Reis"), recipe(2, "Mit Reis"))
        val lines = listOf(line(1, riceCooked), line(2, riceDry))

        assertEquals(
            listOf("Mit gekochtem Reis", "Mit Reis"),
            run(recipes, lines, RecipeFilter(ingredientIds = setOf(riceDry.id))).titles(),
        )
        assertEquals(
            listOf("Mit gekochtem Reis", "Mit Reis"),
            run(recipes, lines, RecipeFilter(ingredientIds = setOf(riceCooked.id))).titles(),
        )
    }

    @Test
    fun ingredientFilter_staplesNeverMatch() {
        val result = run(
            listOf(recipe(1, "Gesalzen"), recipe(2, "Dal")),
            listOf(line(1, salt), line(1, oil), line(2, lentils), line(2, salt)),
            filter = RecipeFilter(ingredientIds = setOf(salt.id, lentils.id)),
        )

        assertEquals(listOf("Dal"), result.titles())
        assertEquals(1, result.single().matchedIngredients)
    }

    @Test
    fun maxCookingTimeAndSource_combineWithIngredientFilter() {
        val result = run(
            listOf(
                recipe(1, "Schnell Plenty", 20, "Plenty"),
                recipe(2, "Langsam Plenty", 60, "Plenty"),
                recipe(3, "Schnell anderes Buch", 20, "Jerusalem"),
                recipe(4, "Schnell ohne Linsen", 20, " plenty "),
            ),
            listOf(line(1, lentils), line(2, lentils), line(3, lentils), line(4, tofu)),
            filter = RecipeFilter(ingredientIds = setOf(lentils.id), maxCookingMinutes = 30, source = "PLENTY"),
        )

        assertEquals(listOf("Schnell Plenty"), result.titles())
    }

    @Test
    fun noIngredientFilter_showsAllWithZeroMatches() {
        val result = run(listOf(recipe(1, "A")), listOf(line(1, salt)))

        assertEquals(0, result.single().matchedIngredients)
    }

    @Test
    fun sources_distinctIgnoringCaseAndBlanks() {
        val result = RecipeQuery.sources(
            listOf(recipe(1, "a", source = "Plenty"), recipe(2, "b", source = " plenty"), recipe(3, "c"), recipe(4, "d", source = "Jerusalem")),
        )

        assertEquals(listOf("Jerusalem", "Plenty"), result)
    }
}
