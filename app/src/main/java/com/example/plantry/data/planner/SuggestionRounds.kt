package com.example.plantry.data.planner

/**
 * The rounds of the suggestion screen: each round avoids the recipes shown in earlier rounds, until
 * too few are left and the exclusion starts over. Planned recipes are never suggested.
 */
class SuggestionRounds(
    private val count: Int = DEFAULT_COUNT,
    /** Counted as shown before the first round, which never suggests them, e.g. a notification's recipe. */
    private val alreadyShown: Set<Long> = emptySet(),
    /** Up to `count` distinct recipe ids, none of them in `exclude`. */
    private val suggest: suspend (count: Int, exclude: Set<Long>) -> List<Long>,
) {
    private val shown = alreadyShown.toMutableSet()
    private var first = true

    /**
     * Up to [count] recipe ids, none of them in [planned]; fewer only if there aren't enough recipes.
     * When too few unseen recipes are left, they are topped up from all recipes and the exclusion
     * starts over with this round.
     */
    suspend fun next(planned: Set<Long>): List<Long> {
        val skip = if (first) alreadyShown else emptySet()
        first = false
        val fresh = suggest(count, shown + planned)
        if (fresh.size == count) {
            shown += fresh
            return fresh
        }
        val picked = fresh + suggest(count - fresh.size, planned + fresh + skip)
        shown.clear()
        shown += picked
        return picked
    }

    companion object {
        const val DEFAULT_COUNT = 3
    }
}
