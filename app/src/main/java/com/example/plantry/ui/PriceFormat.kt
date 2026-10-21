package com.example.plantry.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Euros with two decimals and a decimal comma, as on a German price tag, without the sign, e.g. "3,98". */
fun formatEuros(euros: Double): String = String.format(Locale.GERMANY, "%.2f", euros)

/** The month a price is from, e.g. "03/2026". */
fun formatPriceDate(date: LocalDate): String = date.format(PRICE_DATE)

private val PRICE_DATE = DateTimeFormatter.ofPattern("MM/yyyy")
