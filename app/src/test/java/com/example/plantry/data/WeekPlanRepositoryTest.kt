package com.example.plantry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeekPlanRepositoryTest {

    private val dao = FakeWeekPlanDao()
    private val repository = WeekPlanRepository(dao)
    private val saturday = LocalDate.of(2026, 10, 3)
    private val tuesday = saturday.plusDays(3)

    @Test
    fun pick_replacesSlotAndResetsDone() = runTest {
        repository.pick(saturday, 1, recipeId = 4)
        repository.setDone(SlotRef(saturday, 1), done = true)

        repository.pick(saturday, 1, recipeId = 5)

        assertEquals(listOf(WeekPlanSlot(saturday, 1, 5)), dao.slots.value)
    }

    @Test
    fun remove_emptiesSlot() = runTest {
        repository.pick(saturday, 0, recipeId = 4)
        repository.pick(saturday, 1, recipeId = 5)

        repository.remove(SlotRef(saturday, 0))

        assertEquals(listOf(null, 5L, null, null, null), repository.observeWeek(saturday).first().slots.map { it?.slot?.recipeId })
    }

    @Test
    fun markCooked_marksSlotOfThatWeek() = runTest {
        repository.pick(saturday, 2, recipeId = 4)

        val marked = repository.markCooked(recipeId = 4, cookedOn = tuesday)

        assertEquals(SlotRef(saturday, 2), marked)
        assertEquals(true, dao.slots.value.single().done)
    }

    @Test
    fun markCooked_recipeNotOnMenuLeavesMenuUnchanged() = runTest {
        repository.pick(saturday, 0, recipeId = 4)

        assertNull(repository.markCooked(recipeId = 5, cookedOn = tuesday))
        assertEquals(listOf(WeekPlanSlot(saturday, 0, 4)), dao.slots.value)
    }

    @Test
    fun markCooked_onlyTheWeekContainingTheDate() = runTest {
        repository.pick(saturday, 0, recipeId = 4)

        // Friday before belongs to last week's menu.
        assertNull(repository.markCooked(recipeId = 4, cookedOn = saturday.minusDays(1)))
        assertEquals(false, dao.slots.value.single().done)
    }

    @Test
    fun markCooked_alreadyDoneIsNotMarkedAgain() = runTest {
        repository.pick(saturday, 0, recipeId = 4)
        repository.markCooked(recipeId = 4, cookedOn = tuesday)

        assertNull(repository.markCooked(recipeId = 4, cookedOn = tuesday))
    }

    @Test
    fun rollover_offeredOnlyWhileNewWeekIsEmpty() = runTest {
        val lastSaturday = saturday.minusWeeks(1)
        repository.pick(lastSaturday, 0, recipeId = 1)
        repository.pick(lastSaturday, 1, recipeId = 2)
        repository.pick(lastSaturday, 2, recipeId = 3)
        repository.markCooked(recipeId = 2, cookedOn = lastSaturday)

        assertEquals(2, repository.observeRolloverCount(saturday).first())

        repository.rollover(saturday)

        assertEquals(listOf(1L, 3L, null, null, null), repository.observeWeek(saturday).first().slots.map { it?.slot?.recipeId })
        assertEquals(0, repository.observeRolloverCount(saturday).first())
    }
}

/** In-memory [WeekPlanDao]; the recipe join is covered by the instrumented WeekPlanDaoTest. */
private class FakeWeekPlanDao : WeekPlanDao {
    val slots = MutableStateFlow<List<WeekPlanSlot>>(emptyList())

    private fun week(weekStart: LocalDate) = slots.value.filter { it.weekStart == weekStart }.sortedBy { it.position }

    override fun observeWeek(weekStart: LocalDate): Flow<List<PlannedRecipe>> = observeSlots(weekStart).map { week ->
        week.map { PlannedRecipe(it, recipeTitle = "Rezept ${it.recipeId}") }
    }

    override fun observeSlots(weekStart: LocalDate): Flow<List<WeekPlanSlot>> = slots.map { week(weekStart) }

    override suspend fun getSlots(weekStart: LocalDate): List<WeekPlanSlot> = week(weekStart)

    override suspend fun upsert(slot: WeekPlanSlot) {
        delete(slot.weekStart, slot.position)
        slots.value += slot
    }

    override suspend fun insertAll(slots: List<WeekPlanSlot>) {
        this.slots.value += slots
    }

    override suspend fun delete(weekStart: LocalDate, position: Int) {
        slots.value = slots.value.filterNot { it.weekStart == weekStart && it.position == position }
    }

    override suspend fun setDone(weekStart: LocalDate, position: Int, done: Boolean) {
        slots.value = slots.value.map {
            if (it.weekStart == weekStart && it.position == position) it.copy(done = done) else it
        }
    }
}
