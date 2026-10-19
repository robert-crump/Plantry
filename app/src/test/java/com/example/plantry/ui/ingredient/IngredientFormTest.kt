package com.example.plantry.ui.ingredient

import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientDraft
import com.example.plantry.data.LabelSource
import com.example.plantry.data.MixComponent
import com.example.plantry.data.MixPart
import com.example.plantry.data.Mixes
import com.example.plantry.data.Nutrient
import com.example.plantry.data.Nutrition
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import com.example.plantry.data.openfoodfacts.OffProduct
import com.example.plantry.data.usda.UsdaFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class IngredientFormTest {

    private val ingredient = Ingredient(
        id = 7,
        name = "Süßkartoffel",
        fdcId = 168482,
        usdaDescription = "Sweet potato, raw",
        nutrition = Nutrition(86.0, 1.57, 20.12, 4.18, 0.05, 3.0),
        storeSection = StoreSection.PRODUCE,
        plantPoints = PlantPoints.ONE,
    )

    private val valid = IngredientForm.from(ingredient)

    @Test
    fun components_goIntoTheDraft_andMakeItAMix() {
        val parts = listOf(MixPart(1, "Himbeeren"), MixPart(null, "Johannisbeeren"))
        val form = valid.copy(components = parts)

        assertTrue(form.isMix)
        assertFalse(valid.isMix)
        assertEquals(parts, form.toDraft()?.components)
        assertEquals(form, IngredientForm.from(ingredient, components = parts))
    }

    @Test
    fun componentPlantPoints_sumTheComponents_aNameOnlyAsOnePoint() {
        val berries = ingredient.copy(id = 1, plantPoints = PlantPoints.ONE)
        val garlic = ingredient.copy(id = 2, plantPoints = PlantPoints.QUARTER)
        val form = valid.copy(components = listOf(MixPart(1, ""), MixPart(2, ""), MixPart(null, "Neu")))

        assertEquals(2.25, form.componentPlantPoints(listOf(berries, garlic).associateBy { it.id }), 0.0)
    }

    @Test
    fun componentSuggestions_leaveOutChosenMixesAndTheMixItself() {
        val all = listOf(
            ingredient.copy(id = 1, name = "Himbeeren"),
            ingredient.copy(id = 2, name = "Himbeer-Mix"),
            ingredient.copy(id = 3, name = "Himbeermark"),
            ingredient.copy(id = 4, name = "Erdbeeren"),
        )
        val contents = Mixes.contents(listOf(MixComponent(2, 4)))
        val form = ComponentsForm(mixId = 3, components = listOf(MixPart(4, "Erdbeeren")), query = "him")

        assertEquals(listOf("Himbeeren"), form.suggestions(all, contents).map { it.name })
    }

    @Test
    fun componentNewName_onlyForANameThatIsNoIngredientOrComponentYet() {
        val all = listOf(ingredient.copy(id = 1, name = "Himbeeren"))
        val form = ComponentsForm(mixId = null, components = listOf(MixPart(null, "Johannisbeeren")))

        assertEquals("Erdbeeren", form.withQuery(" Erdbeeren ").newName(all))
        assertNull(form.withQuery("himbeeren").newName(all))
        assertNull(form.withQuery("JOHANNISBEEREN").newName(all))
        assertNull(form.withQuery("  ").newName(all))
    }

    @Test
    fun componentsForm_addingClearsTheField_andRemoving() {
        val berries = ingredient.copy(id = 1, name = "Himbeeren")
        val form = ComponentsForm(mixId = 3, components = emptyList(), query = "him").with(berries).withQuery("Neu").withNew("Neu")

        assertEquals(listOf(MixPart(1, "Himbeeren"), MixPart(null, "Neu")), form.components)
        assertEquals("", form.query)
        assertEquals(listOf(MixPart(null, "Neu")), form.without(MixPart(1, "Himbeeren")).components)
    }

    @Test
    fun newForm_defaultsToOtherAndZeroPoints() {
        val form = IngredientForm(name = "Tofu", nutrition = valid.nutrition)

        assertEquals(StoreSection.OTHER, form.toDraft()?.storeSection)
        assertEquals(PlantPoints.ZERO, form.toDraft()?.plantPoints)
    }

    @Test
    fun from_formatsDecimalsWithComma() {
        assertEquals("1,57", valid.nutrition[Nutrient.PROTEIN])
        assertEquals("3", valid.nutrition[Nutrient.FIBRE])
    }

    @Test
    fun roundTrip_keepsAllValues() {
        assertEquals(
            IngredientDraft(
                name = "Süßkartoffel",
                nutrition = ingredient.nutrition,
                storeSection = StoreSection.PRODUCE,
                plantPoints = PlantPoints.ONE,
            ),
            valid.toDraft(),
        )
    }

    @Test
    fun toDraft_acceptsCommaAndDot() {
        val draft = valid.withNutrient(Nutrient.PROTEIN, "2,5").withNutrient(Nutrient.FAT, " 0.3 ").toDraft()!!

        assertEquals(2.5, draft.nutrition.protein, 0.0)
        assertEquals(0.3, draft.nutrition.fat, 0.0)
    }

    @Test
    fun overridingNutrition_isValidatedPerNutrient() {
        val form = valid.withNutrient(Nutrient.SUGAR, "-1").withNutrient(Nutrient.KCAL, "")

        assertEquals(setOf(Nutrient.SUGAR, Nutrient.KCAL), form.errors().nutrients)
        assertNull(form.toDraft())
    }

    @Test
    fun blankName_isAnError() {
        assertTrue(valid.copy(name = " ").errors().name)
    }

    @Test
    fun formatDecimal_stripsTrailingZeros() {
        assertEquals("0,4", formatDecimal(0.40, Locale.GERMANY))
        assertEquals("130", formatDecimal(130.0, Locale.GERMANY))
    }

    @Test
    fun formatDecimal_usesTheLocaleSeparator() {
        assertEquals("0.4", formatDecimal(0.40, Locale.US))
        assertEquals("1234.5", formatDecimal(1234.5, Locale.US))
    }

    @Test
    fun unitForm_package_keepsWeightAndCountAsEntered() {
        val form = UnitForm(PieceUnit.SLICE, inPackage = true, grams = "750", count = "20")

        assertEquals(IngredientUnit(0, PieceUnit.SLICE, 37.5, packageGrams = 750.0, packageCount = 20), form.toUnit())
    }

    @Test
    fun unitForm_perUnit_ignoresTheCountField() {
        val form = UnitForm(PieceUnit.CLOVE, inPackage = false, grams = "3,5", count = "abc")

        assertEquals(IngredientUnit(0, PieceUnit.CLOVE, 3.5), form.toUnit())
    }

    @Test
    fun unitForm_aPackageOfOne_staysAPackage() {
        val row = UnitForm(PieceUnit.PACKET, inPackage = true, grams = "500", count = "1").toUnit()!!

        assertTrue(row.isPackage)
        assertEquals(UnitForm(PieceUnit.PACKET, inPackage = true, grams = "500", count = "1"), UnitForm.from(row))
    }

    @Test
    fun unitForm_rejectsMissingUnitOrWeightAndNonWholeCount() {
        assertFalse(UnitForm(unit = null, grams = "60").isValid)
        assertNull(UnitForm(unit = null, grams = "60").toUnit())
        listOf("", "0", "-1", "abc").forEach { grams ->
            val form = UnitForm(PieceUnit.PIECE, grams = grams)
            assertTrue(grams, !form.isValid && form.gramsInvalid)
            assertNull(form.toUnit())
        }
        listOf("", "0", "-2", "2,5", "zwei").forEach { count ->
            val form = UnitForm(PieceUnit.SLICE, inPackage = true, grams = "750", count = count)
            assertTrue(count, !form.isValid && form.countInvalid)
            assertNull(form.toUnit())
        }
    }

    @Test
    fun unitForm_reopensInTheModeItWasEnteredIn() {
        assertEquals(
            UnitForm(PieceUnit.SLICE, inPackage = true, grams = "750", count = "20"),
            UnitForm.from(IngredientUnit.ofPackage(PieceUnit.SLICE, 750.0, 20)),
        )
        assertEquals(
            UnitForm(PieceUnit.TBSP, inPackage = false, grams = "12,5", count = ""),
            UnitForm.from(IngredientUnit.perUnit(PieceUnit.TBSP, 12.5)),
        )
        assertEquals(UnitForm(), UnitForm.from(null))
    }

    @Test
    fun units_goFromTheRowsThroughTheFormIntoTheDraft_inUnitOrder() {
        val slice = IngredientUnit(1, PieceUnit.SLICE, 37.5, packageGrams = 750.0, packageCount = 20)
        val can = IngredientUnit(1, PieceUnit.CAN, 240.0)

        val form = IngredientForm.from(ingredient, listOf(can, slice))

        assertEquals(listOf(slice, can), form.units)
        assertEquals(listOf(slice, can), form.toDraft()!!.units)
    }

    @Test
    fun units_addEditAndRemoveRows_atMostOnePerUnit() {
        val slice = IngredientUnit(0, PieceUnit.SLICE, 37.5)
        val tbsp = IngredientUnit(0, PieceUnit.TBSP, 10.0)
        val form = IngredientForm.from(ingredient).withUnit(tbsp).withUnit(slice)

        assertEquals(listOf(slice, tbsp), form.units)
        assertEquals(listOf(slice, tbsp.copy(gramsPerUnit = 12.0)), form.withUnit(tbsp.copy(gramsPerUnit = 12.0)).units)
        val cup = IngredientUnit(0, PieceUnit.CUP, 150.0)
        assertEquals(listOf(slice, cup), form.withUnit(cup, replacing = PieceUnit.TBSP).units)
        assertEquals(listOf(tbsp), form.withoutUnit(PieceUnit.SLICE).units)
    }

    @Test
    fun availableUnits_leaveOutUnitsWithARow_exceptTheEditedOne() {
        val form = IngredientForm.from(ingredient, listOf(IngredientUnit(1, PieceUnit.SLICE, 37.5), IngredientUnit(1, PieceUnit.CAN, 240.0)))

        assertEquals(PieceUnit.entries - PieceUnit.SLICE - PieceUnit.CAN, form.availableUnits())
        assertEquals(PieceUnit.entries - PieceUnit.CAN, form.availableUnits(editing = PieceUnit.SLICE))
    }

    @Test
    fun newUnit_startsFromThePackungRow_whenThereIsOne() {
        val withPacket = IngredientForm.from(ingredient, listOf(IngredientUnit(1, PieceUnit.PACKET, 320.0)))

        assertEquals(320.0, withPacket.packageGrams!!, 0.0)
        assertNull(IngredientForm.from(ingredient).packageGrams)
        assertEquals(UnitForm(PieceUnit.PIECE, inPackage = true, grams = "320"), UnitForm.forNew(PieceUnit.PIECE, withPacket.packageGrams))
        assertEquals(UnitForm(PieceUnit.PIECE), UnitForm.forNew(PieceUnit.PIECE, null))
        assertEquals(
            IngredientUnit(0, PieceUnit.PIECE, 40.0, packageGrams = 320.0, packageCount = 8),
            UnitForm.forNew(PieceUnit.PIECE, 320.0).copy(count = "8").toUnit(),
        )
    }

    @Test
    fun unitWords_areTheTenUnitsInGerman() {
        assertEquals(
            listOf("Stück", "Scheibe", "Zehe", "Bund", "Stange", "Dose", "Packung", "TL", "EL", "Tasse"),
            PieceUnit.entries.map { it.singular },
        )
        assertEquals(
            listOf("Stück", "Scheiben", "Zehen", "Bund", "Stangen", "Dosen", "Packungen", "TL", "EL", "Tassen"),
            PieceUnit.entries.map { it.plural },
        )
    }

    private val tangerine = UsdaFood(169105, "Tangerines, (mandarin oranges), raw", Nutrition(53.0, 0.81, 13.34, 1.8, 0.31, 0.0))

    @Test
    fun withUsda_replacesEveryValue_andGoesIntoTheDraft() {
        val form = valid.copy(scannedLabel = LabelSource("Bio Süßkartoffel", "4000000000001")).withUsda(tangerine)

        assertEquals(Nutrient.entries.associateWith { formatDecimal(tangerine.nutrition[it]) }, form.nutrition)
        assertNull(form.scannedLabel)
        assertEquals("Süßkartoffel", form.name)
        assertEquals(tangerine, form.toDraft()!!.pickedUsda)
    }

    @Test
    fun hasNutrition_onlyForValuesOtherThanBlankOrZero() {
        assertFalse(IngredientForm.from(ingredient.copy(nutrition = Nutrition())).hasNutrition)
        assertFalse(valid.copy(nutrition = Nutrient.entries.associateWith { "" }).hasNutrition)
        assertTrue(valid.hasNutrition)
        assertTrue(valid.copy(nutrition = Nutrient.entries.associateWith { "" } + (Nutrient.KCAL to "abc")).hasNutrition)
    }
}
