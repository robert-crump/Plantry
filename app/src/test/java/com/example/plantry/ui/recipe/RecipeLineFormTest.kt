package com.example.plantry.ui.recipe

import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.LineUnit
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.RecipeIngredientDraft
import com.example.plantry.data.ingredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeLineFormTest {

    private val sweetPotato = ingredient(id = 3, name = "Süßkartoffel")
    private val toast = ingredient(id = 5, name = "Vollkorntoast")
    private val slice = IngredientUnit(5, PieceUnit.SLICE, 37.5, packageGrams = 750.0, packageCount = 20)
    private val sliceUnit = LineUnit.Of(PieceUnit.SLICE)

    @Test
    fun pickingASuggestion_setsIngredientAndName() {
        val form = RecipeLineForm().withIngredientQuery("süß").withIngredient(sweetPotato, emptyList())

        assertEquals(3L, form.ingredientId)
        assertEquals("Süßkartoffel", form.ingredientQuery)
    }

    @Test
    fun applyingAScannedLine_confirmsClaudesNameForThePickedIngredient() {
        val line = RecipeFormLine(240.0, LineUnit.Grams, ingredientId = null, wording = "1 Dose Kichererbsen", ingredientName = "Kichererbsen, abgetropft")

        val form = RecipeLineForm.from(line, null).withIngredientQuery("kicher").withIngredient(7, "Kichererbsen (Dose)")

        assertEquals(
            RecipeFormLine(240.0, LineUnit.Grams, 7, "1 Dose Kichererbsen", ingredientName = "Kichererbsen, abgetropft", confirmed = true),
            form.toFormLine(),
        )
    }

    @Test
    fun applyingAHandEnteredLine_hasNoWordingToLearn() {
        val form = RecipeLineForm(amount = "240").withIngredientQuery("süßkart").withIngredient(sweetPotato, emptyList())

        assertEquals("", form.toFormLine()!!.ingredientName)
    }

    @Test
    fun withAlias_selectsTheAliasedIngredientWithItsUnits_onlyWhenNoneIsSelected() {
        val aliases = mapOf("toastbrot" to 5L)
        val names = mapOf(5L to "Vollkorntoast", 4L to "Kürbis")
        val units = mapOf(5L to listOf(slice))

        val typed = RecipeLineForm().withIngredientQuery("Toastbrot").withAlias(aliases, names, units)
        assertEquals(5L, typed.ingredientId)
        assertEquals("Vollkorntoast", typed.ingredientQuery)
        assertEquals(listOf(slice), typed.units)

        val picked = RecipeLineForm().withIngredientQuery("Toastbrot").withIngredient(4, "Kürbis")
        assertEquals(4L, picked.withAlias(aliases, names, units).ingredientId)

        assertNull(RecipeLineForm().withIngredientQuery("Kürbis").withAlias(aliases, names, units).ingredientId)
    }

    @Test
    fun typingAfterPicking_clearsTheIngredient() {
        val form = RecipeLineForm().withIngredient(sweetPotato, emptyList()).withIngredientQuery("Süßkartoffe")

        assertNull(form.ingredientId)
        assertTrue(form.errors().ingredient)
    }

    @Test
    fun unitOptions_gAndMlThenTheIngredientsUnitsThenTheOthers() {
        val others = PieceUnit.entries.map(LineUnit::Of)
        assertEquals(listOf(LineUnit.Grams, LineUnit.Milliliters) + others, RecipeLineForm().unitOptions())

        val clove = IngredientUnit(5, PieceUnit.CLOVE, 4.0)
        val options = RecipeLineForm().withIngredient(toast, listOf(slice, clove)).unitOptions()
        assertEquals(listOf(LineUnit.Grams, LineUnit.Milliliters, sliceUnit, LineUnit.Of(PieceUnit.CLOVE)), options.take(4))
        assertEquals(others - sliceUnit - LineUnit.Of(PieceUnit.CLOVE), options.drop(4))
    }

    @Test
    fun theUnitStaysWhenTheIngredientChanges_andIsMissingForOneThatLacksIt() {
        val slices = RecipeLineForm(amount = "2").withIngredient(toast, listOf(slice)).withUnit(sliceUnit)

        assertNull(slices.missingUnit)
        assertEquals(sliceUnit, slices.withIngredient(sweetPotato, emptyList()).unit)
        assertEquals(PieceUnit.SLICE, slices.withIngredient(sweetPotato, emptyList()).missingUnit)
        assertNull("no ingredient selected", slices.withIngredientQuery("Brot").missingUnit)
        assertNull("g is always there", slices.withUnit(LineUnit.Grams).withIngredient(sweetPotato, emptyList()).missingUnit)
    }

    @Test
    fun aMissingUnit_needsValidGramsBeforeTheLineApplies() {
        val form = RecipeLineForm(amount = "2").withIngredient(toast, emptyList()).withUnit(sliceUnit)

        assertTrue(form.errors().unitGrams)
        assertNull(form.toFormLine())
        assertNull(form.withDefinition { copy(grams = "0") }.toFormLine())
        assertNull(form.withDefinition { copy(grams = "viel") }.newUnit())

        val defined = form.withDefinition { copy(grams = "37,5") }
        assertFalse(defined.errors().hasAny)
        assertEquals(IngredientUnit(5, PieceUnit.SLICE, 37.5), defined.newUnit())
        assertEquals(RecipeIngredientDraft(2.0, sliceUnit, 5), defined.toDraft())
        assertEquals("2 × 37,5 g = 75 g", defined.resolved()!!.calculation)
    }

    @Test
    fun aScannedMissingUnit_startsWithClaudesGrams_confirmingDefinesIt() {
        val line = RecipeFormLine(2.0, sliceUnit, 5, ingredientName = "Toast", unitGrams = 37.5, missingUnit = true)

        val form = RecipeLineForm.from(line, "Toast")

        assertEquals(PieceUnit.SLICE, form.missingUnit)
        assertEquals(IngredientUnit(5, PieceUnit.SLICE, 37.5), form.newUnit())
        assertEquals(RecipeFormLine(2.0, sliceUnit, 5, ingredientName = "Toast", confirmed = true), form.toFormLine())
    }

    @Test
    fun aMissingUnitThatComesInPackages_isDefinedAsPackageDividedByCount() {
        val form = RecipeLineForm(amount = "2").withIngredient(toast, emptyList()).withUnit(sliceUnit)
            .withDefinition { copy(inPackage = true, grams = "750") }

        assertTrue(form.offersPackage)
        assertTrue(form.errors().unitCount)
        assertNull(form.toFormLine())

        val defined = form.withDefinition { copy(count = "20") }
        assertEquals(slice, defined.newUnit())
        assertEquals("2 × 37,5 g = 75 g", defined.resolved()!!.calculation)
    }

    @Test
    fun aUnitThatDoesNotComeInPackages_ignoresThePackageMode() {
        val form = RecipeLineForm(amount = "1").withIngredient(toast, emptyList()).withUnit(LineUnit.Of(PieceUnit.TBSP))
            .withDefinition { copy(inPackage = true, grams = "15") }

        assertFalse(form.offersPackage)
        assertFalse(form.errors().hasAny)
        assertEquals(IngredientUnit(5, PieceUnit.TBSP, 15.0), form.newUnit())
    }

    @Test
    fun aUnitTheIngredientHas_definesNothing() {
        val form = RecipeLineForm(amount = "2").withIngredient(toast, listOf(slice)).withUnit(sliceUnit)
            .withDefinition { copy(grams = "99") }

        assertNull(form.newUnit())
        assertEquals("2 × 37,5 g = 75 g", form.resolved()!!.calculation)
    }

    @Test
    fun aNewIngredientsMissingUnit_carriesItsTemporaryId() {
        val form = RecipeLineForm(amount = "1").withIngredient(-1, "Seitan").withUnit(sliceUnit).withDefinition { copy(grams = "30") }

        assertEquals(IngredientUnit(-1, PieceUnit.SLICE, 30.0), form.newUnit())
    }

    @Test
    fun resolved_showsTheCalculationOnceAmountAndIngredientAreValid() {
        val form = RecipeLineForm(amount = "2").withIngredient(toast, listOf(slice)).withUnit(sliceUnit)

        assertEquals("2 × 37,5 g = 75 g", form.resolved()!!.calculation)
        assertEquals("2 Scheiben Vollkorntoast", form.resolved()!!.text)
        assertEquals("2 Toasts", form.withWording("2 Toasts").resolved()!!.text)
        assertNull(form.withAmount("").resolved())
        assertNull(form.withIngredientQuery("Vollkorn").resolved())
    }

    @Test
    fun toDraft_parsesDecimalCommaAndTrimsTheWording() {
        val form = RecipeLineForm(amount = "312,5", wording = " 1 große Süßkartoffel ").withIngredient(sweetPotato, emptyList())

        assertEquals(RecipeIngredientDraft(312.5, LineUnit.Grams, 3, "1 große Süßkartoffel"), form.toDraft())
    }

    @Test
    fun toDraft_keepsTheUnit_blankWordingIsNone() {
        val form = RecipeLineForm(amount = "1,5", wording = "  ").withIngredient(toast, listOf(slice)).withUnit(sliceUnit)

        assertEquals(RecipeIngredientDraft(1.5, sliceUnit, 5, wording = null), form.toDraft())
    }

    @Test
    fun toDraft_rejectsMissingAmountOrIngredient() {
        val valid = RecipeLineForm(amount = "300").withIngredient(sweetPotato, emptyList())

        assertNull(valid.copy(amount = "0").toDraft())
        assertNull(valid.copy(amount = "viel").toDraft())
        assertTrue(valid.copy(amount = "").errors().amount)
        assertNull(valid.withIngredientQuery("Süß").toDraft())
        assertFalse(valid.errors().hasAny)
    }

    @Test
    fun from_roundTripsAnExistingLine() {
        val line = RecipeIngredientDraft(1.5, sliceUnit, 5, "1 ½ Scheiben Toast")

        val form = RecipeLineForm.from(line, "Vollkorntoast", listOf(slice))

        assertEquals("1,5", form.amount)
        assertEquals(sliceUnit, form.unit)
        assertEquals("1 ½ Scheiben Toast", form.wording)
        assertEquals(line, form.toDraft())
    }
}
