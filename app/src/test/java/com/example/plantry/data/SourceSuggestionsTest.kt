package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceSuggestionsTest {

    private val sources = listOf("Ottolenghi Simple", "Plenty", "ottolenghi simple", "", "  ", "Jerusalem (Ottolenghi)", "Plenty More")

    @Test
    fun blankQuery_suggestsNothing() {
        assertEquals(emptyList<String>(), SourceSuggestions.match(" ", sources))
    }

    @Test
    fun matchesIgnoringCase_startFirstThenAlphabetical_distinct() {
        assertEquals(
            listOf("Ottolenghi Simple", "Jerusalem (Ottolenghi)"),
            SourceSuggestions.match("OTTO", sources),
        )
    }

    @Test
    fun sourceEqualToQuery_isLeftOut() {
        assertEquals(listOf("Plenty More"), SourceSuggestions.match("plenty ", sources))
    }

    @Test
    fun respectsLimit() {
        assertEquals(1, SourceSuggestions.match("e", sources, limit = 1).size)
    }
}
