package com.example.plantry.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.pill
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import kotlin.math.PI
import kotlin.math.sin

// Stand-ins for material3's Expressive LoadingIndicator and LinearWavyProgressIndicator, which
// are only in the 1.5 alphas, and those make the keyboard bounce between text fields (#124).
// Named like the originals, so going back is a matter of imports once 1.5 is stable.

/** The shapes [LoadingIndicator] morphs through, each fitted into the unit square. */
private val loadingShapes = listOf(
    RoundedPolygon.star(10, innerRadius = 0.75f, rounding = CornerRounding(0.12f)),
    RoundedPolygon.star(9, innerRadius = 0.8f, rounding = CornerRounding(0.4f)),
    RoundedPolygon(5, rounding = CornerRounding(0.3f)),
    RoundedPolygon.pill(width = 2f, height = 1.2f),
    RoundedPolygon.star(8, innerRadius = 0.85f, rounding = CornerRounding(0.25f)),
    RoundedPolygon.star(4, innerRadius = 0.6f, rounding = CornerRounding(0.5f)),
    RoundedPolygon.circle(8),
).map { it.normalized() }

private const val MorphMillis = 650

/** An indeterminate spinner: a shape that turns while morphing from one form into the next. */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val morphs = remember {
        loadingShapes.indices.map { Morph(loadingShapes[it], loadingShapes[(it + 1) % loadingShapes.size]) }
    }
    val androidPath = remember { android.graphics.Path() }
    val path = remember { androidPath.asComposePath() }
    val step by rememberInfiniteTransition(label = "loading").animateFloat(
        initialValue = 0f,
        targetValue = morphs.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(MorphMillis * morphs.size, easing = LinearEasing)),
        label = "loading step",
    )
    Canvas(modifier.size(48.dp)) {
        val index = step.toInt().coerceAtMost(morphs.lastIndex)
        val progress = FastOutSlowInEasing.transform(step - index)
        androidPath.rewind()
        morphs[index].toPath(progress, androidPath)
        // The shape takes 38 of the 48dp, like material3's.
        val side = size.minDimension * 38f / 48f
        translate((size.width - side) / 2, (size.height - side) / 2) {
            rotate((index + progress) * 90f + step * 30f, pivot = Offset(side / 2, side / 2)) {
                scale(side, side, pivot = Offset.Zero) { drawPath(path, color) }
            }
        }
    }
}

/**
 * An indeterminate progress bar whose moving indicator is a wave on a straight track, kept apart
 * by a small gap.
 */
@Composable
fun LinearWavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    val transition = rememberInfiniteTransition(label = "wavy progress")
    val head by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1750, easing = FastOutSlowInEasing)),
        label = "wavy head",
    )
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "wavy phase",
    )
    val wave = remember { Path() }
    Canvas(modifier.height(10.dp)) {
        val stroke = 4.dp.toPx()
        val amplitude = 3.dp.toPx()
        val wavelength = 24.dp.toPx()
        val gap = 4.dp.toPx()
        val centerY = size.height / 2
        val end = (head.coerceAtMost(1f) * size.width)
        val start = ((head - 0.5f).coerceAtLeast(0f) * size.width).coerceAtMost(end)

        fun track(from: Float, to: Float) {
            if (to - from > stroke) {
                drawLine(trackColor, Offset(from + stroke / 2, centerY), Offset(to - stroke / 2, centerY), stroke, StrokeCap.Round)
            }
        }
        if (end - start < 1f) {
            track(0f, size.width)
            return@Canvas
        }
        track(0f, start - gap)
        track(end + gap, size.width)

        wave.reset()
        var x = start
        while (true) {
            val y = centerY + amplitude * sin(2 * PI * (x / wavelength - phase)).toFloat()
            if (x == start) wave.moveTo(x, y) else wave.lineTo(x, y)
            if (x >= end) break
            x = (x + 2f).coerceAtMost(end)
        }
        drawPath(wave, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
