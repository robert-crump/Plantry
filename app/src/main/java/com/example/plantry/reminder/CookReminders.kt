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
import com.example.plantry.data.PlannedRepository
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.planner.RecipeSuggester
import com.example.plantry.data.reminder.CookReminder
import com.example.plantry.data.reminder.ReminderContent
import com.example.plantry.data.settings.ReminderKind
import com.example.plantry.data.settings.Settings
import com.example.plantry.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The notifications: a recipe proposal and a shopping reminder at their times, and the "Habt ihr
 * heute … gekocht?" question on the evening of a planned day. Keeps one alarm per kind set while
 * it is on, shows the notification when the alarm fires and answers the evening one's Ja button.
 */
class CookReminders(
    private val context: Context,
    private val settings: SettingsRepository,
    private val planned: PlannedRepository,
    private val recipes: RecipeRepository,
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
                if (!CookReminder.proposalWanted(settings.reminder(ReminderKind.PROPOSAL).enabled, today, planned, all)) {
                    notifications.cancel(ReminderKind.PROPOSAL.notificationId)
                }
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
                ReminderKind.SHOPPING -> CookReminder.content(enabled, today, plannedNow, recipes.observeRecipes().first())?.let {
                    notifications.notify(kind.notificationId, buildShopping(it))
                }
                ReminderKind.COOKED -> CookReminder.content(enabled, today, plannedNow, recipes.observeRecipes().first())?.let {
                    notifications.notify(kind.notificationId, buildCooked(it))
                }
            }
        }
        sync()
    }

    /** "Ja": logs the recipe as cooked today, unless it was handled in the app in the meantime. */
    suspend fun answerYes(recipeId: Long) {
        notifications.cancel(ReminderKind.COOKED.notificationId)
        if (planned.observe().first().any { it.recipeId == recipeId }) planned.cook(recipeId, LocalDate.now())
    }

    /** "Nein": keeps it on Geplant until it drops off; no more reminders for it unless it is moved to a later day. */
    fun answerNo() = notifications.cancel(ReminderKind.COOKED.notificationId)

    /** The recipe to propose, picked now; null when none is wanted or none is left to propose. */
    private suspend fun proposal(enabled: Boolean, today: LocalDate, planned: List<PlannedRecipe>): Recipe? {
        if (!CookReminder.proposalWanted(enabled, today, planned, recipes.observeRecipes().first())) return null
        val id = suggester.suggest(today, 1, planned.mapTo(mutableSetOf()) { it.recipeId }).firstOrNull() ?: return null
        return recipes.getRecipe(id)
    }

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

    private fun builder(kind: ReminderKind, openRecipeId: Long?): Notification.Builder {
        ensureChannels()
        val open = PendingIntent.getActivity(
            context,
            kind.requestCode + OPEN_REQUEST_OFFSET,
            Intent(context, MainActivity::class.java)
                // A fresh start lands on Kochen, the start destination, or on the recipe it is given.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .apply { if (openRecipeId != null) putExtra(MainActivity.EXTRA_RECIPE_ID, openRecipeId) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(context, kind.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setAutoCancel(true)
    }

    private fun buildProposal(recipe: Recipe): Notification =
        builder(ReminderKind.PROPOSAL, recipe.id)
            .setContentTitle(context.getString(R.string.proposal_title, recipe.title))
            .build()

    private fun buildShopping(content: ReminderContent): Notification =
        when (content) {
            is ReminderContent.Single ->
                builder(ReminderKind.SHOPPING, content.recipeId)
                    .setContentTitle(context.getString(R.string.shopping_single_title, content.title))
                    .build()
            is ReminderContent.Several ->
                builder(ReminderKind.SHOPPING, content.firstRecipeId)
                    .setContentTitle(context.getString(R.string.shopping_several_title))
                    .setContentText(content.titles.joinToString(", "))
                    .setStyle(Notification.BigTextStyle().bigText(content.titles.joinToString("\n")))
                    .build()
        }

    private fun buildCooked(content: ReminderContent): Notification {
        val builder = builder(ReminderKind.COOKED, openRecipeId = null)
        return when (content) {
            is ReminderContent.Single -> {
                val yes = broadcast(ReminderReceiver.ACTION_YES, REQUEST_YES) {
                    putExtra(ReminderReceiver.EXTRA_RECIPE_ID, content.recipeId)
                }
                val no = broadcast(ReminderReceiver.ACTION_NO, REQUEST_NO)
                builder
                    .setContentTitle(context.getString(R.string.reminder_single_title, content.title))
                    .addAction(Notification.Action.Builder(null, context.getString(R.string.action_yes), yes).build())
                    .addAction(Notification.Action.Builder(null, context.getString(R.string.action_no), no).build())
                    .build()
            }
            is ReminderContent.Several -> {
                val titles = content.titles.joinToString(", ")
                builder
                    .setContentTitle(context.getString(R.string.reminder_several_title))
                    .setContentText(titles)
                    .setStyle(Notification.BigTextStyle().bigText(content.titles.joinToString("\n")))
                    .build()
            }
        }
    }

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

        /** Added to a kind's request code for the activity intent, which must not share a code with the alarm's. */
        const val OPEN_REQUEST_OFFSET = 10
    }
}
