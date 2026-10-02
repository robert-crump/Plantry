package com.example.plantry.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The Claude model used to read recipe photos. */
enum class ScanModel(val modelId: String) {
    OPUS("claude-opus-5-5"),
    SONNET("claude-sonnet-5-5");

    companion object {
        val DEFAULT = OPUS

        fun fromModelId(id: String?): ScanModel = entries.firstOrNull { it.modelId == id } ?: DEFAULT
    }
}

/** What the UI may know about the settings: never the key itself, only its masked form. */
data class Settings(
    val maskedApiKey: String?,
    val scanModel: ScanModel,
) {
    val hasApiKey: Boolean get() = maskedApiKey != null
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
        storage.putString(KEY_SCAN_MODEL, model.modelId)
        _settings.value = load()
    }

    private fun load() = Settings(
        maskedApiKey = apiKey()?.let(::maskApiKey),
        scanModel = ScanModel.fromModelId(storage.getString(KEY_SCAN_MODEL)),
    )

    companion object {
        const val KEY_API_KEY = "api_key"
        const val KEY_SCAN_MODEL = "scan_model"
    }
}

/**
 * Shows only the non-secret prefix and the last 4 characters, e.g. "sk-ant-…a1B2".
 * Short keys are fully hidden.
 */
fun maskApiKey(key: String): String =
    if (key.length < 16) "••••" else "${key.take(7)}…${key.takeLast(4)}"
