package com.example.plantry.ui.ingredient

import com.example.plantry.data.BuyUnit
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientDraft
import com.example.plantry.data.Nutrient
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import com.example.plantry.data.UnitWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientFormTest {

    private val ingredient = Ingredient(
        id = 7,
        name = "Süßkartoffel",
        fdcId = 168482,
        usdaDescription = "Sweet potato, raw",
        nutrition = Nutrition(86.0, 1.57, 20.12, 4.18, 0.05, 3.0),
        unitWeights = listOf(UnitWeight("medium", 130.0)),
        buyUnit = BuyUnit.PIECES,
        packSizeGrams = null,
        storeSection = StoreSection.PRODUCE,
        staple = false,
        plantPoints = PlantPoints.ONE,
        buyAsIngredientId = null,
        buyAsYieldFactor = null,
        reviewed = false,
    )

    private val valid = IngredientForm.from(ingredient)

    @Test
    fun from_formatsDecimalsWithComma() {
        assertEquals("1,57", valid.nutrition[Nutrient.PROTEIN])
        assertEquals("3", valid.nutrition[Nutrient.FIBRE])
        assertEquals(listOf(UnitWeightInput("medium", "130")), valid.unitWeights)
    }

    @Test
    fun roundTrip_keepsAllValues() {
        assertEquals(
            IngredientDraft(
                name = "Süßkartoffel",
                nutrition = ingredient.nutrition,
                unitWeights = ingredient.unitWeights,
                buyUnit = BuyUnit.PIECES,
                packSizeGrams = null,
                storeSection = StoreSection.PRODUCE,
                staple = false,
                plantPoints = PlantPoints.ONE,
                buyAsIngredientId = null,
                buyAsYieldFactor = null,
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
    fun unitWeights_canBeAddedEditedAndRemoved() {
        val form = valid.copy(buyUnit = BuyUnit.GRAMS)
            .addUnitWeight()
            .withUnitWeight(1, UnitWeightInput("cup", "133"))
            .removeUnitWeight(0)

        assertEquals(listOf(UnitWeight("cup", 133.0)), form.toDraft()?.unitWeights)
    }

    @Test
    fun incompleteUnitWeightRow_isAnError() {
        val form = valid.addUnitWeight().withUnitWeight(1, UnitWeightInput("cup", "0"))

        assertEquals(setOf(1), form.errors().unitWeights)
    }

    @Test
    fun pieces_requireMediumUnitWeight() {
        assertFalse(valid.errors().pieceWeightMissing)
        assertTrue(valid.removeUnitWeight(0).errors().pieceWeightMissing)
        assertFalse(valid.withUnitWeight(0, UnitWeightInput("Mittel", "120")).errors().pieceWeightMissing)
    }

    @Test
    fun pack_requiresPackSize() {
        val pack = valid.copy(buyUnit = BuyUnit.PACK)

        assertTrue(pack.errors().packSize)
        assertEquals(400.0, pack.copy(packSize = "400").toDraft()?.packSizeGrams)
    }

    @Test
    fun packSize_isDroppedForOtherBuyUnits() {
        assertNull(valid.copy(buyUnit = BuyUnit.GRAMS, packSize = "400").toDraft()?.packSizeGrams)
    }

    @Test
    fun buyAs_requiresPositiveYieldFactor() {
        val linked = valid.copy(buyAsIngredientId = 3)

        assertTrue(linked.errors().buyAsYieldFactor)
        assertEquals(0.4, linked.copy(buyAsYieldFactor = "0,4").toDraft()?.buyAsYieldFactor)
        assertNull(valid.copy(buyAsYieldFactor = "0,4").toDraft()?.buyAsYieldFactor)
    }

    @Test
    fun blankName_isAnError() {
        assertTrue(valid.copy(name = " ").errors().name)
    }

    @Test
    fun formatDecimal_stripsTrailingZeros() {
        assertEquals("0,4", formatDecimal(0.40))
        assertEquals("130", formatDecimal(130.0))
    }
}
