package com.example.plantry.ui.suggest

import com.example.plantry.data.Nutrition
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.ingredient
import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionCardTest {

    private val chickpeas = ingredient(1, "Kichererbsen", Nutrition(protein = 8.0))
    private val sugar = ingredient(2, "Zucker")

    private fun recipe(id: Long, minutes: Int? = 25) = Recipe(
        id = id, title = "Rezept $id", source = "", page = null,
        bookServings = 2, ourServings = 2, cookingTimeMinutes = minutes,
    )

    private fun line(recipeId: Long, position: Int, text: String, ingredientId: Long, grams: Double = 100.0) =
        RecipeIngredient(recipeId = recipeId, position = position, originalText = text, grams = grams, ingredientId = ingredientId)

    @Test
    fun of_ingredientWordingInRecipeOrder_onlyThisRecipesLines() {
        val lines = listOf(
            line(1, 1, "2 TL Zucker", sugar.id, grams = 8.0),
            line(2, 0, "1 Prise Salz", sugar.id),
            line(1, 0, "1 Dose Kichererbsen", chickpeas.id, grams = 250.0),
        )

        val card = SuggestionCard.of(recipe(1), lines, listOf(chickpeas, sugar).associateBy { it.id })

        assertEquals("1 Dose Kichererbsen, 2 TL Zucker", card.ingredients)
        assertEquals("Rezept 1", card.snapshot.title)
        assertEquals(10.0, card.snapshot.stats.proteinPerPortion, 1e-9)
        assertEquals(25, card.cookingTimeMinutes)
    }

    @Test
    fun of_noLinesNoTime() {
        val card = SuggestionCard.of(recipe(3, minutes = null), emptyList(), emptyMap())

        assertEquals("", card.ingredients)
        assertEquals(null, card.cookingTimeMinutes)
    }
}
