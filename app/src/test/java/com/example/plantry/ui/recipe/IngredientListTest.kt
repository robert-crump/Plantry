package com.example.plantry.ui.recipe

import com.example.plantry.data.StoreSection
import com.example.plantry.ui.recipe.IngredientListRow.Header
import com.example.plantry.ui.recipe.IngredientListRow.Line
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

    private val tomato = ShownLine(1, "2 Tomaten", StoreSection.PRODUCE)
    private val onion = ShownLine(2, "1 Zwiebel", StoreSection.PRODUCE)
    private val feta = ShownLine(3, "200 g Feta", StoreSection.DAIRY_CHILLED)
    private val rice = ShownLine(4, "150 g Reis", StoreSection.DRY_GOODS)

    @Test
    fun eachSection_startsWithItsHeader() {
        assertEquals(
            listOf(Header(StoreSection.PRODUCE), Line(tomato), Line(onion), Header(StoreSection.DAIRY_CHILLED), Line(feta)),
            ingredientListRows(listOf(tomato, onion, feta), shown = 3),
        )
    }

    @Test
    fun oneSection_hasNoHeader() {
        assertEquals(listOf(Line(tomato), Line(onion)), ingredientListRows(listOf(tomato, onion), shown = 2))
    }

    @Test
    fun collapsed_showsHeadersOnlyForShownLines() {
        assertEquals(
            listOf(Header(StoreSection.PRODUCE), Line(tomato), Line(onion), Header(StoreSection.DAIRY_CHILLED), Line(feta)),
            ingredientListRows(listOf(tomato, onion, feta, rice), shown = 3),
        )
    }

    @Test
    fun collapsedWithinOneSection_stillHasItsHeader() {
        // The hidden lines are of another section, so the shown section still needs naming.
        assertEquals(listOf(Header(StoreSection.PRODUCE), Line(tomato)), ingredientListRows(listOf(tomato, onion, feta), shown = 1))
    }
}
