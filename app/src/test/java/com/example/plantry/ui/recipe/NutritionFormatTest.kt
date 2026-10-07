package com.example.plantry.ui.recipe

import com.example.plantry.data.Nutrient
import com.example.plantry.data.PlantPoints
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
    fun ingredientPlantPoints_areDecimalsNotFractions() {
        assertEquals(listOf("1", "0,25", "0"), PlantPoints.entries.map { formatPlantPoints(it.value, Locale.GERMANY) })
        assertEquals(listOf("1", "0.25", "0"), PlantPoints.entries.map { formatPlantPoints(it.value, Locale.US) })
    }

    @Test
    fun nutrients_useTheLocaleSeparator() {
        assertEquals("24,3", formatNutrient(24.34, Nutrient.PROTEIN, Locale.GERMANY))
        assertEquals("24.3", formatNutrient(24.34, Nutrient.PROTEIN, Locale.US))
        assertEquals("520", formatNutrient(519.6, Nutrient.KCAL, Locale.US))
    }

    @Test
    fun roundedNutrients_areWholeNumbersWithAGluedUnit() {
        assertEquals("24 g", formatRounded(24.4, Nutrient.PROTEIN, Locale.GERMANY))
        assertEquals("25 g", formatRounded(24.5, Nutrient.PROTEIN, Locale.GERMANY))
        assertEquals("520 kcal", formatRounded(519.6, Nutrient.KCAL, Locale.GERMANY))
    }

    @Test
    fun roundedNutrients_tinyNonzeroAmountsReadLessThanOne() {
        assertEquals("<1 g", formatRounded(0.4, Nutrient.FAT, Locale.GERMANY))
        assertEquals("0 g", formatRounded(0.0, Nutrient.FAT, Locale.GERMANY))
    }
}
