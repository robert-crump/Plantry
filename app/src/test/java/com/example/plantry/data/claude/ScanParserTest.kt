package com.example.plantry.data.claude

import com.example.plantry.data.LineUnit
import com.example.plantry.data.PieceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanParserTest {

    private val known = setOf(3L, 7L)

    private fun line(
        text: String,
        amount: Double,
        id: Long,
        uncertain: Boolean = false,
        unit: String = "g",
        wording: String = "",
        unitGrams: Double = 0.0,
    ) = """{"originalText":"$text","amount":$amount,"unit":"$unit","wording":"$wording","unitGrams":$unitGrams,""" +
        """"ingredientId":$id,"ingredientName":"x","uncertain":$uncertain}"""

    private fun answer(title: String = "Dal", servings: Int = 4, active: Int = 15, time: Int = 40, page: Int = 112, vararg lines: String) =
        """{"title":"$title","servings":$servings,"activeMinutes":$active,"totalMinutes":$time,"page":$page,"lines":[${lines.joinToString(",")}]}"""

    private fun success(text: String?) = (ScanParser.toResult(text, known) as ScanResult.Success).recipe

    @Test
    fun parsesMetadataAndLines() {
        val recipe = success(answer(lines = arrayOf(line("200 g rote Linsen", 200.0, 3))))

        assertEquals("Dal", recipe.title)
        assertEquals(4, recipe.servings)
        assertEquals(15, recipe.activeTimeMinutes)
        assertEquals(40, recipe.totalTimeMinutes)
        assertEquals(112, recipe.page)
        assertEquals(ScannedLine("200 g rote Linsen", 200.0, LineUnit.Grams, 3, "x", uncertain = false), recipe.lines.single())
    }

    @Test
    fun readsUnitWordingAndProposedUnitGrams() {
        val recipe = success(
            answer(
                lines = arrayOf(
                    line("2 Scheiben Toast", 2.0, 3, unit = "Scheibe", unitGrams = 37.5),
                    line("1 Handvoll Rucola", 20.0, 0, uncertain = true, wording = " 1 Handvoll Rucola "),
                    line("½ TL Salz", 0.5, 7, unit = "tl"),
                    line("100 ml Hafermilch", 100.0, 7, unit = "ml", unitGrams = 100.0),
                ),
            ),
        )

        assertEquals(LineUnit.Of(PieceUnit.SLICE), recipe.lines[0].unit)
        assertEquals(2.0, recipe.lines[0].amount, 0.0)
        assertEquals(37.5, recipe.lines[0].unitGrams)
        assertNull(recipe.lines[0].wording)
        assertEquals(LineUnit.Grams, recipe.lines[1].unit)
        assertEquals("1 Handvoll Rucola", recipe.lines[1].wording)
        assertNull(recipe.lines[1].unitGrams)
        assertEquals(LineUnit.Of(PieceUnit.TSP), recipe.lines[2].unit)
        // g and ml need no unit grams.
        assertEquals(LineUnit.Milliliters, recipe.lines[3].unit)
        assertNull(recipe.lines[3].unitGrams)
    }

    @Test
    fun unknownUnit_isGramsAndUncertain() {
        val line = success(answer(lines = arrayOf(line("1 Prise Salz", 1.0, 7, unit = "Prise")))).lines.single()

        assertEquals(LineUnit.Grams, line.unit)
        assertTrue(line.uncertain)
    }

    @Test
    fun zeroMeansUnknown() {
        val recipe = success(answer(servings = 0, active = 0, time = 0, page = 0, lines = arrayOf(line("Salz", 2.0, 7))))

        assertNull(recipe.servings)
        assertNull(recipe.activeTimeMinutes)
        assertNull(recipe.totalTimeMinutes)
        assertNull(recipe.page)
    }

    @Test
    fun unknownOrZeroIngredientId_isNoMatch() {
        val recipe = success(answer(lines = arrayOf(line("1 Bund Koriander", 30.0, 0), line("Tofu", 200.0, 99))))

        assertNull(recipe.lines[0].ingredientId)
        assertNull(recipe.lines[1].ingredientId)
    }

    @Test
    fun searchTerms_onlyKeptForUnmatchedLines() {
        val terms = ""","searchTerms":[" tofu smoked ","tofu",""]}"""
        val recipe = success(
            answer(
                lines = arrayOf(
                    line("200 g Räuchertofu", 200.0, 0).dropLast(1) + terms,
                    line("200 g Linsen", 200.0, 3).dropLast(1) + terms,
                ),
            ),
        )

        assertEquals(listOf("tofu smoked", "tofu"), recipe.lines[0].searchTerms)
        assertEquals(emptyList<String>(), recipe.lines[1].searchTerms)
    }

    @Test
    fun lineWithoutAmount_isUncertain() {
        val recipe = success(answer(lines = arrayOf(line("Salz", 0.0, 7), line("Linsen", 200.0, 3))))

        assertTrue(recipe.lines[0].uncertain)
        assertFalse(recipe.lines[1].uncertain)
    }

    @Test
    fun steps_areReadAndCleanedUp() {
        val text = answer(lines = arrayOf(line("200 g rote Linsen", 200.0, 3)))
            .replace("]}", "],\"steps\":[\" 1. Zwiebel anschwitzen. \",\"\",\"Linsen 20 Min. köcheln.\"]}")

        assertEquals(listOf("Zwiebel anschwitzen.", "Linsen 20 Min. köcheln."), success(text).steps)
    }

    @Test
    fun noMethodOnThePhoto_givesNoSteps() {
        val withEmpty = answer(lines = arrayOf(line("200 g rote Linsen", 200.0, 3))).replace("]}", "],\"steps\":[]}")
        val withoutField = answer(lines = arrayOf(line("200 g rote Linsen", 200.0, 3)))

        assertEquals(emptyList<String>(), success(withEmpty).steps)
        assertEquals(emptyList<String>(), success(withoutField).steps)
    }

    @Test
    fun noTitleAndNoLines_isNotARecipe() {
        assertEquals(ScanResult.Failure(ClaudeFailure.NOT_A_RECIPE), ScanParser.toResult(answer(title = ""), known))
    }

    @Test
    fun malformedOrMissingAnswer_isBadResponse() {
        assertEquals(ScanResult.Failure(ClaudeFailure.BAD_RESPONSE), ScanParser.toResult("{\"title\":", known))
        assertEquals(ScanResult.Failure(ClaudeFailure.BAD_RESPONSE), ScanParser.toResult(null, known))
    }
}
