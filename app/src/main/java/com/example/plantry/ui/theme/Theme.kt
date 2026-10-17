package com.example.plantry.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun PlantryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) PlantryDarkColors else PlantryLightColors, content = content)
}

/** The fresh green of recipe highlights; lighter on dark surfaces so it stays readable. */
val highlightGreen: Color
    @Composable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF6FD58B) else Color(0xFF2E9E4F)
