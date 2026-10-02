package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IngredientSuggestionsTest {

    private val table = listOf(
        "Süßkartoffel",
        "Kartoffel",
        "Reis, gekocht",
        "Reis, trocken",
        "Räuchertofu",
        "Tofu",
        "Kidneybohnen, Dose",
        "Rote Linsen",
    ).mapIndexed { index, name -> ingredient(id = index + 1L, name = name) }

    private fun names(query: String, limit: Int = IngredientSuggestions.DEFAULT_LIMIT) =
        IngredientSuggestions.match(query, table, limit).map { it.name }

    @Test
    fun blankQuery_suggestsNothing() {
        assertEquals(emptyList<String>(), names("  "))
    }

    @Test
    fun matchesIgnoringCase_withNameStartFirst() {
        assertEquals(listOf("Kartoffel", "Süßkartoffel"), names("KARTOFFEL"))
    }

    @Test
    fun wordStartRanksAboveMatchInsideWord() {
        assertEquals(listOf("Tofu", "Räuchertofu"), names("tofu"))
        assertEquals(listOf("Rote Linsen"), names("lins"))
    }

    @Test
    fun everyWordMustMatch() {
        assertEquals(listOf("Reis, trocken"), names("reis tro"))
        assertEquals(listOf("Reis, gekocht", "Reis, trocken"), names("reis"))
    }

    @Test
    fun resultsAreLimited() {
        assertEquals(1, names("e", limit = 1).size)
    }
}
