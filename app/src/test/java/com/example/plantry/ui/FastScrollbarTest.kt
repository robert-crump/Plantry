package com.example.plantry.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastScrollbarTest {
    // 20 items of 100 px in a 500 px viewport: 1500 px to scroll.
    private val even = ItemSizes((0 until 20).associateWith { 100 }, 20)

    @Test
    fun fraction_isTheScrolledPixelsOverTheScrollRange() {
        assertEquals(0f, scrollFraction(0, 0, even, 500), 0.001f)
        assertEquals(0.5f, scrollFraction(7, 50, even, 500), 0.001f)
        assertEquals(1f, scrollFraction(15, 0, even, 500), 0.001f)
    }

    @Test
    fun fraction_isZeroWhenEverythingFits() {
        assertEquals(0f, scrollFraction(0, 0, ItemSizes(mapOf(0 to 100, 1 to 100, 2 to 100), 3), 500), 0.001f)
    }

    @Test
    fun target_isTheInverseOfFraction() {
        assertEquals(0 to 0, scrollTarget(0f, even, 500))
        assertEquals(7 to 50, scrollTarget(0.5f, even, 500))
        assertEquals(15 to 0, scrollTarget(1f, even, 500))
    }

    @Test
    fun target_staysInsideTheList() {
        assertEquals(15 to 0, scrollTarget(2f, even, 500))
        assertEquals(0 to 0, scrollTarget(-1f, even, 500))
        assertEquals(0 to 0, scrollTarget(0.5f, ItemSizes(emptyMap(), 0), 500))
    }

    @Test
    fun unseenItems_countAsTheAverageOfTheKnownOnes() {
        // Known: 0 = 400 (a tall card), 1 = 100, 2 = 100 → average 200 for items 3..9.
        val sizes = ItemSizes(mapOf(0 to 400, 1 to 100, 2 to 100), 10)
        assertEquals(400f, sizes.offsetOf(1), 0.001f)
        assertEquals(600f, sizes.offsetOf(3), 0.001f)
        assertEquals(600f + 7 * 200f, sizes.total, 0.001f)
    }

    @Test
    fun mixedKnownSizes_fractionAndTargetAreInverses() {
        // Kochen-like: a tall Geplant card, a week header, history rows; 1000 px viewport.
        val known = mapOf(0 to 600, 1 to 60, 2 to 120, 3 to 120, 4 to 60, 5 to 120, 6 to 120, 7 to 120)
        val sizes = ItemSizes(known + (8 until 30).associateWith { 120 }, 30)
        // 600 + 60 + 120 + 120 + 60 + 120 * 25 = 3960, scroll range 2960.
        assertEquals(3960f, sizes.total, 0.001f)
        assertEquals((780f + 30f) / 2960f, scrollFraction(3, 30, sizes, 1000), 0.001f)
        assertEquals(3 to 30, scrollTarget((780f + 30f) / 2960f, sizes, 1000))
        // Inside the tall card.
        assertEquals(0 to 300, scrollTarget(300f / 2960f, sizes, 1000))
        for (index in 0 until 30) {
            val fraction = scrollFraction(index, 0, sizes, 1000)
            if (fraction < 1f) assertEquals(index to 0, scrollTarget(fraction, sizes, 1000))
        }
    }

    @Test
    fun knownSizes_keepTheEstimateWhenATallItemScrollsOut() {
        // The tall card stays in the estimate once seen, whatever is visible now.
        val atTop = ItemSizes(mapOf(0 to 600, 1 to 60, 2 to 120, 3 to 120), 30)
        val further = ItemSizes(mapOf(0 to 600, 1 to 60, 2 to 120, 3 to 120, 4 to 60, 5 to 120), 30)
        assertEquals(atTop.offsetOf(4), further.offsetOf(4), 0.001f)
    }

    @Test
    fun scrollbar_showsOnlyFromThreeViewports() {
        assertFalse(showsScrollbar(ItemSizes((0 until 14).associateWith { 100 }, 14), 500))
        assertTrue(showsScrollbar(ItemSizes((0 until 15).associateWith { 100 }, 15), 500))
        assertFalse(showsScrollbar(even, 0))
    }

    @Test
    fun justOverTheThreshold_fractionAndTargetStillMatch() {
        // 1510 px in a 500 px viewport, mixed sizes: 1010 px to scroll.
        val sizes = ItemSizes(mapOf(0 to 700, 1 to 60, 2 to 250, 3 to 250, 4 to 250), 5)
        assertTrue(showsScrollbar(sizes, 500))
        assertEquals(0.5f, scrollFraction(0, 505, sizes, 500), 0.001f)
        assertEquals(0 to 505, scrollTarget(0.5f, sizes, 500))
        assertEquals(1f, scrollFraction(2, 250, sizes, 500), 0.001f)
        assertEquals(3 to 0, scrollTarget(1f, sizes, 500))
    }
}
