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
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.reminder.CookReminder
import com.example.plantry.data.reminder.ReminderContent
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
 * The "Habt ihr heute … gekocht?" notification on the evening of a planned day: keeps one alarm set
 * for the next such day while the reminder is on, shows the notification when it fires and answers
 * its Ja button.
 */
class CookReminders(
    private val context: Context,
    private val settings: SettingsRepository,
    private val planned: PlannedRepository,
    private val recipes: RecipeRepository,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    /** Keeps the alarm in step with the settings and Geplant while the process lives. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(settings.settings, planned.observe(), ::Pair).collect { (settings, planned) ->
                schedule(settings, planned)
                // Nothing left to ask today: an unanswered notification would only be stale.
                if (CookReminder.content(settings.reminderEnabled, LocalDate.now(), planned, recipes.observeRecipes().first()) == null) {
                    notifications.cancel(NOTIFICATION_ID)
                }
            }
        }
    }

    /** Sets or cancels the alarm from the stored state, e.g. after a reboot or a time zone change. */
    suspend fun sync() = schedule(settings.settings.value, planned.observe().first())

    /** The alarm went off: shows the reminder if there is something to ask and sets the next one. */
    suspend fun show() {
        val current = settings.settings.value
        val content = CookReminder.content(current.reminderEnabled, LocalDate.now(), planned.observe().first(), recipes.observeRecipes().first())
        if (content != null && canNotify()) notifications.notify(NOTIFICATION_ID, build(content))
        sync()
    }

    /** "Ja": logs the recipe as cooked today, unless it was handled in the app in the meantime. */
    suspend fun answerYes(recipeId: Long) {
        notifications.cancel(NOTIFICATION_ID)
        if (planned.observe().first().any { it.recipeId == recipeId }) planned.cook(recipeId, LocalDate.now())
    }

    /** "Nein": keeps it on Geplant until it drops off; no more reminders for it unless it is moved to a later day. */
    fun answerNo() = notifications.cancel(NOTIFICATION_ID)

    private fun schedule(settings: Settings, planned: List<PlannedRecipe>) {
        val alarm = broadcast(ReminderReceiver.ACTION_SHOW, REQUEST_SHOW)
        val next = CookReminder.nextAt(LocalDateTime.now(), settings.reminderTime, settings.reminderEnabled, planned.map { it.plannedOn })
        if (next == null) {
            alarms.cancel(alarm)
        } else {
            // A 10-minute window is the tightest without the exact-alarm permission; plain inexact
            // alarms may come up to an hour late.
            val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, ALARM_WINDOW_MILLIS, alarm)
        }
    }

    private fun canNotify() =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun build(content: ReminderContent): Notification {
        ensureChannel()
        val openCooking = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java)
                // A fresh start lands on Kochen, the start destination.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openCooking)
            .setAutoCancel(true)
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

    private fun ensureChannel() {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.reminder_channel_description) },
        )
    }

    private companion object {
        const val ALARM_WINDOW_MILLIS = 10 * 60 * 1000L
        const val CHANNEL_ID = "cook_reminder"
        const val NOTIFICATION_ID = 1
        const val REQUEST_SHOW = 0
        const val REQUEST_YES = 1
        const val REQUEST_NO = 2
        const val REQUEST_OPEN = 3
    }
}
