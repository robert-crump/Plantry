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
    fun migrate4To5_keepsRecipesAndAddsEmptyWeekPlan() {
        helper.createDatabase(DB_NAME, 4).use { db ->
            db.execSQL(
                "INSERT INTO recipes (id, title, source, page, bookServings, ourServings, cookingTimeMinutes, modified) " +
                    "VALUES (1, 'Dal', '', NULL, 2, 2, 30, 0)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 5, true).use { db ->
            db.query("SELECT title FROM recipes").use { assertEquals("Dal", it.apply { moveToFirst() }.getString(0)) }
            db.query("SELECT COUNT(*) FROM week_plan_slots").use { assertEquals(0, it.apply { moveToFirst() }.getInt(0)) }
        }
    }

    @Test
    fun migrate5To6_keepsWeekPlanAndAddsNoTicks() {
        helper.createDatabase(DB_NAME, 5).use { db ->
            db.execSQL(
                "INSERT INTO recipes (id, title, source, page, bookServings, ourServings, cookingTimeMinutes, modified) " +
                    "VALUES (1, 'Dal', '', NULL, 2, 2, 30, 0)",
            )
            db.execSQL("INSERT INTO week_plan_slots (weekStart, position, recipeId, done) VALUES (20729, 0, 1, 0)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 6, true).use { db ->
            db.query("SELECT COUNT(*) FROM week_plan_slots").use { assertEquals(1, it.apply { moveToFirst() }.getInt(0)) }
            db.query("SELECT COUNT(*) FROM shopping_ticks").use { assertEquals(0, it.apply { moveToFirst() }.getInt(0)) }
        }
    }

    @Test
    fun migrate6To7_keepsIngredientsAndAddsNoAliases() {
        helper.createDatabase(DB_NAME, 6).use { db ->
            db.execSQL(
                "INSERT INTO ingredients (id, name, fdcId, usdaDescription, kcal, protein, carbs, sugar, fat, fibre, " +
                    "unitWeights, buyUnit, packSizeGrams, storeSection, staple, plantPoints, buyAsIngredientId, " +
                    "buyAsYieldFactor, reviewed) " +
                    "VALUES (1, 'Reis', NULL, NULL, 0, 0, 0, 0, 0, 0, '[]', 'GRAMS', NULL, 'OTHER', 0, 'ZERO', NULL, NULL, 1)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 7, true).use { db ->
            db.query("SELECT name FROM ingredients").use { assertEquals("Reis", it.apply { moveToFirst() }.getString(0)) }
            db.query("SELECT COUNT(*) FROM ingredient_aliases").use { assertEquals(0, it.apply { moveToFirst() }.getInt(0)) }
        }
    }

    @Test
    fun migrate7To8_keepsRecipesAndLinesAndAllowsNoCookingTime() {
        helper.createDatabase(DB_NAME, 7).use { db ->
            db.execSQL(
                "INSERT INTO recipes (id, title, source, page, bookServings, ourServings, cookingTimeMinutes, modified) " +
                    "VALUES (1, 'Dal', '', NULL, 2, 2, 30, 0)",
            )
            db.execSQL(
                "INSERT INTO ingredients (id, name, fdcId, usdaDescription, kcal, protein, carbs, sugar, fat, fibre, " +
                    "unitWeights, buyUnit, packSizeGrams, storeSection, staple, plantPoints, buyAsIngredientId, " +
                    "buyAsYieldFactor, reviewed) " +
                    "VALUES (1, 'Linsen', NULL, NULL, 0, 0, 0, 0, 0, 0, '[]', 'GRAMS', NULL, 'OTHER', 0, 'ONE', NULL, NULL, 1)",
            )
            db.execSQL(
                "INSERT INTO recipe_ingredients (id, recipeId, position, originalText, grams, ingredientId) " +
                    "VALUES (1, 1, 0, '200 g Linsen', 200, 1)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 8, true).use { db ->
            db.query("SELECT cookingTimeMinutes FROM recipes").use { assertEquals(30, it.apply { moveToFirst() }.getInt(0)) }
            db.query("SELECT COUNT(*) FROM recipe_ingredients").use { assertEquals(1, it.apply { moveToFirst() }.getInt(0)) }
            db.execSQL(
                "INSERT INTO recipes (id, title, source, page, bookServings, ourServings, cookingTimeMinutes, modified) " +
                    "VALUES (2, 'Quesadillas', '', NULL, 2, 2, NULL, 0)",
            )
        }
    }

    @Test
    fun migrate8To9_dropsWeekPlanAndShoppingTicksAndKeepsTheRest() {
        helper.createDatabase(DB_NAME, 8).use { db ->
            db.execSQL(
                "INSERT INTO recipes (id, title, source, page, bookServings, ourServings, cookingTimeMinutes, modified) " +
                    "VALUES (1, 'Dal', '', NULL, 2, 2, 30, 0)",
            )
            db.execSQL(
                "INSERT INTO ingredients (id, name, fdcId, usdaDescription, kcal, protein, carbs, sugar, fat, fibre, " +
                    "unitWeights, buyUnit, packSizeGrams, storeSection, staple, plantPoints, buyAsIngredientId, " +
                    "buyAsYieldFactor, reviewed) " +
                    "VALUES (1, 'Linsen', NULL, NULL, 0, 0, 0, 0, 0, 0, '[]', 'GRAMS', NULL, 'DRY_GOODS', 0, 'ONE', NULL, NULL, 1)",
            )
            db.execSQL("INSERT INTO cook_log (id, recipeId, cookedOn) VALUES (1, 1, 20729)")
            db.execSQL("INSERT INTO week_plan_slots (weekStart, position, recipeId, done) VALUES (20727, 0, 1, 1)")
            db.execSQL("INSERT INTO shopping_ticks (weekStart, ingredientId) VALUES (20727, 1)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 9, true).use { db ->
            db.query("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('week_plan_slots', 'shopping_ticks')").use {
                assertEquals(0, it.apply { moveToFirst() }.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM cook_log").use { assertEquals(1, it.apply { moveToFirst() }.getInt(0)) }
            db.query("SELECT storeSection FROM ingredients").use {
                assertEquals("DRY_GOODS", it.apply { moveToFirst() }.getString(0))
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
