package com.example.plantry.ui.ingredient

import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientDraft
import com.example.plantry.data.Nutrient
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
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
}
