package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingListTest {

    private val riceDry = ingredient(1, "Reis").copy(storeSection = StoreSection.DRY_GOODS)
    private val riceCooked = ingredient(2, "Reis, gekocht").copy(buyAsIngredientId = 1, buyAsYieldFactor = 0.4)
    private val sweetPotato = ingredient(3, "Süßkartoffeln").copy(
        buyUnit = BuyUnit.PIECES,
        unitWeights = listOf(UnitWeight("medium", 130.0)),
        storeSection = StoreSection.PRODUCE,
    )
    private val feta = ingredient(4, "Feta").copy(
        buyUnit = BuyUnit.PACK,
        packSizeGrams = 200.0,
        storeSection = StoreSection.DAIRY_CHILLED,
    )
    private val oil = ingredient(5, "Olivenöl").copy(staple = true, storeSection = StoreSection.DRY_GOODS)
    private val cumin = ingredient(6, "Kreuzkümmel").copy(staple = true)
    private val apple = ingredient(7, "apfel").copy(storeSection = StoreSection.PRODUCE)

    private val ingredients = listOf(riceDry, riceCooked, sweetPotato, feta, oil, cumin, apple).associateBy { it.id }

    private fun recipe(id: Long) = Recipe(
        id = id, title = "Rezept $id", source = "", page = null,
        bookServings = 2, ourServings = 2, cookingTimeMinutes = 20,
    )

    private fun line(recipeId: Long, ingredient: Ingredient, grams: Double) =
        RecipeIngredient(recipeId = recipeId, position = 0, originalText = "", grams = grams, ingredientId = ingredient.id)

    private fun list(recipes: List<Recipe>, lines: List<RecipeIngredient>, ticked: Set<Long> = emptySet()) =
        ShoppingList.of(recipes, lines, ingredients, ticked)

    private fun ShoppingList.item(name: String) = sections.flatMap { it.items }.single { it.name == name }

    @Test
    fun aggregates_sameIngredientAcrossRecipes() {
        val result = list(
            listOf(recipe(1), recipe(2)),
            listOf(line(1, sweetPotato, 200.0), line(2, sweetPotato, 120.0)),
        )

        val item = result.item("Süßkartoffeln")
        assertEquals(320.0, item.neededGrams, 1e-9)
        assertEquals(BuyQuantity.Pieces(3), item.quantity)
    }

    @Test
    fun buyAs_convertsWithYieldFactorBeforeAdding() {
        val result = list(
            listOf(recipe(1), recipe(2)),
            listOf(line(1, riceCooked, 300.0), line(2, riceDry, 110.0)),
        )

        val rice = result.sections.single().items.single()
        assertEquals("Reis", rice.name)
        assertEquals(230.0, rice.neededGrams, 1e-9)
        assertEquals(BuyQuantity.Grams(230), rice.quantity)
    }

    @Test
    fun ignoresLinesOfRecipesNotOnTheMenu() {
        val result = list(listOf(recipe(1)), listOf(line(1, feta, 150.0), line(2, feta, 150.0)))

        assertEquals(150.0, result.item("Feta").neededGrams, 1e-9)
    }

    @Test
    fun groupsByStoreSection_inSectionOrder_itemsByName() {
        val result = list(
            listOf(recipe(1)),
            listOf(line(1, feta, 150.0), line(1, sweetPotato, 300.0), line(1, riceDry, 100.0), line(1, apple, 150.0)),
        )

        assertEquals(
            listOf(StoreSection.PRODUCE, StoreSection.DAIRY_CHILLED, StoreSection.DRY_GOODS),
            result.sections.map { it.storeSection },
        )
        assertEquals(listOf("apfel", "Süßkartoffeln"), result.sections.first().items.map { it.name })
    }

    @Test
    fun staples_listedByNameOnly_notInSections() {
        val result = list(
            listOf(recipe(1), recipe(2)),
            listOf(line(1, oil, 20.0), line(2, oil, 10.0), line(1, cumin, 2.0), line(1, riceDry, 100.0)),
        )

        assertEquals(listOf("Kreuzkümmel", "Olivenöl"), result.staples)
        assertEquals(listOf("Reis"), result.sections.flatMap { it.items }.map { it.name })
    }

    @Test
    fun buyAs_targetDecidesStaple() {
        val oilSpray = ingredient(8, "Ölspray").copy(buyAsIngredientId = oil.id)
        val result = ShoppingList.of(listOf(recipe(1)), listOf(line(1, oilSpray, 5.0)), ingredients + (8L to oilSpray), emptySet())

        assertEquals(listOf("Olivenöl"), result.staples)
        assertEquals(emptyList<ShoppingSection>(), result.sections)
    }

    @Test
    fun ticked_marksItemsByBuyAsTarget() {
        val result = list(listOf(recipe(1)), listOf(line(1, riceCooked, 100.0), line(1, feta, 100.0)), ticked = setOf(riceDry.id))

        assertEquals(true, result.item("Reis").ticked)
        assertEquals(false, result.item("Feta").ticked)
    }

    @Test
    fun emptyMenu_emptyList() {
        assertEquals(ShoppingList(emptyList(), emptyList()), list(emptyList(), listOf(line(1, feta, 100.0))))
    }

    @Test
    fun skipsLinesWithUnknownIngredient() {
        val unknown = RecipeIngredient(recipeId = 1, position = 0, originalText = "", grams = 50.0, ingredientId = 99)

        assertEquals(ShoppingList(emptyList(), emptyList()), list(listOf(recipe(1)), listOf(unknown)))
    }
}

class BuyQuantityTest {

    private val pieces = ingredient(1, "Zwiebel").copy(buyUnit = BuyUnit.PIECES, unitWeights = listOf(UnitWeight("mittel", 110.0)))
    private val pack = ingredient(2, "Feta").copy(buyUnit = BuyUnit.PACK, packSizeGrams = 200.0)
    private val grams = ingredient(3, "Reis")

    @Test
    fun pieces_roundUp() {
        assertEquals(BuyQuantity.Pieces(1), BuyQuantity.of(pieces, 50.0))
        assertEquals(BuyQuantity.Pieces(2), BuyQuantity.of(pieces, 111.0))
    }

    @Test
    fun exactMultiple_notRoundedUpByFloatingPointNoise() {
        assertEquals(BuyQuantity.Pieces(3), BuyQuantity.of(pieces, 0.1 * 3 * 1100.0))
        assertEquals(BuyQuantity.Packs(2, 200.0), BuyQuantity.of(pack, 400.0))
    }

    @Test
    fun packs_roundUp() {
        assertEquals(BuyQuantity.Packs(1, 200.0), BuyQuantity.of(pack, 150.0))
        assertEquals(BuyQuantity.Packs(2, 200.0), BuyQuantity.of(pack, 201.0))
    }

    @Test
    fun grams_roundUpToWholeGrams() {
        assertEquals(BuyQuantity.Grams(231), BuyQuantity.of(grams, 230.2))
    }

    @Test
    fun missingPieceWeightOrPackSize_fallsBackToGrams() {
        assertEquals(BuyQuantity.Grams(120), BuyQuantity.of(pieces.copy(unitWeights = emptyList()), 120.0))
        assertEquals(BuyQuantity.Grams(150), BuyQuantity.of(pack.copy(packSizeGrams = null), 150.0))
    }
}

class ResolveBuyAsTest {

    @Test
    fun multipliesYieldFactorsAlongTheChain_missingFactorCountsAsOne() {
        val a = ingredient(1, "A")
        val b = ingredient(2, "B").copy(buyAsIngredientId = 1)
        val c = ingredient(3, "C").copy(buyAsIngredientId = 2, buyAsYieldFactor = 0.5)
        val all = listOf(a, b, c).associateBy { it.id }

        assertEquals(BuyAsTarget(a, 0.5), resolveBuyAs(c, all))
        assertEquals(BuyAsTarget(a, 1.0), resolveBuyAs(a, all))
    }

    @Test
    fun cycle_stops() {
        val a = ingredient(1, "A").copy(buyAsIngredientId = 2, buyAsYieldFactor = 2.0)
        val b = ingredient(2, "B").copy(buyAsIngredientId = 1, buyAsYieldFactor = 3.0)

        assertEquals(BuyAsTarget(b, 2.0), resolveBuyAs(a, listOf(a, b).associateBy { it.id }))
    }
}
