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

    private fun found(search: String, vararg chips: IngredientChip) =
        IngredientFilters.apply(all, chips.toList(), search).map { it.name }

    @Test
    fun search_findsEveryWordInAnyOrder_ignoringCase() {
        val all = listOf(ingredient(1, "Kichererbsen (Dose)", Nutrition()), ingredient(2, "Erbsen, TK", Nutrition()))
        assertEquals(listOf("Kichererbsen (Dose)"), IngredientFilters.apply(all, emptyList(), "dose erbsen").map { it.name })
        assertEquals(listOf("Kichererbsen (Dose)", "Erbsen, TK"), IngredientFilters.apply(all, emptyList(), "ERBSEN ").map { it.name })
    }

    @Test
    fun search_combinesWithChips() = assertEquals(listOf("Haferflocken"), found("e", IngredientChip.HIGH_CARBS, IngredientChip.HIGH_PROTEIN))

    @Test
    fun blankSearch_keepsEverything() = assertEquals(5, found("  ").size)
}
