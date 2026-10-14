package com.example.plantry.ui.recipe

import com.example.plantry.data.PieceUnit
import com.example.plantry.data.PieceWeight
import com.example.plantry.data.RecipeIngredientDraft
import com.example.plantry.data.ingredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeLineFormTest {

    private val sweetPotato = ingredient(id = 3, name = "Süßkartoffel")

    @Test
    fun pickingASuggestion_setsIngredientAndName() {
        val form = RecipeLineForm().withIngredientQuery("süß").withIngredient(sweetPotato)

        assertEquals(3L, form.ingredientId)
        assertEquals("Süßkartoffel", form.ingredientQuery)
    }

    @Test
    fun applyingAScannedLine_confirmsClaudesNameForThePickedIngredient() {
        val line = RecipeFormLine("1 Dose Kichererbsen", 240.0, ingredientId = null, ingredientName = "Kichererbsen, abgetropft")

        val form = RecipeLineForm.from(line, null).withIngredientQuery("kicher").withIngredient(7, "Kichererbsen (Dose)")

        assertEquals(
            RecipeFormLine("1 Dose Kichererbsen", 240.0, 7, ingredientName = "Kichererbsen, abgetropft", confirmed = true),
            form.toFormLine(),
        )
    }

    @Test
    fun applyingAHandEnteredLine_hasNoWordingToLearn() {
        val form = RecipeLineForm(grams = "240").withIngredientQuery("süßkart").withIngredient(sweetPotato)

        assertEquals("", form.toFormLine()!!.ingredientName)
    }

    @Test
    fun withAlias_selectsTheAliasedIngredientOnlyWhenNoneIsSelected() {
        val aliases = mapOf("süßkartoffeln geschält" to 3L)
        val names = mapOf(3L to "Süßkartoffel", 4L to "Kürbis")

        val typed = RecipeLineForm().withIngredientQuery("Süßkartoffeln, geschält").withAlias(aliases, names)
        assertEquals(3L, typed.ingredientId)
        assertEquals("Süßkartoffel", typed.ingredientQuery)

        val picked = RecipeLineForm().withIngredientQuery("Süßkartoffeln, geschält").withIngredient(4, "Kürbis")
        assertEquals(4L, picked.withAlias(aliases, names).ingredientId)

        assertNull(RecipeLineForm().withIngredientQuery("Kürbis").withAlias(aliases, names).ingredientId)
    }

    @Test
    fun typingAfterPicking_clearsTheIngredient() {
        val form = RecipeLineForm().withIngredient(sweetPotato).withIngredientQuery("Süßkartoffe")

        assertNull(form.ingredientId)
        assertTrue(form.errors().ingredient)
    }

    @Test
    fun toDraft_parsesDecimalCommaAndTrimsText() {
        val form = RecipeLineForm(originalText = " 1 große Süßkartoffel ", grams = "312,5").withIngredient(sweetPotato)

        assertEquals(RecipeIngredientDraft("1 große Süßkartoffel", 312.5, 3), form.toDraft())
    }

    @Test
    fun toDraft_withBlankText_describesAmountAndIngredient() {
        val form = RecipeLineForm(grams = "300").withIngredient(sweetPotato)

        assertEquals("300 g Süßkartoffel", form.toDraft()!!.originalText)
    }

    @Test
    fun toDraft_rejectsMissingGramsOrIngredient() {
        val valid = RecipeLineForm(grams = "300").withIngredient(sweetPotato)

        assertNull(valid.copy(grams = "0").toDraft())
        assertNull(valid.copy(grams = "viel").toDraft())
        assertNull(valid.withIngredientQuery("Süß").toDraft())
        assertFalse(valid.errors().hasAny)
    }

    @Test
    fun from_roundTripsAnExistingLine() {
        val line = RecipeIngredientDraft("1 große Süßkartoffel", 312.5, 3)

        val form = RecipeLineForm.from(line, "Süßkartoffel")

        assertEquals("312,5", form.grams)
        assertEquals(line, form.toDraft())
    }

    private val toast = ingredient(id = 5, name = "Toast").copy(pieceWeight = PieceWeight(PieceUnit.SLICE, 750.0, 20))

    @Test
    fun autoGrams_textThenIngredient() {
        val form = RecipeLineForm().withText("4 Scheiben Toast").withIngredientQuery("Toa").withIngredient(toast)

        assertEquals("150", form.grams)
        assertEquals("4 × 37,5 g", form.autoAmount()!!.calculation())
        assertEquals("225", form.withText("6 Scheiben Toast").grams)
    }

    @Test
    fun autoGrams_ingredientThenText() {
        val form = RecipeLineForm().withIngredient(toast).withText("4").withText("4 Scheiben")

        assertEquals("150", form.grams)
    }

    @Test
    fun autoGrams_gramsWorkWithoutAPieceWeight() {
        val form = RecipeLineForm().withText("150 g Toast")

        assertEquals("150", form.grams)
        assertEquals("150 g", form.autoAmount()!!.calculation())
        assertEquals("150", form.withIngredient(sweetPotato).grams)
        assertEquals("150", form.withIngredient(toast).grams)
    }

    @Test
    fun autoGrams_clearedWhenNothingParses() {
        val form = RecipeLineForm().withIngredient(toast).withText("4 Scheiben")

        assertEquals("", form.withText("etwas Toast").grams)
        assertNull(form.withText("etwas Toast").autoAmount())
        // Another ingredient has no slices.
        assertEquals("", form.withIngredientQuery("Süß").grams)
        assertEquals("", form.withIngredient(sweetPotato).grams)
    }

    @Test
    fun autoGrams_typedGramsAreLeftAlone() {
        val form = RecipeLineForm().withIngredient(toast).withText("4 Scheiben").withGrams("160")

        assertFalse(form.gramsAuto)
        assertNull(form.autoAmount())
        assertEquals("160", form.withText("6 Scheiben").grams)
        assertEquals("160", form.withIngredient(sweetPotato).withIngredient(toast).grams)
        assertEquals("", form.withGrams("").withText("2 Scheiben").grams)
    }

    @Test
    fun autoGrams_anExistingLineKeepsItsGrams() {
        val line = RecipeIngredientDraft("4 Scheiben Toast", 160.0, 5)

        val form = RecipeLineForm.from(line, "Toast", toast.pieceWeight)

        assertFalse(form.gramsAuto)
        assertEquals("160", form.grams)
        assertEquals("160", form.withText("6 Scheiben Toast").grams)
    }

    @Test
    fun autoGrams_aLineWithoutGramsIsFilledIn() {
        val line = RecipeFormLine("4 Scheiben Toast", 0.0, ingredientId = 5)

        val form = RecipeLineForm.from(line, "Toast", toast.pieceWeight)

        assertTrue(form.gramsAuto)
        assertEquals("150", form.grams)
    }

    @Test
    fun autoGrams_aliasPicksThePieceWeight() {
        val form = RecipeLineForm().withText("2 Scheiben").withIngredientQuery("Toastbrot")
            .withAlias(mapOf("toastbrot" to 5L), mapOf(5L to "Toast"), mapOf(5L to toast.pieceWeight!!))

        assertEquals("75", form.grams)
    }
}
