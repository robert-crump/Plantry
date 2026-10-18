package com.example.plantry.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DraggableColumnTest {

    /** Items 100, 50 and 100 high, 10 apart. */
    private val heights = listOf(100, 50, 100)

    private fun target(from: Int, offset: Float) = dropTarget(from, offset, heights.size, 10f, heights::get)

    @Test
    fun dropTarget_staysUntilHalfTheNeighbourIsPassed() {
        assertEquals(0, target(0, 0f))
        assertEquals(0, target(0, 30f))
        assertEquals(1, target(0, 31f))
        assertEquals(1, target(1, 55f))
        assertEquals(2, target(1, 56f))
        assertEquals(1, target(1, -55f))
        assertEquals(0, target(1, -56f))
    }

    @Test
    fun dropTarget_passesSeveralNeighboursButNotTheEnds() {
        assertEquals(1, target(0, 115f))
        assertEquals(2, target(0, 116f))
        assertEquals(2, target(0, 1000f))
        assertEquals(0, target(2, -1000f))
    }
}
