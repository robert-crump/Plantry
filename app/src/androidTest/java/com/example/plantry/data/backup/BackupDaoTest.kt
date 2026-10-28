package com.example.plantry.data.backup

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.plantry.data.CookLog
import com.example.plantry.data.CookLogPlant
import com.example.plantry.data.Ingredient
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.LineUnit
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.IngredientAlias
import com.example.plantry.data.MixComponent
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.PlantryDatabase
import com.example.plantry.data.ProposalEvent
import com.example.plantry.data.ProposalOutcome
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
    )

    private val snapshot = BackupSnapshot(
        ingredients = listOf(ingredient(1, "Reis, gekocht"), ingredient(5, "Reis, trocken"), ingredient(7, "Reismischung")),
        recipes = listOf(Recipe(3, "Curry", "Buch", 12, 2, 30, modified = true)),
        lines = listOf(
            RecipeIngredient(8, 3, 0, 180.0, LineUnit.Grams, 1, wording = "1 Tasse Reis"),
            RecipeIngredient(9, 3, 1, 0.5, LineUnit.Of(PieceUnit.PACKET), 5),
        ),
        cookLog = listOf(
            CookLog(4, 3, LocalDate.of(2026, 9, 30), "Curry", RecipeStats(1.0, 20.0, 40.0)),
            // Its recipe was deleted.
            CookLog(6, null, LocalDate.of(2026, 9, 1), "Weg", RecipeStats(2.5, 30.0, 50.0)),
        ),
        aliases = listOf(IngredientAlias("basmati", 5), IngredientAlias("reis gegart", 1)),
        planned = listOf(PlannedRecipe(3, LocalDate.of(2026, 10, 1))),
        units = listOf(IngredientUnit(5, PieceUnit.CUP, 250.0, 1000.0, 4), IngredientUnit(5, PieceUnit.PACKET, 500.0)),
        components = listOf(MixComponent(7, 1), MixComponent(7, 5)),
        cookLogPlants = listOf(
            CookLogPlant(cookLogId = 4, ingredientId = 1, name = "Reis, gekocht", plantPoints = 1.0),
            CookLogPlant(cookLogId = 6, ingredientId = null, name = "Linsen", plantPoints = 1.0),
        ),
    )

    @Test
    fun replaceAll_restoresEverything_withIds() = runTest {
        db.ingredientDao().insert(ingredient(0, "Alt"))
        db.recipeDao().insert(Recipe(title = "Alt", source = "", page = null, servings = 1, totalTimeMinutes = 1))

        dao.replaceAll(snapshot)

        assertEquals(snapshot, dao.snapshot())
    }

    @Test
    fun replaceAll_withBrokenReference_changesNothing() = runTest {
        dao.replaceAll(snapshot)
        val broken = snapshot.copy(lines = listOf(RecipeIngredient(8, 3, 0, 1.0, LineUnit.Grams, ingredientId = 404)))

        try {
            dao.replaceAll(broken)
            fail("broken reference accepted")
        } catch (_: SQLiteConstraintException) {
        }

        assertEquals(snapshot, dao.snapshot())
    }

    @Test
    fun replaceAll_leavesTheProposalEventLog() = runTest {
        val id = db.proposalEventDao().insert(ProposalEvent(date = LocalDate.of(2026, 10, 28), recipeId = 3))
        db.proposalEventDao().setOutcome(id, ProposalOutcome.OPENED)

        dao.replaceAll(snapshot)

        assertEquals(ProposalOutcome.OPENED, db.proposalEventDao().get(id)?.outcome)
    }
}
