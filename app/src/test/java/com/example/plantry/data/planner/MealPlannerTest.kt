package com.example.plantry.data.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MealPlannerTest {

    private fun candidate(id: Long, days: Long? = null, protein: Double = 30.0) = Candidate(id, days, protein)

    @Test
    fun cooldown_risesWithDaysUntilCooldownPassed() {
        val term = CooldownTerm(cooldownDays = 20)

        assertEquals(0.25, term.weight(candidate(1, days = 5)), 1e-9)
        assertEquals(0.5, term.weight(candidate(1, days = 10)), 1e-9)
        assertEquals(1.0, term.weight(candidate(1, days = 20)), 1e-9)
        assertEquals(1.0, term.weight(candidate(1, days = 90)), 1e-9)
    }

    @Test
    fun cooldown_neverCookedIsMaximallyOverdue() {
        assertEquals(1.0, CooldownTerm(21).weight(candidate(1, days = null)), 1e-9)
    }

    @Test
    fun cooldown_cookedTodayIsNotBlocked() {
        assertEquals(ScoreTerm.MIN_WEIGHT, CooldownTerm(21).weight(candidate(1, days = 0)), 1e-9)
    }

    @Test
    fun protein_lowerInProportionToShortfall() {
        val term = ProteinTerm()

        assertEquals(1.0, term.weight(candidate(1, protein = 30.0)), 1e-9)
        assertEquals(1.0, term.weight(candidate(1, protein = 45.0)), 1e-9)
        assertEquals(0.5, term.weight(candidate(1, protein = 15.0)), 1e-9)
        assertEquals(ScoreTerm.MIN_WEIGHT, term.weight(candidate(1, protein = 0.0)), 1e-9)
    }

    @Test
    fun score_isProductOfTerms() {
        val planner = MealPlanner(listOf(CooldownTerm(20), ProteinTerm()))

        assertEquals(0.5 * 0.5, planner.score(candidate(1, days = 10, protein = 15.0)), 1e-9)
    }

    @Test
    fun score_acceptsAdditionalTerms() {
        val halveOdd = ScoreTerm { if (it.recipeId % 2 == 1L) 0.5 else 1.0 }
        val planner = MealPlanner(listOf(ProteinTerm(), halveOdd))

        assertEquals(0.5, planner.score(candidate(1)), 1e-9)
        assertEquals(1.0, planner.score(candidate(2)), 1e-9)
    }

    @Test
    fun rank_bestFirst() {
        val planner = MealPlanner.default(cooldownDays = 21)
        val ranked = planner.rank(
            listOf(candidate(1, days = 2), candidate(2, days = null), candidate(3, days = 30, protein = 10.0)),
        )

        assertEquals(listOf(2L, 3L, 1L), ranked.map { it.candidate.recipeId })
    }

    @Test
    fun suggest_excludesMenuAndNeverRepeats() {
        val planner = MealPlanner.default(21)
        val candidates = (1L..8L).map { candidate(it) }

        val picked = planner.suggest(candidates, exclude = setOf(1, 2, 3), count = 5, random = Random(42))

        assertEquals(5, picked.size)
        assertEquals(picked.size, picked.toSet().size)
        assertTrue(picked.none { it in setOf(1L, 2L, 3L) })
    }

    @Test
    fun suggest_fewerCandidatesThanRequested() {
        val picked = MealPlanner.default(21).suggest(listOf(candidate(1), candidate(2)), emptySet(), 5, Random(1))

        assertEquals(setOf(1L, 2L), picked.toSet())
    }

    @Test
    fun suggest_sameSeedSameResult() {
        val planner = MealPlanner.default(21)
        val candidates = (1L..20L).map { candidate(it, days = it) }

        assertEquals(
            planner.suggest(candidates, emptySet(), 5, Random(7)),
            planner.suggest(candidates, emptySet(), 5, Random(7)),
        )
    }

    @Test
    fun suggest_drawsOnlyFromTopOfPool() {
        val planner = MealPlanner(listOf(CooldownTerm(100)), poolSize = 3)
        // Score rises with the id, so 8, 9 and 10 are the top 3.
        val candidates = (1L..10L).map { candidate(it, days = it * 10) }

        repeat(50) { seed ->
            val picked = planner.suggest(candidates, emptySet(), 2, Random(seed))
            assertTrue(picked.toString(), picked.all { it in 8L..10L })
        }
    }

    @Test
    fun suggest_randomAmongTop_notAlwaysTheBest() {
        val planner = MealPlanner.default(21)
        val candidates = (1L..10L).map { candidate(it) }

        val firstPicks = (0 until 50).map { seed -> planner.suggest(candidates, emptySet(), 1, Random(seed)).single() }

        assertTrue(firstPicks.toSet().size > 1)
    }

    @Test
    fun suggest_prefersHigherScores() {
        val planner = MealPlanner(listOf(ProteinTerm()))
        val strong = candidate(1, protein = 30.0)
        val weak = candidate(2, protein = 3.0)
        val random = Random(3)

        val picks = (0 until 1000).map { planner.suggest(listOf(strong, weak), emptySet(), 1, random).single() }

        // Weights 1.0 vs 0.1: the strong recipe is drawn about 10 of 11 times.
        val strongShare = picks.count { it == 1L } / 1000.0
        assertEquals(10.0 / 11, strongShare, 0.05)
    }
}
