package com.example.plantry.data

import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.usda.UsdaFood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class IngredientRepository(private val dao: IngredientDao) {

    fun observeIngredients(): Flow<List<Ingredient>> = dao.observeAll()

    fun observeIngredient(id: Long): Flow<Ingredient?> = dao.observeById(id)

    suspend fun getIngredient(id: Long): Ingredient? = dao.getById(id)

    /** The units of every ingredient, by ingredient id; ingredients without units are left out. */
    fun observeUnits(): Flow<Map<Long, List<IngredientUnit>>> = dao.observeUnits().map { units -> units.groupBy { it.ingredientId } }

    suspend fun getUnits(id: Long): List<IngredientUnit> = dao.getUnits(id)

    /** Learned aliases: ingredient id by normalized wording, see [IngredientAliases]. */
    fun observeAliases(): Flow<Map<String, Long>> =
        dao.observeAliases().map { aliases -> aliases.associate { it.wording to it.ingredientId } }

    /** Remembers each wording (as typed or read) as an alias of its ingredient. */
    suspend fun learnAliases(learned: List<Pair<String, Long>>) {
        val aliases = IngredientAliases.of(learned)
        if (aliases.isNotEmpty()) dao.upsertAliases(aliases)
    }

    /**
     * Creates the ingredient from [seed] with the user's values in [draft] (which also holds the
     * nutrition, as the user may have edited it) and returns its id. A scanned label in the draft
     * replaces the seed's source.
     */
    suspend fun create(seed: NewIngredientSeed, draft: IngredientDraft): Long {
        val label = draft.scannedLabel ?: seed.labelSource
        val scanned = draft.scannedLabel != null
        return dao.insertWithUnits(
            Ingredient(
                name = draft.name.trim(),
                fdcId = if (scanned) null else seed.fdcId,
                usdaDescription = if (scanned) null else seed.usdaDescription,
                nutrition = draft.nutrition,
                storeSection = draft.storeSection,
                plantPoints = draft.plantPoints,
                labelSource = label,
            ),
            draft.units,
        )
    }

    /**
     * Creates the ingredients Claude proposed, keyed by their temporary (negative) ids,
     * and returns the new id for each. Every proposal needs a nutrition source.
     */
    suspend fun createProposed(proposals: Map<Long, IngredientProposal>): Map<Long, Long> =
        proposals.mapValues { (_, proposal) -> dao.insertWithUnits(proposal.toIngredient(), proposal.units()) }

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
     * Saves the user's edits. A [IngredientDraft.scannedLabel] becomes the source and clears the USDA reference. Recipes
     * using one of the ingredient's units follow a changed weight right away.
     *
     * Nothing is saved when the edit would remove a unit a recipe line still uses; returns the
     * titles of those recipes then, empty once saved.
     */
    suspend fun update(id: Long, draft: IngredientDraft): List<String> {
        val existing = dao.getById(id) ?: return emptyList()
        val sourced = draft.scannedLabel?.let { existing.copy(fdcId = null, usdaDescription = null, labelSource = it) } ?: existing
        return dao.updateUnlessUnitInUse(
            sourced.copy(
                name = draft.name.trim(),
                nutrition = draft.nutrition,
                storeSection = draft.storeSection,
                plantPoints = draft.plantPoints,
            ),
            draft.units,
        )
    }

    /** Adds [unit] to its ingredient, or replaces the row with the same unit; recipes in that unit follow. */
    suspend fun addUnit(unit: IngredientUnit) = dao.upsertUnit(unit)

    /** Titles of the recipes with a line in [unit] of ingredient [id]; while there are any, the unit can't be removed. */
    suspend fun recipesUsingUnit(id: Long, unit: PieceUnit): List<String> = dao.getRecipeTitlesUsingUnit(id, unit)
}
