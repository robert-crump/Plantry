package com.example.plantry.data.backup

import com.example.plantry.data.BuyUnit
import com.example.plantry.data.CookLog
import com.example.plantry.data.Ingredient
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.ShoppingTick
import com.example.plantry.data.StoreSection
import com.example.plantry.data.UnitWeight
import com.example.plantry.data.WeekPlanSlot
import com.example.plantry.data.settings.ScanModel
import com.example.plantry.data.settings.SecretCipher
import com.example.plantry.data.settings.SettingsRepository
import com.example.plantry.data.settings.SettingsStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class BackupRepositoryTest {

    private var today = LocalDate.of(2026, 10, 2)
    private val apiKey = "sk-ant-api03-secretSECRETsecret1234"

    private val source = Device().apply {
        store.data = sampleData()
        photos.photos = mapOf(2L to byteArrayOf(1, 2, 3, -1))
        settings.setApiKey(apiKey)
        settings.setScanModel(ScanModel.SONNET)
        settings.setCooldownDays(14)
    }

    @Test
    fun exportThenImport_isLossless() = runTest {
        val target = Device().apply {
            store.data = BackupSnapshot(
                ingredients = listOf(ingredient(id = 99, name = "Alt")),
                recipes = emptyList(), lines = emptyList(), cookLog = emptyList(),
                weekPlan = emptyList(), shoppingTicks = emptyList(),
            )
            photos.photos = mapOf(99L to byteArrayOf(9))
        }

        val json = source.export()
        target.repository.import(target.repository.read(json))

        assertEquals(source.store.data, target.store.data)
        assertEquals(source.photos.photos.mapValues { it.value.toList() }, target.photos.photos.mapValues { it.value.toList() })
        assertEquals(ScanModel.SONNET, target.settings.settings.value.scanModel)
        assertEquals(14, target.settings.settings.value.cooldownDays)
        assertEquals(json, target.export())
    }

    @Test
    fun export_neverContainsApiKey_andEmbedsPhotoAsBase64() = runTest {
        val json = source.export()

        assertFalse(json.contains(apiKey))
        assertFalse(json.contains(apiKey.reversed()))
        assertTrue(json.contains("\"photo\": \"AQID/w==\""))
        assertTrue(json.contains("\"formatVersion\": 1"))
    }

    @Test
    fun import_keepsTheApiKeyOfThisDevice() = runTest {
        val target = Device().apply { settings.setApiKey("sk-ant-api03-otherOTHERother9876") }

        target.repository.import(target.repository.read(source.export()))

        assertEquals("sk-ant-api03-otherOTHERother9876", target.settings.apiKey())
    }

    @Test
    fun fileName_getsSuffixForFurtherExportsOnTheSameDay() = runTest {
        assertEquals("261002-plantry.json", source.repository.nextFileName())
        source.export()
        assertEquals("261002-plantry-2.json", source.repository.nextFileName())
        source.export()
        assertEquals("261002-plantry-3.json", source.repository.nextFileName())

        today = today.plusDays(1)

        assertEquals("261003-plantry.json", source.repository.nextFileName())
    }

    @Test
    fun failedWrite_doesNotCountAsExport() = runTest {
        runCatching { source.repository.export { throw IOException("disk full") } }

        assertEquals("261002-plantry.json", source.repository.nextFileName())
    }

    @Test
    fun read_rejectsNewerFormatVersion() = runTest {
        val newer = source.export().replace("\"formatVersion\": 1", "\"formatVersion\": 2")

        val error = readError(newer)

        assertEquals(InvalidBackupException.Reason.NEWER_VERSION, error.reason)
    }

    @Test
    fun read_rejectsOtherFiles() = runTest {
        listOf("", "not json", "[1, 2]", "{\"recipes\": []}", "{\"formatVersion\": 1}").forEach { text ->
            val error = readError(text)
            assertEquals(text, InvalidBackupException.Reason.NOT_A_BACKUP, error.reason)
        }
    }

    @Test
    fun read_rejectsBrokenDates() = runTest {
        val broken = source.export().replace("\"2026-09-30\"", "\"30.09.2026\"")

        assertEquals(InvalidBackupException.Reason.NOT_A_BACKUP, readError(broken).reason)
    }

    @Test
    fun reminder_onlyAfter30DaysWithChanges() = runTest {
        // The first check sets the baseline.
        source.repository.refreshReminder()
        assertFalse(source.repository.reminder.value)

        today = today.plusDays(31)
        source.repository.refreshReminder()
        assertFalse("unchanged data", source.repository.reminder.value)

        source.settings.setCooldownDays(20)
        source.repository.refreshReminder()
        assertTrue(source.repository.reminder.value)

        source.export()
        assertFalse(source.repository.reminder.value)
    }

    @Test
    fun reminder_notWithin30Days() = runTest {
        source.repository.refreshReminder()
        source.store.data = source.store.data.copy(recipes = emptyList())

        today = today.plusDays(30)
        source.repository.refreshReminder()

        assertFalse(source.repository.reminder.value)
    }

    @Test
    fun import_resetsReminderBaseline() = runTest {
        val target = Device()
        target.repository.refreshReminder()
        today = today.plusDays(40)

        target.repository.import(target.repository.read(source.export()))
        today = today.plusDays(40)
        target.repository.refreshReminder()

        assertFalse(target.repository.reminder.value)
    }

    private suspend fun readError(text: String): InvalidBackupException = try {
        source.repository.read(text)
        throw AssertionError("accepted: $text")
    } catch (e: InvalidBackupException) {
        e
    }

    private inner class Device {
        val store = FakeBackupStore()
        val photos = FakePhotoStore()
        val settings = SettingsRepository(MapStorage(), ReversingCipher)
        val repository = BackupRepository(store, photos, settings, MapStorage(), today = { today })

        suspend fun export(): String {
            var written = ""
            repository.export { written = it }
            return written
        }
    }

    private fun sampleData(): BackupSnapshot {
        val saturday = LocalDate.of(2026, 9, 26)
        return BackupSnapshot(
            ingredients = listOf(
                // Linked to an ingredient with a higher id, which must survive the import order.
                ingredient(id = 1, name = "Reis, gekocht", buyAsIngredientId = 5, buyAsYieldFactor = 0.4),
                ingredient(id = 3, name = "Süßkartoffel").copy(
                    fdcId = 168482,
                    usdaDescription = "Sweet potato, raw",
                    unitWeights = listOf(UnitWeight("mittel", 130.0), UnitWeight("1 cup", 133.0)),
                    buyUnit = BuyUnit.PIECES,
                    plantPoints = PlantPoints.ONE,
                ),
                ingredient(id = 5, name = "Reis, trocken").copy(
                    buyUnit = BuyUnit.PACK,
                    packSizeGrams = 500.0,
                    storeSection = StoreSection.DRY_GOODS,
                    staple = true,
                    reviewed = false,
                ),
            ),
            recipes = listOf(
                Recipe(2, "Curry", "Kochbuch", 42, 4, 2, 30, modified = true),
                Recipe(7, "Chili", "", null, 2, 2, 45),
            ),
            lines = listOf(
                RecipeIngredient(10, 2, 0, "300 g Süßkartoffel", 300.0, 3),
                RecipeIngredient(11, 2, 1, "1 Tasse Reis", 180.5, 1),
                RecipeIngredient(4, 7, 0, "Reis", 75.0, 5),
            ),
            cookLog = listOf(CookLog(1, 2, LocalDate.of(2026, 9, 30)), CookLog(2, 7, LocalDate.of(2025, 12, 31))),
            weekPlan = listOf(WeekPlanSlot(saturday, 0, 2, done = true), WeekPlanSlot(saturday, 3, 7)),
            shoppingTicks = listOf(ShoppingTick(saturday, 5)),
        )
    }

    private fun ingredient(
        id: Long,
        name: String,
        buyAsIngredientId: Long? = null,
        buyAsYieldFactor: Double? = null,
    ) = Ingredient(
        id = id,
        name = name,
        fdcId = null,
        usdaDescription = null,
        nutrition = Nutrition(kcal = 130.5, protein = 2.7, carbs = 28.2, sugar = 0.1, fat = 0.3, fibre = 0.4),
        unitWeights = emptyList(),
        buyUnit = BuyUnit.GRAMS,
        packSizeGrams = null,
        storeSection = StoreSection.OTHER,
        staple = false,
        plantPoints = PlantPoints.ZERO,
        buyAsIngredientId = buyAsIngredientId,
        buyAsYieldFactor = buyAsYieldFactor,
        reviewed = true,
    )
}

private class FakeBackupStore : BackupStore {
    var data = BackupSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    override suspend fun snapshot() = data

    override suspend fun replaceAll(snapshot: BackupSnapshot) {
        data = snapshot
    }
}

private class FakePhotoStore : PhotoStore {
    var photos: Photos = emptyMap()

    override fun all() = photos

    override fun replaceAll(photos: Photos) {
        this.photos = photos
    }
}

private class MapStorage : SettingsStorage {
    val values = mutableMapOf<String, String>()
    override fun getString(key: String) = values[key]
    override fun putString(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

private object ReversingCipher : SecretCipher {
    override fun encrypt(plaintext: String) = plaintext.reversed()
    override fun decrypt(ciphertext: String) = ciphertext.reversed()
}
