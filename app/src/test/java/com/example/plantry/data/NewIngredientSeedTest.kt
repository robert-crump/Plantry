package com.example.plantry.data

import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.claude.NutritionSource
import com.example.plantry.data.openfoodfacts.OffProduct
import com.example.plantry.data.usda.UsdaFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewIngredientSeedTest {

    private val label = Nutrition(300.0, 8.0, 50.0, 3.0, 7.0, 4.0)

    private fun product(packageGrams: Double?) = OffProduct(
        barcode = "4001234567890",
        nameSuggestion = "Tortillas",
        productName = "High Protein Tortillas (Mestemacher)",
        quantity = packageGrams?.let { "$it g" },
        packageGrams = packageGrams,
        nutrition = emptyMap(),
    )

    @Test
    fun fromProduct_withPackageSize_hasAPackungUnit() {
        val seed = NewIngredientSeed.fromProduct(product(320.0), "Protein-Tortillas", label)

        assertEquals("Protein-Tortillas", seed.name)
        assertEquals(label, seed.nutrition)
        assertEquals(LabelSource("High Protein Tortillas (Mestemacher)", "4001234567890"), seed.labelSource)
        assertNull(seed.fdcId)
        assertEquals(listOf(IngredientUnit(0, PieceUnit.PACKET, 320.0)), seed.units)
        assertEquals(StoreSection.OTHER, seed.storeSection)
        assertEquals(PlantPoints.ZERO, seed.plantPoints)
    }

    @Test
    fun fromProduct_withoutPackageSize_hasNoUnits() {
        assertEquals(emptyList<IngredientUnit>(), NewIngredientSeed.fromProduct(product(null), "Tortillas", label).units)
    }

    private val tofu = UsdaFood(172475, "Tofu, raw, firm, prepared with calcium sulfate", Nutrition(144.0, 17.3, 2.8, 0.6, 8.7, 2.3))

    private fun proposal(source: NutritionSource?) = IngredientProposal(
        name = "Tofu natur",
        source = source,
        searchTerms = listOf("tofu"),
        storeSection = StoreSection.DAIRY_CHILLED,
        plantPoints = PlantPoints.ONE,
        proposedUnits = listOf(IngredientUnit(0, PieceUnit.PACKET, 200.0)),
    )

    @Test
    fun fromProposal_takesUsdaEntryAndClaudesValues() {
        val seed = NewIngredientSeed.fromProposal(proposal(NutritionSource.Usda(tofu)))

        assertEquals("Tofu natur", seed.name)
        assertEquals(tofu.nutrition, seed.nutrition)
        assertEquals(172475L, seed.fdcId)
        assertEquals(tofu.description, seed.usdaDescription)
        assertNull(seed.labelSource)
        assertEquals(StoreSection.DAIRY_CHILLED, seed.storeSection)
        assertEquals(PlantPoints.ONE, seed.plantPoints)
        assertEquals(listOf(IngredientUnit(0, PieceUnit.PACKET, 200.0)), seed.units)
    }

    @Test
    fun fromProposal_withoutUsdaEntry_usesTheTypedLabelValues() {
        val seed = NewIngredientSeed.fromProposal(proposal(null), name = "Seidentofu", labelNutrition = label)

        assertEquals("Seidentofu", seed.name)
        assertEquals(label, seed.nutrition)
        assertNull(seed.fdcId)
        assertNull(seed.usdaDescription)
        assertEquals(StoreSection.DAIRY_CHILLED, seed.storeSection)
        assertEquals(1, seed.units.size)
    }
}
