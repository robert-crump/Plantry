package com.example.plantry.data

import android.util.Log
import com.example.plantry.data.claude.IngredientProposal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Everything the recipe editor saves at once. [draft] lines and [aliases] may use the temporary
 * (negative) ids of [newIngredients]; saving replaces them with the ids the ingredients get.
 */
data class RecipeSave(
    /** Null for a new recipe. */
    val recipeId: Long?,
    val draft: RecipeDraft,
    /** Claude's proposals to create, by temporary id. */
    val newIngredients: Map<Long, IngredientProposal> = emptyMap(),
    /** Wordings to remember as aliases of their ingredient. */
    val aliases: List<Pair<String, Long>> = emptyList(),
    /** A page photo to append, as compressed JPEG. */
    val addPhoto: ByteArray? = null,
    /** Indexes of the stored page photos to delete. */
    val deletedPages: Set<Int> = emptySet(),
    /** Set when the recipe was scanned: its book is remembered for the next scan, see [BookSession]. */
    val bookPage: BookPage? = null,
)

/** Runs [block] in one database transaction; the app passes Room's withTransaction. */
typealias InTransaction = suspend (block: suspend () -> Long) -> Long

class RecipeRepository(
    private val dao: RecipeDao,
    private val ingredients: IngredientRepository,
    private val photos: RecipePhotoRepository,
    private val bookSession: BookSession,
    private val inTransaction: InTransaction,
) {

    fun observeRecipes(): Flow<List<Recipe>> = dao.observeAll()

    fun observeRecipe(id: Long): Flow<Recipe?> = dao.observeById(id)

    fun observeLines(recipeId: Long): Flow<List<RecipeLine>> = dao.observeLines(recipeId)

    /** The lines of all recipes. */
    fun observeAllLines(): Flow<List<RecipeLine>> = dao.observeAllLines()

    suspend fun getRecipe(id: Long): Recipe? = dao.getById(id)

    suspend fun getLines(recipeId: Long): List<RecipeLine> = dao.getLines(recipeId)

    /**
     * Saves the recipe with its new ingredients and learned aliases in one transaction and returns its
     * id: on failure none of them is stored. Then the page photos follow; a photo that fails is logged,
     * and the recipe stays saved. A scanned recipe's book is remembered last.
     */
    suspend fun save(save: RecipeSave): Long {
        val id = inTransaction {
            val newIds = ingredients.createProposed(save.newIngredients)
            val draft = save.draft.withIngredientIds(newIds)
            ingredients.learnAliases(save.aliases.mapNotNull { (wording, id) -> newIds.real(id)?.let { wording to it } })
            save.recipeId?.also { update(it, draft) } ?: create(draft)
        }
        try {
            save.addPhoto?.let { photos.add(id, it) }
            if (save.deletedPages.isNotEmpty()) photos.delete(id, save.deletedPages)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Recipe $id saved, its page photos not", e)
        }
        save.bookPage?.let { bookSession.remember(it.source, it.page) }
        return id
    }

    /** Inserts a new recipe with its lines and returns its id. */
    internal suspend fun create(draft: RecipeDraft): Long =
        dao.insertWithLines(draft.toRecipe(id = 0, modified = false), draft.lines)

    /**
     * Overwrites the recipe and its lines. Once its lines differ from the stored ones, the recipe
     * is flagged as modified, and stays so. The [Recipe.notes] are kept, the editor has no field for them.
     */
    internal suspend fun update(id: Long, draft: RecipeDraft) {
        val existing = dao.getById(id) ?: return
        val linesChanged = dao.getLines(id).map { it.toDraft() } != draft.normalizedLines()
        dao.updateWithLines(
            draft.toRecipe(id, modified = existing.modified || linesChanged).copy(notes = existing.notes),
            draft.lines,
        )
    }

    /** Replaces the recipe's [Recipe.notes], trimmed; blank text clears them. */
    suspend fun setNotes(id: Long, notes: String) = dao.updateNotes(id, notes.trim())

    /** Deletes the recipe with its page photos; its lines and plans go with it, cook logs keep their entries. */
    suspend fun delete(id: Long) {
        dao.deleteById(id)
        photos.deleteAll(id)
    }

    private fun RecipeDraft.normalizedLines() = lines.map { it.normalized() }

    private fun RecipeDraft.toRecipe(id: Long, modified: Boolean) = Recipe(
        id = id,
        title = title.trim(),
        source = source.trim(),
        page = page,
        servings = servings,
        totalTimeMinutes = totalTimeMinutes,
        modified = modified,
        dish = dish,
        activeTimeMinutes = activeTimeMinutes,
        steps = steps,
    )

    private companion object {
        const val TAG = "RecipeRepository"
    }
}

/** Replaces the temporary ids of new ingredients with the ids they were created with. */
fun RecipeDraft.withIngredientIds(ids: Map<Long, Long>) =
    copy(lines = lines.map { line -> ids[line.ingredientId]?.let { line.copy(ingredientId = it) } ?: line })

/** The stored id for [id]: itself unless it is temporary; null for a temporary id that was not created. */
private fun Map<Long, Long>.real(id: Long): Long? = if (id > 0) id else this[id]
