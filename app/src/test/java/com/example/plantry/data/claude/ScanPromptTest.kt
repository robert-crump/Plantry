package com.example.plantry.data.claude

import com.example.plantry.data.DrainedWeight
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.ingredient
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPromptTest {

    @Test
    fun `grams of canned goods are the container weight`() {
        val system = ScanPrompt.system(emptyList())

        assertTrue(system.contains("net weight printed on the\n    container"))
        assertTrue(system.contains("also for\n    products that are drained"))
    }

    @Test
    fun `ingredient table lists the net weight of drained goods`() {
        val system = ScanPrompt.system(
            listOf(
                ingredient(3, "Kichererbsen (Dose)").copy(drainedWeight = DrainedWeight(400.0, 240.0)),
                ingredient(4, "Artischocken (Glas)").copy(drainedWeight = DrainedWeight(280.0, 140.0)),
                ingredient(5, "Tofu"),
            ),
        )

        assertTrue(system.contains("4\tArtischocken (Glas)\tGlas 280 g\n"))
        assertTrue(system.contains("3\tKichererbsen (Dose)\tDose 400 g\n"))
        assertTrue(system.contains("5\tTofu\n"))
    }

    @Test
    fun `ingredient table lists the weight of one piece`() {
        val system = ScanPrompt.system(
            listOf(
                ingredient(3, "Toast"),
                ingredient(4, "Knoblauch"),
                ingredient(5, "Mehl"),
                ingredient(6, "Brötchen"),
                ingredient(7, "Kidneybohnen (Dose)").copy(drainedWeight = DrainedWeight(400.0, 240.0)),
            ),
            units = listOf(
                IngredientUnit.ofPackage(PieceUnit.SLICE, 750.0, 20, ingredientId = 3)!!,
                IngredientUnit.perUnit(PieceUnit.CLOVE, 4.0, ingredientId = 4)!!,
                IngredientUnit.ofPackage(PieceUnit.PIECE, 500.0, 6, ingredientId = 6)!!,
                IngredientUnit.perUnit(PieceUnit.CAN, 400.0, ingredientId = 7)!!,
            ).groupBy { it.ingredientId },
        )

        assertTrue(system.contains("3\tToast\t1 Scheibe = 37.5 g\n"))
        assertTrue(system.contains("4\tKnoblauch\t1 Zehe = 4 g\n"))
        assertTrue(system.contains("5\tMehl\n"))
        assertTrue(system.contains("6\tBrötchen\t1 Stück = 83.3 g\n"))
        assertTrue(system.contains("7\tKidneybohnen (Dose)\tDose 400 g\t1 Dose = 400 g\n"))
    }
}
