package com.example.plantry.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.plantry.data.settings.SettingsRepository

/**
 * Asks for the notification permission the first time it is called, for the Geplant reminder.
 * Later calls do nothing; if denied, Geplant still works without the reminder.
 */
@Composable
fun rememberNotificationPermissionRequest(settings: SettingsRepository): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return remember(settings, launcher) {
        {
            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted && settings.takeNotificationPermissionRequest()) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
