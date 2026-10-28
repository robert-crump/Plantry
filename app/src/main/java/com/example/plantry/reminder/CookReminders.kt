package com.example.plantry.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import com.example.plantry.MainActivity
import com.example.plantry.R
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.PlannedRepository
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipePrice
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.RecipeStats
import com.example.plantry.data.planner.RecipeSuggester
import com.example.plantry.data.priceLines
import com.example.plantry.data.reminder.CookReminder
import com.example.plantry.data.reminder.ProposalFact
import com.example.plantry.data.reminder.ProposalFacts
import com.example.plantry.data.reminder.ReminderContent
import com.example.plantry.data.settings.ReminderKind
import com.example.plantry.data.settings.Settings
import com.example.plantry.data.settings.SettingsRepository
import com.example.plantry.ui.formatEuros
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The notifications: a recipe proposal (its name and a fact line, see [ProposalFacts]) and a
 * shopping reminder at their times, and the "Habt ihr heute … gekocht?" question on the evening of
 * a planned day. Keeps one alarm per kind set while it is on, shows the notification when the alarm
 * fires and answers the buttons of the proposal and the evening one. The proposal's "Planen" turns
 * it into a "Für heute geplant" confirmation in its place.
 */
class CookReminders(
    private val context: Context,
    private val settings: SettingsRepository,
    private val planned: PlannedRepository,
    private val recipes: RecipeRepository,
    private val ingredients: IngredientRepository,
    private val suggester: RecipeSuggester,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    /** Keeps the alarms in step with the settings and Geplant while the process lives. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(settings.settings, planned.observe(), ::Pair).collect { (settings, planned) ->
                schedule(settings, planned)
                // Nothing left to say today: an unanswered notification would only be stale.
                val today = LocalDate.now()
                val all = recipes.observeRecipes().first()
                listOf(ReminderKind.COOKED, ReminderKind.SHOPPING).forEach { kind ->
                    if (CookReminder.content(settings.reminder(kind).enabled, today, planned, all) == null) {
                        notifications.cancel(kind.notificationId)
                    }
                }
                val confirmed = confirmedRecipeId()
                val current = if (confirmed != null) {
                    CookReminder.confirmationCurrent(confirmed, today, planned)
                } else {
                    CookReminder.proposalWanted(settings.reminder(ReminderKind.PROPOSAL).enabled, today, planned, all)
                }
                if (!current) notifications.cancel(ReminderKind.PROPOSAL.notificationId)
            }
        }
    }

    /** Sets or cancels the alarms from the stored state, e.g. after a reboot or a time zone change. */
    suspend fun sync() = schedule(settings.settings.value, planned.observe().first())

    /** An alarm went off: shows the notification if there is something to say and sets the next alarm. */
    suspend fun show(kind: ReminderKind) {
        val enabled = settings.settings.value.reminder(kind).enabled
        val today = LocalDate.now()
        val plannedNow = planned.observe().first()
        if (canNotify()) {
            when (kind) {
                ReminderKind.PROPOSAL -> proposal(enabled, today, plannedNow)?.let {
                    notifications.notify(kind.notificationId, buildProposal(it))
                }
                // One Plantry notification at a time: these replace a proposal or its confirmation.
                ReminderKind.SHOPPING -> CookReminder.content(enabled, today, plannedNow, recipes.observeRecipes().first())?.let {
                    notifications.cancel(ReminderKind.PROPOSAL.notificationId)
                    notifications.notify(kind.notificationId, buildShopping(it))
                }
                ReminderKind.COOKED -> CookReminder.content(enabled, today, plannedNow, recipes.observeRecipes().first())?.let {
                    notifications.cancel(ReminderKind.PROPOSAL.notificationId)
                    notifications.notify(kind.notificationId, buildCooked(it, today))
                }
            }
        }
        sync()
    }

    /**
     * "Planen": puts the proposed recipe on Geplant for today (moving it there if it was planned for
     * another day) and shows the confirmation in the proposal's place.
     */
    suspend fun planProposal(recipeId: Long) {
        val today = LocalDate.now()
        val proposal = proposalOf(recipeId, today) ?: return notifications.cancel(ReminderKind.PROPOSAL.notificationId)
        val previous = planned.observe().first().firstOrNull { it.recipeId == recipeId }
        // Shown before planning, so the update that follows finds a confirmation, not a proposal no longer wanted.
        if (canNotify()) notifications.notify(ReminderKind.PROPOSAL.notificationId, buildConfirmation(proposal, previous))
        planned.plan(recipeId, today)
    }

    /** "Rückgängig" of the confirmation: the recipe back where it was before "Planen", and the proposal again. */
    suspend fun undoPlanProposal(recipeId: Long, previous: PlannedRecipe?) {
        val proposal = proposalOf(recipeId, LocalDate.now())
        if (proposal != null && canNotify()) {
            notifications.notify(ReminderKind.PROPOSAL.notificationId, buildProposal(proposal))
        } else {
            notifications.cancel(ReminderKind.PROPOSAL.notificationId)
        }
        planned.undoPlan(recipeId, previous)
    }

    /** "Ja": logs the recipe as cooked today, unless it was handled in the app in the meantime. */
    suspend fun answerYes(recipeId: Long) {
        notifications.cancel(ReminderKind.COOKED.notificationId)
        if (planned.observe().first().any { it.recipeId == recipeId }) planned.cook(recipeId, LocalDate.now())
    }

    /** "Nein, morgen" / "Keins, morgen": moves the recipes asked about on [askedOn] to the next day. */
    suspend fun answerNo(recipeIds: List<Long>, askedOn: LocalDate) {
        notifications.cancel(ReminderKind.COOKED.notificationId)
        planned.postpone(recipeIds, askedOn)
    }

    /** "Nein" of a notification from before "Nein, morgen", which carries no recipes. */
    fun dismissCooked() = notifications.cancel(ReminderKind.COOKED.notificationId)

    /** The recipe to propose, picked now; null when none is wanted or none is left to propose. */
    private suspend fun proposal(enabled: Boolean, today: LocalDate, planned: List<PlannedRecipe>): Proposal? {
        if (!CookReminder.proposalWanted(enabled, today, planned, recipes.observeRecipes().first())) return null
        val id = suggester.suggest(today, 1, planned.mapTo(mutableSetOf()) { it.recipeId }).firstOrNull() ?: return null
        return proposalOf(id, today)
    }

    /** The recipe with its facts on [today]; null if it no longer exists. */
    private suspend fun proposalOf(id: Long, today: LocalDate): Proposal? {
        val recipe = recipes.getRecipe(id) ?: return null
        val lines = recipes.getLines(id)
        val byId = ingredients.observeIngredients().first().associateBy { it.id }
        val stats = RecipeStats.of(recipe, lines, byId, ingredients.observeContents().first())
        val facts = ProposalFacts.of(
            newPlantPoints = suggester.week(today).newPoints(lines.map { it.ingredientId }),
            plantPoints = stats.plantPoints,
            proteinPerPortion = stats.proteinPerPortion,
            price = RecipePrice.calculate(priceLines(lines, byId), recipe.servings, today),
        )
        return Proposal(recipe, facts)
    }

    private class Proposal(val recipe: Recipe, val facts: List<ProposalFact>)

    private fun schedule(settings: Settings, planned: List<PlannedRecipe>) {
        val now = LocalDateTime.now()
        ReminderKind.entries.forEach { kind ->
            val reminder = settings.reminder(kind)
            val next = when (kind) {
                ReminderKind.COOKED -> CookReminder.nextAt(now, reminder.time, reminder.enabled, planned.map { it.plannedOn })
                ReminderKind.PROPOSAL, ReminderKind.SHOPPING -> CookReminder.nextDailyAt(now, reminder.time, reminder.enabled)
            }
            val alarm = broadcast(ReminderReceiver.ACTION_SHOW, kind.requestCode) {
                putExtra(ReminderReceiver.EXTRA_KIND, kind.name)
            }
            if (next == null) {
                alarms.cancel(alarm)
            } else {
                // A 10-minute window is the tightest without the exact-alarm permission; plain inexact
                // alarms may come up to an hour late.
                val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, ALARM_WINDOW_MILLIS, alarm)
            }
        }
    }

    private fun canNotify() =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** The recipe of the confirmation showing in the proposal's place; null if none is showing. */
    private fun confirmedRecipeId(): Long? =
        notifications.activeNotifications
            .firstOrNull { it.id == ReminderKind.PROPOSAL.notificationId }
            ?.notification?.extras?.getLong(EXTRA_CONFIRMED_RECIPE_ID, -1)
            ?.takeIf { it >= 0 }

    private fun builder(kind: ReminderKind, openRecipeId: Long?): Notification.Builder {
        ensureChannels()
        return Notification.Builder(context, kind.channelId)
            // The kind is the title, unless the notification names its own; the question goes below it.
            .setContentTitle(context.getString(kind.channelName))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open(kind, openRecipeId))
            .setAutoCancel(true)
    }

    private fun open(kind: ReminderKind, openRecipeId: Long?): PendingIntent =
        PendingIntent.getActivity(
            context,
            kind.requestCode + OPEN_REQUEST_OFFSET,
            Intent(context, MainActivity::class.java)
                // A fresh start lands on Speiseplan, the start destination, or on the recipe it is given.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .apply { if (openRecipeId != null) putExtra(MainActivity.EXTRA_RECIPE_ID, openRecipeId) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    // Both only alert once, so swapping one for the other in place makes no second sound.
    private fun buildProposal(proposal: Proposal): Notification {
        val plan = broadcast(ReminderReceiver.ACTION_PLAN, REQUEST_PLAN) {
            putExtra(ReminderReceiver.EXTRA_RECIPE_ID, proposal.recipe.id)
        }
        return builder(ReminderKind.PROPOSAL, proposal.recipe.id)
            .setContentTitle(proposal.recipe.title)
            .setContentText(ProposalFacts.line(proposal.facts, ::factText))
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_plan), plan).build())
            .build()
    }

    /** "Für heute geplant: …"; [previous] is the recipe's Geplant entry before "Planen", for Rückgängig. */
    private fun buildConfirmation(proposal: Proposal, previous: PlannedRecipe?): Notification {
        val undo = broadcast(ReminderReceiver.ACTION_UNDO_PLAN, REQUEST_UNDO_PLAN) {
            putExtra(ReminderReceiver.EXTRA_RECIPE_ID, proposal.recipe.id)
            if (previous != null) putExtra(ReminderReceiver.EXTRA_DATE, previous.plannedOn.toEpochDay())
        }
        return builder(ReminderKind.PROPOSAL, proposal.recipe.id)
            .setContentTitle(context.getString(R.string.proposal_planned_title, proposal.recipe.title))
            .setContentText(ProposalFacts.line(proposal.facts, ::factText))
            .setOnlyAlertOnce(true)
            .addExtras(Bundle().apply { putLong(EXTRA_CONFIRMED_RECIPE_ID, proposal.recipe.id) })
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_undo), undo).build())
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.action_open_recipe),
                    open(ReminderKind.PROPOSAL, proposal.recipe.id),
                ).build(),
            )
            .build()
    }

    private fun factText(fact: ProposalFact): String = when (fact) {
        is ProposalFact.NewPlants -> context.getString(R.string.proposal_new_plants, fact.points)
        is ProposalFact.Plants -> context.resources.getQuantityString(R.plurals.proposal_plants, fact.points, fact.points)
        is ProposalFact.Protein -> context.getString(R.string.proposal_protein, fact.grams)
        is ProposalFact.Price ->
            if (fact.euros == 0.0) context.getString(R.string.recipe_price_below)
            else context.getString(R.string.recipe_price_value, formatEuros(fact.euros))
    }

    private fun buildShopping(content: ReminderContent): Notification =
        when (content) {
            is ReminderContent.Single ->
                builder(ReminderKind.SHOPPING, content.recipeId)
                    .setContentText(context.getString(R.string.shopping_text, content.title))
                    .build()
            is ReminderContent.Several ->
                builder(ReminderKind.SHOPPING, content.recipeIds.first())
                    .setContentText(context.getString(R.string.shopping_text, content.titles.joinToString(", ")))
                    .setStyle(listed(R.string.shopping_several_question, content.titles))
                    .build()
        }

    private fun buildCooked(content: ReminderContent, askedOn: LocalDate): Notification {
        val builder = builder(ReminderKind.COOKED, openRecipeId = null)
        val ids = when (content) {
            is ReminderContent.Single -> listOf(content.recipeId)
            is ReminderContent.Several -> content.recipeIds
        }
        val no = broadcast(ReminderReceiver.ACTION_NO, REQUEST_NO) {
            putExtra(ReminderReceiver.EXTRA_RECIPE_IDS, ids.toLongArray())
            putExtra(ReminderReceiver.EXTRA_DATE, askedOn.toEpochDay())
        }
        return when (content) {
            is ReminderContent.Single -> {
                val yes = broadcast(ReminderReceiver.ACTION_YES, REQUEST_YES) {
                    putExtra(ReminderReceiver.EXTRA_RECIPE_ID, content.recipeId)
                }
                builder
                    .setContentText(context.getString(R.string.reminder_single_text, content.title))
                    .addAction(Notification.Action.Builder(null, context.getString(R.string.action_yes), yes).build())
                    .addAction(Notification.Action.Builder(null, context.getString(R.string.action_no_tomorrow), no).build())
                    .build()
            }
            // No Ja: it can't tell which one was cooked; tapping the notification opens Speiseplan for that.
            is ReminderContent.Several ->
                builder
                    .setContentText(context.getString(R.string.reminder_several_text, content.titles.joinToString(", ")))
                    .setStyle(listed(R.string.reminder_several_question, content.titles))
                    .addAction(Notification.Action.Builder(null, context.getString(R.string.action_none_tomorrow), no).build())
                    .build()
        }
    }

    /** Expanded: the question, then one title per line. */
    private fun listed(question: Int, titles: List<String>): Notification.BigTextStyle =
        Notification.BigTextStyle().bigText(
            context.getString(question) + titles.joinToString("") { "\n• $it" },
        )

    private fun broadcast(action: String, requestCode: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java).setAction(action).apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun ensureChannels() {
        ReminderKind.entries.forEach { kind ->
            notifications.createNotificationChannel(
                NotificationChannel(kind.channelId, context.getString(kind.channelName), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(kind.channelDescription) },
            )
        }
    }

    private val ReminderKind.channelId
        get() = when (this) {
            ReminderKind.PROPOSAL -> "proposal"
            ReminderKind.SHOPPING -> "shopping"
            ReminderKind.COOKED -> "cook_reminder"
        }

    private val ReminderKind.channelName
        get() = when (this) {
            ReminderKind.PROPOSAL -> R.string.proposal_channel
            ReminderKind.SHOPPING -> R.string.shopping_channel
            ReminderKind.COOKED -> R.string.reminder_channel
        }

    private val ReminderKind.channelDescription
        get() = when (this) {
            ReminderKind.PROPOSAL -> R.string.proposal_channel_description
            ReminderKind.SHOPPING -> R.string.shopping_channel_description
            ReminderKind.COOKED -> R.string.reminder_channel_description
        }

    // COOKED keeps the id and request code it had as the only reminder, so a pending alarm survives an update.
    private val ReminderKind.notificationId
        get() = when (this) {
            ReminderKind.COOKED -> 1
            ReminderKind.PROPOSAL -> 2
            ReminderKind.SHOPPING -> 3
        }

    private val ReminderKind.requestCode
        get() = when (this) {
            ReminderKind.COOKED -> REQUEST_SHOW
            ReminderKind.PROPOSAL -> REQUEST_SHOW_PROPOSAL
            ReminderKind.SHOPPING -> REQUEST_SHOW_SHOPPING
        }

    private companion object {
        const val ALARM_WINDOW_MILLIS = 10 * 60 * 1000L
        const val REQUEST_SHOW = 0
        const val REQUEST_YES = 1
        const val REQUEST_NO = 2
        const val REQUEST_SHOW_PROPOSAL = 4
        const val REQUEST_SHOW_SHOPPING = 5
        const val REQUEST_PLAN = 6
        const val REQUEST_UNDO_PLAN = 7

        /** Marks the proposal's confirmation, with the recipe it planned. */
        const val EXTRA_CONFIRMED_RECIPE_ID = "com.example.plantry.confirmedRecipeId"

        /** Added to a kind's request code for the activity intent, which must not share a code with the alarm's. */
        const val OPEN_REQUEST_OFFSET = 10
    }
}
