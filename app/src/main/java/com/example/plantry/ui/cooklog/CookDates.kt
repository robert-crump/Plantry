package com.example.plantry.ui.cooklog

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.plantry.R
import com.example.plantry.data.CookingStats
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.example.plantry.ui.currentLocale
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** "Heute", "Gestern" or e.g. "Mittwoch, 30. September". */
@Composable
fun cookDateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.minusDays(1) -> stringResource(R.string.date_yesterday)
    else -> formatCookDate(date, today, currentLocale())
}

/** The full date in [locale], without the year when it is the current one: "Mittwoch, 30. September". */
internal fun formatCookDate(date: LocalDate, today: LocalDate, locale: Locale): String {
    val full = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.FULL, null, IsoChronology.INSTANCE, locale)
    val pattern = if (date.year == today.year) full.replace(YearField, "").trim(' ', ',', '.') else full
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

/** The abbreviated weekday, day and month, plus the year when it isn't the current one: "Do, 8. Okt". */
internal fun formatPlannedDate(date: LocalDate, today: LocalDate, locale: Locale): String {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale).trimEnd('.')
    return "$weekday, ${formatShortDate(date, today, locale)}"
}

/** Day and abbreviated month, plus the year when it isn't the current one: "8. Okt", "8. Okt 2025". */
internal fun formatShortDate(date: LocalDate, today: LocalDate, locale: Locale): String {
    val year = if (date.year == today.year) "" else " ${date.year}"
    return "${date.dayOfMonth}. ${cookMonthLabel(date, locale)}$year"
}

/** The abbreviated month in [locale] without a trailing dot, e.g. "Okt" or "Oct". */
internal fun cookMonthLabel(date: LocalDate, locale: Locale): String =
    date.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale).trimEnd('.')

/** The abbreviated month and the year, e.g. "Okt 2025", for the history's scrollbar bubble. */
internal fun cookMonthYearLabel(date: LocalDate, locale: Locale): String = "${cookMonthLabel(date, locale)} ${date.year}"

/** The Monday of [date]'s week. */
internal fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Groups [items] (newest first) into Monday–Sunday weeks, keyed by each week's Monday, keeping their order. */
internal fun <T> groupByWeek(items: List<T>, date: (T) -> LocalDate): Map<LocalDate, List<T>> =
    items.groupBy { weekStart(date(it)) }

/**
 * The header for the week starting on [monday]: [thisWeek] or [lastWeek] relative to [today], otherwise
 * its range, e.g. "5.–11. Okt", "28. Sep – 4. Okt", "29. Dez 2025 – 4. Jan 2026", and with the year
 * when the whole week lies before [today]'s year, e.g. "6.–12. Okt 2025".
 */
internal fun cookWeekLabel(monday: LocalDate, today: LocalDate, locale: Locale, thisWeek: String, lastWeek: String): String {
    val sunday = monday.plusDays(6)
    return when {
        monday == weekStart(today) -> thisWeek
        monday == weekStart(today).minusWeeks(1) -> lastWeek
        monday.year != sunday.year ->
            "${monday.dayOfMonth}. ${cookMonthLabel(monday, locale)} ${monday.year} – " +
                "${sunday.dayOfMonth}. ${cookMonthLabel(sunday, locale)} ${sunday.year}"
        else -> {
            val year = if (sunday.year < today.year) " ${sunday.year}" else ""
            if (monday.month == sunday.month) {
                "${monday.dayOfMonth}.–${sunday.dayOfMonth}. ${cookMonthLabel(sunday, locale)}$year"
            } else {
                "${monday.dayOfMonth}. ${cookMonthLabel(monday, locale)} – ${sunday.dayOfMonth}. ${cookMonthLabel(sunday, locale)}$year"
            }
        }
    }
}

/** The year with a leading separator and a trailing suffix: ", y" in English, " y 'г'." in Russian, "y年" in Japanese. */
private val YearField = Regex("""[\s,]*y+(?:\s*'[^']*'\.?|[年년])?""")

/** E.g. "Zuletzt: 8. Okt", or "Noch nie gekocht" without a [lastCookedOn]. */
@Composable
fun lastCookedLabel(lastCookedOn: LocalDate?, today: LocalDate): String =
    if (lastCookedOn == null) {
        stringResource(R.string.cooked_never)
    } else {
        stringResource(R.string.cooked_last_on, formatShortDate(lastCookedOn, today, currentLocale()))
    }

/** E.g. "Zuletzt vor 3 Tagen gekocht" or "Noch nie gekocht". */
@Composable
fun lastCookedLabel(stats: CookingStats): String = when (val days = stats.daysSinceLastCooked) {
    null -> stringResource(R.string.cooked_never)
    0L -> stringResource(R.string.cooked_last_today)
    1L -> stringResource(R.string.cooked_last_yesterday)
    else -> stringResource(R.string.cooked_last_days_ago, days)
}

/** Picks a cooking date up to [today]. */
@Composable
fun CookDatePickerDialog(
    date: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) = DayPickerDialog(date, (today.year - 5)..today.year, { it <= today }, onPick, onDismiss)

/** Picks the day to cook a planned recipe: [today] or later. */
@Composable
fun PlanDatePickerDialog(
    date: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) = DayPickerDialog(date, today.year..(today.year + 1), { it >= today }, onPick, onDismiss)

/** A date picker limited to [years] and the days [selectable] allows; the picker works in UTC milliseconds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(
    date: LocalDate,
    years: IntRange,
    selectable: (LocalDate) -> Boolean,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.toUtcMillis(),
        yearRange = years,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = selectable(utcTimeMillis.toUtcDate())
            override fun isSelectableYear(year: Int) = year in years
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPick(it.toUtcDate()) }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state)
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
