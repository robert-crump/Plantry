package com.example.plantry.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import java.time.LocalDate

/**
 * One recipe proposal notification and what came of it, to tell later whether proposals are taken
 * up. Stays on the device: not in backups, and a restore leaves it alone. No foreign key, so a
 * deleted recipe keeps its rows. Whether a planned recipe was cooked is read from the cook log.
 */
@Entity(tableName = "proposal_events")
data class ProposalEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The day it was shown. */
    val date: LocalDate,
    val recipeId: Long,
    /** The hook template of its title ([com.example.plantry.data.reminder.TitleHook.id]); null when it had none. */
    val hookId: String? = null,
    /** Null while nothing has come of it yet. */
    val outcome: ProposalOutcome? = null,
)

/** What came of a proposal, weakest first: a later outcome replaces only a weaker one. */
enum class ProposalOutcome(val id: String) {
    /** Replaced by the next notification, or the day ended without any of the others. */
    IGNORED("ignored"),

    /** Swiped away. */
    DISMISSED("dismissed"),

    /** The notification's body (or the confirmation's "Rezept öffnen") tapped. */
    OPENED("opened"),

    /** "Anderer Vorschlag" tapped. */
    OTHER_SUGGESTIONS("other_suggestions"),

    /** After "Anderer Vorschlag", a recipe planned for today from Vorschläge. */
    PLANNED_FROM_SUGGESTIONS("planned_from_suggestions"),

    /** "Planen" tapped. */
    PLANNED_FROM_NOTIFICATION("planned_from_notification"),
    ;

    companion object {
        fun fromId(id: String): ProposalOutcome? = entries.firstOrNull { it.id == id }

        /** The outcome once [next] happens to a proposal that had [current]. */
        fun after(current: ProposalOutcome?, next: ProposalOutcome): ProposalOutcome =
            if (current == null || next > current) next else current

        /** "Rückgängig" of "Planen": as if nothing had come of it yet, unless something else did. */
        fun afterUndoPlan(current: ProposalOutcome?): ProposalOutcome? =
            current.takeUnless { it == PLANNED_FROM_NOTIFICATION }
    }
}

@Dao
interface ProposalEventDao {

    @Insert
    suspend fun insert(event: ProposalEvent): Long

    @Query("SELECT * FROM proposal_events WHERE id = :id")
    suspend fun get(id: Long): ProposalEvent?

    @Query("UPDATE proposal_events SET outcome = :outcome WHERE id = :id")
    suspend fun setOutcome(id: Long, outcome: ProposalOutcome?)

    /** The hook of the latest proposal's title; null if it had none or there was none. */
    @Query("SELECT hookId FROM proposal_events ORDER BY id DESC LIMIT 1")
    suspend fun lastHookId(): String?

    /** Marks every proposal shown before [date] that nothing came of as ignored. */
    @Query("UPDATE proposal_events SET outcome = 'ignored' WHERE outcome IS NULL AND date < :date")
    suspend fun ignoreOpenBefore(date: LocalDate)

    /** Marks every proposal that nothing came of as ignored. */
    @Query("UPDATE proposal_events SET outcome = 'ignored' WHERE outcome IS NULL")
    suspend fun ignoreOpen()
}
