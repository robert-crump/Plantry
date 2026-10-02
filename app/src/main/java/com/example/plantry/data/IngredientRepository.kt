package com.example.plantry.data

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
