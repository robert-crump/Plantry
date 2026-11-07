package com.example.plantry.data

import java.time.LocalDate

/** The [ProposalEvent] log: a row per proposal shown, with what came of it. */
class ProposalEventRepository(
    private val dao: ProposalEventDao,
    private val today: () -> LocalDate = LocalDate::now,
) {
    /**
     * A proposal for [recipeId] is shown today; returns its row id, which its buttons carry. It
     * replaces any earlier one, so those that nothing came of are ignored.
     */
    suspend fun shown(recipeId: Long, hookId: String? = null): Long {
        dao.ignoreOpen()
        return dao.insert(ProposalEvent(date = today(), recipeId = recipeId, hookId = hookId))
    }

    /** The hook of the latest proposal's title, which the next one must not repeat. */
    suspend fun lastHookId(): String? = dao.lastHookId()

    /** The latest proposal shown today, with what came of it so far; null if none was. */
    suspend fun latestToday(): ProposalEvent? = dao.latestOn(today())

    /** The hook of the proposal [id]'s title, to show it the same again; null if it had none. */
    suspend fun hookId(id: Long): String? = dao.get(id)?.hookId

    /**
     * [outcome] happened to the proposal [id], unless something stronger already did. Outcomes of
     * planning are recorded by [MealPlan] with the plan.
     */
    suspend fun record(id: Long, outcome: ProposalOutcome) = dao.record(id, outcome)

    /** Another notification took the proposal's place: if nothing came of it, it was ignored. */
    suspend fun replaced() = dao.ignoreOpen()

    /** Proposals from before today that nothing came of were ignored. */
    suspend fun closePastDays() = dao.ignoreOpenBefore(today())
}
