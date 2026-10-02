package com.example.plantry.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    private val storage = MapStorage()
    private val repository = SettingsRepository(storage, ReversingCipher)

    private val key = "sk-ant-api03-abcdefghijklmnopXYZ9"

    @Test
    fun noKeyStored_byDefault() {
        assertFalse(repository.settings.value.hasApiKey)
        assertNull(repository.apiKey())
        assertEquals(ScanModel.OPUS, repository.settings.value.scanModel)
    }

    @Test
    fun cooldownDays_defaultsTo21_andIsStored() {
        assertEquals(21, repository.settings.value.cooldownDays)

        assertTrue(repository.setCooldownDays(14))

        assertEquals(14, repository.settings.value.cooldownDays)
        assertEquals(14, SettingsRepository(storage, ReversingCipher).settings.value.cooldownDays)
    }

    @Test
    fun setCooldownDays_rejectsOutOfRange() {
        assertFalse(repository.setCooldownDays(0))
        assertFalse(repository.setCooldownDays(366))

        assertEquals(21, repository.settings.value.cooldownDays)
    }

    @Test
    fun setApiKey_storesTrimmedKeyEncrypted_andExposesOnlyMasked() {
        assertTrue(repository.setApiKey("  $key\n"))

        assertEquals(key, repository.apiKey())
        assertEquals(key.reversed(), storage.values[SettingsRepository.KEY_API_KEY])
        assertEquals("sk-ant-…XYZ9", repository.settings.value.maskedApiKey)
    }

    @Test
    fun setApiKey_rejectsBlank() {
        repository.setApiKey(key)

        assertFalse(repository.setApiKey("   "))
        assertEquals(key, repository.apiKey())
    }

    @Test
    fun setApiKey_replacesExistingKey() {
        repository.setApiKey(key)
        repository.setApiKey("sk-ant-api03-newnewnewnewnew0000")

        assertEquals("sk-ant-api03-newnewnewnewnew0000", repository.apiKey())
        assertEquals("sk-ant-…0000", repository.settings.value.maskedApiKey)
    }

    @Test
    fun deleteApiKey_removesIt() {
        repository.setApiKey(key)
        repository.deleteApiKey()

        assertFalse(repository.settings.value.hasApiKey)
        assertNull(repository.apiKey())
        assertFalse(SettingsRepository.KEY_API_KEY in storage.values)
    }

    @Test
    fun undecryptableKey_countsAsMissing() {
        storage.values[SettingsRepository.KEY_API_KEY] = "garbage"
        val repository = SettingsRepository(storage, object : SecretCipher {
            override fun encrypt(plaintext: String) = plaintext
            override fun decrypt(ciphertext: String): String? = null
        })

        assertFalse(repository.settings.value.hasApiKey)
    }

    @Test
    fun scanModel_isPersisted() {
        repository.setScanModel(ScanModel.SONNET)

        assertEquals(ScanModel.SONNET, repository.settings.value.scanModel)
        assertEquals("claude-sonnet-5-5", storage.values[SettingsRepository.KEY_SCAN_MODEL])
        assertEquals(ScanModel.SONNET, SettingsRepository(storage, ReversingCipher).settings.value.scanModel)
    }

    @Test
    fun unknownStoredModel_fallsBackToDefault() {
        storage.values[SettingsRepository.KEY_SCAN_MODEL] = "claude-retired-1"

        assertEquals(ScanModel.DEFAULT, SettingsRepository(storage, ReversingCipher).settings.value.scanModel)
    }

    @Test
    fun maskApiKey_hidesShortKeysCompletely() {
        assertEquals("••••", maskApiKey("sk-ant-short"))
    }
}

private class MapStorage : SettingsStorage {
    val values = mutableMapOf<String, String>()
    override fun getString(key: String) = values[key]
    override fun putString(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

/** Stand-in for the Keystore cipher: enough to prove the stored value is not the plaintext. */
private object ReversingCipher : SecretCipher {
    override fun encrypt(plaintext: String) = plaintext.reversed()
    override fun decrypt(ciphertext: String) = ciphertext.reversed()
}
