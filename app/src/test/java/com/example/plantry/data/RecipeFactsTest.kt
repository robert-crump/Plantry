package com.example.plantry.data

import com.example.plantry.data.planner.WeekPlants
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeFactsTest {

    private val recipe = Recipe(1, "Dal", "", null, servings = 2, totalTimeMinutes = 30)
    private val other = Recipe(2, "Brot", "", null, servings = 1, totalTimeMinutes = 10)

    private val book = IngredientBook(
        listOf(
            ingredient(1, "Linsen", PlantPoints.ONE, Nutrition(protein = 24.0)),
            ingredient(2, "Knoblauch", PlantPoints.QUARTER, Nutrition(protein = 6.0)),
        ),
    )

    @Test
    fun facts_agreeWithTheSingleFormulas_andIgnoreOtherRecipesLines() {
        val lines = listOf(
            line(1, 10.0, ingredientId = 2),
            line(0, 200.0, ingredientId = 1),
            line(0, 500.0, ingredientId = 1).copy(recipeId = 2),
        )

        val facts = RecipeFacts.of(recipe, lines, book)

        assertEquals(listOf(1L, 2L), facts.lines.map { it.ingredientId }.sorted())
        assertEquals(RecipeStats.of(recipe, facts.lines, book.byId), facts.stats)
        assertEquals(1.25, facts.stats.plantPoints, 0.0)
        assertEquals((48.0 + 0.6) / 2, facts.nutrition.perPortion.protein, 1e-9)
        assertEquals(facts.stats, facts.snapshot.stats)
    }

    @Test
    fun newPlantPoints_countOnlyPlantsTheWeekLacks() {
        val facts = RecipeFacts.of(recipe, listOf(line(0, 1.0, ingredientId = 1), line(1, 1.0, ingredientId = 2)), book)
        val week = WeekPlants(listOf(CookLogPlant(ingredientId = 1L, name = "Linsen", plantPoints = 1.0)), book.byId, book.contents)

        assertEquals(0.25, facts.newPlantPoints(week), 0.0)
    }

    @Test
    fun all_giveEachRecipeItsOwnLines() {
        val lines = listOf(line(0, 1.0, ingredientId = 1), line(0, 1.0, ingredientId = 2).copy(recipeId = 2))

        val facts = RecipeFacts.all(listOf(recipe, other), lines, book)

        assertEquals(listOf(1.0, 0.25), facts.map { it.stats.plantPoints })
    }

    private fun line(position: Int, grams: Double, ingredientId: Long) =
        RecipeLine(id = position + 1L, recipeId = 1, position = position, amount = grams, unit = LineUnit.Grams, ingredientId = ingredientId)

    private fun ingredient(id: Long, name: String, points: PlantPoints, nutrition: Nutrition) =
        Ingredient(
            id = id,
            name = name,
            fdcId = null,
            usdaDescription = null,
            nutrition = nutrition,
            storeSection = StoreSection.OTHER,
            plantPoints = points,
        )
}
