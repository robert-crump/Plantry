package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IngredientFiltersTest {

    private val all = listOf(
        ingredient(1, "Knoblauch"),
        ingredient(2, "Kurkuma"),
        ingredient(3, "Linsen"),
        ingredient(4, "Spinat"),
    )

    // Kurkuma's 0 counts as unused just like Spinat's missing entry.
    private val counts = mapOf(1L to 2, 2L to 0, 3L to 1)

    private fun names(vararg chips: IngredientChip) = IngredientFilters.apply(all, counts, chips.toList()).map { it.name }

    @Test
    fun noChip_keepsEverythingInOrder() =
        assertEquals(listOf("Knoblauch", "Kurkuma", "Linsen", "Spinat"), names())

    @Test
    fun used_needsAtLeastOneRecipe() = assertEquals(listOf("Knoblauch", "Linsen"), names(IngredientChip.USED))
}
