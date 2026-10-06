package com.example.plantry.data.backup

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.plantry.data.CookLog
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientAlias
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.PlantryDatabase
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.RecipeStats
import com.example.plantry.data.StoreSection
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BackupDaoTest {

    private lateinit var db: PlantryDatabase
    private lateinit var dao: BackupDao


    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PlantryDatabase::class.java,
        ).build()
        dao = db.backupDao()
    }

    @After
    fun tearDown() = db.close()

    private fun ingredient(id: Long, name: String) = Ingredient(
        id = id,
        name = name,
        fdcId = null,
        usdaDescription = null,
        nutrition = Nutrition(kcal = 130.0, protein = 2.7),
        storeSection = StoreSection.OTHER,
        plantPoints = PlantPoints.ONE,
        reviewed = true,
    )

    private val snapshot = BackupSnapshot(
        ingredients = listOf(ingredient(1, "Reis, gekocht"), ingredient(5, "Reis, trocken")),
        recipes = listOf(Recipe(3, "Curry", "Buch", 12, 4, 2, 30, modified = true)),
        lines = listOf(RecipeIngredient(8, 3, 0, "1 Tasse Reis", 180.0, 1), RecipeIngredient(9, 3, 1, "Reis", 50.0, 5)),
        cookLog = listOf(
            CookLog(4, 3, LocalDate.of(2026, 9, 30), "Curry", RecipeStats(1.0, 20.0, 40.0)),
            // Its recipe was deleted.
            CookLog(6, null, LocalDate.of(2026, 9, 1), "Weg", RecipeStats(2.5, 30.0, 50.0)),
        ),
        aliases = listOf(IngredientAlias("basmati", 5), IngredientAlias("reis gegart", 1)),
        planned = listOf(PlannedRecipe(3, LocalDate.of(2026, 10, 1))),
    )

    @Test
    fun replaceAll_restoresEverything_withIds() = runTest {
        db.ingredientDao().insert(ingredient(0, "Alt"))
        db.recipeDao().insert(Recipe(title = "Alt", source = "", page = null, bookServings = 1, ourServings = 1, cookingTimeMinutes = 1))

        dao.replaceAll(snapshot)

        assertEquals(snapshot, dao.snapshot())
    }

    @Test
    fun replaceAll_withBrokenReference_changesNothing() = runTest {
        dao.replaceAll(snapshot)
        val broken = snapshot.copy(lines = listOf(RecipeIngredient(8, 3, 0, "?", 1.0, ingredientId = 404)))

        try {
            dao.replaceAll(broken)
            fail("broken reference accepted")
        } catch (_: SQLiteConstraintException) {
        }

        assertEquals(snapshot, dao.snapshot())
    }
}
