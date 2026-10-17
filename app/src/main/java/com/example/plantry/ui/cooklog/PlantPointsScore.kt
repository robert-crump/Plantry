package com.example.plantry.ui.cooklog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.plantry.R
import com.example.plantry.data.planner.WeeklyPlantPointsGoal
import com.example.plantry.data.planner.plantPointsGoalReached
import com.example.plantry.ui.theme.highlightGreen
import kotlin.math.roundToInt

/**
 * A leaf and [text], e.g. "23/30" or "+4", green and bold when [reached], otherwise in the muted
 * on-surface colour; screen readers say [description] instead.
 */
@Composable
fun PlantPointsScore(text: String, description: String, reached: Boolean, modifier: Modifier = Modifier) {
    val tint = if (reached) highlightGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Eco, contentDescription = null, Modifier.size(16.dp), tint = tint)
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = tint,
            fontWeight = if (reached) FontWeight.Bold else null,
        )
    }
}

/** A week's [plantPoints] against the goal, e.g. "23/30", green and bold once reached. */
@Composable
fun WeekPlantPointsScore(plantPoints: Double, modifier: Modifier = Modifier) {
    val points = plantPoints.roundToInt()
    PlantPointsScore(
        text = "$points/$WeeklyPlantPointsGoal",
        description = stringResource(R.string.cook_week_plant_points, points, WeeklyPlantPointsGoal),
        reached = plantPointsGoalReached(plantPoints),
        modifier = modifier,
    )
}
