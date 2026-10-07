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
     * Creates the unreviewed ingredient from [seed] with the user's decisions in [draft] (which also
     * holds the nutrition, as the user may have edited it) and returns its id. A scanned label in
     * the draft replaces the seed's source and is what makes the ingredient a barcode one.
     */
    suspend fun create(seed: NewIngredientSeed, draft: IngredientDraft): Long {
        val label = draft.scannedLabel ?: seed.labelSource
        val scanned = draft.scannedLabel != null
        return dao.insert(
            Ingredient(
                name = draft.name.trim(),
                fdcId = if (scanned) null else seed.fdcId,
                usdaDescription = if (scanned) null else seed.usdaDescription,
                nutrition = draft.nutrition,
                storeSection = draft.storeSection,
                plantPoints = draft.plantPoints,
                reviewed = false,
                drainedWeight = draft.drainedWeight,
                labelSource = label,
                origin = if (label != null) IngredientOrigin.BARCODE else IngredientOrigin.MANUAL,
            ),
        )
    }

    /**
     * Creates the unreviewed ingredients Claude proposed, keyed by their temporary (negative) ids,
     * and returns the new id for each. Every proposal needs a nutrition source.
     */
    suspend fun createProposed(proposals: Map<Long, IngredientProposal>): Map<Long, Long> =
        proposals.mapValues { (_, proposal) -> dao.insert(proposal.toIngredient()) }

    /** Sets the plant points or store section of [group] on each of [ids]; reviewed stays as it is. */
    suspend fun moveTo(ids: Set<Long>, group: SortGroup) {
        if (ids.isEmpty()) return
        val changed = IngredientSorting.move(dao.getByIds(ids.toList()), ids, group)
        if (changed.isNotEmpty()) dao.updateAll(changed)
    }

    /** Ingredients with an undecided property stay unreviewed. */
    suspend fun markReviewed(ids: Collection<Long>) {
        if (ids.isNotEmpty()) dao.markReviewed(ids.toList())
    }

    /** Titles of the recipes that use the ingredient, see [delete]. */
    suspend fun recipesUsing(id: Long): List<String> = dao.getRecipeTitlesUsing(id)

    /**
     * Deletes the ingredient unless a recipe still uses it. Returns the titles of those recipes,
     * empty if the ingredient was deleted.
     */
    suspend fun delete(id: Long): List<String> {
        val usedIn = dao.getRecipeTitlesUsing(id)
        if (usedIn.isEmpty()) dao.deleteById(id)
        return usedIn
    }

    /**
     * Saves the user's edits; reviewed stays as it is, see [setReviewed]. A
     * [IngredientDraft.scannedLabel] becomes the source and clears the USDA reference.
     */
    suspend fun update(id: Long, draft: IngredientDraft) {
        val existing = dao.getById(id) ?: return
        val sourced = draft.scannedLabel?.let { existing.copy(fdcId = null, usdaDescription = null, labelSource = it) } ?: existing
        dao.update(
            sourced.copy(
                name = draft.name.trim(),
                nutrition = draft.nutrition,
                storeSection = draft.storeSection,
                plantPoints = draft.plantPoints,
                drainedWeight = draft.drainedWeight,
                storeSectionUndecided = false,
                plantPointsUndecided = false,
            ),
        )
    }

    suspend fun setReviewed(id: Long, reviewed: Boolean) {
        val existing = dao.getById(id) ?: return
        if (reviewed && existing.hasUndecided) return
        if (existing.reviewed != reviewed) dao.update(existing.copy(reviewed = reviewed))
    }
}
