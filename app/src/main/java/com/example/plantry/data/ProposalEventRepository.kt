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

    /** The hook of the proposal [id]'s title, to show it the same again; null if it had none. */
    suspend fun hookId(id: Long): String? = dao.get(id)?.hookId

    /** [outcome] happened to the proposal [id], unless something stronger already did. */
    suspend fun record(id: Long, outcome: ProposalOutcome) {
        val event = dao.get(id) ?: return
        val next = ProposalOutcome.after(event.outcome, outcome)
        if (next != event.outcome) dao.setOutcome(id, next)
    }

    /**
     * A recipe was planned for [date] from Vorschläge opened by the proposal [id]'s "Anderer
     * Vorschlag"; only planning for today counts. Nothing to record without an [id].
     */
    suspend fun plannedFromSuggestions(id: Long?, date: LocalDate) {
        if (id != null && date == today()) record(id, ProposalOutcome.PLANNED_FROM_SUGGESTIONS)
    }

    /** "Rückgängig" of "Planen" on the proposal [id]. */
    suspend fun undoPlan(id: Long) {
        val event = dao.get(id) ?: return
        dao.setOutcome(id, ProposalOutcome.afterUndoPlan(event.outcome))
    }

    /** Another notification took the proposal's place: if nothing came of it, it was ignored. */
    suspend fun replaced() = dao.ignoreOpen()

    /** Proposals from before today that nothing came of were ignored. */
    suspend fun closePastDays() = dao.ignoreOpenBefore(today())
}
