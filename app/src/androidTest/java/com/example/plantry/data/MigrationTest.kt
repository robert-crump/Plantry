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

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
