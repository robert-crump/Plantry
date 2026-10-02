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
import java.time.format.DateTimeFormatter
import java.util.Locale

private val weekdayFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val fullDateFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)

/** "Heute", "Gestern" or e.g. "Mittwoch, 30. September". */
@Composable
fun cookDateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.minusDays(1) -> stringResource(R.string.date_yesterday)
    else -> date.format(if (date.year == today.year) weekdayFormat else fullDateFormat)
}

/** E.g. "Zuletzt vor 3 Tagen gekocht" or "Noch nie gekocht". */
@Composable
fun lastCookedLabel(stats: CookingStats): String = when (val days = stats.daysSinceLastCooked) {
    null -> stringResource(R.string.cooked_never)
    0L -> stringResource(R.string.cooked_last_today)
    1L -> stringResource(R.string.cooked_last_yesterday)
    else -> stringResource(R.string.cooked_last_days_ago, days)
}

/** Picks a cooking date up to [today]; the picker works in UTC milliseconds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookDatePickerDialog(
    date: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayMillis = today.toUtcMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.toUtcMillis(),
        yearRange = (today.year - 5)..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= today.year
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
