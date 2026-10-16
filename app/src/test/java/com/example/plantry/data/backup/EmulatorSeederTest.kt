package com.example.plantry.data.backup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class EmulatorSeederTest {

    private val today = LocalDate.of(2026, 11, 3)
    private val storage = MapStorage()
    private var hasData = false
    private val imported = mutableListOf<BackupFile>()
    private val seeder = EmulatorSeeder({ hasData }, { imported += it }, storage, { today })

    @Test
    fun emptyDatabase_importsFixtureWithDatesMovedToToday() = runTest {
        val seeded = seeder.seed(fixture(cooked = listOf("2026-10-12", "2026-10-14"), planned = "2026-10-15"))

        assertEquals(20L, seeded?.daysMoved)
        val file = imported.single()
        assertEquals(listOf("2026-11-01", "2026-11-03"), file.cookLog.map { it.cookedOn })
        assertEquals("2026-11-04", file.planned.single().plannedOn)
    }

    @Test
    fun sameFixtureWithData_keepsData() = runTest {
        val fixture = fixture()
        seeder.seed(fixture)
        hasData = true

        assertNull(seeder.seed(fixture))
        assertEquals(1, imported.size)
    }

    @Test
    fun sameFixtureAfterWipe_seedsAgain() = runTest {
        val fixture = fixture()
        seeder.seed(fixture)

        seeder.seed(fixture)

        assertEquals(2, imported.size)
    }

    @Test
    fun changedFixture_replacesData() = runTest {
        seeder.seed(fixture(cooked = listOf("2026-10-14")))
        hasData = true

        seeder.seed(fixture(cooked = listOf("2026-10-13", "2026-10-14")))

        assertEquals(2, imported.size)
        assertEquals(2, imported.last().cookLog.size)
    }

    @Test
    fun noCookingLog_keepsDates() = runTest {
        assertEquals(0L, seeder.seed(fixture(cooked = emptyList(), planned = "2026-10-15"))?.daysMoved)
        assertEquals("2026-10-15", imported.single().planned.single().plannedOn)
    }

    /** The bundled fixture must stay importable and keep up with the backup format. */
    @Test
    fun bundledFixture_isCurrentAndValid() {
        val file = BackupFile.decode(File("src/debug/assets/emulator-fixture.json").readText())

        assertEquals(
            "Backup format bumped: add the new fields to app/src/debug/assets/emulator-fixture.json and set its formatVersion",
            BackupFile.FORMAT_VERSION,
            file.formatVersion,
        )
        val snapshot = file.toSnapshot()
        file.photos()
        val ingredientIds = snapshot.ingredients.map { it.id }.toSet()
        val recipeIds = snapshot.recipes.map { it.id }.toSet()
        assertTrue(snapshot.lines.all { it.ingredientId in ingredientIds })
        assertTrue(snapshot.aliases.all { it.ingredientId in ingredientIds })
        assertTrue(snapshot.cookLog.all { it.recipeId in recipeIds })
        assertTrue(snapshot.planned.all { it.recipeId in recipeIds })
    }

    private fun fixture(cooked: List<String> = listOf("2026-10-14"), planned: String? = null) = BackupFile.encode(
        BackupFile(
            formatVersion = BackupFile.FORMAT_VERSION,
            settings = BackupSettings("opus", 21),
            ingredients = emptyList(),
            recipes = emptyList(),
            cookLog = cooked.mapIndexed { i, date -> BackupCookLog(i + 1L, null, date, "Dal", 3.0, 10.0, 40.0, null, null) },
            aliases = emptyList(),
            planned = listOfNotNull(planned?.let { BackupPlanned(1, it) }),
        ),
    )
}
