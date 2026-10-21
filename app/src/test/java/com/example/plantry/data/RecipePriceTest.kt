package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecipePriceTest {

    private val today = LocalDate.of(2026, 10, 21)

    private fun price(perKg: Double, date: LocalDate = today) = Price(perKg, date)

    private fun line(id: Long, name: String, grams: Double?, price: Price?) = PriceLine(id, name, grams, price)

    private fun calculate(vararg lines: PriceLine, servings: Int = 1) = RecipePrice.calculate(lines.toList(), servings, today)

    @Test
    fun perPortion_isSumOfGramsTimesPricePerKgDividedByOurServings() {
        val price = calculate(
            line(1, "Zucchini", 500.0, price(2.99)),
            line(2, "Lachs", 400.0, price(17.0)),
            servings = 4,
        )

        assertEquals((0.5 * 2.99 + 0.4 * 17.0) / 4, price.perPortion!!, 1e-9)
    }

    @Test
    fun costs_arePerIngredientForTheWholeRecipeMostExpensiveFirst() {
        val price = calculate(
            line(1, "Zucchini", 500.0, price(3.0)),
            line(2, "Lachs", 400.0, price(17.0)),
            line(1, "Zucchini", 500.0, price(3.0)),
            servings = 4,
        )

        assertEquals(listOf("Lachs", "Zucchini"), price.costs.map { it.name })
        assertEquals(6.8, price.costs[0].cost, 1e-9)
        assertEquals(3.0, price.costs[1].cost, 1e-9)
    }

    @Test
    fun noPrices_haveNoEstimate() {
        val price = calculate(line(1, "Zucchini", 500.0, null))

        assertNull(price.perPortion)
        assertNull(price.roundedPerPortion)
        assertTrue(price.costs.isEmpty())
    }

    @Test
    fun noLines_haveNoEstimateAndNoGaps() {
        val price = calculate()

        assertNull(price.perPortion)
        assertFalse(price.hasGaps)
    }

    @Test
    fun nonPositiveServings_areRejected() {
        assertThrows(IllegalArgumentException::class.java) { RecipePrice.calculate(emptyList(), servings = 0, today) }
    }

    @Test
    fun aZeroPrice_countsAsPriced() {
        val price = calculate(line(1, "Wasser", 500.0, price(0.0)))

        assertEquals(0.0, price.perPortion!!, 1e-9)
        assertTrue(price.missing.isEmpty())
    }

    @Test
    fun missing_ignoresUnpricedIngredientsUnderFiveGramsOverTheWholeRecipe() {
        val price = calculate(
            line(1, "Zucchini", 500.0, price(3.0)),
            line(2, "Salz", 4.9, null),
            line(3, "Kreuzkümmel", 5.0, null),
        )

        assertEquals(listOf("Kreuzkümmel"), price.missing.map { it.name })
        assertTrue(price.hasGaps)
    }

    @Test
    fun missing_sumsTheGramsOfAnIngredientOverItsLines() {
        val price = calculate(line(1, "Butter", 3.0, null), line(2, "Mehl", 200.0, price(1.0)), line(1, "Butter", 4.0, null))

        assertEquals(listOf("Butter"), price.missing.map { it.name })
    }

    @Test
    fun missing_alwaysListsLinesWithoutAWeight() {
        val price = calculate(
            line(1, "Ingwer", null, null),
            line(2, "Toast", null, price(3.0)),
        )

        assertEquals(listOf("Ingwer", "Toast"), price.missing.map { it.name })
        assertNull(price.perPortion)
    }

    @Test
    fun aPricedIngredientWithAnUnweighedLine_isCostedForItsWeighedLinesAndMissing() {
        val price = calculate(line(1, "Toast", 100.0, price(3.0)), line(1, "Toast", null, price(3.0)))

        assertEquals(0.3, price.perPortion!!, 1e-9)
        assertEquals(listOf("Toast"), price.missing.map { it.name })
    }

    @Test
    fun outdated_listsPricesOlderThanTwoYearsOfAtLeastFiveGrams() {
        val old = today.minusYears(2).minusDays(1)
        val price = calculate(
            line(1, "Lachs", 400.0, price(17.0, old)),
            line(2, "Safran", 0.1, price(5000.0, old)),
            line(3, "Zucchini", 500.0, price(3.0, today.minusYears(2))),
        )

        assertEquals(listOf(PriceGap(1, "Lachs", old)), price.outdated)
        assertTrue(price.missing.isEmpty())
        // The old prices still count.
        assertEquals(400 * 0.017 + 0.1 * 5.0 + 500 * 0.003, price.perPortion!!, 1e-9)
    }

    @Test
    fun roundedPerPortion_isToTenCents() {
        assertEquals(2.4, calculate(line(1, "Lachs", 141.0, price(17.0))).roundedPerPortion!!, 1e-9)
        assertEquals(2.5, calculate(line(1, "Lachs", 147.0, price(17.0))).roundedPerPortion!!, 1e-9)
        assertEquals(0.0, calculate(line(1, "Salz", 10.0, price(0.4))).roundedPerPortion!!, 1e-9)
    }

    @Test
    fun priceLines_takeGramsAndPriceFromTheIngredient() {
        val toast = Ingredient(
            id = 7,
            name = "Toast",
            fdcId = null,
            usdaDescription = null,
            nutrition = Nutrition(),
            storeSection = StoreSection.DRY_GOODS,
            plantPoints = PlantPoints.ZERO,
            price = price(3.0),
        )
        val lines = listOf(
            RecipeLine(recipeId = 1, position = 0, amount = 2.0, unit = LineUnit.Of(PieceUnit.SLICE), ingredientId = 7, unitGrams = 37.5),
            RecipeLine(recipeId = 1, position = 1, amount = 1.0, unit = LineUnit.Of(PieceUnit.PACKET), ingredientId = 7),
            RecipeLine(recipeId = 1, position = 2, amount = 1.0, unit = LineUnit.Grams, ingredientId = 99),
        )

        assertEquals(
            listOf(PriceLine(7, "Toast", 75.0, price(3.0)), PriceLine(7, "Toast", null, price(3.0))),
            priceLines(lines, mapOf(7L to toast)),
        )
    }
}
