package com.example.plantry.data.claude

import com.example.plantry.data.PieceUnit
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.ingredient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPromptTest {

    @Test
    fun `grams of drained canned goods are drained grams, no net weight`() {
        val system = ScanPrompt.system(emptyList())

        assertTrue(system.contains("Canned goods that are drained count in drained grams"))
        assertFalse(system.contains("net weight"))
    }

    @Test
    fun `ingredient table lists the weight of one piece`() {
        val system = ScanPrompt.system(
            listOf(
                ingredient(3, "Toast"),
                ingredient(4, "Knoblauch"),
                ingredient(5, "Mehl"),
                ingredient(6, "Brötchen"),
                ingredient(7, "Kidneybohnen (Dose)"),
            ),
            units = listOf(
                IngredientUnit.ofPackage(PieceUnit.SLICE, 750.0, 20, ingredientId = 3)!!,
                IngredientUnit.perUnit(PieceUnit.CLOVE, 4.0, ingredientId = 4)!!,
                IngredientUnit.ofPackage(PieceUnit.PIECE, 500.0, 6, ingredientId = 6)!!,
                IngredientUnit.perUnit(PieceUnit.CAN, 240.0, ingredientId = 7)!!,
            ).groupBy { it.ingredientId },
        )

        assertTrue(system.contains("3\tToast\t1 Scheibe = 37.5 g\n"))
        assertTrue(system.contains("4\tKnoblauch\t1 Zehe = 4 g\n"))
        assertTrue(system.contains("5\tMehl\n"))
        assertTrue(system.contains("6\tBrötchen\t1 Stück = 83.3 g\n"))
        assertTrue(system.contains("7\tKidneybohnen (Dose)\t1 Dose = 240 g\n"))
    }
}
