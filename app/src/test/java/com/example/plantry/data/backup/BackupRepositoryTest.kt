package com.example.plantry.data.backup

import com.example.plantry.data.CookLog
import com.example.plantry.data.CookLogPlant
import com.example.plantry.data.Dish
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientAlias
import com.example.plantry.data.LabelSource
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.LineUnit
import com.example.plantry.data.MixComponent
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.Price
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
        photos.photos = mapOf(2L to listOf(byteArrayOf(1, 2, 3, -1), byteArrayOf(4)))
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
            photos.photos = mapOf(99L to listOf(byteArrayOf(9)))
        }

        val json = source.export()
        target.repository.import(target.repository.read(json))

        assertEquals(source.store.data, target.store.data)
        assertEquals(source.photos.contents(), target.photos.contents())
        assertEquals(ScanModel.SONNET, target.settings.settings.value.scanModel)
        assertEquals(14, target.settings.settings.value.cooldownDays)
        assertEquals(json, target.export())
    }

    @Test
    fun export_neverContainsApiKey_andEmbedsPhotosAsBase64() = runTest {
        val json = source.export()

        assertFalse(json.contains(apiKey))
        assertFalse(json.contains(apiKey.reversed()))
        assertTrue(json.replace(Regex("\\s+"), "").contains("\"photos\":[\"AQID/w==\",\"BA==\"]"))
        assertTrue(json.contains("\"photos\": []"))
        assertFalse(json.contains("\"photo\":"))
        assertTrue(json.contains("\"formatVersion\": 5"))
        assertTrue(json.contains("\"dish\": \"MAIN\""))
        assertFalse(json.contains("\"reviewed\""))
        assertFalse(json.contains("\"origin\""))
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
        val newer = source.export().replace("\"formatVersion\": 5,", "\"formatVersion\": 6,")

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
    fun components_areWrittenWithTheMix() = runTest {
        val ingredients = Json.parseToJsonElement(source.export()).jsonObject.getValue("ingredients").jsonArray.map { it.jsonObject }

        val contains = ingredients.associate { it.getValue("id").toString() to it.getValue("contains").jsonArray.map(Any::toString) }
        assertEquals(listOf("14", "15"), contains["13"])
        assertEquals(emptyList<String>(), contains["14"])
    }

    @Test
    fun read_importsAFormat1FileWithoutComponents() = runTest {
        val file = Json.parseToJsonElement(source.export()).jsonObject
        val ingredients = JsonArray(file.getValue("ingredients").jsonArray.map { JsonObject(it.jsonObject - "contains") })
        val format1 = JsonObject(file + ("formatVersion" to JsonPrimitive(1)) + ("ingredients" to ingredients)).toString()
        val target = Device()

        target.repository.import(target.repository.read(format1))

        assertEquals(source.store.data.copy(components = emptyList()), target.store.data)
    }

    @Test
    fun price_isWrittenPerKgWithItsDate_bothNullWithout() = runTest {
        val ingredients = Json.parseToJsonElement(source.export()).jsonObject.getValue("ingredients").jsonArray.map { it.jsonObject }

        val prices = ingredients.associate { it.getValue("id").toString() to (it.getValue("pricePerKg").toString() to it.getValue("priceDate").toString()) }
        assertEquals("2.49" to "\"2026-03-14\"", prices["5"])
        assertEquals("null" to "null", prices["1"])
    }

    @Test
    fun read_importsAFormat4FileWithoutPrices() = runTest {
        val file = Json.parseToJsonElement(source.export()).jsonObject
        val ingredients = JsonArray(file.getValue("ingredients").jsonArray.map { JsonObject(it.jsonObject - "pricePerKg" - "priceDate") })
        val format4 = JsonObject(file + ("formatVersion" to JsonPrimitive(4)) + ("ingredients" to ingredients)).toString()
        val target = Device()

        target.repository.import(target.repository.read(format4))

        assertEquals(source.store.data.copy(ingredients = source.store.data.ingredients.map { it.copy(price = null) }), target.store.data)
    }

    @Test
    fun cookLogPlants_areWrittenWithTheirEntry() = runTest {
        val cookLog = Json.parseToJsonElement(source.export()).jsonObject.getValue("cookLog").jsonArray.map { it.jsonObject }

        assertEquals(listOf(1, 0, 2), cookLog.map { it.getValue("plants").jsonArray.size })
        assertEquals(
            """{"ingredientId":null,"name":"Paprika","plantPoints":1.0}""",
            cookLog[2].getValue("plants").jsonArray.first().toString(),
        )
    }

    @Test
    fun read_takesTheCookLogPlantsOfAFormat3FileFromTheRecipes() = runTest {
        val file = Json.parseToJsonElement(source.export()).jsonObject
        val cookLog = JsonArray(file.getValue("cookLog").jsonArray.map { JsonObject(it.jsonObject - "plants") })
        val format3 = JsonObject(file + ("formatVersion" to JsonPrimitive(3)) + ("cookLog" to cookLog)).toString()
        val target = Device()

        target.repository.import(target.repository.read(format3))

        // Curry's plants as it is now; Chili has none worth points, the deleted recipe's entry none at all.
        val backfilled = listOf(CookLogPlant(cookLogId = 1, ingredientId = 3, name = "Süßkartoffel", plantPoints = 1.0))
        assertEquals(source.store.data.copy(cookLogPlants = backfilled), target.store.data)
    }

    @Test
    fun read_cookLogPlantOfAnIngredientNotInTheFile_readsAsDeleted() = runTest {
        val file = Json.parseToJsonElement(source.export()).jsonObject
        val text = file.toString().replace(""""ingredientId":14,"name":"Himbeeren"""", """"ingredientId":99,"name":"Himbeeren"""")
        val target = Device()

        target.repository.import(target.repository.read(text))

        assertEquals(null, target.store.data.cookLogPlants.single { it.name == "Himbeeren" }.ingredientId)
    }

    @Test
    fun read_takesTheOnePhotoOfAFormat2FileAsTheFirstPage() = runTest {
        val file = Json.parseToJsonElement(source.export()).jsonObject
        val recipes = JsonArray(
            file.getValue("recipes").jsonArray.map { recipe ->
                val pages = recipe.jsonObject.getValue("photos").jsonArray
                JsonObject(recipe.jsonObject - "photos" + ("photo" to (pages.firstOrNull() ?: JsonNull)))
            },
        )
        val format2 = JsonObject(file + ("formatVersion" to JsonPrimitive(2)) + ("recipes" to recipes)).toString()
        val target = Device()

        target.repository.import(target.repository.read(format2))

        assertEquals(source.store.data, target.store.data)
        assertEquals(mapOf(2L to listOf(listOf<Byte>(1, 2, 3, -1))), target.photos.contents())
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
    fun brokenReferences_areRejectedWithoutChanges() = runTest {
        val target = Device().apply { store.data = sampleData().copy(recipes = emptyList(), lines = emptyList()) }
        val before = target.store.data
        val json = source.export()
        val broken = listOf(
            // A line of an ingredient the file doesn't have.
            json.replace("\"ingredientId\": 3,", "\"ingredientId\": 42,"),
            // A line in a unit its ingredient lacks: Süßkartoffel has no package.
            json.replace("\"unit\": \"PIECE\",\n                    \"ingredientId\": 3", "\"unit\": \"PACKET\",\n                    \"ingredientId\": 3"),
            // A component the file doesn't have.
            json.replace("\n                14,", "\n                42,"),
            // A mix as a component: Himbeeren would contain the Beerenmischung.
            json.replace("\"contains\": []", "\"contains\": [13]"),
        )

        broken.forEach { text ->
            assertTrue(text != json)
            val error = runCatching { target.repository.import(target.repository.read(text)) }.exceptionOrNull()
            assertEquals(InvalidBackupException.Reason.BROKEN_REFERENCE, (error as InvalidBackupException).reason)
        }
        assertEquals(before, target.store.data)
    }

    @Test
    fun read_takesOurServingsFromABackupBeforeTheMerge() = runTest {
        val old = source.export().replace("\"servings\": 2,", "\"bookServings\": 4, \"ourServings\": 2,")

        assertTrue(old.contains("\"ourServings\": 2"))
        val target = Device()
        target.repository.import(target.repository.read(old))
        assertEquals(source.store.data.recipes, target.store.data.recipes)
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

    @Test
    fun unexportedChanges_sinceLastExport() = runTest {
        source.export()
        assertFalse(source.repository.hasUnexportedChanges())

        source.store.data = source.store.data.copy(planned = emptyList())
        assertTrue(source.repository.hasUnexportedChanges())

        source.export()
        assertFalse(source.repository.hasUnexportedChanges())

        // Settings are replaced by an import too.
        source.settings.setCooldownDays(20)
        assertTrue(source.repository.hasUnexportedChanges())
    }

    @Test
    fun unexportedChanges_noneAfterImport() = runTest {
        val target = Device()
        target.repository.import(target.repository.read(source.export()))

        assertFalse(target.repository.hasUnexportedChanges())
    }

    @Test
    fun unexportedChanges_noneWithoutBaseline() = runTest {
        assertFalse(source.repository.hasUnexportedChanges())
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
                    price = Price(2.49, LocalDate.of(2026, 3, 14)),
                ),
                ingredient(id = 6, name = "Kichererbsen (Dose)").copy(
                    labelSource = LabelSource("Bio Kichererbsen (Alnatura)", "4104420208117"),
                ),
                ingredient(id = 13, name = "Beerenmischung"),
                ingredient(id = 14, name = "Himbeeren").copy(plantPoints = PlantPoints.ONE),
                ingredient(id = 15, name = "Erdbeeren").copy(plantPoints = PlantPoints.ONE),
            ),
            recipes = listOf(
                Recipe(2, "Curry", "Kochbuch", 42, 2, 30, modified = true, activeTimeMinutes = 15),
                Recipe(7, "Chili", "", null, 2, totalTimeMinutes = null),
                Recipe(8, "Skyr mit Banane", "", null, 1, totalTimeMinutes = 2, dish = Dish.SNACK),
                Recipe(9, "Porridge", "Frühstücksbuch", 7, 1, totalTimeMinutes = 10, dish = Dish.BREAKFAST, activeTimeMinutes = 5),
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
            components = listOf(MixComponent(13, 14), MixComponent(13, 15)),
            // As logged: Süßkartoffel was ¼ then, Paprika was deleted since.
            cookLogPlants = listOf(
                CookLogPlant(cookLogId = 1, ingredientId = 3, name = "Süßkartoffel", plantPoints = 0.25),
                CookLogPlant(cookLogId = 3, ingredientId = null, name = "Paprika", plantPoints = 1.0),
                CookLogPlant(cookLogId = 3, ingredientId = 14, name = "Himbeeren", plantPoints = 1.0),
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

    override fun list(recipeId: Long) = photos[recipeId].orEmpty()

    override fun add(recipeId: Long, bytes: ByteArray) {
        photos = photos + (recipeId to list(recipeId) + bytes)
    }

    override fun delete(recipeId: Long, index: Int) {
        photos = photos + (recipeId to list(recipeId).filterIndexed { i, _ -> i != index })
    }

    override fun deleteAll(recipeId: Long) {
        photos = photos - recipeId
    }

    /** Comparable by value, unlike the arrays. */
    fun contents() = photos.mapValues { (_, pages) -> pages.map { it.toList() } }
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
