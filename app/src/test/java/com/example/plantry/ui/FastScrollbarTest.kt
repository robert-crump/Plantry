package com.example.plantry.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FastScrollbarTest {
    // 20 items of 100 px in a 500 px viewport: 1500 px to scroll.

    @Test
    fun fraction_isTheScrolledPixelsOverTheScrollRange() {
        assertEquals(0f, scrollFraction(0, 0, 100f, 20, 500), 0.001f)
        assertEquals(0.5f, scrollFraction(7, 50, 100f, 20, 500), 0.001f)
        assertEquals(1f, scrollFraction(15, 0, 100f, 20, 500), 0.001f)
    }

    @Test
    fun fraction_isZeroWhenEverythingFits() {
        assertEquals(0f, scrollFraction(0, 0, 100f, 3, 500), 0.001f)
    }

    @Test
    fun target_isTheInverseOfFraction() {
        assertEquals(0 to 0, scrollTarget(0f, 100f, 20, 500))
        assertEquals(7 to 50, scrollTarget(0.5f, 100f, 20, 500))
        assertEquals(15 to 0, scrollTarget(1f, 100f, 20, 500))
    }

    @Test
    fun target_staysInsideTheList() {
        assertEquals(15 to 0, scrollTarget(2f, 100f, 20, 500))
        assertEquals(0 to 0, scrollTarget(-1f, 100f, 20, 500))
        assertEquals(0 to 0, scrollTarget(0.5f, 100f, 0, 500))
    }
}
