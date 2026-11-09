package com.example.plantry.data.reminder

import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.MealPlan
import com.example.plantry.data.PlanOrigin
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.ProposalEventRepository
import com.example.plantry.data.ProposalOutcome
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeFacts
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.planner.RecipeSuggester
import com.example.plantry.data.settings.ReminderKind
import com.example.plantry.data.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/** A recipe to propose: its [facts], its factual [hook] if one applies, and the [titleHook] that leads its title. */
data class RecipeProposal(
    val recipe: Recipe,
    val facts: List<ProposalFact>,
    val hook: ProposalHook?,
    val titleHook: TitleHook?,
)

/** A notification to show, as [Reminders] decides it; the notifications only render it. */
sealed interface Notice {
    /** A recipe proposal; its buttons carry [eventId], its row in the proposal log (null for one from before the log). */
    data class Proposal(val proposal: RecipeProposal, val eventId: Long?) : Notice

    /** "Für heute geplant" in the proposal's place; [previous] is the recipe's Geplant entry before "Planen", for Rückgängig. */
    data class Confirmation(val proposal: RecipeProposal, val previous: PlannedRecipe?, val eventId: Long?) : Notice

    /** What to buy for today's planned recipes. */
    data class Shopping(val content: ReminderContent) : Notice

    /** "Hast du heute … gekocht?" about the recipes planned for [askedOn]. */
    data class CookedQuestion(val content: ReminderContent, val askedOn: LocalDate) : Notice
}

/** What the notifications should do after the settings or Geplant changed. */
data class ReminderUpdate(
    /** When each kind's alarm is due next; null to cancel it. */
    val alarms: Map<ReminderKind, LocalDateTime?>,
    /** The kinds whose notification, if showing, has nothing left to say. */
    val stale: Set<ReminderKind>,
)

/**
 * The reminders' rules: when the alarms go off, what a notification says when they do, when one has
 * gone stale, and what its buttons do. The proposal names a recipe behind a [ProposalHook] or a
 * [MoodHook] (see [TitleHook.choose]) with a fact line (see [ProposalFacts]); "Planen" turns it into a
 * confirmation. Each proposal shown gets a row in the [ProposalEventRepository] log.
 */
class Reminders(
    private val settings: StateFlow<Settings>,
    private val mealPlan: MealPlan,
    private val recipes: RecipeRepository,
    private val ingredients: IngredientRepository,
    private val suggester: RecipeSuggester,
    private val events: ProposalEventRepository,
    private val moods: List<MoodHook>,
    private val now: () -> LocalDateTime,
    private val random: Random = Random.Default,
) {
    /**
     * A [ReminderUpdate] whenever the settings or Geplant change; past days' proposals that nothing
     * came of are closed as ignored on the way.
     */
    fun observe(): Flow<ReminderUpdate> =
        combine(settings, mealPlan.observe(), ::Pair).map { (settings, planned) ->
            val now = now()
            ReminderUpdate(alarms(settings, planned, now), stale(settings, planned, now.toLocalDate()))
        }

    /** When each kind's alarm is due next, from the stored state; null to cancel it. */
    suspend fun alarms(): Map<ReminderKind, LocalDateTime?> =
        alarms(settings.value, mealPlan.observe().first(), now())

    /** The kinds whose notification has nothing left to say now. */
    suspend fun stale(): Set<ReminderKind> =
        stale(settings.value, mealPlan.observe().first(), now().toLocalDate())

    /**
     * An alarm of [kind] went off: what to show, or null if there is nothing to say. Showing a
     * proposal logs it; the others take the place of a proposal, which then counts as ignored.
     */
    suspend fun due(kind: ReminderKind): Notice? {
        val today = now().toLocalDate()
        val enabled = settings.value.reminder(kind).enabled
        val planned = mealPlan.observe().first()
        return when (kind) {
            ReminderKind.PROPOSAL -> proposal(enabled, today, planned)?.let {
                Notice.Proposal(it, events.shown(it.recipe.id, it.titleHook?.id))
            }
            ReminderKind.SHOPPING -> CookReminder.content(enabled, today, planned, recipes.observeRecipes().first())?.let {
                events.replaced()
                Notice.Shopping(it)
            }
            ReminderKind.COOKED -> CookReminder.content(enabled, today, planned, recipes.observeRecipes().first())?.let {
                events.replaced()
                Notice.CookedQuestion(it, today)
            }
        }
    }

    /**
     * "Planen": puts the proposed recipe on Geplant for today (moving it there if it was planned for
     * another day) and returns the confirmation to show in the proposal's place; null if the recipe
     * no longer exists. [eventId] is the proposal's log row; null for a notification from before the log.
     */
    suspend fun plan(recipeId: Long, eventId: Long?): Notice.Confirmation? {
        val today = now().toLocalDate()
        val proposal = proposalOf(recipeId, today) { factual, _ -> factual } ?: return null
        val previous = mealPlan.plan(recipeId, today, eventId?.let(PlanOrigin::FromNotification))
        return Notice.Confirmation(proposal, previous, eventId)
    }

    /**
     * "Rückgängig" of the confirmation: the recipe back where it was before "Planen", and the same
     * proposal again, with nothing come of it yet; null if no proposal is wanted any more.
     */
    suspend fun undoPlan(recipeId: Long, previous: PlannedRecipe?, eventId: Long?): Notice.Proposal? {
        mealPlan.undoPlan(recipeId, previous, eventId?.let(PlanOrigin::FromNotification))
        val today = now().toLocalDate()
        val enabled = settings.value.reminder(ReminderKind.PROPOSAL).enabled
        if (!CookReminder.proposalWanted(enabled, today, mealPlan.observe().first(), recipes.observeRecipes().first())) return null
        // The same title as before "Planen"; a notification from before the log had the factual hook.
        val hookId = eventId?.let { events.hookId(it) }
        val proposal = proposalOf(recipeId, today) { factual, _ ->
            if (eventId == null) factual else moods.firstOrNull { it.id == hookId } ?: factual?.takeIf { it.id == hookId }
        } ?: return null
        return Notice.Proposal(proposal, eventId)
    }

    /**
     * The app was opened from the proposal [eventId]: its body or "Rezept öffnen", or "Anderer
     * Vorschlag" ([otherSuggestions]).
     */
    suspend fun opened(eventId: Long, otherSuggestions: Boolean) =
        events.record(eventId, if (otherSuggestions) ProposalOutcome.OTHER_SUGGESTIONS else ProposalOutcome.OPENED)

    /** The proposal [eventId] or its confirmation was swiped away. */
    suspend fun dismissed(eventId: Long) = events.record(eventId, ProposalOutcome.DISMISSED)

    /** "Ja": logs the recipe as cooked today, unless it was handled in the app in the meantime. */
    suspend fun yes(recipeId: Long) {
        if (mealPlan.observe().first().any { it.recipeId == recipeId }) mealPlan.cook(recipeId, now().toLocalDate())
    }

    /** "Nein, morgen" / "Keins, morgen": moves the recipes asked about on [askedOn] to the next day. */
    suspend fun no(recipeIds: List<Long>, askedOn: LocalDate) = mealPlan.postpone(recipeIds, askedOn)

    private fun alarms(settings: Settings, planned: List<PlannedRecipe>, now: LocalDateTime): Map<ReminderKind, LocalDateTime?> =
        ReminderKind.entries.associateWith { kind ->
            val reminder = settings.reminder(kind)
            when (kind) {
                ReminderKind.COOKED -> CookReminder.nextAt(now, reminder.time, reminder.enabled, planned.map { it.plannedOn })
                ReminderKind.PROPOSAL, ReminderKind.SHOPPING -> CookReminder.nextDailyAt(now, reminder.time, reminder.enabled)
            }
        }

    /**
     * Nothing left to say on [today]: the evening and shopping reminders once nothing is planned for
     * it; the proposal once a main dish is planned for it, and its confirmation once its recipe is no
     * longer planned for it. The proposal log tells which of the two shows: "Planen" records its
     * outcome together with the plan.
     */
    private suspend fun stale(settings: Settings, planned: List<PlannedRecipe>, today: LocalDate): Set<ReminderKind> {
        events.closePastDays()
        val all = recipes.observeRecipes().first()
        val stale = listOf(ReminderKind.COOKED, ReminderKind.SHOPPING).filterTo(mutableSetOf()) { kind ->
            CookReminder.content(settings.reminder(kind).enabled, today, planned, all) == null
        }
        val latest = events.latestToday()
        val current = if (latest?.outcome == ProposalOutcome.PLANNED_FROM_NOTIFICATION) {
            CookReminder.confirmationCurrent(latest.recipeId, today, planned)
        } else {
            CookReminder.proposalWanted(settings.reminder(ReminderKind.PROPOSAL).enabled, today, planned, all)
        }
        if (!current) stale += ReminderKind.PROPOSAL
        return stale
    }

    /** The recipe to propose, picked now; null when none is wanted or none is left to propose. */
    private suspend fun proposal(enabled: Boolean, today: LocalDate, planned: List<PlannedRecipe>): RecipeProposal? {
        if (!CookReminder.proposalWanted(enabled, today, planned, recipes.observeRecipes().first())) return null
        val id = suggester.suggest(today, 1, planned.mapTo(mutableSetOf()) { it.recipeId }).firstOrNull() ?: return null
        val lastHookId = events.lastHookId()
        return proposalOf(id, today) { factual, name -> TitleHook.choose(factual, moods, name, today, lastHookId, random) }
    }

    /**
     * The recipe with its facts on [today], and [titleHook] of its factual hook and name for the
     * title; null if it no longer exists.
     */
    private suspend fun proposalOf(
        id: Long,
        today: LocalDate,
        titleHook: (factual: ProposalHook?, name: String) -> TitleHook?,
    ): RecipeProposal? {
        val recipe = recipes.getRecipe(id) ?: return null
        val recipeFacts = RecipeFacts.of(recipe, recipes.getLines(id), ingredients.observeBook().first())
        val stats = recipeFacts.stats
        val week = suggester.week(today)
        val newPlantPoints = recipeFacts.newPlantPoints(week)
        val facts = ProposalFacts.of(
            newPlantPoints = newPlantPoints,
            plantPoints = stats.plantPoints,
            proteinPerPortion = stats.proteinPerPortion,
            price = recipeFacts.price(today),
        )
        val hook = ProposalHook.of(
            weekPoints = week.points,
            newPlantPoints = newPlantPoints,
            lastCooked = suggester.lastCooked(id),
            today = today,
            cooldownDays = settings.value.cooldownDays,
            proteinPerPortion = stats.proteinPerPortion,
        )
        return RecipeProposal(recipe, facts, hook, titleHook(hook, recipe.title))
    }
}
