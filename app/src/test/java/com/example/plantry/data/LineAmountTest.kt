package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineAmountTest {

    private val slice = IngredientUnit(1, PieceUnit.SLICE, 37.5, packageGrams = 750.0, packageCount = 20)
    private val can = IngredientUnit(1, PieceUnit.CAN, 240.0)
    private val units = listOf(slice, can)

    @Test
    fun grams_gAsGiven_mlCountsAsGrams() {
        assertEquals(ResolvedAmount(200.0, "200 g", "200 g Mehl"), LineAmount.resolve(200.0, LineUnit.Grams, units, "Mehl"))
        assertEquals(ResolvedAmount(200.0, "200 g", "200 ml Hafermilch"), LineAmount.resolve(200.0, LineUnit.Milliliters, units, "Hafermilch"))
    }

    @Test
    fun aUnit_gramsFromTheIngredientsUnit() {
        assertEquals(
            ResolvedAmount(75.0, "2 × 37,5 g = 75 g", "2 Scheiben Vollkorntoast"),
            LineAmount.resolve(2.0, LineUnit.Of(PieceUnit.SLICE), units, "Vollkorntoast"),
        )
        assertEquals(240.0, LineAmount.resolve(1.0, LineUnit.Of(PieceUnit.CAN), units, "Kichererbsen").grams!!, 0.0)
    }

    @Test
    fun aPackageUnit_roundedInTheCalculation() {
        val tortilla = IngredientUnit(2, PieceUnit.PIECE, 320.0 / 8, packageGrams = 320.0, packageCount = 8)
        val toast = IngredientUnit(3, PieceUnit.SLICE, 750.0 / 22, packageGrams = 750.0, packageCount = 22)

        assertEquals("3 × 40 g = 120 g", LineAmount.resolve(3.0, LineUnit.Of(PieceUnit.PIECE), listOf(tortilla), "Tortilla").calculation)
        val resolved = LineAmount.resolve(4.0, LineUnit.Of(PieceUnit.SLICE), listOf(toast), "Toast")
        assertEquals(4 * 750.0 / 22, resolved.grams!!, 1e-9)
        assertEquals("4 × 34,1 g = 136,4 g", resolved.calculation)
    }

    @Test
    fun aUnitTheIngredientLacks_hasNoGrams() {
        val resolved = LineAmount.resolve(2.0, LineUnit.Of(PieceUnit.CLOVE), units, "Knoblauch")

        assertNull(resolved.grams)
        assertNull(resolved.calculation)
        assertEquals("2 Zehen Knoblauch", resolved.text)
    }

    @Test
    fun text_singularUpToOne_pluralAbove() {
        assertEquals("1 Scheibe Toast", LineAmount.text(1.0, LineUnit.Of(PieceUnit.SLICE), "Toast"))
        assertEquals("½ Dose Mais", LineAmount.text(0.5, LineUnit.Of(PieceUnit.CAN), "Mais"))
        assertEquals("1½ Dosen Mais", LineAmount.text(1.5, LineUnit.Of(PieceUnit.CAN), "Mais"))
        assertEquals("3 Zehen Knoblauch", LineAmount.text(3.0, LineUnit.Of(PieceUnit.CLOVE), "Knoblauch"))
        assertEquals("2 Stück Ei", LineAmount.text(2.0, LineUnit.Of(PieceUnit.PIECE), "Ei"))
        assertEquals("2 Bund Petersilie", LineAmount.text(2.0, LineUnit.Of(PieceUnit.BUNCH), "Petersilie"))
    }

    @Test
    fun text_countedUnitsWithFractions_gAndMlAsDecimals() {
        assertEquals("¼ Packung Hefe",LineAmount.text(0.25, LineUnit.Of(PieceUnit.PACKET), "Hefe"))
        assertEquals("⅓ Bund Dill", LineAmount.text(1.0 / 3, LineUnit.Of(PieceUnit.BUNCH), "Dill"))
        assertEquals("2⅔ Scheiben Käse", LineAmount.text(2 + 2.0 / 3, LineUnit.Of(PieceUnit.SLICE), "Käse"))
        assertEquals("1¾ Dosen Tomaten", LineAmount.text(1.75, LineUnit.Of(PieceUnit.CAN), "Tomaten"))
        assertEquals("0,2 Dose Mais", LineAmount.text(0.2, LineUnit.Of(PieceUnit.CAN), "Mais"))
        assertEquals("37,5 g Toast", LineAmount.text(37.5, LineUnit.Grams, "Toast"))
        assertEquals("0,5 ml Vanilleextrakt", LineAmount.text(0.5, LineUnit.Milliliters, "Vanilleextrakt"))
    }

    @Test
    fun theWordingReplacesTheGeneratedText_gramsStayExact() {
        val ginger = LineAmount.resolve(15.0, LineUnit.Grams, emptyList(), "Ingwer", wording = " 1 daumengroßes Stück Ingwer ")

        assertEquals(ResolvedAmount(15.0, "15 g", "1 daumengroßes Stück Ingwer"), ginger)
        assertEquals("15 g Ingwer", LineAmount.resolve(15.0, LineUnit.Grams, emptyList(), "Ingwer", wording = " ").text)
    }

    @Test
    fun unitCodes_roundTrip() {
        (LineUnit.ALWAYS + PieceUnit.entries.map { LineUnit.Of(it) }).forEach {
            assertEquals(it, LineUnit.fromCode(it.code))
        }
        assertEquals("SLICE", LineUnit.Of(PieceUnit.SLICE).code)
        assertNull(LineUnit.fromCode("PINCH"))
    }

    @Test
    fun aStoredLinesGrams_followItsUnitGrams() {
        val line = RecipeLine(recipeId = 1, position = 0, amount = 2.0, unit = LineUnit.Of(PieceUnit.SLICE), ingredientId = 1, unitGrams = 37.5)

        assertEquals(75.0, line.grams!!, 0.0)
        assertEquals(80.0, line.copy(unitGrams = 40.0).grams!!, 0.0)
        assertNull(line.copy(unitGrams = null).grams)
        assertEquals(200.0, line.copy(amount = 200.0, unit = LineUnit.Milliliters, unitGrams = null).grams!!, 0.0)
    }
}
