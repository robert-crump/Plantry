package com.example.plantry.data.backup

import com.example.plantry.data.settings.SettingsStorage
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Keeps an emulator stocked with the bundled test data (debug builds only, see EmulatorSeed):
 * imports the fixture into an empty database, and replaces everything once the fixture changed.
 */
class EmulatorSeeder(
    private val hasData: suspend () -> Boolean,
    private val import: suspend (BackupFile) -> Unit,
    /** Remembers the fixture last imported. */
    private val storage: SettingsStorage,
    private val today: () -> LocalDate = LocalDate::now,
) {
    data class Seeded(val fingerprint: String, val daysMoved: Long)

    /** Imports [fixture], a backup file, if needed; null when the data was kept. */
    suspend fun seed(fixture: String): Seeded? {
        val fingerprint = BackupRepository.fingerprint(fixture)
        if (storage.getString(KEY_FIXTURE) == fingerprint && hasData()) return null
        val file = BackupFile.decode(fixture)
        val daysMoved = file.daysToMove(today())
        import(file.movedBy(daysMoved))
        storage.putString(KEY_FIXTURE, fingerprint)
        return Seeded(fingerprint, daysMoved)
    }

    companion object {
        const val KEY_FIXTURE = "emulator_fixture_fingerprint"

        /** Days that move the latest cooking log entry to [today], so the history ends today. */
        fun BackupFile.daysToMove(today: LocalDate): Long =
            cookLog.maxOfOrNull { LocalDate.parse(it.cookedOn) }?.let { ChronoUnit.DAYS.between(it, today) } ?: 0

        /** All cooking log and Geplant dates [days] later. */
        fun BackupFile.movedBy(days: Long): BackupFile {
            fun move(date: String) = LocalDate.parse(date).plusDays(days).toString()
            return copy(
                cookLog = cookLog.map { it.copy(cookedOn = move(it.cookedOn)) },
                planned = planned.map { it.copy(plannedOn = move(it.plannedOn)) },
            )
        }
    }
}
