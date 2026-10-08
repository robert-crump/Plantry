package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IngredientFiltersTest {

    private val all = listOf(
        ingredient(1, "Knoblauch"),
        ingredient(2, "Kurkuma"),
        ingredient(3, "Linsen").copy(reviewed = false),
        ingredient(4, "Spinat").copy(reviewed = false, origin = IngredientOrigin.MANUAL),
        ingredient(5, "Haferdrink").copy(origin = IngredientOrigin.BARCODE),
        ingredient(6, "Seitan").copy(reviewed = false, origin = IngredientOrigin.CLAUDE),
    )

    // Kurkuma's 0 counts as unused just like Spinat's missing entry.
    private val counts = mapOf(1L to 2, 2L to 0, 3L to 1, 6L to 1)

    private fun names(vararg chips: IngredientChip) = IngredientFilters.apply(all, counts, chips.toList()).map { it.name }

    @Test
    fun noChip_keepsEverythingInOrder() =
        assertEquals(listOf("Knoblauch", "Kurkuma", "Linsen", "Spinat", "Haferdrink", "Seitan"), names())

    @Test
    fun unreviewed() = assertEquals(listOf("Linsen", "Spinat", "Seitan"), names(IngredientChip.UNREVIEWED))

    @Test
    fun used_needsAtLeastOneRecipe() = assertEquals(listOf("Knoblauch", "Linsen", "Seitan"), names(IngredientChip.USED))

    @Test
    fun added_isEverythingButTheSeed() = assertEquals(listOf("Spinat", "Haferdrink", "Seitan"), names(IngredientChip.ADDED))

    @Test
    fun chips_combine() {
        assertEquals(listOf("Linsen", "Seitan"), names(IngredientChip.UNREVIEWED, IngredientChip.USED))
        assertEquals(listOf("Seitan"), names(IngredientChip.UNREVIEWED, IngredientChip.USED, IngredientChip.ADDED))
    }

    @Test
    fun chips_switchOnTogether() {
        val selection = ChipSelection<IngredientChip>().toggle(IngredientChip.UNREVIEWED).toggle(IngredientChip.ADDED)

        assertEquals(listOf(IngredientChip.UNREVIEWED, IngredientChip.ADDED), selection.active)
    }
}
