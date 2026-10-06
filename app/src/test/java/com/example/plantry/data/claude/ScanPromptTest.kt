package com.example.plantry.data.claude

import com.example.plantry.data.DrainedWeight
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
}
