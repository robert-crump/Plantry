package com.example.plantry.ui.ingredient

import com.example.plantry.data.Nutrient
import com.example.plantry.data.Nutrition
import com.example.plantry.data.openfoodfacts.OffProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelNutritionFormTest {

    private val filled = LabelNutritionForm(
        values = mapOf(
            Nutrient.KCAL to "52",
            Nutrient.PROTEIN to "1,0",
            Nutrient.CARBS to "9.1",
            Nutrient.SUGAR to "4",
            Nutrient.FAT to "1,5",
            Nutrient.FIBRE to "0",
        ),
    )

    @Test
    fun allValues_parseWithCommaOrDotAndZero() {
        assertEquals(Nutrition(52.0, 1.0, 9.1, 4.0, 1.5, 0.0), filled.toNutrition())
        assertTrue(filled.invalid().isEmpty())
    }

    @Test
    fun blankValue_isMissingButNotInvalid() {
        val form = filled.withValue(Nutrient.FIBRE, " ")

        assertNull(form.toNutrition())
        assertTrue(form.invalid().isEmpty())
    }

    @Test
    fun negativeOrNonNumericValue_isInvalid() {
        val form = filled.withValue(Nutrient.FAT, "-1").withValue(Nutrient.SUGAR, "viel")

        assertNull(form.toNutrition())
        assertEquals(setOf(Nutrient.FAT, Nutrient.SUGAR), form.invalid())
    }

    @Test
    fun from_roundTrips() {
        val nutrition = Nutrition(52.0, 1.0, 9.1, 4.0, 1.5, 0.0)

        assertEquals(nutrition, LabelNutritionForm.from(nutrition).toNutrition())
    }

    @Test
    fun fromProduct_prefillsNameAndFoundValuesAndLeavesMissingBlank() {
        val product = OffProduct(
            barcode = "1",
            nameSuggestion = "Kichererbsen, gegart",
            productName = "Bio Kichererbsen",
            quantity = null,
            nutrition = mapOf(Nutrient.KCAL to 128.0, Nutrient.PROTEIN to 7.0, Nutrient.CARBS to 15.4, Nutrient.SUGAR to 0.5, Nutrient.FAT to 2.1),
        )

        val form = LabelNutritionForm.from(product)

        assertEquals("Kichererbsen, gegart", form.name)
        assertEquals("15,4", form.values[Nutrient.CARBS])
        assertEquals("", form.values[Nutrient.FIBRE])
        assertNull(form.toNutrition())
        assertEquals(Nutrition(128.0, 7.0, 15.4, 0.5, 2.1, 0.0), form.withValue(Nutrient.FIBRE, "0").toNutrition())
    }
}
