package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PieceWeightUpdateTest {

    private val toast20 = PieceWeight(PieceUnit.SLICE, 750.0, 20)
    private val toast22 = PieceWeight(PieceUnit.SLICE, 750.0, 22)

    private fun line(id: Long, recipeId: Long, text: String, grams: Double) =
        RecipeIngredient(id = id, recipeId = recipeId, position = 0, originalText = text, grams = grams, ingredientId = 1)

    @Test
    fun changed_newWeightPerPiece() {
        assertTrue(PieceWeightUpdate.changed(toast20, toast22))
    }

    @Test
    fun changed_setForTheFirstTime() {
        assertTrue(PieceWeightUpdate.changed(null, toast20))
    }

    @Test
    fun changed_otherUnit() {
        assertTrue(PieceWeightUpdate.changed(toast20, toast20.copy(pieceUnit = PieceUnit.PIECE)))
    }

    @Test
    fun changed_notWhenRemovedOrTheSameWeightPerPiece() {
        assertFalse(PieceWeightUpdate.changed(toast20, null))
        assertFalse(PieceWeightUpdate.changed(null, null))
        assertFalse(PieceWeightUpdate.changed(toast20, toast20))
        assertFalse(PieceWeightUpdate.changed(toast20, PieceWeight(PieceUnit.SLICE, 1500.0, 40)))
    }

    @Test
    fun affectedLines_reparsesWithTheNewWeight() {
        val lines = listOf(line(1, 10, "4 Scheiben Toast", 150.0), line(2, 11, "4 Scheiben Toast", 150.0))

        val affected = PieceWeightUpdate.affectedLines(lines, toast22)

        assertEquals(listOf(136.4, 136.4), affected.map { it.grams })
        assertEquals(listOf(1L, 2L), affected.map { it.id })
        assertEquals(2, LineGramsUpdate(affected).recipeCount)
    }

    @Test
    fun affectedLines_firstWeightReplacesDifferingGrams() {
        val lines = listOf(line(1, 10, "2 Scheiben Toast", 80.0), line(2, 10, "1 Scheibe Toast", 37.5))

        val affected = PieceWeightUpdate.affectedLines(lines, toast20)

        assertEquals(listOf(line(1, 10, "2 Scheiben Toast", 75.0)), affected)
    }

    @Test
    fun affectedLines_leavesOutMatchingAndUnparsedLines() {
        val lines = listOf(
            line(1, 10, "4 Scheiben Toast", 136.4),
            line(2, 10, "Toast, 4 Scheiben", 150.0),
            line(3, 11, "2 EL Paniermehl", 20.0),
            line(4, 11, "100 g Toast", 100.0),
            line(5, 12, "1 Packung Toast", 750.0),
        )

        assertEquals(emptyList<RecipeIngredient>(), PieceWeightUpdate.affectedLines(lines, toast22))
    }

    @Test
    fun recipeCount_countsRecipesNotLines() {
        val update = LineGramsUpdate(listOf(line(1, 10, "4 Scheiben", 1.0), line(2, 10, "2 Scheiben", 1.0), line(3, 11, "1 Scheibe", 1.0)))

        assertEquals(2, update.recipeCount)
    }
}
