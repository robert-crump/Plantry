package com.example.plantry.data

import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.usda.UsdaFood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class IngredientRepository(private val dao: IngredientDao) {

    fun observeIngredients(): Flow<List<Ingredient>> = dao.observeAll()

    fun observeIngredient(id: Long): Flow<Ingredient?> = dao.observeById(id)

    suspend fun getIngredient(id: Long): Ingredient? = dao.getById(id)

    /** Learned aliases: ingredient id by normalized wording, see [IngredientAliases]. */
    fun observeAliases(): Flow<Map<String, Long>> =
        dao.observeAliases().map { aliases -> aliases.associate { it.wording to it.ingredientId } }

    /** Remembers each wording (as typed or read) as an alias of its ingredient. */
    suspend fun learnAliases(learned: List<Pair<String, Long>>) {
        val aliases = IngredientAliases.of(learned)
        if (aliases.isNotEmpty()) dao.upsertAliases(aliases)
    }

    /**
     * Creates an unreviewed ingredient with the nutrition and portion weights of [food] and returns
     * its id. The remaining attributes get neutral defaults until the user reviews it.
     */
    suspend fun createFromUsda(food: UsdaFood, name: String): Long =
        insertUnreviewed(name, food.nutrition, food.portions, food.fdcId, food.description)

    /**
     * Creates an unreviewed ingredient without a USDA reference, with the [nutrition] per 100 g
     * from the package label, and returns its id. Other attributes get the same defaults as
     * [createFromUsda].
     */
    suspend fun createFromLabel(name: String, nutrition: Nutrition): Long =
        insertUnreviewed(name, nutrition, unitWeights = emptyList(), fdcId = null, usdaDescription = null)

    private suspend fun insertUnreviewed(
        name: String,
        nutrition: Nutrition,
        unitWeights: List<UnitWeight>,
        fdcId: Long?,
        usdaDescription: String?,
    ): Long = dao.insert(
        Ingredient(
            name = name.trim(),
            fdcId = fdcId,
            usdaDescription = usdaDescription,
            nutrition = nutrition,
            unitWeights = unitWeights,
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
     * dropped. Every proposal needs a nutrition source.
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

    /** Sets the plant points or store section of [group] on each of [ids]; reviewed stays as it is. */
    suspend fun moveTo(ids: Set<Long>, group: SortGroup) {
        if (ids.isEmpty()) return
        val changed = IngredientSorting.move(dao.getByIds(ids.toList()), ids, group)
        if (changed.isNotEmpty()) dao.updateAll(changed)
    }

    suspend fun markReviewed(ids: Collection<Long>) {
        if (ids.isNotEmpty()) dao.markReviewed(ids.toList())
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
