package com.example.plantry.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.plantry.PlantryApplication
import com.example.plantry.data.settings.ReminderKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The reminder alarm, the notification's Ja / Nein, and the system events after which the alarm is set again. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminders = (context.applicationContext as PlantryApplication).cookReminders
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_SHOW -> reminders.show(kindOf(intent))
                    ACTION_YES -> intent.getLongExtra(EXTRA_RECIPE_ID, -1).takeIf { it >= 0 }?.let { reminders.answerYes(it) }
                    ACTION_NO -> reminders.answerNo()
                    // Boot, app update, time or time zone change.
                    else -> reminders.sync()
                }
            } finally {
                pending.finish()
            }
        }
    }

    // An alarm set before the other kinds existed carries no kind and is the evening reminder.
    private fun kindOf(intent: Intent) =
        ReminderKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) } ?: ReminderKind.COOKED

    companion object {
        const val ACTION_SHOW = "com.example.plantry.reminder.SHOW"
        const val ACTION_YES = "com.example.plantry.reminder.YES"
        const val ACTION_NO = "com.example.plantry.reminder.NO"
        const val EXTRA_RECIPE_ID = "recipeId"
        const val EXTRA_KIND = "kind"
    }
}
