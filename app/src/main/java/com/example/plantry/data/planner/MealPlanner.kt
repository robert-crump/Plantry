package com.example.plantry.data.planner

import com.example.plantry.data.ProteinRating
import kotlin.random.Random

/** What the planner knows about a recipe. */
data class Candidate(
    val recipeId: Long,
    /** Null if never cooked, which counts as maximally overdue. */
    val daysSinceLastCooked: Long?,
    /** 0 for recipes without ingredient lines. */
    val proteinPerPortion: Double,
)

/**
 * One aspect of how well a recipe fits the plan, as a weight in [MIN_WEIGHT]..1. The score of a
 * candidate is the product of all weights, so a term can only rank a recipe lower, never exclude it.
 */
fun interface ScoreTerm {
    fun weight(candidate: Candidate): Double

    companion object {
        /** The lowest weight a term gives, so no recipe is ever hard-blocked. */
        const val MIN_WEIGHT = 0.05
    }
}

/** Rises linearly with days since last cooked, up to 1 once [cooldownDays] have passed. */
class CooldownTerm(private val cooldownDays: Int) : ScoreTerm {
    init {
        require(cooldownDays > 0) { "cooldownDays must be positive, was $cooldownDays" }
    }

    override fun weight(candidate: Candidate): Double {
        val days = candidate.daysSinceLastCooked ?: return 1.0
        return (days.toDouble() / cooldownDays).coerceIn(ScoreTerm.MIN_WEIGHT, 1.0)
    }
}

/** 1 at [targetGrams] protein per portion or more; below, lower in proportion to the shortfall. */
class ProteinTerm(private val targetGrams: Double = ProteinRating.GREEN_MIN) : ScoreTerm {
    override fun weight(candidate: Candidate): Double =
        (candidate.proteinPerPortion / targetGrams).coerceIn(ScoreTerm.MIN_WEIGHT, 1.0)
}

data class ScoredCandidate(val candidate: Candidate, val score: Double)

/**
 * Suggests recipes by weighted random among the best-scoring candidates, so plans stay varied
 * instead of always being the top [count].
 */
class MealPlanner(
    private val terms: List<ScoreTerm>,
    /** How many of the best candidates the random pick draws from (at least the number requested). */
    private val poolSize: Int = DEFAULT_POOL_SIZE,
) {
    fun score(candidate: Candidate): Double = terms.fold(1.0) { score, term -> score * term.weight(candidate) }

    /** Best first; ties keep the input order. */
    fun rank(candidates: List<Candidate>): List<ScoredCandidate> =
        candidates.map { ScoredCandidate(it, score(it)) }.sortedByDescending { it.score }

    /**
     * Up to [count] distinct recipe ids, none of them in [exclude], drawn without replacement from
     * the best [poolSize] candidates with probability proportional to their score.
     */
    fun suggest(candidates: List<Candidate>, exclude: Set<Long>, count: Int, random: Random): List<Long> {
        val pool = rank(candidates.filter { it.recipeId !in exclude }.distinctBy { it.recipeId })
            .take(maxOf(poolSize, count))
            .toMutableList()
        return List(minOf(count, pool.size)) { pool.removeAt(weightedIndex(pool, random)).candidate.recipeId }
    }

    private fun weightedIndex(pool: List<ScoredCandidate>, random: Random): Int {
        var remaining = random.nextDouble() * pool.sumOf { it.score }
        pool.forEachIndexed { index, scored ->
            remaining -= scored.score
            if (remaining < 0) return index
        }
        return pool.lastIndex
    }

    companion object {
        const val DEFAULT_POOL_SIZE = 10

        fun default(cooldownDays: Int) = MealPlanner(listOf(CooldownTerm(cooldownDays), ProteinTerm()))
    }
}
