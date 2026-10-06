package com.example.plantry.ui.ingredient

import com.example.plantry.data.DrainedWeight
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientDraft
import com.example.plantry.data.LabelSource
import com.example.plantry.data.Nutrient
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import com.example.plantry.data.openfoodfacts.OffProduct
import org.junit.Assert.assertEquals
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
        reviewed = false,
    )

    private val valid = IngredientForm.from(ingredient)

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
    fun drainedWeight_isKeptInTheDraft() {
        val can = ingredient.copy(drainedWeight = DrainedWeight(400.0, 240.0))

        assertEquals(DrainedWeight(400.0, 240.0), IngredientForm.from(can).toDraft()!!.drainedWeight)
        assertNull(valid.toDraft()!!.drainedWeight)
    }

    @Test
    fun drainedWeightForm_bothEmptyMeansNotDrained() {
        val form = DrainedWeightForm(" ", "")

        assertTrue(form.isValid)
        assertNull(form.toDrainedWeight())
    }

    @Test
    fun drainedWeightForm_acceptsCommaDecimalsUpToTheNetWeight() {
        assertEquals(DrainedWeight(400.0, 240.5), DrainedWeightForm("400", "240,5").toDrainedWeight())
        assertEquals(DrainedWeight(400.0, 400.0), DrainedWeightForm("400", "400").toDrainedWeight())
    }

    @Test
    fun drainedWeightForm_rejectsOneEmptyFieldZeroAndMoreThanNet() {
        assertTrue(DrainedWeightForm("400", "").let { !it.isValid && it.drainedError == DrainedWeightError.INVALID })
        assertTrue(DrainedWeightForm("", "240").let { !it.isValid && it.netInvalid })
        assertTrue(DrainedWeightForm("0", "0").let { it.netInvalid && it.drainedError == DrainedWeightError.INVALID })
        assertEquals(DrainedWeightError.EXCEEDS_NET, DrainedWeightForm("240", "400").drainedError)
        assertNull(DrainedWeightForm("240", "400").toDrainedWeight())
    }

    @Test
    fun drainedWeightForm_fromWeight_formatsWithoutTrailingZeros() {
        assertEquals(DrainedWeightForm("400", "240"), DrainedWeightForm.from(DrainedWeight(400.0, 240.0)))
        assertEquals(DrainedWeightForm(), DrainedWeightForm.from(null))
    }

    @Test
    fun withScanned_takesFoundValuesKeepsMissingOnesAndTheName() {
        val product = OffProduct(
            barcode = "4000000000001",
            nameSuggestion = "Süßkartoffeln",
            productName = "Süßkartoffel-Würfel (Frosta)",
            quantity = "400 g",
            nutrition = mapOf(Nutrient.KCAL to 90.0, Nutrient.PROTEIN to 1.6, Nutrient.FAT to 0.1),
        )

        val scanned = valid.withScanned(product)

        assertEquals("Süßkartoffel", scanned.name)
        assertEquals("90", scanned.nutrition[Nutrient.KCAL])
        assertEquals("1,6", scanned.nutrition[Nutrient.PROTEIN])
        assertEquals("0,1", scanned.nutrition[Nutrient.FAT])
        assertEquals(valid.nutrition[Nutrient.CARBS], scanned.nutrition[Nutrient.CARBS])
        assertEquals(valid.nutrition[Nutrient.FIBRE], scanned.nutrition[Nutrient.FIBRE])
        assertEquals(LabelSource("Süßkartoffel-Würfel (Frosta)", "4000000000001"), scanned.toDraft()!!.scannedLabel)
    }

    @Test
    fun loadedForm_hasNoScannedLabel() {
        assertNull(valid.toDraft()!!.scannedLabel)
    }
}
