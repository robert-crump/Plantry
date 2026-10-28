package com.example.plantry.data

import com.example.plantry.data.ProposalOutcome.DISMISSED
import com.example.plantry.data.ProposalOutcome.IGNORED
import com.example.plantry.data.ProposalOutcome.OPENED
import com.example.plantry.data.ProposalOutcome.OTHER_SUGGESTIONS
import com.example.plantry.data.ProposalOutcome.PLANNED_FROM_NOTIFICATION
import com.example.plantry.data.ProposalOutcome.PLANNED_FROM_SUGGESTIONS
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ProposalEventRepositoryTest {

    private var today = LocalDate.of(2026, 10, 28)
    private val dao = FakeProposalEventDao()
    private val repository = ProposalEventRepository(dao) { today }

    @Test
    fun after_takesAnyOutcomeWhileThereIsNone() {
        ProposalOutcome.entries.forEach { assertEquals(it, ProposalOutcome.after(null, it)) }
    }

    @Test
    fun after_aStrongerOutcomeReplacesAWeakerOne() {
        assertEquals(PLANNED_FROM_NOTIFICATION, ProposalOutcome.after(OPENED, PLANNED_FROM_NOTIFICATION))
        assertEquals(PLANNED_FROM_SUGGESTIONS, ProposalOutcome.after(OTHER_SUGGESTIONS, PLANNED_FROM_SUGGESTIONS))
        assertEquals(OPENED, ProposalOutcome.after(DISMISSED, OPENED))
        assertEquals(DISMISSED, ProposalOutcome.after(IGNORED, DISMISSED))
    }

    @Test
    fun after_aWeakerOutcomeKeepsTheStrongerOne() {
        // "Rezept öffnen" or swiping away the confirmation after "Planen".
        assertEquals(PLANNED_FROM_NOTIFICATION, ProposalOutcome.after(PLANNED_FROM_NOTIFICATION, OPENED))
        assertEquals(PLANNED_FROM_NOTIFICATION, ProposalOutcome.after(PLANNED_FROM_NOTIFICATION, DISMISSED))
        assertEquals(OTHER_SUGGESTIONS, ProposalOutcome.after(OTHER_SUGGESTIONS, IGNORED))
    }

    @Test
    fun afterUndoPlan_revertsOnlyPlanen() {
        assertNull(ProposalOutcome.afterUndoPlan(PLANNED_FROM_NOTIFICATION))
        assertEquals(OPENED, ProposalOutcome.afterUndoPlan(OPENED))
        assertNull(ProposalOutcome.afterUndoPlan(null))
    }

    @Test
    fun fromId_readsEveryId() {
        ProposalOutcome.entries.forEach { assertEquals(it, ProposalOutcome.fromId(it.id)) }
        assertNull(ProposalOutcome.fromId("unknown"))
    }

    @Test
    fun shown_writesARowForToday() = runTest {
        val id = repository.shown(recipeId = 3)

        assertEquals(ProposalEvent(id, today, 3, hookId = null, outcome = null), dao.get(id))
    }

    @Test
    fun shown_marksEarlierProposalsWithoutAnOutcomeAsIgnored() = runTest {
        val opened = repository.shown(1).also { repository.record(it, OPENED) }
        val open = repository.shown(2)

        repository.shown(3)

        assertEquals(OPENED, dao.get(opened)?.outcome)
        assertEquals(IGNORED, dao.get(open)?.outcome)
    }

    @Test
    fun record_keepsTheStrongerOutcome() = runTest {
        val id = repository.shown(1)

        repository.record(id, OPENED)
        repository.record(id, PLANNED_FROM_NOTIFICATION)
        repository.record(id, DISMISSED)

        assertEquals(PLANNED_FROM_NOTIFICATION, dao.get(id)?.outcome)
    }

    @Test
    fun undoPlan_leavesNoOutcome_andAPlanAgainCounts() = runTest {
        val id = repository.shown(1)
        repository.record(id, PLANNED_FROM_NOTIFICATION)

        repository.undoPlan(id)
        assertNull(dao.get(id)?.outcome)

        repository.record(id, PLANNED_FROM_NOTIFICATION)
        assertEquals(PLANNED_FROM_NOTIFICATION, dao.get(id)?.outcome)
    }

    @Test
    fun plannedFromSuggestions_countsOnlyForToday() = runTest {
        val id = repository.shown(1).also { repository.record(it, OTHER_SUGGESTIONS) }

        repository.plannedFromSuggestions(id, today.plusDays(1))
        assertEquals(OTHER_SUGGESTIONS, dao.get(id)?.outcome)

        repository.plannedFromSuggestions(id, today)
        assertEquals(PLANNED_FROM_SUGGESTIONS, dao.get(id)?.outcome)

        // Vorschläge not opened from a proposal.
        repository.plannedFromSuggestions(null, today)
    }

    @Test
    fun replaced_marksTheOpenProposalAsIgnored() = runTest {
        val id = repository.shown(1)

        repository.replaced()

        assertEquals(IGNORED, dao.get(id)?.outcome)
    }

    @Test
    fun closePastDays_leavesTodaysOpenProposal() = runTest {
        val yesterday = repository.shown(1)
        today = today.plusDays(1)
        val current = repository.shown(2).also { dao.setOutcome(yesterday, null) }

        repository.closePastDays()

        assertEquals(IGNORED, dao.get(yesterday)?.outcome)
        assertNull(dao.get(current)?.outcome)
    }

    @Test
    fun record_ofAMissingRowDoesNothing() = runTest {
        repository.record(42, OPENED)
        repository.undoPlan(42)

        assertNull(dao.get(42))
    }

    private class FakeProposalEventDao : ProposalEventDao {
        private val rows = linkedMapOf<Long, ProposalEvent>()

        override suspend fun insert(event: ProposalEvent): Long {
            val id = rows.size + 1L
            rows[id] = event.copy(id = id)
            return id
        }

        override suspend fun get(id: Long) = rows[id]

        override suspend fun setOutcome(id: Long, outcome: ProposalOutcome?) {
            rows[id]?.let { rows[id] = it.copy(outcome = outcome) }
        }

        override suspend fun ignoreOpenBefore(date: LocalDate) {
            rows.replaceAll { _, it -> if (it.outcome == null && it.date < date) it.copy(outcome = IGNORED) else it }
        }

        override suspend fun ignoreOpen() {
            rows.replaceAll { _, it -> if (it.outcome == null) it.copy(outcome = IGNORED) else it }
        }
    }
}
