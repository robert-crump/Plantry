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
