package com.example.plantry.data.planner

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionRoundsTest {

    /** Suggests the lowest ids not excluded, so rounds are predictable. */
    private fun rounds(recipes: LongRange) = SuggestionRounds { count, exclude ->
        recipes.filter { it !in exclude }.take(count)
    }

    @Test
    fun next_threeRecipes() = runTest {
        assertEquals(listOf(1L, 2L, 3L), rounds(1L..10L).next(planned = emptySet()))
    }

    @Test
    fun next_excludesRecipesShownInEarlierRounds() = runTest {
        val rounds = rounds(1L..10L)

        rounds.next(emptySet())

        assertEquals(listOf(4L, 5L, 6L), rounds.next(emptySet()))
        assertEquals(listOf(7L, 8L, 9L), rounds.next(emptySet()))
    }

    @Test
    fun next_startsOverWhenTooFewAreLeft() = runTest {
        val rounds = rounds(1L..5L)

        rounds.next(emptySet())

        // Only 4 and 5 haven't been shown: topped up from all recipes.
        assertEquals(listOf(4L, 5L, 1L), rounds.next(emptySet()))
        // The exclusion starts over with that round.
        assertEquals(listOf(2L, 3L, 1L), rounds.next(emptySet()))
    }

    @Test
    fun next_neverSuggestsPlanned_evenAfterReset() = runTest {
        val rounds = rounds(1L..5L)
        val planned = setOf(1L, 4L)

        assertEquals(listOf(2L, 3L, 5L), rounds.next(planned))
        assertEquals(listOf(2L, 3L, 5L), rounds.next(planned))
    }

    @Test
    fun next_plannedChangesBetweenRounds() = runTest {
        val rounds = rounds(1L..10L)

        rounds.next(emptySet())

        assertEquals(listOf(5L, 6L, 7L), rounds.next(planned = setOf(4L)))
    }

    @Test
    fun next_fewerRecipesThanCards() = runTest {
        val rounds = rounds(1L..2L)

        assertEquals(listOf(1L, 2L), rounds.next(emptySet()))
        assertEquals(listOf(1L, 2L), rounds.next(emptySet()))
    }

    @Test
    fun next_noRecipes() = runTest {
        assertTrue(rounds(LongRange.EMPTY).next(emptySet()).isEmpty())
        assertTrue(rounds(1L..2L).next(planned = setOf(1L, 2L)).isEmpty())
    }
}
