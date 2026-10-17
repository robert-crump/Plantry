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

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
