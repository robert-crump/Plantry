package com.example.plantry.ui.recipe

import com.example.plantry.data.Nutrient
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class NutritionFormatTest {

    @Test
    fun plantPoints_useTheLocaleSeparator() {
        assertEquals("12", formatPlantPoints(12.0, Locale.GERMANY))
        assertEquals("12,25", formatPlantPoints(12.25, Locale.GERMANY))
        assertEquals("12.5", formatPlantPoints(12.5, Locale.US))
    }

    @Test
    fun nutrients_useTheLocaleSeparator() {
        assertEquals("24,3", formatNutrient(24.34, Nutrient.PROTEIN, Locale.GERMANY))
        assertEquals("24.3", formatNutrient(24.34, Nutrient.PROTEIN, Locale.US))
        assertEquals("520", formatNutrient(519.6, Nutrient.KCAL, Locale.US))
    }
}
