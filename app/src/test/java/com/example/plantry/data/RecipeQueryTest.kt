package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecipeQueryTest {

    private val riceDry = ingredient(1, "Reis").copy(plantPoints = PlantPoints.ONE)
    private val riceCooked = ingredient(2, "Reis, gekocht").copy(plantPoints = PlantPoints.ONE)
    private val lentils = ingredient(3, "Linsen", Nutrition(protein = 25.0)).copy(plantPoints = PlantPoints.ONE)
    private val tofu = ingredient(4, "Tofu", Nutrition(protein = 15.0))
    private val cumin = ingredient(5, "Kreuzkümmel").copy(plantPoints = PlantPoints.QUARTER)
    private val salt = ingredient(6, "Salz")
    private val oil = ingredient(7, "Olivenöl")

    private val ingredients = listOf(riceDry, riceCooked, lentils, tofu, cumin, salt, oil).associateBy { it.id }

    private fun recipe(id: Long, title: String, minutes: Int? = 30) = Recipe(
        id = id, title = title, source = "", page = null,
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
    fun recipeCounts_distinctRecipesPerIngredient() {
        val counts = RecipeQuery.recipeCounts(
            listOf(line(1, lentils), line(1, lentils), line(2, lentils), line(2, tofu), line(3, salt)),
            ingredients,
        )

        assertEquals(2, counts[lentils.id])
        assertEquals(1, counts[tofu.id])
        assertEquals(1, counts[salt.id])
        assertEquals(null, counts[cumin.id])
    }

    @Test
    fun recipeCounts_countOnlyTheIngredientItself() {
        val counts = RecipeQuery.recipeCounts(listOf(line(1, riceDry), line(2, riceCooked), line(2, riceDry)), ingredients)

        assertEquals(2, counts[riceDry.id])
        assertEquals(1, counts[riceCooked.id])
    }

    @Test
    fun filterFromRecipeCount_findsTheCountedRecipes() {
        val recipes = listOf(recipe(1, "Reis pur"), recipe(2, "Gebratener Reis"), recipe(3, "Tofu"), recipe(4, "Salzig"))
        val lines = listOf(line(1, riceDry), line(2, riceCooked), line(2, salt), line(3, tofu), line(4, salt))

        listOf(riceDry, salt).forEach { ingredient ->
            val items = run(recipes, lines, RecipeFilter(ingredientIds = setOf(ingredient.id)))

            assertEquals(RecipeQuery.recipeCounts(lines, ingredients)[ingredient.id], items.size)
        }
        assertEquals(listOf("Reis pur"), run(recipes, lines, RecipeFilter(ingredientIds = setOf(riceDry.id))).titles())
    }

    @Test
    fun recipeCounts_skipLinesWithMissingIngredient() {
        val missing = RecipeIngredient(recipeId = 1, position = 0, originalText = "", grams = 1.0, ingredientId = 99)

        assertEquals(emptyMap<Long, Int>(), RecipeQuery.recipeCounts(listOf(missing), ingredients))
    }

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
    fun cookingTime_recipesWithoutTimeLast() {
        val result = run(listOf(recipe(1, "A", null), recipe(2, "B", 45), recipe(3, "C", 15)), sort = RecipeSort.COOKING_TIME)

        assertEquals(listOf("C", "B", "A"), result.titles())
    }

    @Test
    fun maxCookingMinutes_keepsRecipesWithoutTime() {
        val result = run(
            listOf(recipe(1, "Schnell", 15), recipe(2, "Langsam", 60), recipe(3, "Ohne", null)),
            filter = RecipeFilter(maxCookingMinutes = 30),
        )

        assertEquals(listOf("Ohne", "Schnell"), result.titles())
    }

    @Test
    fun plantPoints_mostFirst_eachIngredientCountsOnce() {
        val result = run(
            listOf(recipe(1, "Reis doppelt"), recipe(2, "Dal"), recipe(3, "Leer")),
            listOf(
                line(1, riceDry), line(1, riceDry),
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
    fun ingredientFilter_matchesOnlyTheIngredientItself() {
        val recipes = listOf(recipe(1, "Mit gekochtem Reis"), recipe(2, "Mit Reis"))
        val lines = listOf(line(1, riceCooked), line(2, riceDry))

        assertEquals(listOf("Mit Reis"), run(recipes, lines, RecipeFilter(ingredientIds = setOf(riceDry.id))).titles())
        assertEquals(
            listOf("Mit gekochtem Reis"),
            run(recipes, lines, RecipeFilter(ingredientIds = setOf(riceCooked.id))).titles(),
        )
    }

    @Test
    fun ingredientFilter_saltAndOilMatchLikeAnyOther() {
        val result = run(
            listOf(recipe(1, "Gesalzen"), recipe(2, "Dal"), recipe(3, "Tofu")),
            listOf(line(1, salt), line(1, oil), line(2, lentils), line(2, salt), line(3, tofu)),
            filter = RecipeFilter(ingredientIds = setOf(salt.id, lentils.id)),
        )

        assertEquals(listOf("Dal", "Gesalzen"), result.titles())
        assertEquals(listOf(2, 1), result.map { it.matchedIngredients })
    }

    @Test
    fun maxCookingTime_combinesWithIngredientFilter() {
        val result = run(
            listOf(
                recipe(1, "Schnell", 20),
                recipe(2, "Langsam", 60),
                recipe(3, "Schnell ohne Linsen", 20),
            ),
            listOf(line(1, lentils), line(2, lentils), line(3, tofu)),
            filter = RecipeFilter(ingredientIds = setOf(lentils.id), maxCookingMinutes = 30),
        )

        assertEquals(listOf("Schnell"), result.titles())
    }

    @Test
    fun filterOfChips_ingredientsInPickOrder_andTheCookingTime() {
        val chips = ChipSelection<RecipeChip>()
            .toggle(RecipeChip.WithIngredient(tofu.id))
            .toggle(RecipeChip.MaxCookingTime(20))
            .toggle(RecipeChip.WithIngredient(lentils.id))
            .toggle(RecipeChip.MaxCookingTime(45))

        val filter = RecipeFilter.of(chips.active)

        assertEquals(listOf(tofu.id, lentils.id), filter.ingredientIds.toList())
        assertEquals(45, filter.maxCookingMinutes)
        assertEquals(RecipeFilter(), RecipeFilter.of(emptyList()))
    }

    @Test
    fun noIngredientFilter_showsAllWithZeroMatches() {
        val result = run(listOf(recipe(1, "A")), listOf(line(1, salt)))

        assertEquals(0, result.single().matchedIngredients)
    }

    @Test
    fun byTitle_blankQuery_listsAllAToZ() {
        val result = RecipeQuery.byTitle(listOf(recipe(1, "Zucchini-Pasta"), recipe(2, "Äpfel im Ofen"), recipe(3, "bohnen-Chili")), " ")

        assertEquals(listOf("Äpfel im Ofen", "bohnen-Chili", "Zucchini-Pasta"), result.map { it.title })
    }

    @Test
    fun byTitle_matchesAnywhereIgnoringCase() {
        val result = RecipeQuery.byTitle(listOf(recipe(1, "Linsen-Dal"), recipe(2, "Rote Linsensuppe"), recipe(3, "Curry")), " LINSEN")

        assertEquals(listOf("Linsen-Dal", "Rote Linsensuppe"), result.map { it.title })
    }
}
