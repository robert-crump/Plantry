package com.example.plantry.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowMoreToggleTest {

    @Test
    fun upToFiveRows_allShow() {
        assertEquals(0, collapsedCount(0))
        assertEquals(1, collapsedCount(1))
        assertEquals(5, collapsedCount(5))
    }

    @Test
    fun fromSixRows_theFirstFiveShow() {
        // Even a single hidden row gets the toggle.
        assertEquals(5, collapsedCount(6))
        assertEquals(5, collapsedCount(20))
    }
}
