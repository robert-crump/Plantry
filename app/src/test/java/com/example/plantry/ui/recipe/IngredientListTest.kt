package com.example.plantry.ui.recipe

import org.junit.Assert.assertEquals
import org.junit.Test

class IngredientListTest {

    @Test
    fun upToEightLines_allShow() {
        assertEquals(0, collapsedLineCount(0))
        assertEquals(6, collapsedLineCount(6))
        assertEquals(8, collapsedLineCount(8))
    }

    @Test
    fun fromNineLines_theFirstSixShow() {
        assertEquals(6, collapsedLineCount(9))
        assertEquals(6, collapsedLineCount(20))
    }
}
