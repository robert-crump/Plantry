package com.example.plantry.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

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
    fun reminders_defaultToOffAtTheirOwnTimes() {
        val settings = repository.settings.value

        assertEquals(ReminderSetting(false, LocalTime.of(7, 0)), settings.reminder(ReminderKind.PROPOSAL))
        assertEquals(ReminderSetting(false, LocalTime.of(12, 0)), settings.reminder(ReminderKind.SHOPPING))
        assertEquals(ReminderSetting(false, LocalTime.of(19, 30)), settings.reminder(ReminderKind.COOKED))
    }

    @Test
    fun reminders_areStoredIndependently() {
        repository.setReminderEnabled(ReminderKind.SHOPPING, true)
        repository.setReminderTime(ReminderKind.SHOPPING, LocalTime.of(17, 5, 42))
        repository.setReminderEnabled(ReminderKind.COOKED, true)
        repository.setReminderEnabled(ReminderKind.COOKED, false)

        val reloaded = SettingsRepository(storage, ReversingCipher).settings.value
        assertEquals(ReminderSetting(true, LocalTime.of(17, 5)), reloaded.reminder(ReminderKind.SHOPPING))
        assertFalse(reloaded.reminder(ReminderKind.PROPOSAL).enabled)
        assertFalse(reloaded.reminder(ReminderKind.COOKED).enabled)
        assertEquals(LocalTime.of(7, 0), reloaded.reminder(ReminderKind.PROPOSAL).time)
    }

    @Test
    fun reminderTime_unreadable_fallsBackToDefault() {
        storage.values["reminder_time"] = "abends"

        val reminder = SettingsRepository(storage, ReversingCipher).settings.value.reminder(ReminderKind.COOKED)
        assertEquals(LocalTime.of(19, 30), reminder.time)
    }

    @Test
    fun themeMode_defaultsToSystem_andIsStored() {
        assertEquals(ThemeMode.SYSTEM, repository.settings.value.themeMode)

        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.settings.value.themeMode)
        assertEquals(ThemeMode.DARK, SettingsRepository(storage, ReversingCipher).settings.value.themeMode)
    }

    @Test
    fun themeMode_unknown_fallsBackToSystem() {
        storage.values[SettingsRepository.KEY_THEME_MODE] = "sepia"

        assertEquals(ThemeMode.SYSTEM, SettingsRepository(storage, ReversingCipher).settings.value.themeMode)
    }

    @Test
    fun notificationPermission_isRequestedOnlyOnce() {
        assertTrue(repository.takeNotificationPermissionRequest())
        assertFalse(repository.takeNotificationPermissionRequest())
        assertFalse(SettingsRepository(storage, ReversingCipher).takeNotificationPermissionRequest())
    }

    @Test
    fun swipeHintSeen_isPersisted() {
        assertFalse(repository.settings.value.swipeHintSeen)

        repository.setSwipeHintSeen()

        assertTrue(repository.settings.value.swipeHintSeen)
        assertTrue(SettingsRepository(storage, ReversingCipher).settings.value.swipeHintSeen)
    }

    @Test
    fun claudeCostsAccepted_isPersistedAndSurvivesDeletingTheKey() {
        assertFalse(repository.settings.value.claudeCostsAccepted)
        repository.setApiKey("sk-ant-api03-abcdefghijklmnop")

        repository.acceptClaudeCosts()
        repository.deleteApiKey()

        assertTrue(repository.settings.value.claudeCostsAccepted)
        assertTrue(SettingsRepository(storage, ReversingCipher).settings.value.claudeCostsAccepted)
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
        assertEquals("sonnet", storage.values[SettingsRepository.KEY_SCAN_MODEL])
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
