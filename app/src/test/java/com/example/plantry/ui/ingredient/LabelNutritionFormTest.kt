package com.example.plantry.ui.ingredient

import com.example.plantry.data.Nutrient
import com.example.plantry.data.Nutrition
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
}
