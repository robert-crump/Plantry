package com.example.plantry.data.backup

import com.example.plantry.data.BuyUnit
import com.example.plantry.data.CookLog
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientAlias
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.RecipeStats
import com.example.plantry.data.StoreSection
import com.example.plantry.data.UnitWeight
import com.example.plantry.data.settings.ScanModel
import com.example.plantry.data.settings.SecretCipher
import com.example.plantry.data.settings.SettingsRepository
import com.example.plantry.data.settings.SettingsStorage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
        assertTrue(json.contains("\"formatVersion\": 5"))
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
        val newer = source.export().replace("\"formatVersion\": 5", "\"formatVersion\": 6")

        val error = readError(newer)

        assertEquals(InvalidBackupException.Reason.NEWER_VERSION, error.reason)
    }

    @Test
    fun version1File_importsWithoutAliases() = runTest {
        val v2 = Json.parseToJsonElement(source.export()).jsonObject
        val v1 = JsonObject(v2 - "aliases" + ("formatVersion" to JsonPrimitive(1))).toString()
        val target = Device()

        target.repository.import(target.repository.read(v1))

        assertEquals(source.store.data.copy(aliases = emptyList()), target.store.data)
    }

    @Test
    fun version3File_importsAndSkipsWeekPlanAndShoppingTicks() = runTest {
        val v4 = Json.parseToJsonElement(source.export()).jsonObject
        val weekPlan = Json.parseToJsonElement("""[{"weekStart": "2026-09-26", "position": 0, "recipeId": 2, "done": true}]""")
        val ticks = Json.parseToJsonElement("""[{"weekStart": "2026-09-26", "ingredientId": 5}]""")
        val v3 = JsonObject(
            v4 + ("formatVersion" to JsonPrimitive(3)) + ("weekPlan" to weekPlan) + ("shoppingTicks" to ticks),
        ).toString()
        val target = Device()

        target.repository.import(target.repository.read(v3))

        assertEquals(source.store.data, target.store.data)
    }

    @Test
    fun version4File_computesSnapshotsFromTheRecipes() = runTest {
        val v5 = Json.parseToJsonElement(source.export()).jsonObject
        val v4CookLog = JsonArray(
            v5.getValue("cookLog").jsonArray
                .map { it.jsonObject }
                .filter { it["recipeId"] != JsonNull }
                .map { JsonObject(it - listOf("title", "plantPoints", "proteinPerPortion", "carbsPerPortion")) },
        )
        val v4 = JsonObject(v5 + ("formatVersion" to JsonPrimitive(4)) + ("cookLog" to v4CookLog)).toString()
        val target = Device()

        target.repository.import(target.repository.read(v4))

        val cookLog = target.store.data.cookLog
        assertEquals(listOf("Curry", "Chili"), cookLog.map { it.title })
        // Curry for 2: 480.5 g at 2.7 g protein and 28.2 g carbs per 100 g; Süßkartoffel 1 point,
        // Reis, gekocht is bought as Reis, trocken with 0.
        assertStats(RecipeStats(1.0, 480.5 * 0.027 / 2, 480.5 * 0.282 / 2), cookLog[0].stats)
        assertStats(RecipeStats(0.0, 75 * 0.027 / 2, 75 * 0.282 / 2), cookLog[1].stats)
    }

    private fun assertStats(expected: RecipeStats, actual: RecipeStats) {
        assertEquals(expected.plantPoints, actual.plantPoints, 1e-9)
        assertEquals(expected.proteinPerPortion, actual.proteinPerPortion, 1e-9)
        assertEquals(expected.carbsPerPortion, actual.carbsPerPortion, 1e-9)
    }

    @Test
    fun read_rejectsVersion4EntryWithoutRecipe() = runTest {
        val v5 = Json.parseToJsonElement(source.export()).jsonObject
        val orphan = Json.parseToJsonElement("""[{"id": 1, "recipeId": 99, "cookedOn": "2026-09-30"}]""")
        val v4 = JsonObject(v5 + ("formatVersion" to JsonPrimitive(4)) + ("cookLog" to orphan)).toString()

        assertEquals(InvalidBackupException.Reason.NOT_A_BACKUP, readError(v4).reason)
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
                Recipe(7, "Chili", "", null, 2, 2, cookingTimeMinutes = null),
            ),
            lines = listOf(
                RecipeIngredient(10, 2, 0, "300 g Süßkartoffel", 300.0, 3),
                RecipeIngredient(11, 2, 1, "1 Tasse Reis", 180.5, 1),
                RecipeIngredient(4, 7, 0, "Reis", 75.0, 5),
            ),
            // Snapshots differ from the recipes' current values, as after an edit; they must survive as they are.
            cookLog = listOf(
                CookLog(1, 2, LocalDate.of(2026, 9, 30), "Curry", RecipeStats(1.0, 6.5, 67.75)),
                CookLog(2, 7, LocalDate.of(2025, 12, 31), "Chili alt", RecipeStats(0.25, 20.0, 40.0)),
                CookLog(3, recipeId = null, LocalDate.of(2025, 11, 1), "Gelöscht", RecipeStats(3.0, 31.0, 55.0)),
            ),
            aliases = listOf(IngredientAlias("reis basmati", 5), IngredientAlias("süßkartoffeln geschält", 3)),
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
    var data = BackupSnapshot(emptyList(), emptyList(), emptyList(), emptyList())

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

    override fun get(recipeId: Long) = photos[recipeId]

    override fun put(recipeId: Long, bytes: ByteArray) {
        photos = photos + (recipeId to bytes)
    }

    override fun delete(recipeId: Long) {
        photos = photos - recipeId
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
