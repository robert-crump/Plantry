package com.example.plantry.ui.ingredient

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.plantry.R
import com.example.plantry.data.PlantPoints
import com.example.plantry.ui.currentLocale
import com.example.plantry.ui.recipe.formatPlantPoints

/** "1 Punkt", "0,25 Punkte", "0 Punkte" – the number in the locale's decimal format. */
@Composable
internal fun plantPointsLabel(points: PlantPoints): String = stringResource(
    if (points == PlantPoints.ONE) R.string.plant_points_one else R.string.plant_points_other,
    formatPlantPoints(points.value, currentLocale()),
)
