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
import com.example.plantry.MainActivity
import com.example.plantry.R
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.planner.WeeklyPlantPointsGoal
import com.example.plantry.data.reminder.FriendlyDuration
import com.example.plantry.data.reminder.MoodHook
import com.example.plantry.data.reminder.Notice
import com.example.plantry.data.reminder.ProposalFact
import com.example.plantry.data.reminder.ProposalFacts
import com.example.plantry.data.reminder.ProposalHook
import com.example.plantry.data.reminder.ProposalText
import com.example.plantry.data.reminder.RecipeProposal
import com.example.plantry.data.reminder.ReminderContent
import com.example.plantry.data.reminder.Reminders
import com.example.plantry.data.settings.ReminderKind
import com.example.plantry.ui.formatEuros
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The notifications [Reminders] decides on: a recipe proposal and a shopping reminder at their times,
 * and the "Hast du heute … gekocht?" question on the evening of a planned day. Keeps one alarm per
 * kind set, renders each [Notice] and turns the buttons back into [Reminders] calls. The proposal's
 * "Planen" turns it into a "Für heute geplant" confirmation in its place; "Anderer Vorschlag" opens
 * Vorschläge without it.
 */
class ReminderNotifications(
    private val context: Context,
    private val reminders: Reminders,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    /** Keeps the alarms in step with the settings and Geplant while the process lives, and cancels what went stale. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            reminders.observe().collect { update ->
                schedule(update.alarms)
                update.stale.forEach { notifications.cancel(it.notificationId) }
            }
        }
    }

    /** Sets or cancels the alarms from the stored state, e.g. after a reboot or a time zone change. */
    suspend fun sync() = schedule(reminders.alarms())

    /** An alarm went off: shows the notification if there is something to say and sets the next alarm. */
    suspend fun show(kind: ReminderKind) {
        // Without the permission nothing is decided, so no proposal is logged as shown.
        if (canNotify()) {
            when (val notice = reminders.due(kind)) {
                is Notice.Proposal -> notifications.notify(kind.notificationId, buildProposal(notice.proposal, notice.eventId))
                // One Plantry notification at a time: these replace a proposal or its confirmation.
                is Notice.Shopping -> {
                    notifications.cancel(ReminderKind.PROPOSAL.notificationId)
                    notifications.notify(kind.notificationId, buildShopping(notice.content))
                }
                is Notice.CookedQuestion -> {
                    notifications.cancel(ReminderKind.PROPOSAL.notificationId)
                    notifications.notify(kind.notificationId, buildCooked(notice.content, notice.askedOn))
                }
                is Notice.Confirmation, null -> Unit
            }
        }
        sync()
    }

    /** "Planen": the confirmation in the proposal's place. [eventId] is null for a notification from before the log. */
    suspend fun planProposal(recipeId: Long, eventId: Long?) {
        val confirmation = reminders.plan(recipeId, eventId)
        if (confirmation != null && canNotify()) {
            notifications.notify(
                ReminderKind.PROPOSAL.notificationId,
                buildConfirmation(confirmation.proposal, confirmation.previous, confirmation.eventId),
            )
        } else {
            notifications.cancel(ReminderKind.PROPOSAL.notificationId)
        }
    }

    /** "Rückgängig" of the confirmation: the same proposal again, if one is still wanted. */
    suspend fun undoPlanProposal(recipeId: Long, previous: PlannedRecipe?, eventId: Long?) {
        val proposal = reminders.undoPlan(recipeId, previous, eventId)
        if (proposal != null && canNotify()) {
            notifications.notify(ReminderKind.PROPOSAL.notificationId, buildProposal(proposal.proposal, proposal.eventId))
        } else {
            notifications.cancel(ReminderKind.PROPOSAL.notificationId)
        }
    }

    /**
     * The app was opened from a notification: its body or "Rezept öffnen", or "Anderer Vorschlag"
     * ([otherSuggestions]), which doesn't cancel the proposal itself. [eventId] is the proposal's
     * log row, if it came from one.
     */
    suspend fun opened(eventId: Long?, otherSuggestions: Boolean) {
        if (otherSuggestions) notifications.cancel(ReminderKind.PROPOSAL.notificationId)
        if (eventId != null) reminders.opened(eventId, otherSuggestions)
    }

    /** The proposal or its confirmation was swiped away. */
    suspend fun proposalDismissed(eventId: Long) = reminders.dismissed(eventId)

    /** "Ja": logs the recipe as cooked today, unless it was handled in the app in the meantime. */
    suspend fun answerYes(recipeId: Long) {
        notifications.cancel(ReminderKind.COOKED.notificationId)
        reminders.yes(recipeId)
    }

    /** "Nein, morgen" / "Keins, morgen": moves the recipes asked about on [askedOn] to the next day. */
    suspend fun answerNo(recipeIds: List<Long>, askedOn: LocalDate) {
        notifications.cancel(ReminderKind.COOKED.notificationId)
        reminders.no(recipeIds, askedOn)
    }

    /** "Nein" of a notification from before "Nein, morgen", which carries no recipes. */
    fun dismissCooked() = notifications.cancel(ReminderKind.COOKED.notificationId)

    private fun schedule(next: Map<ReminderKind, LocalDateTime?>) {
        next.forEach { (kind, at) ->
            val alarm = broadcast(ReminderReceiver.ACTION_SHOW, kind.requestCode) {
                putExtra(ReminderReceiver.EXTRA_KIND, kind.name)
            }
            if (at == null) {
                alarms.cancel(alarm)
            } else {
                // A 10-minute window is the tightest without the exact-alarm permission; plain inexact
                // alarms may come up to an hour late.
                val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, ALARM_WINDOW_MILLIS, alarm)
            }
        }
    }

    private fun canNotify() =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun builder(kind: ReminderKind, openRecipeId: Long?, proposalEventId: Long? = null): Notification.Builder {
        ensureChannels()
        return Notification.Builder(context, kind.channelId)
            // The kind is the title, unless the notification names its own; the question goes below it.
            .setContentTitle(context.getString(kind.channelName))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open(kind, openRecipeId, proposalEventId))
            .setAutoCancel(true)
    }

    private fun open(kind: ReminderKind, openRecipeId: Long?, proposalEventId: Long?): PendingIntent =
        activity(kind.requestCode + OPEN_REQUEST_OFFSET) {
            if (openRecipeId != null) putExtra(MainActivity.EXTRA_RECIPE_ID, openRecipeId)
            if (proposalEventId != null) putExtra(MainActivity.EXTRA_PROPOSAL_EVENT_ID, proposalEventId)
        }

    private fun activity(requestCode: Int, extras: Intent.() -> Unit): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                // A fresh start lands on Speiseplan, the start destination, or on what the extras ask for.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    // Both only alert once, so swapping one for the other in place makes no second sound.
    private fun buildProposal(proposal: RecipeProposal, eventId: Long?): Notification {
        val plan = broadcast(ReminderReceiver.ACTION_PLAN, REQUEST_PLAN) {
            putExtra(ReminderReceiver.EXTRA_RECIPE_ID, proposal.recipe.id)
            if (eventId != null) putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
        }
        val other = activity(REQUEST_OTHER_SUGGESTIONS) {
            putExtra(MainActivity.EXTRA_OTHER_SUGGESTIONS_THAN, proposal.recipe.id)
            if (eventId != null) putExtra(MainActivity.EXTRA_PROPOSAL_EVENT_ID, eventId)
        }
        val facts = ProposalFacts.line(proposal.facts, ::factText)
        val name = proposal.recipe.title
        val factual = proposal.hook
        val text = when (val title = proposal.titleHook) {
            is ProposalHook -> ProposalText.of(name, hookLabel(title), hookSentence(title), facts)
            is MoodHook -> ProposalText.mood(name, title, factual?.let(::hookSentence), facts)
            null -> if (factual == null) ProposalText(name, facts) else ProposalText.behind(name, hookLabel(factual), hookSentence(factual), facts)
        }
        return builder(ReminderKind.PROPOSAL, proposal.recipe.id, eventId)
            .setContentTitle(text.title)
            .setContentText(facts)
            .setStyle(Notification.BigTextStyle().bigText(text.expanded))
            .setOnlyAlertOnce(true)
            .setDeleteIntent(dismissed(eventId))
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_plan), plan).build())
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_other_suggestion), other).build())
            .build()
    }

    /** "Für heute geplant: …"; [previous] is the recipe's Geplant entry before "Planen", for Rückgängig. */
    private fun buildConfirmation(proposal: RecipeProposal, previous: PlannedRecipe?, eventId: Long?): Notification {
        val undo = broadcast(ReminderReceiver.ACTION_UNDO_PLAN, REQUEST_UNDO_PLAN) {
            putExtra(ReminderReceiver.EXTRA_RECIPE_ID, proposal.recipe.id)
            if (previous != null) putExtra(ReminderReceiver.EXTRA_DATE, previous.plannedOn.toEpochDay())
            if (eventId != null) putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
        }
        return builder(ReminderKind.PROPOSAL, proposal.recipe.id, eventId)
            .setContentTitle(context.getString(R.string.proposal_planned_title, proposal.recipe.title))
            .setContentText(ProposalFacts.line(proposal.facts, ::factText))
            .setOnlyAlertOnce(true)
            .setDeleteIntent(dismissed(eventId))
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_undo), undo).build())
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.action_open_recipe),
                    open(ReminderKind.PROPOSAL, proposal.recipe.id, eventId),
                ).build(),
            )
            .build()
    }

    /** Swiping the proposal or its confirmation away; nothing to record without a log row. */
    private fun dismissed(eventId: Long?): PendingIntent? =
        eventId?.let {
            broadcast(ReminderReceiver.ACTION_PROPOSAL_DISMISSED, REQUEST_PROPOSAL_DISMISSED) {
                putExtra(ReminderReceiver.EXTRA_EVENT_ID, it)
            }
        }

    private fun hookLabel(hook: ProposalHook): String = context.getString(
        when (hook) {
            ProposalHook.WeeklyGoal -> R.string.proposal_hook_weekly_goal
            ProposalHook.New -> R.string.proposal_hook_new
            is ProposalHook.LongAgo -> R.string.proposal_hook_long_ago
            is ProposalHook.ProteinPower -> R.string.proposal_hook_protein_power
        },
    )

    private fun hookSentence(hook: ProposalHook): String = when (hook) {
        ProposalHook.WeeklyGoal -> context.getString(R.string.proposal_hook_weekly_goal_sentence, WeeklyPlantPointsGoal)
        ProposalHook.New -> context.getString(R.string.proposal_hook_new_sentence)
        is ProposalHook.LongAgo -> context.getString(R.string.proposal_hook_long_ago_sentence, FriendlyDuration.ago(hook.days))
        is ProposalHook.ProteinPower -> context.getString(R.string.proposal_hook_protein_power_sentence, hook.grams)
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
        const val REQUEST_PROPOSAL_DISMISSED = 8

        // Not 10 + a kind's request code: those are the activity intents of the notifications' bodies.
        const val REQUEST_OTHER_SUGGESTIONS = 20

        /** Added to a kind's request code for the activity intent, which must not share a code with the alarm's. */
        const val OPEN_REQUEST_OFFSET = 10
    }
}
