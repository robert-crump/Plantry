package com.example.plantry.ui.recipe

import com.example.plantry.data.BookPage
import com.example.plantry.data.Dish
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.Recipe
import com.example.plantry.data.StoreSection
import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.claude.NewFood
import com.example.plantry.data.claude.NutritionSource
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
    fun dish_newFormIsMainDish_andAnExistingSnackStaysOne() {
        val snack = Recipe(1, "Porridge", "", null, bookServings = 1, ourServings = 1, cookingTimeMinutes = 10, dish = Dish.SNACK)

        assertEquals(Dish.MAIN, valid.withLine(null, tofu).toDraft()?.dish)
        assertEquals(Dish.SNACK, RecipeForm.from(snack, emptyList()).withLine(null, tofu).toDraft()?.dish)
    }

    @Test
    fun toDraft_parsesValidInput() {
        val form = valid.copy(source = "Buch", page = " 12 ").withOurServings("").withLine(null, tofu)

        assertEquals(
            RecipeDraft(
                "Chili", "Buch", page = 12, bookServings = 4, ourServings = null, cookingTimeMinutes = 45,
                lines = listOf(tofu),
            ),
            form.toDraft(),
        )
    }

    @Test
    fun toDraft_rejectsInvalidInput() {
        assertNull(valid.copy(title = " ").toDraft())
        assertNull(valid.copy(bookServings = "0").toDraft())
        assertNull(valid.copy(cookingTime = "abc").toDraft())
        assertNull(valid.copy(page = "abc").toDraft())
        assertNull(valid.withOurServings("-1").toDraft())
    }

    @Test
    fun toDraft_needsAtLeastOneLine() {
        assertTrue(valid.errors().noLines)
        assertNull(valid.toDraft())
        assertFalse(valid.withLine(null, tofu).errors().noLines)
    }

    @Test
    fun cookingTime_isOptional() {
        val draft = valid.copy(cookingTime = " ").withLine(null, tofu).toDraft()

        assertEquals(null, draft?.cookingTimeMinutes)
        assertEquals("Chili", draft?.title)
    }

    @Test
    fun from_recipeWithoutCookingTime_leavesTheFieldEmpty() {
        val recipe = Recipe(1, "Dal", "Handschriftlich", null, bookServings = 4, ourServings = 4, cookingTimeMinutes = null)

        assertEquals("", RecipeForm.from(recipe, emptyList()).cookingTime)
    }

    @Test
    fun errors_flagOnlyInvalidFields() {
        val errors = RecipeForm(title = "Chili", bookServings = "x").errors()

        assertTrue(errors.bookServings)
        assertFalse(errors.cookingTime)
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
    fun withScan_learnedAliasWinsOverClaudesMatchAndResolvesTheLine() {
        val scan = ScannedRecipe(
            title = "Hummus",
            servings = 2,
            cookingTimeMinutes = 10,
            page = null,
            lines = listOf(
                ScannedLine("1 Dose Kichererbsen", 240.0, ingredientId = 3, ingredientName = "Kichererbsen, abgetropft", uncertain = false),
                ScannedLine("2 EL Tahin", 30.0, ingredientId = null, ingredientName = "Tahin", uncertain = false, searchTerms = listOf("tahini")),
                ScannedLine("Salz", 2.0, ingredientId = null, ingredientName = "Salz", uncertain = false, searchTerms = listOf("salt")),
            ),
        )

        val form = RecipeForm().withScan(scan, aliases = mapOf("kichererbsen abgetropft" to 7L, "tahin" to 8L))

        assertEquals(listOf(7L, 8L, null), form.lines.map { it.ingredientId })
        assertEquals(emptyList<String>(), form.lines[1].searchTerms)
        assertEquals(listOf("salt"), form.lines[2].searchTerms)
        assertNull(form.problem(form.lines[0]))
        assertNull(form.problem(form.lines[1]))
        // Aliased lines need no new ingredient.
        assertEquals(listOf("Salz"), form.unmatchedFoods(ids()).map { it.name })
    }

    @Test
    fun aliasesToLearn_takesEditorConfirmedLinesAndNewIngredients() {
        val form = valid.copy(
            lines = listOf(
                // Claude's match, not confirmed by the user.
                RecipeFormLine("200 g Tofu", 200.0, ingredientId = 7, ingredientName = "Tofu"),
                RecipeFormLine("1 Dose Kichererbsen", 240.0, ingredientId = 3, ingredientName = "Kichererbsen, abgetropft", confirmed = true),
                // Confirmed, but typed nothing: an existing line whose grams were changed.
                RecipeFormLine("1 Tasse Reis", 185.0, ingredientId = 8, confirmed = true),
                RecipeFormLine("200 g Räuchertofu", 200.0, ingredientId = -1, ingredientName = "Räuchertofu"),
                RecipeFormLine("1 Zwiebel", 110.0, ingredientId = -2, ingredientName = "Zwiebel"),
            ),
        )

        assertEquals(
            listOf("Kichererbsen, abgetropft" to 3L, "Räuchertofu" to 21L),
            form.aliasesToLearn(createdIds = mapOf(-1L to 21L)),
        )
    }

    @Test
    fun withScan_takesSourceAndPageFromBook() {
        val scan = ScannedRecipe("Linsen-Dal", servings = 4, cookingTimeMinutes = 40, page = null, lines = emptyList())

        val form = RecipeForm().withScan(scan, BookPage("Plenty", 113))

        assertEquals("Plenty", form.source)
        assertEquals("113", form.page)
    }

    @Test
    fun fieldErrors_ignoreTheLines() {
        val incompleteLine = RecipeFormLine("1 Bund Koriander", 30.0, ingredientId = null)

        assertFalse(valid.withLine(null, incompleteLine).errors().fields)
        assertTrue(valid.copy(cookingTime = "abc").errors().fields)
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

    private val smokedTofu = UsdaFood(172476, "Tofu, smoked", Nutrition(160.0, 16.0, 3.0, 1.0, 9.0, 1.0))

    private fun proposal(name: String, food: UsdaFood? = smokedTofu) = IngredientProposal(
        name, food?.let(NutritionSource::Usda), emptyList(), StoreSection.OTHER, PlantPoints.ONE,
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
    fun proposalWithoutUsdaEntry_labelValuesConfirmIt() {
        val label = Nutrition(180.0, 18.0, 2.0, 1.0, 11.0, 0.0)
        val form = valid.withNewIngredient(-1, proposal("Räuchertofu", food = null))
            .withLine(null, RecipeFormLine("200 g Räuchertofu", 200.0, ingredientId = -1))

        val labelled = form.withNewIngredientLabel(-1, label)

        assertTrue(labelled.isReady(-1))
        assertFalse(labelled.errors().lines)
        val preview = labelled.previewIngredients().single()
        assertEquals(label, preview.nutrition)
        assertNull(preview.fdcId)
        assertNull(preview.usdaDescription)
    }

    @Test
    fun labelValues_replaceAProposedUsdaEntry() {
        val label = Nutrition(180.0, 18.0, 2.0, 1.0, 11.0, 0.0)
        val form = valid.withNewIngredient(-1, proposal("Räuchertofu")).withNewIngredientLabel(-1, label)

        val proposal = form.newIngredients.getValue(-1).proposal
        assertEquals(NutritionSource.Label(label), proposal.source)
        assertNull(proposal.food)
    }

    private val checklist = valid.withNewIngredient(-1, proposal("Räuchertofu")).copy(
        lines = listOf(
            RecipeFormLine("200 g Tofu", 200.0, ingredientId = 7),
            RecipeFormLine("1 Bund Koriander", 30.0, ingredientId = null, ingredientName = "Koriander"),
            RecipeFormLine("1 Tasse Reis", 185.0, ingredientId = 8),
            RecipeFormLine("200 g Räuchertofu", 200.0, ingredientId = -1),
            RecipeFormLine("Salz", 0.0, ingredientId = 9),
            RecipeFormLine("1 Zwiebel", 110.0, ingredientId = 10, uncertain = true),
        ),
    )

    @Test
    fun problem_detectsEachKindOfProblemLine() {
        assertEquals(
            listOf(null, LineProblem.NO_INGREDIENT, null, LineProblem.NEW_INGREDIENT, LineProblem.NO_GRAMS, LineProblem.UNCERTAIN),
            checklist.lines.map(checklist::problem),
        )
    }

    @Test
    fun confirmedNewIngredient_isNoLongerAProblem() {
        val form = checklist.confirmNewIngredient(-1)

        assertNull(form.problem(form.lines[3]))
    }

    @Test
    fun uncertainLine_blocksSaveUntilApplied() {
        val form = valid.withLine(null, RecipeFormLine("1 Zwiebel", 110.0, ingredientId = 10, uncertain = true))

        assertTrue(form.errors().lines)
        assertNull(form.toDraft())
        assertFalse(form.withLine(0, RecipeIngredientDraft("1 Zwiebel", 110.0, 10)).errors().lines)
    }

    @Test
    fun checklistOrder_putsProblemLinesFirstInRecipeOrder() {
        assertEquals(listOf(1, 3, 4, 5, 0, 2), checklist.checklistOrder())
    }

    @Test
    fun nextProblem_isTheFollowingProblemLineWrappingAround() {
        assertEquals(3, checklist.nextProblem(1))
        assertEquals(3, checklist.nextProblem(2))
        assertEquals(1, checklist.nextProblem(5))
    }

    @Test
    fun nextProblem_isNullWhenNoOtherProblemIsLeft() {
        val form = valid.copy(
            lines = listOf(
                RecipeFormLine("200 g Tofu", 200.0, ingredientId = 7),
                RecipeFormLine("Salz", 0.0, ingredientId = 9),
            ),
        )

        assertNull(form.nextProblem(1))
        assertEquals(listOf(1, 0), form.checklistOrder())
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

    @Test
    fun isEmpty_untilSomethingIsEntered() {
        assertTrue(RecipeForm().isEmpty)
        assertFalse(RecipeForm().copy(title = "C").isEmpty)
        assertFalse(RecipeForm().withBookServings("4").isEmpty)
        assertFalse(RecipeForm().withLine(null, RecipeIngredientDraft("Salz", 1.0, 1)).isEmpty)
    }
}
