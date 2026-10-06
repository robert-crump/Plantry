package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeHighlightTest {

    @Test
    fun of_atEachThreshold_hasAllFour() {
        val stats = RecipeStats(plantPoints = 5.0, proteinPerPortion = 30.0, carbsPerPortion = 0.0, kcalPerPortion = 600.0, fibrePerPortion = 10.0)

        assertEquals(RecipeHighlight.entries.toSet(), RecipeHighlight.of(stats, hasLines = true))
    }

    @Test
    fun of_justMissingEachThreshold_hasNone() {
        val stats = RecipeStats(plantPoints = 4.25, proteinPerPortion = 29.4, carbsPerPortion = 0.0, kcalPerPortion = 600.5, fibrePerPortion = 9.4)

        assertEquals(emptySet<RecipeHighlight>(), RecipeHighlight.of(stats, hasLines = true))
    }

    @Test
    fun of_judgesTheRoundedNumbersShown() {
        // Shown as 30 g, 5 points, 600 kcal and 10 g.
        val stats = RecipeStats(plantPoints = 4.75, proteinPerPortion = 29.5, carbsPerPortion = 0.0, kcalPerPortion = 600.4, fibrePerPortion = 9.5)

        assertEquals(RecipeHighlight.entries.toSet(), RecipeHighlight.of(stats, hasLines = true))
    }

    @Test
    fun of_withoutLines_hasNone() {
        assertEquals(emptySet<RecipeHighlight>(), RecipeHighlight.of(RecipeStats(0.0, 0.0, 0.0, 0.0, 0.0), hasLines = false))
    }

    @Test
    fun of_zeroOrMissingKcal_isNotLowCalorie() {
        val zero = RecipeStats(plantPoints = 0.0, proteinPerPortion = 0.0, carbsPerPortion = 0.0, kcalPerPortion = 0.0, fibrePerPortion = 0.0)
        val missing = RecipeStats(plantPoints = 0.0, proteinPerPortion = 0.0, carbsPerPortion = 0.0)

        assertEquals(emptySet<RecipeHighlight>(), RecipeHighlight.of(zero, hasLines = true))
        assertEquals(emptySet<RecipeHighlight>(), RecipeHighlight.of(missing, hasLines = true))
    }
}
