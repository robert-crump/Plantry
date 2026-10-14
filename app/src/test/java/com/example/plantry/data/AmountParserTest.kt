package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParserTest {

    private val toast = PieceWeight(PieceUnit.SLICE, 750.0, 20)
    private val egg = PieceWeight(PieceUnit.PIECE, 60.0, 1)
    private val garlic = PieceWeight(PieceUnit.CLOVE, 4.0, 1)

    private fun grams(text: String, pieceWeight: PieceWeight? = null) = AmountParser.parse(text, pieceWeight)?.grams

    @Test
    fun numbers_wholeDecimalAndFractions() {
        assertEquals(120.0, grams("2 Eier", egg))
        assertEquals(90.0, grams("1,5 Eier", egg))
        assertEquals(90.0, grams("1.5 Eier", egg))
        assertEquals(30.0, grams("½ Ei", egg))
        assertEquals(15.0, grams("¼ Ei", egg))
        assertEquals(45.0, grams("¾ Ei", egg))
        assertEquals(20.0, grams("⅓ Ei", egg))
        assertEquals(40.0, grams("⅔ Ei", egg))
        assertEquals(30.0, grams("1/2 Ei", egg))
    }

    @Test
    fun numbers_mixed() {
        assertEquals(90.0, grams("1 ½ Eier", egg))
        assertEquals(90.0, grams("1½ Eier", egg))
        assertEquals(90.0, grams("1 1/2 Eier", egg))
    }

    @Test
    fun numbers_rangesAreTheMidpoint() {
        assertEquals(150.0, grams("2-3 Eier", egg))
        assertEquals(150.0, grams("2–3 Eier", egg))
        assertEquals(150.0, grams("2 - 3 Eier", egg))
        assertEquals(150.0, grams("2 bis 3 Eier", egg))
        assertEquals(250.0, grams("200-300 g Mehl"))
    }

    @Test
    fun numbers_articlesAreOne() {
        listOf("ein Ei", "Eine Zwiebel", "einen Apfel", "einer Zitrone", "one egg", "an egg", "A large egg").forEach {
            assertEquals(it, 60.0, grams(it, egg))
        }
        assertEquals(4.0, grams("a clove of garlic", garlic))
    }

    @Test
    fun numbers_otherWordsAreNotSupported() {
        assertNull(grams("zwei Eier", egg))
        assertNull(grams("halbe Zwiebel", egg))
        assertNull(grams("half an egg", egg))
        assertNull(grams("Ananas", egg))
    }

    @Test
    fun grams_workForEveryIngredient() {
        assertEquals(150.0, grams("150 g Toast", toast))
        assertEquals(150.0, grams("150 g Mehl"))
        assertEquals(150.0, grams("150g Mehl"))
        assertEquals(150.0, grams("150 gr. Mehl"))
        assertEquals(150.0, grams("150 Gramm Mehl"))
        assertEquals(500.0, grams("0,5 kg Kartoffeln"))
        assertEquals(1500.0, grams("1 ½ kg Kartoffeln"))
    }

    @Test
    fun units_matchingThePieceUnit() {
        assertEquals(150.0, grams("4 Scheiben Toast", toast))
        assertEquals(37.5, grams("1 Scheibe Toast", toast))
        assertEquals(75.0, grams("2 slices of toast", toast))
        assertEquals(8.0, grams("2 Zehen Knoblauch", garlic))
        assertEquals(4.0, grams("1 clove garlic", garlic))
        assertEquals(120.0, grams("2 Stück Eier", egg))
        assertEquals(120.0, grams("2 Stk. Eier", egg))
        assertEquals(120.0, grams("2 pcs eggs", egg))
    }

    @Test
    fun units_caseInsensitiveWithAsciiUmlauts() {
        val leaf = PieceWeight(PieceUnit.LEAF, 0.5, 1)
        assertEquals(1.0, grams("2 Blätter Salbei", leaf))
        assertEquals(1.0, grams("2 Blaetter Salbei", leaf))
        assertEquals(1.0, grams("2 BLÄTTER Salbei", leaf))
        val head = PieceWeight(PieceUnit.HEAD, 500.0, 1)
        assertEquals(1000.0, grams("2 Köpfe Salat", head))
        assertEquals(1000.0, grams("2 Kopfe Salat", head))
        assertEquals(1000.0, grams("2 Koepfe Salat", head))
    }

    @Test
    fun units_everyUnitHasWords() {
        mapOf(
            PieceUnit.STALK to "2 Stangen Lauch", PieceUnit.SPRIG to "2 Zweige Rosmarin", PieceUnit.BULB to "2 Knollen Fenchel",
            PieceUnit.BUNCH to "2 Bund Petersilie", PieceUnit.CAN to "2 Dosen Tomaten", PieceUnit.JAR to "2 Gläser Pesto",
            PieceUnit.TUB to "2 Becher Joghurt", PieceUnit.PACKET to "2 Päckchen Hefe",
        ).forEach { (unit, text) -> assertEquals(text, 20.0, grams(text, PieceWeight(unit, 10.0, 1))) }
    }

    @Test
    fun bareCount_onlyForPieces() {
        assertEquals(60.0, grams("1 große Zwiebel", PieceWeight(PieceUnit.PIECE, 60.0, 1)))
        assertEquals(120.0, grams("2", egg))
        assertNull(grams("4 Toast", toast))
        assertNull(grams("2 Eier"))
    }

    @Test
    fun stopWords_giveNothing() {
        listOf("2 EL Öl", "1 TL Salz", "1 Prise Salz", "1 Msp. Muskat", "200 ml Milch", "1 l Brühe",
            "2 tbsp oil", "1 tsp salt", "1 cup flour", "4 oz cheese").forEach {
            assertNull(it, grams(it, egg))
        }
    }

    @Test
    fun aDifferentUnit_givesNothing() {
        assertNull(grams("2 Zehen Knoblauch", egg))
        assertNull(grams("1 Packung Toast", toast))
    }

    @Test
    fun theAmountMustComeFirst() {
        assertNull(grams("Toast, 4 Scheiben", toast))
        assertNull(grams("", toast))
        assertNull(grams("0 Scheiben Toast", toast))
    }

    @Test
    fun grams_areRoundedToATenth() {
        val amount = AmountParser.parse("3 Scheiben", PieceWeight(PieceUnit.SLICE, 750.0, 22))!!

        assertEquals(102.3, amount.grams, 0.0)
        assertEquals(3.0, amount.count, 0.0)
    }
}
