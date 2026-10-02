package com.example.plantry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CookLogRepositoryTest {

    private val dao = FakeCookLogDao()
    private val repository = CookLogRepository(dao)
    private var today = LocalDate.of(2026, 10, 2)

    @Test
    fun log_updatesStatsOfThatRecipeOnly() = runTest {
        repository.log(recipeId = 1, date = today.minusDays(4))
        repository.log(recipeId = 1, date = today.minusDays(1))
        repository.log(recipeId = 2, date = today)

        assertEquals(CookingStats(2, 1), repository.observeStats(1) { today }.first())
        assertEquals(CookingStats(0, null), repository.observeStats(3) { today }.first())
    }

    @Test
    fun observeStats_readsTodayOnEveryEmission() = runTest {
        repository.log(recipeId = 1, date = today)
        val stats = repository.observeStats(1) { today }

        today = today.plusDays(2)

        assertEquals(2L, stats.first().daysSinceLastCooked)
    }

    @Test
    fun delete_undoesLog() = runTest {
        repository.log(recipeId = 1, date = today.minusDays(7))
        val id = repository.log(recipeId = 1, date = today)

        repository.delete(id)

        assertEquals(CookingStats(1, 7), repository.observeStats(1) { today }.first())
    }

    @Test
    fun restore_putsEntryBackWithItsId() = runTest {
        val id = repository.log(recipeId = 1, date = today)
        val log = CookLog(id = id, recipeId = 1, cookedOn = today)
        repository.delete(id)

        repository.restore(log)

        assertEquals(listOf(log), dao.logs.value)
    }
}

/** In-memory [CookLogDao]; history ordering is covered by the instrumented CookLogDaoTest. */
private class FakeCookLogDao : CookLogDao {
    val logs = MutableStateFlow<List<CookLog>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(log: CookLog): Long {
        val id = if (log.id == 0L) nextId++ else log.id
        logs.value += log.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        logs.value = logs.value.filter { it.id != id }
    }

    override fun observeHistory(): Flow<List<CookLogEntry>> =
        logs.map { all -> all.map { CookLogEntry(it, recipeTitle = "Rezept ${it.recipeId}") } }

    override fun observeDates(recipeId: Long): Flow<List<LocalDate>> =
        logs.map { all -> all.filter { it.recipeId == recipeId }.map { it.cookedOn } }

    override suspend fun getLastCooked(): List<LastCooked> =
        logs.value.groupBy { it.recipeId }.map { (id, entries) -> LastCooked(id, entries.maxOf { it.cookedOn }) }
}
