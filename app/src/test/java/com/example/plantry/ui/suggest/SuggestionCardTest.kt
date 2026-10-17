package com.example.plantry.ui.suggest

import com.example.plantry.data.LineUnit
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeLine
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.ingredient
import com.example.plantry.data.planner.WeekPlants
import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionCardTest {

    private val chickpeas = ingredient(1, "Kichererbsen", Nutrition(protein = 8.0))
    private val sugar = ingredient(2, "Zucker")

    private fun recipe(id: Long, minutes: Int? = 25) = Recipe(
        id = id, title = "Rezept $id", source = "", page = null,
        servings = 2, totalTimeMinutes = minutes,
    )

    private fun line(
        recipeId: Long,
        position: Int,
        ingredientId: Long,
        amount: Double = 100.0,
        unit: LineUnit = LineUnit.Grams,
        wording: String? = null,
        unitGrams: Double? = null,
    ) = RecipeLine(
        recipeId = recipeId, position = position, amount = amount, unit = unit, ingredientId = ingredientId,
        wording = wording, unitGrams = unitGrams,
    )

    @Test
    fun of_linesAsShownInRecipeOrder_onlyThisRecipesLines() {
        val lines = listOf(
            line(1, 1, sugar.id, amount = 8.0, wording = "2 TL Zucker"),
            line(2, 0, sugar.id, wording = "1 Prise Salz"),
            line(1, 0, chickpeas.id, amount = 1.0, unit = LineUnit.Of(PieceUnit.CAN), unitGrams = 250.0),
        )

        val card = SuggestionCard.of(recipe(1), lines, listOf(chickpeas, sugar).associateBy { it.id })

        assertEquals("1 Dose Kichererbsen, 2 TL Zucker", card.ingredients)
        assertEquals("Rezept 1", card.snapshot.title)
        assertEquals(10.0, card.snapshot.stats.proteinPerPortion, 1e-9)
        assertEquals(25, card.totalTimeMinutes)
    }

    @Test
    fun of_newPlantPointsAgainstTheWeek() {
        val spinach = ingredient(3, "Spinat").copy(plantPoints = PlantPoints.ONE)
        val chili = ingredient(4, "Chili").copy(plantPoints = PlantPoints.QUARTER)
        val ingredients = listOf(spinach, chili).associateBy { it.id }
        val lines = listOf(line(1, 0, spinach.id), line(1, 1, chili.id))

        assertEquals(1.25, SuggestionCard.of(recipe(1), lines, ingredients).newPlantPoints, 1e-9)
        val week = WeekPlants(setOf(spinach.id), ingredients, emptyMap())
        assertEquals(0.25, SuggestionCard.of(recipe(1), lines, ingredients, week = week).newPlantPoints, 1e-9)
    }

    @Test
    fun of_noLinesNoTime() {
        val card = SuggestionCard.of(recipe(3, minutes = null), emptyList(), emptyMap())

        assertEquals("", card.ingredients)
        assertEquals(null, card.totalTimeMinutes)
    }
}
