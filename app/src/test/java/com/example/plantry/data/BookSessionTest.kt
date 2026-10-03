package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BookSessionTest {

    private val session = BookSession()

    @Test
    fun firstScan_takesWhatWasRead() {
        assertEquals(BookPage("", 12), session.defaults(BookPage("", 12)))
        assertEquals(BookPage("", null), session.defaults(BookPage("", null)))
    }

    @Test
    fun nextScan_carriesSourceOverAndSuggestsNextPage() {
        session.remember(" Plenty ", 112)

        assertEquals(BookPage("Plenty", 113), session.defaults(BookPage("", null)))
    }

    @Test
    fun readPage_winsOverSuggestion() {
        session.remember("Plenty", 112)

        assertEquals(BookPage("Plenty", 140), session.defaults(BookPage("", 140)))
    }

    @Test
    fun readSource_wins_andOnlyTheSameBookContinuesItsPages() {
        session.remember("Plenty", 112)

        assertEquals(BookPage("Simple", null), session.defaults(BookPage("Simple", null)))
        assertEquals(BookPage("plenty", 113), session.defaults(BookPage("plenty", null)))
    }

    @Test
    fun lastScanWithoutPage_suggestsNoPage() {
        session.remember("Plenty", null)

        assertEquals(BookPage("Plenty", null), session.defaults(BookPage("", null)))
    }

    @Test
    fun recipeWithoutSource_endsTheSession() {
        session.remember("Plenty", 112)
        session.remember(" ", 5)

        assertEquals(BookPage("", null), session.defaults(BookPage("", null)))
    }
}
