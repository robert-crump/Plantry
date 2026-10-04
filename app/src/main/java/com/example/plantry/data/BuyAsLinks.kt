package com.example.plantry.data

enum class BuyAsError { SELF_LINK, CYCLE }

object BuyAsLinks {

    /**
     * Checks whether [ingredientId] may be bought as [targetId], given the current buy-as links of
     * all ingredients (`id -> buyAsIngredientId`). Following the chain from [targetId] must never
     * lead back to [ingredientId].
     */
    fun validate(ingredientId: Long, targetId: Long?, links: Map<Long, Long?>): BuyAsError? {
        if (targetId == null) return null
        if (targetId == ingredientId) return BuyAsError.SELF_LINK
        val visited = mutableSetOf<Long>()
        var current: Long? = targetId
        while (current != null && visited.add(current)) {
            if (current == ingredientId) return BuyAsError.CYCLE
            current = links[current]
        }
        return null
    }
}

/** What an ingredient is bought as: [ingredient] in grams = grams of the original × [factor]. */
data class BuyAsTarget(val ingredient: Ingredient, val factor: Double)

/**
 * Follows [ingredient]'s buy-as chain to its end, multiplying the yield factors on the way; a link
 * without a factor counts as 1. The ingredient itself with factor 1 if it has no link.
 */
fun resolveBuyAs(ingredient: Ingredient, ingredients: Map<Long, Ingredient>): BuyAsTarget {
    var current = ingredient
    var factor = 1.0
    // Cycles are rejected when saving; the visited set only guards against looping forever.
    val visited = mutableSetOf(current.id)
    while (true) {
        val next = current.buyAsIngredientId?.let(ingredients::get) ?: break
        if (!visited.add(next.id)) break
        factor *= current.buyAsYieldFactor ?: 1.0
        current = next
    }
    return BuyAsTarget(current, factor)
}

/** The end of [ingredient]'s buy-as chain; the ingredient itself if it has no link. */
fun buyAsRoot(ingredient: Ingredient, ingredients: Map<Long, Ingredient>): Ingredient =
    resolveBuyAs(ingredient, ingredients).ingredient
