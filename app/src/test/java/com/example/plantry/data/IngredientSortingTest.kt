package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class IngredientSortingTest {

    private val all = listOf(
        ingredient(1, "Reis", Nutrition(protein = 7.0, carbs = 78.0, fibre = 1.3)),
        ingredient(2, "Quark", Nutrition(protein = 12.0, carbs = 4.0)),
        ingredient(3, "Äpfel", Nutrition(protein = 0.3, carbs = 14.0, fibre = 2.4)),
        ingredient(4, "Linsen", Nutrition(protein = 24.0, carbs = 49.9, fibre = 11.0)),
        ingredient(5, "apfelmus", Nutrition(protein = 0.3, carbs = 14.0, fibre = 2.4)),
    )

    private fun names(sort: IngredientSort, recipeCounts: Map<Long, Int> = emptyMap()) =
        IngredientSorting.sorted(all, sort, recipeCounts, Locale.GERMAN).map { it.name }

    @Test
    fun name_ignoresCase_umlautsWithTheirBaseLetter() =
        assertEquals(listOf("Äpfel", "apfelmus", "Linsen", "Quark", "Reis"), names(IngredientSort.NAME))

    @Test
    fun protein_highestFirst() =
        assertEquals(listOf("Linsen", "Quark", "Reis", "Äpfel", "apfelmus"), names(IngredientSort.PROTEIN))

    @Test
    fun carbs_highestFirst() =
        assertEquals(listOf("Reis", "Linsen", "Äpfel", "apfelmus", "Quark"), names(IngredientSort.CARBS))

    @Test
    fun fibre_highestFirst_tiesByName() =
        assertEquals(listOf("Linsen", "Äpfel", "apfelmus", "Reis", "Quark"), names(IngredientSort.FIBRE))

    @Test
    fun recipes_mostFirst_unusedLastByName() =
        assertEquals(
            listOf("Quark", "Linsen", "Reis", "Äpfel", "apfelmus"),
            names(IngredientSort.RECIPES, mapOf(2L to 5, 4L to 2, 1L to 2)),
        )
}
