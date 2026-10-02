package com.example.plantry.data.claude

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanParserTest {

    private val known = setOf(3L, 7L)

    private fun line(text: String, grams: Double, id: Long, uncertain: Boolean = false) =
        """{"originalText":"$text","grams":$grams,"ingredientId":$id,"ingredientName":"x","uncertain":$uncertain}"""

    private fun answer(title: String = "Dal", servings: Int = 4, time: Int = 40, page: Int = 112, vararg lines: String) =
        """{"title":"$title","servings":$servings,"cookingTimeMinutes":$time,"page":$page,"lines":[${lines.joinToString(",")}]}"""

    private fun success(text: String?) = (ScanParser.toResult(text, known) as ScanResult.Success).recipe

    @Test
    fun parsesMetadataAndLines() {
        val recipe = success(answer(lines = arrayOf(line("200 g rote Linsen", 200.0, 3))))

        assertEquals("Dal", recipe.title)
        assertEquals(4, recipe.servings)
        assertEquals(40, recipe.cookingTimeMinutes)
        assertEquals(112, recipe.page)
        assertEquals(ScannedLine("200 g rote Linsen", 200.0, 3, "x", uncertain = false), recipe.lines.single())
    }

    @Test
    fun zeroMeansUnknown() {
        val recipe = success(answer(servings = 0, time = 0, page = 0, lines = arrayOf(line("Salz", 2.0, 7))))

        assertNull(recipe.servings)
        assertNull(recipe.cookingTimeMinutes)
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
    fun lineWithoutWeight_isUncertain() {
        val recipe = success(answer(lines = arrayOf(line("Salz", 0.0, 7), line("Linsen", 200.0, 3))))

        assertTrue(recipe.lines[0].uncertain)
        assertFalse(recipe.lines[1].uncertain)
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
