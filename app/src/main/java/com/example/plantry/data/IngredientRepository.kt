package com.example.plantry.data

import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.usda.UsdaFood
import kotlinx.coroutines.flow.Flow

class IngredientRepository(private val dao: IngredientDao) {

    fun observeIngredients(): Flow<List<Ingredient>> = dao.observeAll()

    fun observeIngredient(id: Long): Flow<Ingredient?> = dao.observeById(id)

    suspend fun getIngredient(id: Long): Ingredient? = dao.getById(id)

    /**
     * Creates an unreviewed ingredient with the nutrition and portion weights of [food] and returns
     * its id. The remaining attributes get neutral defaults until the user reviews it.
     */
    suspend fun createFromUsda(food: UsdaFood, name: String): Long = dao.insert(
        Ingredient(
            name = name.trim(),
            fdcId = food.fdcId,
            usdaDescription = food.description,
            nutrition = food.nutrition,
            unitWeights = food.portions,
            buyUnit = BuyUnit.GRAMS,
            packSizeGrams = null,
            storeSection = StoreSection.OTHER,
            staple = false,
            plantPoints = PlantPoints.ZERO,
            buyAsIngredientId = null,
            buyAsYieldFactor = null,
            reviewed = false,
        ),
    )

    /**
     * Creates the unreviewed ingredients Claude proposed, keyed by their temporary (negative) ids,
     * and returns the new id for each. Buy-as links are set afterwards, so they can point to another
     * ingredient of the same batch; links to unknown ingredients, or that would close a cycle, are
     * dropped. Every proposal needs a USDA entry.
     */
    suspend fun createProposed(proposals: Map<Long, IngredientProposal>): Map<Long, Long> {
        val ids = proposals.mapValues { (_, proposal) -> dao.insert(proposal.toIngredient()) }
        val links = dao.getBuyAsLinks().associate { it.id to it.buyAsIngredientId }.toMutableMap()
        proposals.forEach { (tempId, proposal) ->
            val buyAs = proposal.buyAs ?: return@forEach
            val target = if (buyAs.ingredientId < 0) ids[buyAs.ingredientId] else buyAs.ingredientId.takeIf { it in links }
            val id = ids.getValue(tempId)
            if (target == null || BuyAsLinks.validate(id, target, links) != null) return@forEach
            val created = dao.getById(id) ?: return@forEach
            dao.update(created.copy(buyAsIngredientId = target, buyAsYieldFactor = buyAs.yieldFactor))
            links[id] = target
        }
        return ids
    }

    /**
     * Saves the user's edits and marks the ingredient reviewed. Returns an error, and saves nothing,
     * if the buy-as link would point to the ingredient itself or close a cycle.
     */
    suspend fun update(id: Long, draft: IngredientDraft): BuyAsError? {
        val existing = dao.getById(id) ?: return null
        val links = dao.getBuyAsLinks().associate { it.id to it.buyAsIngredientId }
        BuyAsLinks.validate(id, draft.buyAsIngredientId, links)?.let { return it }
        dao.update(
            existing.copy(
                name = draft.name.trim(),
                nutrition = draft.nutrition,
                unitWeights = draft.unitWeights,
                buyUnit = draft.buyUnit,
                packSizeGrams = draft.packSizeGrams.takeIf { draft.buyUnit == BuyUnit.PACK },
                storeSection = draft.storeSection,
                staple = draft.staple,
                plantPoints = draft.plantPoints,
                buyAsIngredientId = draft.buyAsIngredientId,
                buyAsYieldFactor = draft.buyAsYieldFactor.takeIf { draft.buyAsIngredientId != null },
                reviewed = true,
            ),
        )
        return null
    }
}
