package com.example.plantry.data.backup

import com.example.plantry.data.settings.ScanModel
import com.example.plantry.data.settings.SettingsRepository
import com.example.plantry.data.settings.SettingsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Manual backup: export everything to one JSON file, import replaces everything.
 *
 * The reminder compares against a baseline (date + fingerprint of the exported content), set by
 * every export and import, and on the first check after install.
 */
class BackupRepository(
    private val store: BackupStore,
    private val photos: PhotoStore,
    private val settings: SettingsRepository,
    /** Export bookkeeping; never part of the export itself. */
    private val storage: SettingsStorage,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val _reminder = MutableStateFlow(false)

    /** True when the last export is more than [REMINDER_DAYS] days old and data has changed since. */
    val reminder: StateFlow<Boolean> = _reminder.asStateFlow()

    /** "YYMMDD-plantry.json"; the second export of a day gets "-2", and so on. */
    fun nextFileName(): String {
        val date = today()
        return fileName(date, exportsOn(date) + 1)
    }

    /** Passes the export to [write]; only if that succeeds it counts as an export. */
    suspend fun export(write: suspend (String) -> Unit) {
        val json = currentJson()
        write(json)
        val date = today()
        val count = exportsOn(date) + 1
        storage.putString(KEY_NAME_DATE, date.toString())
        storage.putString(KEY_NAME_COUNT, count.toString())
        setBaseline(json)
    }

    /** Parses and validates [text] without changing anything; throws [InvalidBackupException]. */
    suspend fun read(text: String): BackupFile = withContext(Dispatchers.Default) {
        BackupFile.decode(text).also {
            it.toSnapshot()
            it.photos()
        }
    }

    /**
     * Replaces all data, photos and settings (except the API key) with [file]. The database is
     * replaced atomically; a file with broken references throws and changes nothing.
     */
    suspend fun import(file: BackupFile) {
        store.replaceAll(file.toSnapshot())
        withContext(Dispatchers.IO) { photos.replaceAll(file.photos()) }
        settings.setScanModel(ScanModel.fromStored(file.settings.scanModel))
        settings.setCooldownDays(file.settings.cooldownDays)
        setBaseline(currentJson())
    }

    /** Re-evaluates [reminder]; cheap unless the last export is old. */
    suspend fun refreshReminder() {
        val baselineDate = storage.getString(KEY_BASELINE_DATE)?.let(LocalDate::parse)
        if (baselineDate == null) {
            setBaseline(currentJson())
            return
        }
        _reminder.value = ChronoUnit.DAYS.between(baselineDate, today()) > REMINDER_DAYS &&
            fingerprint(currentJson()) != storage.getString(KEY_BASELINE_FINGERPRINT)
    }

    private fun exportsOn(date: LocalDate): Int =
        if (storage.getString(KEY_NAME_DATE) == date.toString()) storage.getString(KEY_NAME_COUNT)?.toIntOrNull() ?: 0 else 0

    private suspend fun currentJson(): String {
        val snapshot = store.snapshot()
        val photos = withContext(Dispatchers.IO) { photos.all() }
        val settings = settings.settings.value.let { BackupSettings(it.scanModel.storageValue, it.cooldownDays) }
        return withContext(Dispatchers.Default) { BackupFile.encode(snapshot.toFile(photos, settings)) }
    }

    private fun setBaseline(json: String) {
        storage.putString(KEY_BASELINE_DATE, today().toString())
        storage.putString(KEY_BASELINE_FINGERPRINT, fingerprint(json))
        _reminder.value = false
    }

    companion object {
        const val REMINDER_DAYS = 30

        const val KEY_NAME_DATE = "backup_name_date"
        const val KEY_NAME_COUNT = "backup_name_count"
        const val KEY_BASELINE_DATE = "backup_baseline_date"
        const val KEY_BASELINE_FINGERPRINT = "backup_baseline_fingerprint"

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd")

        /** The [number]th export on [date], counting from 1. */
        fun fileName(date: LocalDate, number: Int): String =
            date.format(DATE_FORMAT) + "-plantry" + (if (number > 1) "-$number" else "") + ".json"

        private fun fingerprint(json: String): String =
            MessageDigest.getInstance("SHA-256").digest(json.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
