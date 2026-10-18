package com.example.plantry.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** How a bar is drawn: [STRONG] in the dark tone, [WEAK] in the light one, [OUTLINE] as an outline in the light one. */
enum class BarTone { STRONG, WEAK, OUTLINE }

/**
 * One bar spanning [start] to [end] on the chart's axis, drawn centred on that span; a null [value]
 * is an empty slot. [badge] puts a ✓ badge on top where the bar is wide enough.
 */
data class ChartBar(val start: Float, val end: Float, val value: Double?, val tone: BarTone = BarTone.WEAK, val badge: Boolean = false)

/** How an [AxisLabel] marks its place on the baseline. */
enum class AxisMark { NONE, TICK, DOT }

/** A label below the baseline at [at] on the chart's axis, [text] optional. */
data class AxisLabel(val at: Float, val text: String?, val mark: AxisMark = AxisMark.NONE)

private val ChartHeight = 200.dp
private val MaxBarWidth = 36.dp
private val MinBadgeBarWidth = 14.dp
private const val FadedAlpha = 0.3f

/**
 * A bar chart drawn on a Canvas: capsule bars along an axis from 0 to [axisLength], Y labels on the
 * right at 0, ⅓, ⅔ and the highest value, an optional [goal] line labelled in the accent colour, and
 * [labels] below the baseline. Tapping a bar with a value calls [onSelect] with its index, tapping it
 * again or anywhere else on the chart with null; the [selected] bar stays, the others fade. Screen
 * readers read [description] instead.
 */
@Composable
fun BarChart(
    bars: List<ChartBar>,
    axisLength: Float,
    labels: List<AxisLabel>,
    description: String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    goal: Double? = null,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val goalStyle = labelStyle.copy(color = MaterialTheme.colorScheme.tertiary)
    val colors = ChartColors(
        strong = MaterialTheme.colorScheme.primary,
        // Primary is light on dark surfaces, so the weaker tone is the darker container there.
        weak = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.primaryFixedDim
        },
        onStrong = MaterialTheme.colorScheme.onPrimary,
        goal = MaterialTheme.colorScheme.tertiary,
        grid = MaterialTheme.colorScheme.outlineVariant,
    )
    val currentBars by rememberUpdatedState(bars)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .clearAndSetSemantics { contentDescription = description }
            .pointerInput(axisLength) {
                detectTapGestures { tap ->
                    val plotWidth = size.width - yLabelWidth(this@pointerInput.density)
                    val at = tap.x / plotWidth * axisLength
                    val hit = currentBars.indexOfFirst { it.value != null && at >= it.start && at <= it.end }.takeIf { it >= 0 }
                    currentOnSelect(if (hit == currentSelected) null else hit)
                }
            },
    ) {
        val labelHeight = measurer.measure("0", labelStyle).size.height.toFloat()
        val plotWidth = size.width - yLabelWidth(density)
        val baseline = size.height - labelHeight - 10.dp.toPx()
        val badgeRoom = 22.dp.toPx()
        val max = bars.mapNotNull { it.value }.maxOrNull() ?: 0.0
        val top = maxOf(max, goal ?: 0.0, 1.0)
        fun y(value: Double) = baseline - ((value / top) * (baseline - badgeRoom)).toFloat()
        fun x(at: Float) = at / axisLength * plotWidth

        // Grid and Y labels; one too close to the goal label gives way to it.
        val goalY = goal?.let(::y)
        val yValues = if (max > 0) listOf(0.0, max / 3, max * 2 / 3, max) else listOf(0.0)
        yValues.forEach { value ->
            val lineY = y(value)
            drawLine(colors.grid, Offset(0f, lineY), Offset(plotWidth, lineY), strokeWidth = 1.dp.toPx())
            if (goalY == null || abs(goalY - lineY) > labelHeight) {
                drawLabel(measurer, value.roundToInt().toString(), labelStyle, plotWidth + 6.dp.toPx(), lineY - labelHeight / 2)
            }
        }

        val spanWidth = bars.minOfOrNull { x(it.end) - x(it.start) } ?: 0f
        val barWidth = min(spanWidth * 0.6f, MaxBarWidth.toPx())
        bars.forEachIndexed { index, bar ->
            val value = bar.value ?: return@forEachIndexed
            val alpha = if (selected == null || selected == index) 1f else FadedAlpha
            drawBar(bar, value, x((bar.start + bar.end) / 2), barWidth, baseline, ::y, colors, alpha, MinBadgeBarWidth.toPx())
        }

        if (goal != null && goalY != null) {
            drawLine(colors.goal, Offset(0f, goalY), Offset(plotWidth, goalY), strokeWidth = 2.dp.toPx())
            drawLabel(measurer, goal.roundToInt().toString(), goalStyle, plotWidth + 6.dp.toPx(), goalY - labelHeight / 2)
        }

        labels.forEach { label ->
            val labelX = x(label.at)
            when (label.mark) {
                AxisMark.NONE -> {}
                AxisMark.TICK -> drawLine(colors.grid, Offset(labelX, baseline), Offset(labelX, baseline + 4.dp.toPx()), 1.dp.toPx())
                AxisMark.DOT -> drawCircle(colors.grid, 2.dp.toPx(), Offset(labelX, baseline + 4.dp.toPx()))
            }
            label.text?.let { text ->
                val measured = measurer.measure(text, labelStyle)
                val textX = (labelX - measured.size.width / 2).coerceIn(0f, size.width - measured.size.width)
                drawText(measured, topLeft = Offset(textX, baseline + 8.dp.toPx()))
            }
        }
    }
}

private class ChartColors(val strong: Color, val weak: Color, val onStrong: Color, val goal: Color, val grid: Color)

/** Room on the right for the Y labels. */
private fun yLabelWidth(density: Float) = 32 * density

private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, style: TextStyle, x: Float, y: Float) {
    drawText(measurer.measure(text, style), topLeft = Offset(x, y))
}

/** A capsule from the baseline up to [value]; at least round, and a flat stub for 0. */
private fun DrawScope.drawBar(
    bar: ChartBar,
    value: Double,
    centerX: Float,
    width: Float,
    baseline: Float,
    y: (Double) -> Float,
    colors: ChartColors,
    alpha: Float,
    minBadgeWidth: Float,
) {
    val height = if (value <= 0) 3.dp.toPx() else maxOf(baseline - y(value), width)
    val stroke = 2.dp.toPx()
    val color = if (bar.tone == BarTone.STRONG) colors.strong else colors.weak
    val left = centerX - width / 2
    val topY = baseline - height
    val radius = CornerRadius(min(width, height) / 2)
    if (bar.tone == BarTone.OUTLINE) {
        drawRoundRect(
            color,
            Offset(left + stroke / 2, topY + stroke / 2),
            Size(width - stroke, height - stroke),
            radius,
            style = Stroke(stroke),
            alpha = alpha,
        )
    } else {
        drawRoundRect(color, Offset(left, topY), Size(width, height), radius, alpha = alpha)
    }
    if (bar.badge && width >= minBadgeWidth) {
        drawCheckBadge(Offset(centerX, topY - 10.dp.toPx()), 9.dp.toPx(), colors.strong, colors.onStrong, alpha)
    }
}

/** A scalloped badge with a check mark, as on the Statistik screen's reached weeks. */
internal fun DrawScope.drawCheckBadge(center: Offset, radius: Float, fill: Color, check: Color, alpha: Float = 1f) {
    drawPath(scallopedCircle(center, radius), fill, alpha = alpha)
    val mark = Path().apply {
        moveTo(center.x - radius * 0.42f, center.y + radius * 0.02f)
        lineTo(center.x - radius * 0.1f, center.y + radius * 0.34f)
        lineTo(center.x + radius * 0.45f, center.y - radius * 0.3f)
    }
    drawPath(mark, check, alpha = alpha, style = Stroke(radius * 0.22f, cap = StrokeCap.Round))
}

/** A circle with [Scallops] gentle bumps around its edge. */
private fun scallopedCircle(center: Offset, radius: Float): Path = Path().apply {
    val steps = 120
    for (i in 0..steps) {
        val angle = 2 * PI * i / steps
        val r = radius * (1 + 0.07 * cos(Scallops * angle))
        val point = Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat())
        if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

private const val Scallops = 10
