package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IngredientFiltersTest {

    private val all = listOf(
        ingredient(1, "Haferflocken", Nutrition(protein = 13.0, carbs = 59.0, fibre = 10.0)),
        ingredient(2, "Quark", Nutrition(protein = 12.0, carbs = 4.0)),
        ingredient(3, "Reis", Nutrition(protein = 7.0, carbs = 78.0, fibre = 1.3)),
        ingredient(4, "Spinat", Nutrition(protein = 2.9, carbs = 1.1, fibre = 2.2)),
        ingredient(5, "Linsen", Nutrition(protein = 24.0, carbs = 49.9, fibre = 11.0)),
    )

    private fun names(vararg chips: IngredientChip) = IngredientFilters.apply(all, chips.toList()).map { it.name }

    @Test
    fun noChip_keepsEverythingInOrder() =
        assertEquals(listOf("Haferflocken", "Quark", "Reis", "Spinat", "Linsen"), names())

    @Test
    fun protein_from12gPer100g() = assertEquals(listOf("Haferflocken", "Quark", "Linsen"), names(IngredientChip.HIGH_PROTEIN))

    @Test
    fun carbs_from50gPer100g() = assertEquals(listOf("Haferflocken", "Reis"), names(IngredientChip.HIGH_CARBS))

    @Test
    fun fibre_from6gPer100g() = assertEquals(listOf("Haferflocken", "Linsen"), names(IngredientChip.HIGH_FIBRE))

    @Test
    fun chipsCombine_needEveryOne() =
        assertEquals(listOf("Haferflocken"), names(IngredientChip.HIGH_PROTEIN, IngredientChip.HIGH_CARBS, IngredientChip.HIGH_FIBRE))
}
