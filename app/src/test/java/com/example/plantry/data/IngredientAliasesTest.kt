package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngredientAliasesTest {

    @Test
    fun normalize_lowercasesAndDropsPunctuationAndSpaces() {
        assertEquals("kichererbsen abgetropft", IngredientAliases.normalize("  Kichererbsen,  abgetropft "))
        assertEquals("tomaten gehackt dose", IngredientAliases.normalize("Tomaten (gehackt) / Dose."))
        assertEquals("süßkartoffel", IngredientAliases.normalize("SÜßKARTOFFEL"))
        assertEquals("", IngredientAliases.normalize(" , "))
    }

    @Test
    fun match_findsTheIngredientOfAnEquivalentWording() {
        val aliases = mapOf("kichererbsen abgetropft" to 7L)

        assertEquals(7L, IngredientAliases.match("Kichererbsen, abgetropft", aliases))
        assertEquals(7L, IngredientAliases.match("kichererbsen abgetropft", aliases))
        assertNull(IngredientAliases.match("Kichererbsen", aliases))
        assertNull(IngredientAliases.match("", aliases))
    }

    @Test
    fun of_normalizesSkipsBlanksAndLastWins() {
        val aliases = IngredientAliases.of(
            listOf("Kichererbsen, abgetropft" to 7L, " " to 3L, "kichererbsen abgetropft" to 8L, "Reis" to 5L),
        )

        assertEquals(
            listOf(IngredientAlias("kichererbsen abgetropft", 8L), IngredientAlias("reis", 5L)),
            aliases,
        )
    }
}
