package com.example.plantry.data.backup

import com.example.plantry.data.CookLog
import com.example.plantry.data.Dish
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientOrigin
import com.example.plantry.data.IngredientAlias
import com.example.plantry.data.LabelSource
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.LineUnit
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.RecipeStats
import com.example.plantry.data.StoreSection
import com.example.plantry.data.settings.ScanModel
import com.example.plantry.data.settings.SecretCipher
import com.example.plantry.data.settings.SettingsRepository
import com.example.plantry.data.settings.SettingsStorage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
        assertTrue(json.contains("\"formatVersion\": 1"))
        assertTrue(json.contains("\"dish\": \"MAIN\""))
        assertTrue(json.contains("\"origin\": \"SEED\""))
        assertFalse(json.contains("\"staple\""))
        assertFalse(json.contains("\"buyAsIngredientId\""))
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
        val newer = source.export().replace("\"formatVersion\": 1,", "\"formatVersion\": 2,")

        val error = readError(newer)

        assertEquals(InvalidBackupException.Reason.NEWER_VERSION, error.reason)
    }

    @Test
    fun read_rejectsTheFormatBeforeTheRestart() = runTest {
        val file = Json.parseToJsonElement(source.export()).jsonObject
        val oldIngredients = JsonArray(file.getValue("ingredients").jsonArray.map { JsonObject(it.jsonObject - "units") })
        val old = JsonObject(file + ("formatVersion" to JsonPrimitive(15)) + ("ingredients" to oldIngredients)).toString()

        assertEquals(InvalidBackupException.Reason.OLD_FORMAT, readError(old).reason)
    }

    @Test
    fun units_areWrittenAsEntered_linesWithoutGrams() = runTest {
        val json = source.export()

        assertTrue(json.contains("\"unit\": \"PACKET\""))
        assertTrue(json.contains("\"gramsPerUnit\": 125.0"))
        assertTrue(json.contains("\"packageGrams\": 500.0"))
        assertTrue(json.contains("\"packageCount\": 4"))
        assertTrue(json.contains("\"unit\": \"ML\""))
        assertTrue(json.contains("\"wording\": \"1 Tasse Reis\""))
        assertFalse(json.contains("\"grams\""))
    }

    @Test
    fun origin_survivesExportAndImport() = runTest {
        val added = source.store.data.ingredients.map { it.copy(origin = IngredientOrigin.BARCODE) }
        source.store.data = source.store.data.copy(ingredients = added)
        val target = Device()

        target.repository.import(target.repository.read(source.export()))

        assertTrue(target.store.data.ingredients.all { it.origin == IngredientOrigin.BARCODE })
    }

    @Test
    fun brokenReferences_areRejectedWithoutChanges() = runTest {
        val target = Device().apply { store.data = sampleData().copy(recipes = emptyList(), lines = emptyList()) }
        val before = target.store.data
        val json = source.export()
        val broken = listOf(
            // A line of an ingredient the file doesn't have.
            json.replace("\"ingredientId\": 3,", "\"ingredientId\": 42,"),
            // A line in a unit its ingredient lacks: Süßkartoffel has no package.
            json.replace("\"unit\": \"PIECE\",\n                    \"ingredientId\": 3", "\"unit\": \"PACKET\",\n                    \"ingredientId\": 3"),
        )

        broken.forEach { text ->
            assertTrue(text != json)
            val error = runCatching { target.repository.import(target.repository.read(text)) }.exceptionOrNull()
            assertEquals(InvalidBackupException.Reason.BROKEN_REFERENCE, (error as InvalidBackupException).reason)
        }
        assertEquals(before, target.store.data)
    }

    @Test
    fun read_rejectsAnUnknownUnit() = runTest {
        val unknown = source.export().replace("\"unit\": \"ML\"", "\"unit\": \"PINCH\"")

        assertEquals(InvalidBackupException.Reason.NOT_A_BACKUP, readError(unknown).reason)
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
                ingredient(id = 1, name = "Reis, gekocht"),
                ingredient(id = 3, name = "Süßkartoffel").copy(
                    fdcId = 168482,
                    usdaDescription = "Sweet potato, raw",
                    plantPoints = PlantPoints.ONE,
                ),
                ingredient(id = 5, name = "Reis, trocken").copy(
                    storeSection = StoreSection.DRY_GOODS,
                    reviewed = false,
                ),
                ingredient(id = 6, name = "Kichererbsen (Dose)").copy(
                    labelSource = LabelSource("Bio Kichererbsen (Alnatura)", "4104420208117"),
                ),
            ),
            recipes = listOf(
                Recipe(2, "Curry", "Kochbuch", 42, 4, 2, 30, modified = true, activeTimeMinutes = 15),
                Recipe(7, "Chili", "", null, 2, 2, totalTimeMinutes = null),
                Recipe(8, "Skyr mit Banane", "", null, 1, 1, totalTimeMinutes = 2, dish = Dish.SNACK),
            ),
            lines = listOf(
                RecipeIngredient(10, 2, 0, 1.5, LineUnit.Of(PieceUnit.PIECE), 3),
                RecipeIngredient(11, 2, 1, 180.5, LineUnit.Grams, 1, wording = "1 Tasse Reis"),
                RecipeIngredient(12, 2, 2, 100.0, LineUnit.Milliliters, 6),
                RecipeIngredient(4, 7, 0, 0.5, LineUnit.Of(PieceUnit.PACKET), 5),
            ),
            // Older entries have no kcal and fibre. Snapshots differ from the recipes' current values, as after an edit; they must survive as they are.
            cookLog = listOf(
                CookLog(1, 2, LocalDate.of(2026, 9, 30), "Curry", RecipeStats(1.0, 6.5, 67.75, 412.25, 3.5)),
                CookLog(2, 7, LocalDate.of(2025, 12, 31), "Chili alt", RecipeStats(0.25, 20.0, 40.0)),
                CookLog(3, recipeId = null, LocalDate.of(2025, 11, 1), "Gelöscht", RecipeStats(3.0, 31.0, 55.0)),
            ),
            aliases = listOf(IngredientAlias("reis basmati", 5), IngredientAlias("süßkartoffeln geschält", 3)),
            planned = listOf(PlannedRecipe(2, LocalDate.of(2026, 9, 29)), PlannedRecipe(7, LocalDate.of(2026, 10, 2))),
            units = listOf(
                IngredientUnit(3, PieceUnit.PIECE, 250.0),
                IngredientUnit(5, PieceUnit.PACKET, 125.0, packageGrams = 500.0, packageCount = 4),
                IngredientUnit(6, PieceUnit.CAN, 240.0),
            ),
        )
    }

    private fun ingredient(id: Long, name: String) = Ingredient(
        id = id,
        name = name,
        fdcId = null,
        usdaDescription = null,
        nutrition = Nutrition(kcal = 130.5, protein = 2.7, carbs = 28.2, sugar = 0.1, fat = 0.3, fibre = 0.4),
        storeSection = StoreSection.OTHER,
        plantPoints = PlantPoints.ZERO,
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

/** Also used by [EmulatorSeederTest]. */
internal class MapStorage : SettingsStorage {
    val values = mutableMapOf<String, String>()
    override fun getString(key: String) = values[key]
    override fun putString(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

private object ReversingCipher : SecretCipher {
    override fun encrypt(plaintext: String) = plaintext.reversed()
    override fun decrypt(ciphertext: String) = ciphertext.reversed()
}
