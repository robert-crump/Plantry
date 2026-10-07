package com.example.plantry.data.settings

import com.example.plantry.data.claude.ResolvedModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * The Claude tier used to read recipe photos. The concrete model is the newest of its [family] the
 * API lists, see `ModelCatalog`; [fallback] is used while that is unknown.
 */
enum class ScanModel(val storageValue: String, val family: String, val fallback: ResolvedModel) {
    OPUS("opus", "opus", ResolvedModel("claude-opus-5-5", "Opus 5.5")),
    SONNET("sonnet", "sonnet", ResolvedModel("claude-sonnet-5-5", "Sonnet 5.5"));

    companion object {
        val DEFAULT = OPUS

        /** Reads the tier name, and also the model id older versions stored (e.g. "claude-opus-5-5"). */
        fun fromStored(value: String?): ScanModel =
            entries.firstOrNull { value != null && it.family in value.lowercase() } ?: DEFAULT
    }
}

/** Light or dark colours; [SYSTEM] follows the device. Stored by [storageValue]. */
enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        val DEFAULT = SYSTEM

        fun fromStorageValue(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/** The notifications the app sends; each has its own switch and time, off until the user turns it on. */
enum class ReminderKind(val keyPrefix: String, val defaultTime: LocalTime) {
    /** Proposes a recipe on a day with nothing planned. */
    PROPOSAL("proposal", LocalTime.of(7, 0)),

    /** Reminds to buy what a recipe planned for today needs. */
    SHOPPING("shopping", LocalTime.of(12, 0)),

    /** Asks in the evening whether the recipe planned for today was cooked. */
    COOKED("reminder", LocalTime.of(19, 30)),
}

data class ReminderSetting(val enabled: Boolean, val time: LocalTime)

/** What the UI may know about the settings: never the key itself, only its masked form. */
data class Settings(
    val maskedApiKey: String?,
    val scanModel: ScanModel,
    /** Days after cooking before the planner suggests a recipe at full weight again. */
    val cooldownDays: Int,
    val reminders: Map<ReminderKind, ReminderSetting> =
        ReminderKind.entries.associateWith { ReminderSetting(enabled = false, time = it.defaultTime) },
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
    /** Whether a Geplant card was swiped once, which retires the swipe hint for good. */
    val swipeHintSeen: Boolean = false,
) {
    val hasApiKey: Boolean get() = maskedApiKey != null

    fun reminder(kind: ReminderKind): ReminderSetting = reminders.getValue(kind)
}

/** Plain string storage; backed by SharedPreferences in the app and by a map in tests. */
interface SettingsStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

/** Encrypts the API key at rest; backed by the Android Keystore in the app. */
interface SecretCipher {
    fun encrypt(plaintext: String): String
    /** Null when the ciphertext can no longer be decrypted (e.g. the Keystore key is gone). */
    fun decrypt(ciphertext: String): String?
}

class SettingsRepository(
    private val storage: SettingsStorage,
    private val cipher: SecretCipher,
) {
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    /** The decrypted key for API calls, or null when none is stored. */
    fun apiKey(): String? = storage.getString(KEY_API_KEY)?.let(cipher::decrypt)

    /** Stores [key] trimmed and encrypted. Returns false (and stores nothing) when it is blank. */
    fun setApiKey(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return false
        storage.putString(KEY_API_KEY, cipher.encrypt(trimmed))
        _settings.value = load()
        return true
    }

    fun deleteApiKey() {
        storage.remove(KEY_API_KEY)
        _settings.value = load()
    }

    fun setScanModel(model: ScanModel) {
        storage.putString(KEY_SCAN_MODEL, model.storageValue)
        _settings.value = load()
    }

    /** Stores [days] if it is within [COOLDOWN_RANGE]; returns false (and stores nothing) otherwise. */
    fun setCooldownDays(days: Int): Boolean {
        if (days !in COOLDOWN_RANGE) return false
        storage.putString(KEY_COOLDOWN_DAYS, days.toString())
        _settings.value = load()
        return true
    }

    fun setReminderEnabled(kind: ReminderKind, enabled: Boolean) {
        storage.putString(kind.enabledKey, enabled.toString())
        _settings.value = load()
    }

    /** Stored to the minute. */
    fun setReminderTime(kind: ReminderKind, time: LocalTime) {
        storage.putString(kind.timeKey, time.withSecond(0).withNano(0).toString())
        _settings.value = load()
    }

    fun setThemeMode(mode: ThemeMode) {
        storage.putString(KEY_THEME_MODE, mode.storageValue)
        _settings.value = load()
    }

    fun setSwipeHintSeen() {
        if (_settings.value.swipeHintSeen) return
        storage.putString(KEY_SWIPE_HINT_SEEN, true.toString())
        _settings.value = load()
    }

    /**
     * True only the first time it is called: the evening reminder is offered once, when the first
     * recipe goes on Geplant.
     */
    fun takeNotificationPermissionRequest(): Boolean {
        if (storage.getString(KEY_NOTIFICATION_PERMISSION_ASKED) != null) return false
        storage.putString(KEY_NOTIFICATION_PERMISSION_ASKED, true.toString())
        return true
    }

    private fun load() = Settings(
        maskedApiKey = apiKey()?.let(::maskApiKey),
        scanModel = ScanModel.fromStored(storage.getString(KEY_SCAN_MODEL)),
        cooldownDays = storage.getString(KEY_COOLDOWN_DAYS)?.toIntOrNull()?.takeIf { it in COOLDOWN_RANGE }
            ?: DEFAULT_COOLDOWN_DAYS,
        reminders = ReminderKind.entries.associateWith { kind ->
            ReminderSetting(
                enabled = storage.getString(kind.enabledKey)?.toBooleanStrictOrNull() ?: false,
                time = storage.getString(kind.timeKey)?.let(::parseTime) ?: kind.defaultTime,
            )
        },
        themeMode = ThemeMode.fromStorageValue(storage.getString(KEY_THEME_MODE)),
        swipeHintSeen = storage.getString(KEY_SWIPE_HINT_SEEN)?.toBooleanStrictOrNull() ?: false,
    )

    private fun parseTime(text: String): LocalTime? =
        try {
            LocalTime.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }

    private val ReminderKind.enabledKey get() = "${keyPrefix}_enabled"
    private val ReminderKind.timeKey get() = "${keyPrefix}_time"

    companion object {
        const val KEY_API_KEY = "api_key"
        const val KEY_SCAN_MODEL = "scan_model"
        const val KEY_COOLDOWN_DAYS = "cooldown_days"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
        const val KEY_SWIPE_HINT_SEEN = "swipe_hint_seen"

        const val DEFAULT_COOLDOWN_DAYS = 21
        val COOLDOWN_RANGE = 1..365
    }
}

/**
 * Shows only the non-secret prefix and the last 4 characters, e.g. "sk-ant-…a1B2".
 * Short keys are fully hidden.
 */
fun maskApiKey(key: String): String =
    if (key.length < 16) "••••" else "${key.take(7)}…${key.takeLast(4)}"
