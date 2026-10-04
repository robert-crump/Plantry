package com.example.plantry.data.planner

/**
 * The rounds of the suggestion screen: each round avoids the recipes shown in earlier rounds, until
 * too few are left and the exclusion starts over. Planned recipes are never suggested.
 */
class SuggestionRounds(
    private val count: Int = DEFAULT_COUNT,
    /** Up to `count` distinct recipe ids, none of them in `exclude`. */
    private val suggest: suspend (count: Int, exclude: Set<Long>) -> List<Long>,
) {
    private val shown = mutableSetOf<Long>()

    /**
     * Up to [count] recipe ids, none of them in [planned]; fewer only if there aren't enough recipes.
     * When too few unseen recipes are left, they are topped up from all recipes and the exclusion
     * starts over with this round.
     */
    suspend fun next(planned: Set<Long>): List<Long> {
        val fresh = suggest(count, shown + planned)
        if (fresh.size == count) {
            shown += fresh
            return fresh
        }
        val picked = fresh + suggest(count - fresh.size, planned + fresh)
        shown.clear()
        shown += picked
        return picked
    }

    companion object {
        const val DEFAULT_COUNT = 3
    }
}
