package com.example.plantry.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PriceFormatTest {

    @Test
    fun formatEuros_hasTwoDecimalsAndAComma() {
        assertEquals("2,10", formatEuros(2.1))
        assertEquals("3,98", formatEuros(3.978))
        assertEquals("0,00", formatEuros(0.0))
    }

    @Test
    fun formatPriceDate_isMonthAndYear() {
        assertEquals("03/2026", formatPriceDate(LocalDate.of(2026, 3, 14)))
    }
}
