package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UsualUnitsTest {

    private val slice = LineUnit.Of(PieceUnit.SLICE)

    private fun line(id: Long, ingredientId: Long, unit: LineUnit) =
        RecipeLine(id = id, recipeId = 1, position = 0, amount = 1.0, unit = unit, ingredientId = ingredientId)

    @Test
    fun theUnitOfMostLines_byIngredient() {
        val lines = listOf(
            line(1, ingredientId = 5, slice),
            line(2, ingredientId = 5, LineUnit.Grams),
            line(3, ingredientId = 5, slice),
            line(4, ingredientId = 3, LineUnit.Milliliters),
        )

        assertEquals(mapOf(5L to slice, 3L to LineUnit.Milliliters), UsualUnits.of(lines))
    }

    @Test
    fun aTie_goesToTheUnitOfTheLatestLine() {
        val lines = listOf(
            line(1, ingredientId = 5, slice),
            line(7, ingredientId = 5, LineUnit.Grams),
            line(4, ingredientId = 5, slice),
            line(2, ingredientId = 5, LineUnit.Grams),
        )

        assertEquals(LineUnit.Grams, UsualUnits.of(lines)[5L])
    }

    @Test
    fun noLines_noUnits() {
        assertEquals(emptyMap<Long, LineUnit>(), UsualUnits.of(emptyList()))
    }
}
