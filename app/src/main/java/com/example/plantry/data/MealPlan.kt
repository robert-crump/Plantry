package com.example.plantry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** The proposal notification a plan came from; the plan records its outcome in the proposal log. */
sealed interface PlanOrigin {
    /** The proposal's row in the log. */
    val eventId: Long

    /** "Planen" on the proposal. */
    data class FromNotification(override val eventId: Long) : PlanOrigin

    /** Vorschläge, opened by the proposal's "Anderer Vorschlag"; only planning for today counts. */
    data class FromSuggestions(override val eventId: Long) : PlanOrigin
}

/** A recipe logged as cooked, with what to undo: the log entry and the Geplant entry it cleared. */
data class Cooked(val logId: Long, val unplanned: PlannedRecipe?)

/**
 * Geplant and what happens to its recipes: planned for a day, moved, postponed, logged as cooked
 * (into the cook log, with a snapshot of the recipe) and undone, and what a plan made from a
 * proposal notification means for that proposal ([PlanOrigin]). Entries drop off a week after their
 * day. Every action is one [transaction] and reads [today] once.
 */
class MealPlan(
    private val plannedDao: PlannedRecipeDao,
    private val cookLogDao: CookLogDao,
    private val proposalEventDao: ProposalEventDao,
    /** The recipe as it is now; null if it doesn't exist. */
    private val snapshot: suspend (recipeId: Long) -> RecipeSnapshot?,
    private val transaction: Transaction,
    private val today: () -> LocalDate = LocalDate::now,
) {
    /** Earliest planned day first; [today] is read on every emission, so old entries drop off across midnight. */
    fun observe(): Flow<List<PlannedRecipe>> =
        plannedDao.observeAll().map { all -> all.filter { it.isCurrent(today()) } }

    /** The recipe's Geplant entry, null while it isn't planned. */
    fun observePlanned(recipeId: Long): Flow<PlannedRecipe?> =
        observe().map { all -> all.firstOrNull { it.recipeId == recipeId } }

    /** How often [plan] was called since the app started, so Speiseplan can tell that something new is on Geplant. */
    private val _plans = MutableStateFlow(0)
    val plans: StateFlow<Int> = _plans.asStateFlow()

    /**
     * Puts the recipe on Geplant for [date], or moves it there if it already is, and records it on
     * the proposal it came from, if any. Returns the entry it replaced, for [undoPlan]; null if the
     * recipe wasn't on Geplant.
     */
    suspend fun plan(recipeId: Long, date: LocalDate, origin: PlanOrigin? = null): PlannedRecipe? {
        val today = today()
        val previous = transaction {
            current(recipeId, today).also {
                plannedDao.upsert(PlannedRecipe(recipeId, date))
                if (origin != null && (origin is PlanOrigin.FromNotification || date == today)) {
                    proposalEventDao.record(origin.eventId, origin.outcome)
                }
            }
        }
        _plans.update { it + 1 }
        return previous
    }

    /**
     * Undoes [plan]: back to the [previous] day, or off Geplant if there was none, and the proposal
     * it came from as before.
     */
    suspend fun undoPlan(recipeId: Long, previous: PlannedRecipe?, origin: PlanOrigin? = null) = transaction {
        if (previous != null) plannedDao.upsert(previous) else plannedDao.delete(recipeId)
        if (origin != null) proposalEventDao.undoPlan(origin.eventId, origin.outcome)
    }

    /**
     * "Nein, morgen" in the evening reminder: moves the recipes still planned for [askedOn] to the
     * day after, or to today if that day has passed already. Recipes moved or cooked in the
     * meantime are left alone.
     */
    suspend fun postpone(recipeIds: Collection<Long>, askedOn: LocalDate) {
        val to = maxOf(askedOn.plusDays(1), today())
        transaction {
            recipeIds.forEach { id ->
                if (plannedDao.get(id)?.plannedOn == askedOn) plannedDao.upsert(PlannedRecipe(id, to))
            }
        }
    }

    /**
     * Logs [recipeId] as cooked on [date] and takes it off Geplant. Null if the recipe doesn't
     * exist; then Geplant is left alone.
     */
    suspend fun cook(recipeId: Long, date: LocalDate): Cooked? {
        val today = today()
        return transaction { cook(recipeId, date, today) }
    }

    /**
     * "Erledigt": logs a planned recipe on its planned day, or today if that day is still ahead,
     * and takes it off Geplant.
     */
    suspend fun done(planned: PlannedRecipe): Cooked? = done(listOf(planned)).firstOrNull()

    /** [done] for several, all or none; recipes deleted in the meantime are skipped. */
    suspend fun done(planned: Collection<PlannedRecipe>): List<Cooked> {
        val today = today()
        return transaction { planned.mapNotNull { cook(it.recipeId, minOf(it.plannedOn, today), today) } }
    }

    /** Undoes [cook]: deletes the log entry and puts the recipe back on Geplant if it was there. */
    suspend fun undoCook(cooked: Cooked) = transaction {
        cookLogDao.deleteById(cooked.logId)
        cooked.unplanned?.let { plannedDao.upsert(it) }
    }

    /** Takes the [planned] recipes off Geplant and deletes the cook log [entries], all or none. */
    suspend fun delete(planned: Collection<Long>, entries: Collection<Long>) = transaction {
        planned.forEach { plannedDao.delete(it) }
        entries.forEach { cookLogDao.deleteById(it) }
    }

    private val PlanOrigin.outcome: ProposalOutcome
        get() = when (this) {
            is PlanOrigin.FromNotification -> ProposalOutcome.PLANNED_FROM_NOTIFICATION
            is PlanOrigin.FromSuggestions -> ProposalOutcome.PLANNED_FROM_SUGGESTIONS
        }

    /** The recipe's entry if it is still on Geplant on [today]. */
    private suspend fun current(recipeId: Long, today: LocalDate): PlannedRecipe? =
        plannedDao.get(recipeId)?.takeIf { it.isCurrent(today) }

    /** Inside a transaction: the log entry with a snapshot of the recipe as it is now, and its Geplant entry removed. */
    private suspend fun cook(recipeId: Long, date: LocalDate, today: LocalDate): Cooked? {
        val snapshot = snapshot(recipeId) ?: return null
        val logId = cookLogDao.insert(CookLog.of(recipeId, date, snapshot), snapshot.plants)
        val unplanned = current(recipeId, today)
        plannedDao.delete(recipeId)
        return Cooked(logId, unplanned)
    }
}

/** A Geplant entry with its recipe as it is now. */
data class PlannedItem(val planned: PlannedRecipe, val snapshot: RecipeSnapshot) {
    val recipeId: Long get() = planned.recipeId

    companion object {
        /** In the order of [planned]; entries whose recipe is missing are skipped. */
        fun of(
            planned: List<PlannedRecipe>,
            recipes: List<Recipe>,
            lines: List<RecipeLine>,
            ingredients: Map<Long, Ingredient>,
            contents: MixContents = emptyMap(),
        ): List<PlannedItem> {
            val recipesById = recipes.associateBy { it.id }
            val linesByRecipe = lines.groupBy { it.recipeId }
            return planned.mapNotNull { entry ->
                val recipe = recipesById[entry.recipeId] ?: return@mapNotNull null
                PlannedItem(entry, RecipeSnapshot.of(recipe, linesByRecipe[recipe.id].orEmpty(), ingredients, contents))
            }
        }
    }
}
