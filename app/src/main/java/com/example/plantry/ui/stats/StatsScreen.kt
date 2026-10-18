package com.example.plantry.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.CookLogRepository
import com.example.plantry.data.planner.WeeklyPlantPointsGoal
import com.example.plantry.data.stats.NutrientBar
import com.example.plantry.data.stats.NutrientStats
import com.example.plantry.data.stats.PlantPointsStats
import com.example.plantry.data.stats.StatsCalendar
import com.example.plantry.data.stats.StatsPeriod
import com.example.plantry.data.stats.StatsRange
import com.example.plantry.data.stats.WeekPoints
import com.example.plantry.data.stats.dailyNutrient
import com.example.plantry.ui.cooklog.cookMonthLabel
import com.example.plantry.ui.cooklog.cookWeekLabel
import com.example.plantry.ui.cooklog.formatPlannedDate
import com.example.plantry.ui.cooklog.ProteinIcon
import com.example.plantry.ui.cooklog.weeklyPlantPoints
import com.example.plantry.ui.currentLocale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What the Statistik screen draws from: the first entry's date (null without entries), each week's
 * plant points by its Monday, and each day's logged protein and fibre, see [dailyNutrient].
 */
data class StatsData(
    val firstEntry: LocalDate?,
    val weeklyPlantPoints: Map<LocalDate, Double>,
    val dailyProtein: Map<LocalDate, Double> = emptyMap(),
    val dailyFibre: Map<LocalDate, Double> = emptyMap(),
)

class StatsViewModel(repository: CookLogRepository) : ViewModel() {
    /** Null until loaded, so the empty state doesn't flash. */
    val data: StateFlow<StatsData?> = combine(repository.observeHistory(), repository.observePlants()) { entries, plants ->
        StatsData(
            entries.minOfOrNull { it.cookedOn },
            weeklyPlantPoints(entries, plants),
            dailyNutrient(entries) { it.proteinPerPortion },
            dailyNutrient(entries) { it.fibrePerPortion },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** The charts on the Statistik screen; one bar among them can be selected. */
private enum class StatsChart { PLANT_POINTS, PROTEIN, FIBRE }

/**
 * Statistik, opened from Speiseplan: a Woche / Monat / 3 Monate toggle and the period title with
 * ‹ › and back-to-today drive the cards below; swiping steps periods too. It always opens on the
 * current week. The plant-points card shows a week's progress towards the goal, or a bar per week.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val stats = data ?: return@Scaffold
        if (stats.firstEntry == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.stats_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        val today = remember { LocalDate.now() }
        val calendar = remember(stats.firstEntry) { StatsCalendar(today, stats.firstEntry) }
        var range by rememberSaveable { mutableStateOf(StatsRange.WEEK) }
        var offset by rememberSaveable(range) { mutableIntStateOf(0) }
        val period = calendar.current(range).plus(offset.toLong())
        var selected by rememberSaveable(range, offset) { mutableStateOf<Pair<StatsChart, Int>?>(null) }
        fun selectedIn(chart: StatsChart) = selected?.takeIf { it.first == chart }?.second
        fun select(chart: StatsChart): (Int?) -> Unit = { index -> selected = index?.let { chart to it } }
        val canBack = calendar.canStepBack(period)
        val canForward = calendar.canStepForward(period)

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(canBack, canForward) {
                    var dragged = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = 0f },
                        onDragEnd = {
                            val threshold = 56.dp.toPx()
                            if (dragged > threshold && canBack) offset-- else if (dragged < -threshold && canForward) offset++
                        },
                    ) { _, amount -> dragged += amount }
                }
                .pointerInput(Unit) { detectTapGestures { selected = null } }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RangeToggle(range, onSelect = { range = it })
            PeriodHeader(
                title = periodTitle(period, today),
                canBack = canBack,
                canForward = canForward,
                onBack = { offset-- },
                onForward = { offset++ },
                onToday = { offset = 0 },
            )
            PlantPointsCard(
                PlantPointsStats.of(period, today, stats.firstEntry, stats.weeklyPlantPoints),
                period,
                today,
                selectedIn(StatsChart.PLANT_POINTS),
                onSelect = select(StatsChart.PLANT_POINTS),
            )
            NutrientCard(
                stringResource(R.string.stats_protein_title),
                ProteinIcon,
                stringResource(R.string.stats_protein_title),
                NutrientStats.of(period, stats.dailyProtein),
                period,
                today,
                selectedIn(StatsChart.PROTEIN),
                onSelect = select(StatsChart.PROTEIN),
            )
            NutrientCard(
                stringResource(R.string.stats_fibre_title),
                rememberVectorPainter(Icons.Filled.Grass),
                stringResource(R.string.stats_fibre_title),
                NutrientStats.of(period, stats.dailyFibre),
                period,
                today,
                selectedIn(StatsChart.FIBRE),
                onSelect = select(StatsChart.FIBRE),
            )
        }
    }
}

/** A full-width row of pills, the selected one filled. */
@Composable
private fun RangeToggle(range: StatsRange, onSelect: (StatsRange) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsRange.entries.forEach { option ->
            val chosen = option == range
            Button(
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
                colors = if (chosen) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                },
            ) { Text(stringResource(option.label), maxLines = 1) }
        }
    }
}

private val StatsRange.label: Int
    get() = when (this) {
        StatsRange.WEEK -> R.string.stats_range_week
        StatsRange.MONTH -> R.string.stats_range_month
        StatsRange.QUARTER -> R.string.stats_range_quarter
    }

@Composable
private fun PeriodHeader(
    title: String,
    canBack: Boolean,
    canForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onToday: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onBack, enabled = canBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.stats_previous))
        }
        FilledTonalIconButton(onClick = onForward, enabled = canForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.stats_next))
        }
        FilledTonalIconButton(onClick = onToday, enabled = canForward) {
            Icon(Icons.Filled.Today, stringResource(R.string.stats_today))
        }
    }
}

/** "Diese Woche", "5.–11. Okt", "Oktober 2026", "Aug–Okt 2026". */
@Composable
private fun periodTitle(period: StatsPeriod, today: LocalDate): String {
    val locale = currentLocale()
    val thisWeek = stringResource(R.string.cook_week_this)
    val lastWeek = stringResource(R.string.cook_week_last)
    return when (period.range) {
        StatsRange.WEEK -> cookWeekLabel(period.start, today, locale, thisWeek, lastWeek)
        else -> formatPeriodTitle(period, locale)
    }
}

/** A month as "Oktober 2026", three as "Aug–Okt 2026" or "Nov 2025 – Jan 2026". */
internal fun formatPeriodTitle(period: StatsPeriod, locale: Locale): String {
    val start = period.start
    val end = period.end
    return when {
        period.range == StatsRange.MONTH -> "${start.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)} ${start.year}"
        start.year == end.year -> "${cookMonthLabel(start, locale)}–${cookMonthLabel(end, locale)} ${end.year}"
        else -> "${cookMonthLabel(start, locale)} ${start.year} – ${cookMonthLabel(end, locale)} ${end.year}"
    }
}

@Composable
private fun StatsCard(title: String, icon: Painter, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

@Composable
private fun PlantPointsCard(stats: PlantPointsStats, period: StatsPeriod, today: LocalDate, selected: Int?, onSelect: (Int?) -> Unit) {
    StatsCard(stringResource(R.string.stats_plant_points_title), rememberVectorPainter(Icons.Filled.Eco)) {
        if (period.range == StatsRange.WEEK) {
            WeekProgress(stats.weeks.single())
        } else {
            WeeklyBars(stats, period, today, selected, onSelect)
        }
    }
}

/** "18 von 30 Pflanzenpunkten", a progress bar, "bisher" while the week runs and a ✓ badge once reached. */
@Composable
private fun WeekProgress(week: WeekPoints) {
    val points = week.points ?: 0.0
    Row(verticalAlignment = Alignment.CenterVertically) {
        BigNumber(
            points.roundToInt().toString(),
            stringResource(R.string.stats_week_of_goal, WeeklyPlantPointsGoal),
            Modifier.weight(1f),
        )
        if (week.reached) CheckBadge()
    }
    LinearProgressIndicator(
        progress = { min(1f, (points / WeeklyPlantPointsGoal).toFloat()) },
        modifier = Modifier.fillMaxWidth().height(10.dp),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
    if (week.current) {
        Text(
            stringResource(R.string.stats_week_so_far),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A bar per week with the goal line; the headline shows the average, or the tapped week. */
@Composable
private fun WeeklyBars(stats: PlantPointsStats, period: StatsPeriod, today: LocalDate, selected: Int?, onSelect: (Int?) -> Unit) {
    val locale = currentLocale()
    val thisWeek = stringResource(R.string.cook_week_this)
    val lastWeek = stringResource(R.string.cook_week_last)
    fun weekLabel(monday: LocalDate) = cookWeekLabel(monday, today, locale, thisWeek, lastWeek)

    val tapped = selected?.let { stats.weeks.getOrNull(it) }?.takeIf { it.points != null }
    if (tapped != null) {
        val points = tapped.points!!.roundToInt()
        BigNumber(points.toString(), pluralStringResource(R.plurals.stats_plant_points, points))
        Summary(weekLabel(tapped.monday))
    } else {
        val average = stats.average?.roundToInt()
        BigNumber(average?.toString() ?: "–", pluralStringResource(R.plurals.stats_plant_points_per_week, average ?: 0))
        Summary(
            if (stats.countedWeeks == 0) {
                stringResource(R.string.stats_no_finished_week)
            } else {
                pluralStringResource(R.plurals.stats_goal_weeks, stats.countedWeeks, stats.reachedWeeks, stats.countedWeeks)
            },
        )
    }
    val description = stringResource(
        R.string.stats_plant_points_chart,
        WeeklyPlantPointsGoal,
        stats.weeks.filter { it.points != null }.map { week ->
            stringResource(R.string.stats_chart_week, weekLabel(week.monday), week.points!!.roundToInt())
        }.joinToString(", "),
    )
    val axis = remember(period, locale) { weekAxis(period, locale) }
    BarChart(
        bars = stats.weeks.mapIndexed { index, week ->
            val (start, end) = axis.spans[index]
            ChartBar(
                start,
                end,
                week.points,
                tone = when {
                    week.reached -> BarTone.STRONG
                    week.current -> BarTone.OUTLINE
                    else -> BarTone.WEAK
                },
                badge = week.reached,
            )
        },
        axisLength = axis.length,
        labels = axis.labels,
        description = description,
        selected = selected,
        onSelect = onSelect,
        goal = WeeklyPlantPointsGoal.toDouble(),
    )
}

/**
 * A logged nutrient in grams, e.g. "64 g Protein pro Tag (Ø)" over the days with entries and a bar
 * per day (3 Monate: per week); the headline shows the tapped bar instead. [unit] is e.g. "Protein".
 */
@Composable
private fun NutrientCard(
    title: String,
    icon: Painter,
    unit: String,
    stats: NutrientStats,
    period: StatsPeriod,
    today: LocalDate,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    val locale = currentLocale()
    val thisWeek = stringResource(R.string.cook_week_this)
    val lastWeek = stringResource(R.string.cook_week_last)
    val weekly = period.range == StatsRange.QUARTER
    fun barLabel(bar: NutrientBar) =
        if (weekly) cookWeekLabel(bar.start, today, locale, thisWeek, lastWeek) else formatPlannedDate(bar.start, today, locale)

    StatsCard(title, icon) {
        val tapped = selected?.let { stats.bars.getOrNull(it) }?.takeIf { it.value != null }
        val perDay = stringResource(R.string.stats_grams_per_day, unit)
        if (tapped != null) {
            BigNumber(tapped.value!!.roundToInt().toString(), if (weekly) perDay else stringResource(R.string.stats_grams, unit))
            Summary(barLabel(tapped))
        } else {
            BigNumber(stats.average?.roundToInt()?.toString() ?: "–", perDay)
            Summary(
                if (stats.days == 0) {
                    stringResource(R.string.stats_no_entries)
                } else {
                    pluralStringResource(R.plurals.stats_logged_days, stats.days, stats.days, stats.total.roundToInt())
                },
            )
        }
        val description = stringResource(
            if (weekly) R.string.stats_nutrient_chart_weekly else R.string.stats_nutrient_chart,
            unit,
            stats.bars.filter { it.value != null }.map { bar ->
                stringResource(R.string.stats_chart_week, barLabel(bar), bar.value!!.roundToInt())
            }.joinToString(", "),
        )
        val axis = remember(period, locale) { dayAxis(period, locale) }
        BarChart(
            bars = stats.bars.mapIndexed { index, bar ->
                val (start, end) = axis.spans[index]
                ChartBar(start, end, bar.value)
            },
            axisLength = axis.length,
            labels = axis.labels,
            description = description,
            selected = selected,
            onSelect = onSelect,
        )
    }
}

/** Where a period's bars sit: each bar's span on the axis, its length and its labels. */
internal class PeriodAxis(val length: Float, val spans: List<Pair<Float, Float>>, val labels: List<AxisLabel>)

/**
 * Monat: the month's days, each week over its days in the month, labelled 1, 5, 10 … and the last day
 * with ticks. 3 Monate: a slot per week, each with a dot, the month's name at its first week.
 */
internal fun weekAxis(period: StatsPeriod, locale: Locale): PeriodAxis {
    val weeks = period.weeks
    return when (period.range) {
        StatsRange.MONTH -> {
            val days = period.end.dayOfMonth
            PeriodAxis(
                days.toFloat(),
                weeks.map { monday -> (monday.dayOfMonth - 1f) to min(monday.dayOfMonth + 6, days).toFloat() },
                monthDayLabels(days),
            )
        }
        else -> PeriodAxis(
            weeks.size.toFloat(),
            weeks.indices.map { it.toFloat() to it + 1f },
            weeks.mapIndexed { index, monday ->
                val firstOfMonth = index == 0 || weeks[index - 1].month != monday.month
                AxisLabel(index + 0.5f, if (firstOfMonth) cookMonthLabel(monday, locale) else null, AxisMark.DOT)
            },
        )
    }
}

/** Woche: a slot per day labelled "M D M D F S S". Monat: a slot per day, labelled as in [weekAxis]. 3 Monate: [weekAxis]. */
internal fun dayAxis(period: StatsPeriod, locale: Locale): PeriodAxis = when (period.range) {
    StatsRange.WEEK -> PeriodAxis(
        7f,
        (0 until 7).map { it.toFloat() to it + 1f },
        (0 until 7).map { day ->
            AxisLabel(day + 0.5f, period.start.plusDays(day.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW_STANDALONE, locale))
        },
    )
    StatsRange.MONTH -> {
        val days = period.end.dayOfMonth
        PeriodAxis(days.toFloat(), (0 until days).map { it.toFloat() to it + 1f }, monthDayLabels(days))
    }
    StatsRange.QUARTER -> weekAxis(period, locale)
}

/** 1, 5, 10 … 25 and the last of a month's [days], each with a tick. */
private fun monthDayLabels(days: Int): List<AxisLabel> =
    (listOf(1) + (5..25 step 5) + days).distinct().map { AxisLabel(it - 0.5f, it.toString(), AxisMark.TICK) }

/** A big number with its unit inline, e.g. "24" and "Pflanzenpunkte pro Woche (Ø)". */
@Composable
private fun BigNumber(number: String, unit: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(number, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(unit, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
private fun Summary(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CheckBadge() {
    val description = stringResource(R.string.stats_goal_reached)
    val fill = MaterialTheme.colorScheme.primary
    val check = MaterialTheme.colorScheme.onPrimary
    Canvas(Modifier.size(36.dp).clearAndSetSemantics { contentDescription = description }) {
        drawCheckBadge(center, size.minDimension / 2.2f, fill, check)
    }
}
