package com.example.plantry.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun migrate9To10_backfillsSnapshotsFromTheRecipes() {
        helper.createDatabase(DB_NAME, 9).use { db ->
            db.execSQL(
                "INSERT INTO recipes (id, title, source, page, bookServings, ourServings, cookingTimeMinutes, modified) " +
                    "VALUES (1, 'Dal', '', NULL, 4, 2, 30, 0), (2, 'Leer', '', NULL, 2, 2, NULL, 0)",
            )
            // Reis, gekocht (3) is bought as Reis, trocken (4): one plant together.
            db.execSQL(ingredientSql(1, "Linsen", "ONE", protein = 24.0, carbs = 50.0))
            db.execSQL(ingredientSql(2, "Knoblauch", "QUARTER", protein = 6.0, carbs = 33.0))
            db.execSQL(ingredientSql(4, "Reis, trocken", "ONE", protein = 7.0, carbs = 80.0))
            db.execSQL(ingredientSql(3, "Reis, gekocht", "ZERO", protein = 2.7, carbs = 28.0, buyAs = 4))
            db.execSQL(
                "INSERT INTO recipe_ingredients (id, recipeId, position, originalText, grams, ingredientId) VALUES " +
                    "(1, 1, 0, 'Linsen', 200, 1), (2, 1, 1, 'Knoblauch', 10, 2), " +
                    "(3, 1, 2, 'Reis', 150, 3), (4, 1, 3, 'Reis', 50, 4), (5, 1, 4, 'Linsen', 100, 1)",
            )
            db.execSQL("INSERT INTO cook_log (id, recipeId, cookedOn) VALUES (1, 1, 20729), (2, 2, 20730), (3, 1, 20700)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 10, true, PlantryDatabase.MIGRATION_9_10).use { db ->
            db.query(
                "SELECT id, recipeId, cookedOn, title, plantPoints, proteinPerPortion, carbsPerPortion FROM cook_log ORDER BY id",
            ).use { c ->
                assertEquals(3, c.count)
                c.moveToFirst()
                assertEquals(listOf(1L, 1L, 20729L), listOf(c.getLong(0), c.getLong(1), c.getLong(2)))
                assertEquals("Dal", c.getString(3))
                assertEquals(2.25, c.getDouble(4), 1e-9)
                assertEquals((300 * 24.0 + 10 * 6.0 + 150 * 2.7 + 50 * 7.0) / 100 / 2, c.getDouble(5), 1e-9)
                assertEquals((300 * 50.0 + 10 * 33.0 + 150 * 28.0 + 50 * 80.0) / 100 / 2, c.getDouble(6), 1e-9)
                c.moveToNext()
                assertEquals("Leer", c.getString(3))
                assertEquals(listOf(0.0, 0.0, 0.0), listOf(c.getDouble(4), c.getDouble(5), c.getDouble(6)))
                c.moveToNext()
                assertEquals(listOf(3L, 20700L), listOf(c.getLong(0), c.getLong(2)))
                assertEquals("Dal", c.getString(3))
            }
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("DELETE FROM recipes WHERE id = 2")
            db.query("SELECT recipeId, title FROM cook_log WHERE id = 2").use { c ->
                c.moveToFirst()
                assertTrue(c.isNull(0))
                assertEquals("Leer", c.getString(1))
            }
        }
    }

    @Test
    fun migrate10To11_keepsTheRestAndAddsEmptyGeplant() {
        helper.createDatabase(DB_NAME, 10).use { db ->
            db.execSQL(
                "INSERT INTO recipes (id, title, source, page, bookServings, ourServings, cookingTimeMinutes, modified) " +
                    "VALUES (1, 'Dal', '', NULL, 2, 2, 30, 0)",
            )
            db.execSQL(
                "INSERT INTO cook_log (id, recipeId, cookedOn, title, plantPoints, proteinPerPortion, carbsPerPortion) " +
                    "VALUES (1, 1, 20729, 'Dal', 2.0, 20.0, 40.0)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 11, true).use { db ->
            db.query("SELECT title FROM cook_log").use { assertEquals("Dal", it.apply { moveToFirst() }.getString(0)) }
            db.query("SELECT COUNT(*) FROM planned_recipes").use { assertEquals(0, it.apply { moveToFirst() }.getInt(0)) }
        }
    }

    @Test
    fun migrate11To12_keepsCookLogWithoutKcalAndFibre() {
        helper.createDatabase(DB_NAME, 11).use { db ->
            db.execSQL(
                "INSERT INTO cook_log (id, recipeId, cookedOn, title, plantPoints, proteinPerPortion, carbsPerPortion) " +
                    "VALUES (1, NULL, 20729, 'Dal', 2.0, 20.0, 40.0)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 12, true).use { db ->
            db.query("SELECT title, proteinPerPortion, kcalPerPortion, fibrePerPortion FROM cook_log").use { c ->
                c.moveToFirst()
                assertEquals("Dal", c.getString(0))
                assertEquals(20.0, c.getDouble(1), 0.0)
                assertTrue(c.isNull(2))
                assertTrue(c.isNull(3))
            }
        }
    }

    @Test
    fun migrate12To13_dropsTheShoppingAttributesAndKeepsIngredientsLinesAndAliases() {
        helper.createDatabase(DB_NAME, 12).use { db ->
            db.execSQL(ingredientSql(4, "Zitrone", "ONE", protein = 1.1, carbs = 9.3))
            db.execSQL(ingredientSql(3, "Zitronensaft", "ZERO", protein = 0.4, carbs = 6.9, buyAs = 4))
            db.execSQL(
                "INSERT INTO recipes (id, title, source, page, bookServings, ourServings, cookingTimeMinutes, modified) " +
                    "VALUES (1, 'Dal', '', NULL, 2, 2, 30, 0)",
            )
            db.execSQL(
                "INSERT INTO recipe_ingredients (id, recipeId, position, originalText, grams, ingredientId) " +
                    "VALUES (1, 1, 0, 'Saft einer Zitrone', 40, 3)",
            )
            db.execSQL("INSERT INTO ingredient_aliases (wording, ingredientId) VALUES ('zitronensaft', 3)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 13, true).use { db ->
            db.query("SELECT id, name, protein, plantPoints, reviewed FROM ingredients ORDER BY id").use { c ->
                c.moveToFirst()
                assertEquals(3L, c.getLong(0))
                assertEquals("Zitronensaft", c.getString(1))
                assertEquals(0.4, c.getDouble(2), 0.0)
                assertEquals("ZERO", c.getString(3))
                assertEquals(1, c.getInt(4))
                assertEquals(2, c.count)
            }
            db.query("SELECT * FROM ingredients").use { c ->
                listOf("unitWeights", "buyUnit", "packSizeGrams", "staple", "buyAsIngredientId", "buyAsYieldFactor")
                    .forEach { assertEquals(it, -1, c.getColumnIndex(it)) }
            }
            db.query("SELECT ingredientId FROM recipe_ingredients").use { assertEquals(3L, it.apply { moveToFirst() }.getLong(0)) }
            db.query("SELECT ingredientId FROM ingredient_aliases").use { assertEquals(3L, it.apply { moveToFirst() }.getLong(0)) }
        }
    }

    private fun ingredientSql(id: Long, name: String, points: String, protein: Double, carbs: Double, buyAs: Long? = null) =
        "INSERT INTO ingredients (id, name, fdcId, usdaDescription, kcal, protein, carbs, sugar, fat, fibre, " +
            "unitWeights, buyUnit, packSizeGrams, storeSection, staple, plantPoints, buyAsIngredientId, " +
            "buyAsYieldFactor, reviewed) " +
            "VALUES ($id, '$name', NULL, NULL, 0, $protein, $carbs, 0, 0, 0, '[]', 'GRAMS', NULL, 'OTHER', 0, '$points', " +
            "${buyAs ?: "NULL"}, NULL, 1)"

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
