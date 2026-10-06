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
    fun defaults_areReviewedAndUsed() {
        assertEquals(ReviewFilter.REVIEWED, IngredientFilters.DEFAULT_REVIEW)
        assertEquals(UsageFilter.USED, IngredientFilters.DEFAULT_USAGE)
    }
}
