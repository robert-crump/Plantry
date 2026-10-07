package com.example.plantry

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.plantry.data.settings.ThemeMode
import com.example.plantry.ui.PlantryNavHost
import com.example.plantry.ui.theme.PlantryTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A notification's recipe opens once, not again after a rotation.
        val openRecipeId = if (savedInstanceState == null) {
            intent.getLongExtra(EXTRA_RECIPE_ID, -1).takeIf { it >= 0 }
        } else {
            null
        }
        val settingsRepository =(application as PlantryApplication).settingsRepository
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // The default bar styles follow the device, so a forced mode would get unreadable bar icons.
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
                )
            }
            PlantryTheme(darkTheme = darkTheme) {
                PlantryNavHost(openRecipeId)
            }
        }
    }

    companion object {
        /** The recipe a notification opens instead of Kochen. */
        const val EXTRA_RECIPE_ID = "openRecipeId"

        // The scrims androidx.activity uses by default.
        private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DarkScrim =Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
