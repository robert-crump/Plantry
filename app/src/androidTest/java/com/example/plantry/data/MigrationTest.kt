package com.example.plantry.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlantryDatabase::class.java)

    @Test
    fun migrate1To2_keepsIngredientsAndUnitsAndAddsNoComponents() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO ingredients (id, name, fdcId, usdaDescription, storeSection, plantPoints, kcal, protein, carbs, " +
                    "sugar, fat, fibre, labelProduct, labelBarcode) " +
                    "VALUES (1, 'Beerenmischung', NULL, NULL, 'FROZEN', 'ONE', 40, 1, 7, 6, 0.3, 4, 'TK Beeren', '4000000000001')",
            )
            db.execSQL("INSERT INTO ingredient_units (ingredientId, unit, gramsPerUnit, packageGrams, packageCount) VALUES (1, 'CUP', 140, NULL, NULL)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 2, true).use { db ->
            db.query("SELECT name, storeSection, plantPoints, labelProduct FROM ingredients").use {
                it.moveToFirst()
                assertEquals(listOf("Beerenmischung", "FROZEN", "ONE", "TK Beeren"), (0..3).map(it::getString))
            }
            db.query("SELECT gramsPerUnit FROM ingredient_units").use { assertEquals(140.0, it.apply { moveToFirst() }.getDouble(0), 0.0) }
            db.query("SELECT COUNT(*) FROM mix_components").use { assertEquals(0, it.apply { moveToFirst() }.getInt(0)) }
            // The new table works, with its foreign keys.
            db.execSQL("INSERT INTO ingredients (id, name, storeSection, plantPoints, kcal, protein, carbs, sugar, fat, fibre) VALUES (2, 'Himbeeren', 'FROZEN', 'ONE', 0, 0, 0, 0, 0, 0)")
            db.execSQL("INSERT INTO mix_components (mixId, componentId) VALUES (1, 2)")
            db.query("SELECT componentId FROM mix_components WHERE mixId = 1").use { assertEquals(2L, it.apply { moveToFirst() }.getLong(0)) }
        }
    }

    @Test
    fun migrate2To3_backfillsEachEntrysPlantsFromItsRecipeAsItIsNow() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            fun ingredient(id: Int, name: String, points: String) = db.execSQL(
                "INSERT INTO ingredients (id, name, storeSection, plantPoints, kcal, protein, carbs, sugar, fat, fibre) " +
                    "VALUES ($id, '$name', 'OTHER', '$points', 0, 0, 0, 0, 0, 0)",
            )
            ingredient(1, "Zwiebel", "ONE")
            ingredient(2, "Kreuzkümmel", "QUARTER")
            ingredient(3, "Salz", "ZERO")
            ingredient(4, "Himbeeren", "ONE")
            ingredient(5, "Erdbeeren", "ONE")
            // A mix of 4 and 5; its own point is ignored.
            ingredient(6, "Beerenmischung", "ONE")
            db.execSQL("INSERT INTO mix_components (mixId, componentId) VALUES (6, 4), (6, 5)")
            db.execSQL("INSERT INTO recipes (id, title, source, page, servings, cookingTimeMinutes, modified, dish) VALUES (1, 'Dal', '', NULL, 2, 30, 0, 'MAIN'), (2, 'Porridge', '', NULL, 1, 10, 0, 'BREAKFAST')")
            // Dal has Zwiebel twice, Porridge the mix and one of its berries.
            listOf(1 to 1, 1 to 2, 1 to 3, 1 to 1, 2 to 6, 2 to 4).forEachIndexed { i, (recipe, ingredient) ->
                db.execSQL("INSERT INTO recipe_ingredients (id, recipeId, position, amount, unit, ingredientId, wording) VALUES (${i + 1}, $recipe, $i, 100, 'G', $ingredient, NULL)")
            }
            db.execSQL(
                "INSERT INTO cook_log (id, recipeId, cookedOn, title, plantPoints, proteinPerPortion, carbsPerPortion, kcalPerPortion, fibrePerPortion) " +
                    "VALUES (1, 1, 20000, 'Dal', 1.25, 0, 0, NULL, NULL), (2, 2, 20001, 'Porridge', 2, 0, 0, NULL, NULL), (3, NULL, 20002, 'Gelöscht', 3, 0, 0, NULL, NULL)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 3, true).use { db ->
            val plants = mutableListOf<String>()
            db.query("SELECT cookLogId, ingredientId, name, plantPoints FROM cook_log_plants ORDER BY cookLogId, ingredientId").use {
                while (it.moveToNext()) plants += "${it.getLong(0)} ${it.getLong(1)} ${it.getString(2)} ${it.getDouble(3)}"
            }
            assertEquals(listOf("1 1 Zwiebel 1.0", "1 2 Kreuzkümmel 0.25", "2 4 Himbeeren 1.0", "2 5 Erdbeeren 1.0"), plants)
        }
    }

    @Test
    fun migrate3To4_keepsIngredientsWithoutAPrice() {
        helper.createDatabase(DB_NAME, 3).use { db ->
            db.execSQL(
                "INSERT INTO ingredients (id, name, storeSection, plantPoints, kcal, protein, carbs, sugar, fat, fibre) " +
                    "VALUES (1, 'Zucchini', 'PRODUCE', 'ONE', 17, 1.2, 3.1, 2.5, 0.3, 1)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 4, true).use { db ->
            db.query("SELECT name, pricePerKg, priceDate FROM ingredients").use {
                it.moveToFirst()
                assertEquals("Zucchini", it.getString(0))
                assertEquals(listOf(true, true), listOf(it.isNull(1), it.isNull(2)))
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
