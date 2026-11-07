package com.example.plantry.data.backup

import com.example.plantry.data.Dish
import com.example.plantry.data.Ingredient
import com.example.plantry.data.LabelSource
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.Price
import com.example.plantry.data.Recipe
import com.example.plantry.data.StoreSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.time.LocalDate

/**
 * Every field of a [Recipe] and an [Ingredient] survives a backup: entity → file → JSON → file →
 * entity. A field missing from `toBackup` or `toEntity` comes back as its default and fails here.
 * The fixtures set every field to a non-default value, which [assertEveryFieldDiffers] checks, so a
 * new field with a default has to be added to them (and so to the backup) on purpose.
 */
class EntityRoundTripTest {

    private val recipe = Recipe(
        id = 7,
        title = "Linsen-Dal",
        source = "Kochbuch",
        page = 42,
        servings = 3,
        totalTimeMinutes = 45,
        modified = true,
        dish = Dish.SNACK,
        activeTimeMinutes = 15,
        steps = listOf("Linsen waschen.", "Alles 20 Minuten köcheln."),
        notes = "Mehr Zitrone",
    )

    private val nutrition = Nutrition(kcal = 116.0, protein = 9.0, carbs = 20.1, sugar = 1.8, fat = 0.4, fibre = 7.9)

    private val ingredient = Ingredient(
        id = 5,
        name = "Rote Linsen",
        fdcId = 172420,
        usdaDescription = "Lentils, mature seeds, cooked",
        nutrition = nutrition,
        storeSection = StoreSection.DRY_GOODS,
        plantPoints = PlantPoints.ONE,
        labelSource = LabelSource(labelProduct = "Bio Linsen", labelBarcode = "4000000000000"),
        price = Price(pricePerKg = 4.5, priceDate = LocalDate.of(2026, 10, 2)),
    )

    @Test
    fun fixtures_setEveryFieldToANonDefaultValue() {
        assertEveryFieldDiffers(recipe, Recipe(title = "", source = "", page = null, servings = 0, totalTimeMinutes = null))
        assertEveryFieldDiffers(
            ingredient,
            Ingredient(
                name = "", fdcId = null, usdaDescription = null, nutrition = Nutrition(),
                storeSection = StoreSection.OTHER, plantPoints = PlantPoints.ZERO,
            ),
        )
        assertEveryFieldDiffers(nutrition, Nutrition())
    }

    @Test
    fun backup_keepsEveryRecipeField() {
        assertEquals(recipe, roundTrip().recipes.single())
    }

    @Test
    fun backup_keepsEveryIngredientField() {
        assertEquals(ingredient, roundTrip().ingredients.single())
    }

    private fun roundTrip(): BackupSnapshot {
        val snapshot = BackupSnapshot(ingredients = listOf(ingredient), recipes = listOf(recipe), lines = emptyList(), cookLog = emptyList())
        val file = snapshot.toFile(photos = emptyMap(), settings = BackupSettings(scanModel = "HAIKU", cooldownDays = 7))
        return BackupFile.decode(BackupFile.encode(file)).toSnapshot()
    }

    /** Fails for each field of [value] that still has the value it has in [blank], an instance built from defaults. */
    private fun <T : Any> assertEveryFieldDiffers(value: T, blank: T) {
        val unset = value.javaClass.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .onEach { it.isAccessible = true }
            .filter { it.get(value) == it.get(blank) }
            .map { it.name }
        assertTrue("${value.javaClass.simpleName} fixture leaves fields at their default: $unset", unset.isEmpty())
    }
}
