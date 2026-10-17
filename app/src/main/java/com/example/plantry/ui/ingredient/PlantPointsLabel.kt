package com.example.plantry.ui.ingredient

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.plantry.R
import com.example.plantry.data.PlantPoints
import com.example.plantry.ui.currentLocale
import com.example.plantry.ui.recipe.formatPlantPoints

/** "1 Punkt", "0,25 Punkte", "0 Punkte" – the number in the locale's decimal format. */
@Composable
internal fun plantPointsLabel(points: PlantPoints): String = plantPointsLabel(points.value)

/** Like the [PlantPoints] one, for a sum such as a mix's "6 Punkte". */
@Composable
internal fun plantPointsLabel(points: Double): String = stringResource(
    if (points == 1.0) R.string.plant_points_one else R.string.plant_points_other,
    formatPlantPoints(points, currentLocale()),
)
