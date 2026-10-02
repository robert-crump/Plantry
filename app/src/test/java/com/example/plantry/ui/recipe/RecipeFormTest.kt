package com.example.plantry.ui.recipe

import com.example.plantry.data.BuyUnit
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.Recipe
import com.example.plantry.data.StoreSection
import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.claude.NewFood
import com.example.plantry.data.usda.UsdaFood
import com.example.plantry.data.RecipeDraft
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.RecipeIngredientDraft
import com.example.plantry.data.claude.ScannedLine
import com.example.plantry.data.claude.ScannedRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeFormTest {

    private val valid = RecipeForm(title = "Chili", bookServings = "4", cookingTime = "45")

    @Test
    fun newForm_ourServingsFollowsBookServings() {
        val form = RecipeForm().withBookServings("4")

        assertEquals("4", form.ourServings)
    }

    @Test
    fun editedOurServings_noLongerFollowsBookServings() {
        val form = RecipeForm().withBookServings("4").withOurServings("2").withBookServings("6")

        assertEquals("2", form.ourServings)
    }

    @Test
    fun formFromExistingRecipe_keepsOurServingsWhenBookServingsChange() {
        val recipe = Recipe(1, "Chili", "Buch", null, bookServings = 4, ourServings = 2, cookingTimeMinutes = 45)

        val form = RecipeForm.from(recipe, emptyList()).withBookServings("6")

        assertEquals("2", form.ourServings)
    }

    @Test
    fun toDraft_parsesValidInput() {
        val form = valid.copy(source = "Buch", page = " 12 ").withOurServings("")

        assertEquals(
            RecipeDraft(
                "Chili", "Buch", page = 12, bookServings = 4, ourServings = null, cookingTimeMinutes = 45,
                lines = emptyList(),
            ),
            form.toDraft(),
        )
    }

    @Test
    fun toDraft_rejectsInvalidInput() {
        assertNull(valid.copy(title = " ").toDraft())
        assertNull(valid.copy(bookServings = "0").toDraft())
        assertNull(valid.copy(cookingTime = "").toDraft())
        assertNull(valid.copy(page = "abc").toDraft())
        assertNull(valid.withOurServings("-1").toDraft())
    }

    @Test
    fun errors_flagOnlyInvalidFields() {
        val errors = RecipeForm(title = "Chili", bookServings = "x").errors()

        assertTrue(errors.bookServings)
        assertTrue(errors.cookingTime)
        assertFalse(errors.title)
        assertFalse(errors.page)
        assertFalse(errors.ourServings)
    }

    private val tofu = RecipeIngredientDraft("200 g Tofu", 200.0, ingredientId = 7)
    private val rice = RecipeIngredientDraft("1 Tasse Reis", 185.0, ingredientId = 8)

    @Test
    fun withLine_appendsOrReplacesAndRemoveLineDeletes() {
        val form = valid.withLine(null, tofu).withLine(null, rice).withLine(0, tofu.copy(grams = 300.0))

        assertEquals(listOf(tofu.copy(grams = 300.0), rice), form.completeLines())
        assertEquals(listOf(rice), form.removeLine(0).completeLines())
        assertEquals(listOf(tofu.copy(grams = 300.0), rice), form.toDraft()!!.lines)
    }

    @Test
    fun withScan_takesMetadataAndLinesButKeepsSource() {
        val scan = ScannedRecipe(
            title = "Linsen-Dal",
            servings = 4,
            cookingTimeMinutes = 40,
            page = 112,
            lines = listOf(
                ScannedLine("200 g rote Linsen", 200.0, ingredientId = 3, ingredientName = "Linsen", uncertain = false),
                ScannedLine("1 Bund Koriander", 30.0, ingredientId = null, ingredientName = "Koriander", uncertain = true),
            ),
        )

        val form = RecipeForm(source = "Ottolenghi").withScan(scan)

        assertEquals("Linsen-Dal", form.title)
        assertEquals("Ottolenghi", form.source)
        assertEquals("112", form.page)
        assertEquals("4", form.bookServings)
        assertEquals("4", form.ourServings)
        assertEquals("40", form.cookingTime)
        assertEquals(RecipeFormLine("1 Bund Koriander", 30.0, null, "Koriander", uncertain = true), form.lines[1])
    }

    @Test
    fun unmatchedLine_blocksSaveAndIsLeftOutOfNutrition() {
        val form = valid.withLine(null, tofu).withLine(null, RecipeFormLine("1 Bund Koriander", 30.0, ingredientId = null))

        assertTrue(form.errors().lines)
        assertNull(form.toDraft())
        assertEquals(listOf(tofu), form.completeLines())
    }

    @Test
    fun lineWithoutWeight_blocksSave() {
        val form = valid.withLine(null, RecipeFormLine("Salz", 0.0, ingredientId = 7))

        assertTrue(form.errors().lines)
        assertNull(form.toDraft())
    }

    @Test
    fun resolvedLines_allowSave() {
        val form = valid.withLine(null, RecipeFormLine("1 Bund Koriander", 30.0, ingredientId = null, uncertain = true))
            .withLine(0, RecipeIngredientDraft("1 Bund Koriander", 25.0, ingredientId = 9))

        assertFalse(form.lines[0].uncertain)
        assertEquals(listOf(RecipeIngredientDraft("1 Bund Koriander", 25.0, 9)), form.toDraft()!!.lines)
    }

    @Test
    fun from_keepsLinesInStoredOrder() {
        val recipe = Recipe(1, "Bowl", "Buch", null, bookServings = 2, ourServings = 2, cookingTimeMinutes = 20)
        val stored = listOf(
            RecipeIngredient(10, recipeId = 1, position = 0, originalText = "200 g Tofu", grams = 200.0, ingredientId = 7),
            RecipeIngredient(11, recipeId = 1, position = 1, originalText = "1 Tasse Reis", grams = 185.0, ingredientId = 8),
        )

        assertEquals(listOf(tofu, rice), RecipeForm.from(recipe, stored).completeLines())
    }

    private val smokedTofu = UsdaFood(172476, "Tofu, smoked", Nutrition(160.0, 16.0, 3.0, 1.0, 9.0, 1.0), emptyList())

    private fun proposal(name: String, food: UsdaFood? = smokedTofu) = IngredientProposal(
        name, food, emptyList(), emptyList(), BuyUnit.GRAMS, null, StoreSection.OTHER, false, PlantPoints.ONE, null,
    )

    private val scanned = valid.copy(
        lines = listOf(
            RecipeFormLine("200 g Räuchertofu", 200.0, null, "Räuchertofu", searchTerms = listOf("tofu smoked")),
            RecipeFormLine("200 g Tofu", 200.0, ingredientId = 7, ingredientName = "Tofu"),
            RecipeFormLine("100 g räuchertofu", 100.0, null, " räuchertofu ", searchTerms = listOf("tofu")),
            RecipeFormLine("1 Zwiebel", 110.0, null, "Zwiebel"),
        ),
    )

    private fun ids(): () -> Long {
        var last = 0L
        return { --last }
    }

    @Test
    fun unmatchedFoods_onePerDistinctNameWithTheLinesTerms() {
        val foods = scanned.unmatchedFoods(ids())

        assertEquals(
            listOf(
                NewFood(-1, "Räuchertofu", "200 g Räuchertofu", listOf("tofu smoked", "tofu")),
                NewFood(-2, "Zwiebel", "1 Zwiebel", emptyList()),
            ),
            foods,
        )
    }

    @Test
    fun withProposals_assignsUnmatchedLinesAndKeepsResolvedOnes() {
        val foods = scanned.unmatchedFoods(ids())
        // Meanwhile the user picked an existing ingredient for the onion.
        val edited = scanned.withLine(3, RecipeIngredientDraft("1 Zwiebel", 110.0, ingredientId = 9))

        val form = edited.withProposals(foods, mapOf(-1L to proposal("Räuchertofu"), -2L to proposal("Zwiebel")))

        assertEquals(listOf(-1L, 7L, -1L, 9L), form.lines.map { it.ingredientId })
        assertEquals(setOf(-1L, -2L), form.newIngredients.keys)
        // Only the used proposal is created on save.
        assertEquals(setOf(-1L), form.newIngredientsToCreate().keys)
    }

    @Test
    fun unconfirmedNewIngredient_blocksSaveUntilConfirmed() {
        val foods = scanned.unmatchedFoods(ids())
        val form = scanned.withProposals(foods, mapOf(-1L to proposal("Räuchertofu"), -2L to proposal("Zwiebel")))

        assertTrue(form.errors().lines)
        assertFalse(form.isReady(-1))

        val confirmed = form.confirmNewIngredient(-1).confirmNewIngredient(-2)

        assertTrue(confirmed.isReady(-1))
        assertEquals(listOf(-1L, 7L, -1L, -2L), confirmed.toDraft()!!.lines.map { it.ingredientId })
    }

    @Test
    fun proposalWithoutUsdaEntry_cannotBeConfirmed_butPickingOneConfirmsIt() {
        val form = valid.withNewIngredient(-1, proposal("Räuchertofu", food = null))
            .withLine(null, RecipeFormLine("200 g Räuchertofu", 200.0, ingredientId = -1))

        assertFalse(form.confirmNewIngredient(-1).isReady(-1))
        assertTrue(form.previewIngredients().isEmpty())

        val picked = form.withNewIngredientFood(-1, smokedTofu)

        assertTrue(picked.isReady(-1))
        assertEquals(smokedTofu, picked.newIngredients.getValue(-1).proposal.food)
        assertEquals(listOf(-1L), picked.previewIngredients().map { it.id })
    }

    @Test
    fun withIngredientIds_replacesTemporaryIds() {
        val draft = RecipeDraft("Bowl", "", null, 2, null, 20, listOf(tofu, tofu.copy(ingredientId = -1)))

        assertEquals(listOf(7L, 42L), draft.withIngredientIds(mapOf(-1L to 42L)).lines.map { it.ingredientId })
    }

    @Test
    fun effectiveServings_prefersOurServingsThenBookServings() {
        assertEquals(4, valid.effectiveServings())
        assertEquals(2, valid.withOurServings("2").effectiveServings())
        assertEquals(4, valid.withOurServings("x").effectiveServings())
        assertNull(RecipeForm().effectiveServings())
    }
}
