package com.example.plantry.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.plantry.R
import com.example.plantry.data.settings.ReminderKind
import com.example.plantry.data.settings.SettingsRepository

fun Context.hasNotificationPermission() =
    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Returns a function that calls [onResult] with true right away if the notification permission is
 * granted, and otherwise asks for it and calls [onResult] with the answer.
 */
@Composable
fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { currentOnResult(it) }
    return remember(context, launcher) {
        {
            if (context.hasNotificationPermission()) currentOnResult(true) else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Returns the function to call when a recipe is planned. The first time, it offers the evening
 * "gekocht?" reminder, unless that is on already; "Ja" turns it on and asks for the permission,
 * which stays unanswered or denied without turning the reminder on. Later calls do nothing.
 */
@Composable
fun rememberFirstPlanReminderOffer(settings: SettingsRepository): () -> Unit {
    var offering by rememberSaveable { mutableStateOf(false) }
    val request = rememberNotificationPermissionRequest { granted ->
        if (granted) settings.setReminderEnabled(ReminderKind.COOKED, true)
    }
    if (offering) {
        AlertDialog(
            onDismissRequest = { offering = false },
            title = { Text(stringResource(R.string.settings_reminder)) },
            text = { Text(stringResource(R.string.reminder_offer_message)) },
            confirmButton = {
                TextButton(onClick = {
                    offering = false
                    request()
                }) { Text(stringResource(R.string.action_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { offering = false }) { Text(stringResource(R.string.action_no)) }
            },
        )
    }
    return remember(settings) {
        {
            if (settings.takeNotificationPermissionRequest() && !settings.settings.value.reminder(ReminderKind.COOKED).enabled) {
                offering = true
            }
        }
    }
}
