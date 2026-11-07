package com.example.plantry.data.reminder

import com.example.plantry.data.BookSession
import com.example.plantry.data.CookLog
import com.example.plantry.data.Dish
import com.example.plantry.data.FakeCookLogDao
import com.example.plantry.data.FakeIngredientDao
import com.example.plantry.data.FakePlannedRecipeDao
import com.example.plantry.data.FakeProposalEventDao
import com.example.plantry.data.FakeRecipeDao
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.MealPlan
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.ProposalEvent
import com.example.plantry.data.ProposalEventRepository
import com.example.plantry.data.ProposalOutcome
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipePhotoRepository
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.RecipeSnapshot
import com.example.plantry.data.RecipeStats
import com.example.plantry.data.Transaction
import com.example.plantry.data.backup.FakePhotoStore
import com.example.plantry.data.planner.RecipeSuggester
import com.example.plantry.data.settings.ReminderKind
import com.example.plantry.data.settings.ReminderSetting
import com.example.plantry.data.settings.ScanModel
import com.example.plantry.data.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random

class RemindersTest {

    /** A Wednesday at noon: after the proposal (11:00), before shopping (15:00) and the question (20:00). */
    private var now = LocalDateTime.of(2026, 10, 28, 12, 0)
    private val today get() = now.toLocalDate()

    private val recipeDao = FakeRecipeDao()
    private val ingredientDao = FakeIngredientDao()
    private val cookLogDao = FakeCookLogDao()
    private val plannedDao = FakePlannedRecipeDao()
    private val eventDao = FakeProposalEventDao()

    private val times = mapOf(
        ReminderKind.PROPOSAL to LocalTime.of(11, 0),
        ReminderKind.SHOPPING to LocalTime.of(15, 0),
        ReminderKind.COOKED to LocalTime.of(20, 0),
    )
    private val settings = MutableStateFlow(
        Settings(
            maskedApiKey = null,
            scanModel = ScanModel.OPUS,
            cooldownDays = 21,
            reminders = times.mapValues { (_, time) -> ReminderSetting(enabled = true, time = time) },
        ),
    )

    private val ingredients = IngredientRepository(ingredientDao)
    private val recipes = RecipeRepository(recipeDao, ingredients, RecipePhotoRepository(FakePhotoStore()), BookSession(), Transaction.None)
    private val mealPlan = MealPlan(
        plannedDao,
        cookLogDao,
        eventDao,
        snapshot = { id -> recipeDao.getById(id)?.let { RecipeSnapshot(it.title, RecipeStats(0.0, 0.0, 0.0)) } },
        Transaction.None,
    ) { today }
    private val events = ProposalEventRepository(eventDao) { today }
    private val reminders = Reminders(
        settings,
        mealPlan,
        recipes,
        ingredients,
        RecipeSuggester(recipeDao, ingredientDao, cookLogDao, cooldownDays = { 21 }, random = Random(1)),
        events,
        moods = listOf(MoodHook("fancy", "Lust auf {name}?")),
        now = { now },
        random = Random(1),
    )

    private suspend fun recipe(title: String, dish: Dish = Dish.MAIN) =
        recipeDao.insert(Recipe(title = title, source = "", page = null, servings = 2, totalTimeMinutes = 20, dish = dish))

    private fun enable(kind: ReminderKind, enabled: Boolean) {
        settings.value = settings.value.copy(reminders = settings.value.reminders + (kind to ReminderSetting(enabled, times.getValue(kind))))
    }

    @Test
    fun alarms_questionOnlyOnPlannedDays_theOthersDaily_noneWhenOff() = runTest {
        val dal = recipe("Dal")
        mealPlan.plan(dal, today.plusDays(2))

        assertEquals(
            mapOf(
                ReminderKind.PROPOSAL to today.plusDays(1).atTime(11, 0),
                ReminderKind.SHOPPING to today.atTime(15, 0),
                ReminderKind.COOKED to today.plusDays(2).atTime(20, 0),
            ),
            reminders.alarms(),
        )

        enable(ReminderKind.SHOPPING, false)
        assertNull(reminders.alarms().getValue(ReminderKind.SHOPPING))
    }

    @Test
    fun observe_followsGeplant() = runTest {
        val dal = recipe("Dal")
        mealPlan.plan(dal, today)

        val update = reminders.observe().first()

        assertEquals(today.atTime(20, 0), update.alarms.getValue(ReminderKind.COOKED))
        // A main dish is planned for today: no proposal wanted, but the question and shopping are.
        assertEquals(setOf(ReminderKind.PROPOSAL), update.stale)
    }

    @Test
    fun dueProposal_logsItAsShownWithItsTitleHook() = runTest {
        val dal = recipe("Dal")

        val notice = reminders.due(ReminderKind.PROPOSAL) as Notice.Proposal

        assertEquals(dal, notice.proposal.recipe.id)
        val event = eventDao.get(notice.eventId!!)!!
        assertEquals(ProposalEvent(notice.eventId, today, dal, notice.proposal.titleHook?.id), event)
    }

    @Test
    fun dueProposal_nothingWhenAMainDishIsPlannedForToday() = runTest {
        recipe("Dal")
        val curry = recipe("Curry")
        mealPlan.plan(curry, today)

        assertNull(reminders.due(ReminderKind.PROPOSAL))
        assertNull(events.latestToday())
    }

    @Test
    fun dueProposal_aPlannedSnackLeavesDinnerOpen() = runTest {
        recipe("Dal")
        val nuts = recipe("Nüsse", Dish.SNACK)
        mealPlan.plan(nuts, today)

        assertNotNull(reminders.due(ReminderKind.PROPOSAL))
    }

    @Test
    fun dueShoppingAndQuestion_takeTheProposalsPlace() = runTest {
        recipe("Dal")
        val proposal = reminders.due(ReminderKind.PROPOSAL) as Notice.Proposal
        val curry = recipe("Curry")
        mealPlan.plan(curry, today)

        val shopping = reminders.due(ReminderKind.SHOPPING)
        val question = reminders.due(ReminderKind.COOKED)

        assertEquals(Notice.Shopping(ReminderContent.Single(curry, "Curry")), shopping)
        assertEquals(Notice.CookedQuestion(ReminderContent.Single(curry, "Curry"), today), question)
        assertEquals(ProposalOutcome.IGNORED, eventDao.get(proposal.eventId!!)?.outcome)
    }

    @Test
    fun dueShoppingAndQuestion_nothingPlannedForToday_nothingToSay() = runTest {
        mealPlan.plan(recipe("Dal"), today.plusDays(1))

        assertNull(reminders.due(ReminderKind.SHOPPING))
        assertNull(reminders.due(ReminderKind.COOKED))
    }

    @Test
    fun plan_confirmationStaysAfterGeplantChanges_untilItsRecipeIsCookedOrMoved() = runTest {
        val dal = recipe("Dal")
        val proposal = reminders.due(ReminderKind.PROPOSAL) as Notice.Proposal
        // Planned for another day in the app while the proposal was showing.
        mealPlan.plan(dal, today.plusDays(3))

        val confirmation = reminders.plan(dal, proposal.eventId)!!

        assertEquals(PlannedRecipe(dal, today.plusDays(3)), confirmation.previous)
        assertEquals(listOf(PlannedRecipe(dal, today)), mealPlan.observe().first())
        // A main dish is planned for today now, yet the confirmation of that very plan holds.
        assertTrue(ReminderKind.PROPOSAL !in reminders.stale())

        mealPlan.plan(dal, today.plusDays(1))
        assertTrue(ReminderKind.PROPOSAL in reminders.stale())

        mealPlan.plan(dal, today)
        mealPlan.cook(dal, today)
        assertTrue(ReminderKind.PROPOSAL in reminders.stale())
    }

    @Test
    fun plan_missingRecipe_noConfirmation() = runTest {
        assertNull(reminders.plan(99, eventId = null))
        assertEquals(emptyList<PlannedRecipe>(), mealPlan.observe().first())
    }

    @Test
    fun undoPlan_theSameProposalAgain_nothingComeOfIt_recipeBackOnItsDay() = runTest {
        val dal = recipe("Dal")
        val proposal = reminders.due(ReminderKind.PROPOSAL) as Notice.Proposal
        // Planned for another day in the app while the proposal was showing.
        mealPlan.plan(dal, today.plusDays(3))
        val confirmation = reminders.plan(dal, proposal.eventId)!!

        val again = reminders.undoPlan(dal, confirmation.previous, proposal.eventId)!!

        assertEquals(proposal.proposal.titleHook?.id, again.proposal.titleHook?.id)
        assertEquals(proposal.eventId, again.eventId)
        assertNull(eventDao.get(proposal.eventId!!)?.outcome)
        assertEquals(listOf(PlannedRecipe(dal, today.plusDays(3))), mealPlan.observe().first())
        assertTrue(ReminderKind.PROPOSAL !in reminders.stale())
    }

    @Test
    fun undoPlan_anotherMainPlannedForTodayMeanwhile_noProposal() = runTest {
        val dal = recipe("Dal")
        val proposal = reminders.due(ReminderKind.PROPOSAL) as Notice.Proposal
        val confirmation = reminders.plan(dal, proposal.eventId)!!
        mealPlan.plan(recipe("Curry"), today)

        assertNull(reminders.undoPlan(dal, confirmation.previous, proposal.eventId))
        assertEquals(listOf(today), mealPlan.observe().first().map { it.plannedOn })
    }

    @Test
    fun yes_logsOnlyWhileTheRecipeIsStillPlanned() = runTest {
        val dal = recipe("Dal")
        val curry = recipe("Curry")
        mealPlan.plan(dal, today)

        reminders.yes(dal)
        reminders.yes(curry)

        assertEquals(listOf(dal), cookLogDao.logs.value.map(CookLog::recipeId))
        assertEquals(listOf(today), cookLogDao.logs.value.map(CookLog::cookedOn))
        assertEquals(emptyList<PlannedRecipe>(), mealPlan.observe().first())
    }

    @Test
    fun no_postponesToTheNextDay_orToTodayOnceThatDayBegan() = runTest {
        val dal = recipe("Dal")
        val curry = recipe("Curry")
        mealPlan.plan(dal, today)
        mealPlan.plan(curry, today)

        reminders.no(listOf(dal), today)
        assertEquals(PlannedRecipe(dal, today.plusDays(1)), plannedDao.get(dal))

        val askedOn = today
        now = now.plusDays(2)
        reminders.no(listOf(curry), askedOn)
        assertEquals(PlannedRecipe(curry, today), plannedDao.get(curry))
    }

    @Test
    fun acrossMidnight_yesterdaysProposalIsIgnored_andYesterdaysQuestionStale() = runTest {
        recipe("Dal")
        val curry = recipe("Curry")
        val proposal = reminders.due(ReminderKind.PROPOSAL) as Notice.Proposal
        mealPlan.plan(curry, today)
        assertTrue(ReminderKind.COOKED !in reminders.stale())

        now = now.plusDays(1).withHour(8)

        assertTrue(ReminderKind.COOKED in reminders.stale())
        assertEquals(ProposalOutcome.IGNORED, eventDao.get(proposal.eventId!!)?.outcome)
        // Nothing planned for the new day: a proposal is wanted again.
        assertTrue(ReminderKind.PROPOSAL !in reminders.stale())
    }

    @Test
    fun openedAndDismissed_recordTheTap() = runTest {
        recipe("Dal")
        val first = (reminders.due(ReminderKind.PROPOSAL) as Notice.Proposal).eventId!!
        reminders.opened(first, otherSuggestions = true)
        assertEquals(ProposalOutcome.OTHER_SUGGESTIONS, eventDao.get(first)?.outcome)

        val second = (reminders.due(ReminderKind.PROPOSAL) as Notice.Proposal).eventId!!
        reminders.dismissed(second)
        assertEquals(ProposalOutcome.DISMISSED, eventDao.get(second)?.outcome)
    }
}
