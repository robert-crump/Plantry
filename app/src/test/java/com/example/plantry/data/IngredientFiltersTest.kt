package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IngredientFiltersTest {

    private val reviewedUsed = ingredient(1, "Knoblauch")
    private val reviewedUnused = ingredient(2, "Kurkuma")
    private val unreviewedUsed = ingredient(3, "Linsen").copy(reviewed = false)
    private val unreviewedUnused = ingredient(4, "Spinat").copy(reviewed = false)
    private val all = listOf(reviewedUsed, reviewedUnused, unreviewedUsed, unreviewedUnused)
    // Kurkuma's 0 counts as unused just like Spinat's missing entry.
    private val counts = mapOf(1L to 2, 2L to 0, 3L to 1)

    private fun names(review: ReviewFilter, usage: UsageFilter) =
        IngredientFilters.apply(all, counts, review, usage).map { it.name }

    @Test
    fun allAll_keepsEverythingInOrder() =
        assertEquals(listOf("Knoblauch", "Kurkuma", "Linsen", "Spinat"), names(ReviewFilter.ALL, UsageFilter.ALL))

    @Test
    fun allUsed() = assertEquals(listOf("Knoblauch", "Linsen"), names(ReviewFilter.ALL, UsageFilter.USED))

    @Test
    fun allUnused() = assertEquals(listOf("Kurkuma", "Spinat"), names(ReviewFilter.ALL, UsageFilter.UNUSED))

    @Test
    fun reviewedAll() = assertEquals(listOf("Knoblauch", "Kurkuma"), names(ReviewFilter.REVIEWED, UsageFilter.ALL))

    @Test
    fun reviewedUsed() = assertEquals(listOf("Knoblauch"), names(ReviewFilter.REVIEWED, UsageFilter.USED))

    @Test
    fun reviewedUnused() = assertEquals(listOf("Kurkuma"), names(ReviewFilter.REVIEWED, UsageFilter.UNUSED))

    @Test
    fun unreviewedAll() = assertEquals(listOf("Linsen", "Spinat"), names(ReviewFilter.UNREVIEWED, UsageFilter.ALL))

    @Test
    fun unreviewedUsed() = assertEquals(listOf("Linsen"), names(ReviewFilter.UNREVIEWED, UsageFilter.USED))

    @Test
    fun unreviewedUnused() = assertEquals(listOf("Spinat"), names(ReviewFilter.UNREVIEWED, UsageFilter.UNUSED))

    @Test
    fun origin_seedKeepsOnlySeedIngredients() {
        val mixed = listOf(
            ingredient(1, "Knoblauch"),
            ingredient(2, "Haferdrink").copy(origin = IngredientOrigin.BARCODE),
            ingredient(3, "Seitan").copy(origin = IngredientOrigin.CLAUDE),
            ingredient(4, "Miso").copy(origin = IngredientOrigin.MANUAL),
        )

        fun names(origin: OriginFilter) =
            IngredientFilters.apply(mixed, emptyMap(), ReviewFilter.ALL, UsageFilter.ALL, origin).map { it.name }

        assertEquals(listOf("Knoblauch", "Haferdrink", "Seitan", "Miso"), names(OriginFilter.ALL))
        assertEquals(listOf("Knoblauch"), names(OriginFilter.SEED))
        assertEquals(listOf("Haferdrink", "Seitan", "Miso"), names(OriginFilter.ADDED))
    }

    @Test
    fun chips_combineAcrossGroups() {
        fun names(vararg chips: IngredientChip) = IngredientFilters.apply(all, counts, chips.toList()).map { it.name }

        assertEquals(listOf("Knoblauch", "Kurkuma", "Linsen", "Spinat"), names())
        assertEquals(listOf("Linsen"), names(IngredientChip.UNREVIEWED, IngredientChip.USED))
        assertEquals(listOf("Kurkuma"), names(IngredientChip.UNUSED, IngredientChip.REVIEWED))
        assertEquals(listOf("Knoblauch", "Kurkuma", "Linsen", "Spinat"), names(IngredientChip.SEED))
        assertEquals(emptyList<String>(), names(IngredientChip.ADDED))
    }

    @Test
    fun chips_pairsExcludeEachOther() {
        val selection = ChipSelection<IngredientChip>().toggle(IngredientChip.REVIEWED).toggle(IngredientChip.UNREVIEWED)

        assertEquals(listOf(IngredientChip.UNREVIEWED), selection.active)
    }
}
