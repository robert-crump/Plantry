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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.example.plantry.ui.currentLocale
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
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
    val year = if (date.year == today.year) "" else " ${date.year}"
    return "$weekday, ${date.dayOfMonth}. ${cookMonthLabel(date, locale)}$year"
}

/** The abbreviated month in [locale] without a trailing dot, e.g. "Okt" or "Oct". */
internal fun cookMonthLabel(date: LocalDate, locale: Locale): String =
    date.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale).trimEnd('.')

/** The abbreviated month and the year, e.g. "Okt 2025", for the history's scrollbar bubble. */
internal fun cookMonthYearLabel(date: LocalDate, locale: Locale): String = "${cookMonthLabel(date, locale)} ${date.year}"

/** The year with a leading separator and a trailing suffix: ", y" in English, " y 'г'." in Russian, "y年" in Japanese. */
private val YearField = Regex("""[\s,]*y+(?:\s*'[^']*'\.?|[年년])?""")

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
