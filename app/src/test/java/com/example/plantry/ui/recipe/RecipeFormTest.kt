package com.example.plantry.ui.recipe

import com.example.plantry.data.BookPage
import com.example.plantry.data.Dish
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.LineUnit
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.Recipe
import com.example.plantry.data.StoreSection
import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.claude.NewFood
import com.example.plantry.data.claude.NutritionSource
import com.example.plantry.data.usda.UsdaFood
import com.example.plantry.data.RecipeDraft
import com.example.plantry.data.RecipeLine
import com.example.plantry.data.RecipeIngredientDraft
import com.example.plantry.data.claude.ScannedLine
import com.example.plantry.data.claude.ScannedRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeFormTest {

    private val valid = RecipeForm(title = "Chili", servings = "4", totalTime = "45")

    @Test
    fun formFromExistingRecipe_takesItsServings() {
        val recipe = Recipe(1, "Chili", "Buch", null, servings = 2, totalTimeMinutes = 45)

        assertEquals("2", RecipeForm.from(recipe, emptyList()).servings)
    }

    @Test
    fun withDish_snackWithoutServingsStartsAtOne() {
        val snack = RecipeForm().withDish(Dish.SNACK)

        assertEquals(Dish.SNACK, snack.dish)
        assertEquals("1", snack.servings)
    }

    @Test
    fun withDish_breakfastKeepsServingsEmpty() {
        assertEquals("", RecipeForm().withDish(Dish.BREAKFAST).servings)
    }

    @Test
    fun withDish_keepsServingsAlreadyEntered() {
        assertEquals("20", RecipeForm(servings = "20").withDish(Dish.SNACK).servings)
        assertEquals("4", valid.withDish(Dish.SNACK).withDish(Dish.MAIN).servings)
    }

    @Test
    fun snack_servingsStayEditable() {
        val form = RecipeForm(title = "Reiswaffeln").withDish(Dish.SNACK).copy(servings = "20").withLine(null, tofu)

        assertEquals(20, form.toDraft()?.servings)
    }

    @Test
    fun everyDish_keepsSourcePageAndTimesOnSave() {
        val main = valid.copy(source = "Buch", page = "12", activeTime = "10").withLine(null, tofu)

        for (dish in Dish.entries) {
            val draft = main.withDish(dish).toDraft()!!

            assertEquals("Buch", draft.source)
            assertEquals(12, draft.page)
            assertEquals(10, draft.activeTimeMinutes)
            assertEquals(45, draft.totalTimeMinutes)
            assertEquals(4, draft.servings)
            assertEquals(dish, draft.dish)
        }
    }

    @Test
    fun everyDish_validatesPageAndTimes() {
        for (dish in Dish.entries) {
            val errors = valid.copy(page = "abc", activeTime = "0", totalTime = "x").withLine(null, tofu).withDish(dish).errors()

            assertTrue(errors.page)
            assertTrue(errors.activeTime)
            assertTrue(errors.totalTime)
        }
    }

    @Test
    fun everyDish_needsOnlyTitleServingsAndALine() {
        for (dish in Dish.entries) {
            val form = RecipeForm(title = "Porridge", servings = "1").withDish(dish)

            assertTrue(form.errors().noLines)
            assertEquals(dish, form.withLine(null, tofu).toDraft()?.dish)
        }
    }

    @Test
    fun snack_stillNeedsServings() {
        assertTrue(RecipeForm(title = "Riegel").withDish(Dish.SNACK).copy(servings = "").errors().servings)
    }

    @Test
    fun dish_newFormIsMainDish_andAnExistingSnackOrBreakfastStaysOne() {
        val snack = Recipe(1, "Skyr", "", null, servings = 1, totalTimeMinutes = 10, dish = Dish.SNACK)
        val breakfast = Recipe(2, "Porridge", "Buch", 12, servings = 1, totalTimeMinutes = 10, dish = Dish.BREAKFAST)

        assertEquals(Dish.MAIN, valid.withLine(null, tofu).toDraft()?.dish)
        assertEquals(Dish.SNACK, RecipeForm.from(snack, emptyList()).withLine(null, tofu).toDraft()?.dish)
        val draft = RecipeForm.from(breakfast, emptyList()).withLine(null, tofu).toDraft()!!
        assertEquals(Dish.BREAKFAST, draft.dish)
        assertEquals("Buch", draft.source)
        assertEquals(12, draft.page)
    }

    @Test
    fun toDraft_parsesValidInput() {
        val form = valid.copy(source = "Buch", page = " 12 ").withLine(null, tofu)

        assertEquals(
            RecipeDraft(
                "Chili", "Buch", page = 12, servings = 4, totalTimeMinutes = 45,
                lines = listOf(tofu),
            ),
            form.toDraft(),
        )
    }

    @Test
    fun toDraft_rejectsInvalidInput() {
        assertNull(valid.copy(title = " ").toDraft())
        assertNull(valid.copy(servings = "0").toDraft())
        assertNull(valid.copy(servings = "").toDraft())
        assertNull(valid.copy(totalTime = "abc").toDraft())
        assertNull(valid.copy(page = "abc").toDraft())
    }

    @Test
    fun toDraft_needsAtLeastOneLine() {
        assertTrue(valid.errors().noLines)
        assertNull(valid.toDraft())
        assertFalse(valid.withLine(null, tofu).errors().noLines)
    }

    @Test
    fun totalTime_isOptional() {
        val draft = valid.copy(totalTime = " ").withLine(null, tofu).toDraft()

        assertEquals(null, draft?.totalTimeMinutes)
        assertEquals("Chili", draft?.title)
    }

    @Test
    fun activeTime_isOptional_andRoundTrips() {
        val draft = valid.copy(activeTime = "20", totalTime = "65").withLine(null, tofu).toDraft()
        val recipe = Recipe(1, "Lasagne", "", null, servings = 4, totalTimeMinutes = 65, activeTimeMinutes = 20)

        assertEquals(20, draft?.activeTimeMinutes)
        assertEquals(null, valid.withLine(null, tofu).toDraft()?.activeTimeMinutes)
        assertEquals("20", RecipeForm.from(recipe, emptyList()).activeTime)
    }

    @Test
    fun activeTime_mustBeANumberNoLongerThanTheTotal() {
        assertTrue(valid.copy(activeTime = "abc").errors().activeTime)
        assertTrue(valid.copy(activeTime = "50", totalTime = "45").errors().activeTime)
        assertFalse(valid.copy(activeTime = "45", totalTime = "45").errors().activeTime)
        assertFalse(valid.copy(activeTime = "50", totalTime = "").errors().activeTime)
        assertFalse(valid.copy(activeTime = "50", totalTime = "45").errors().totalTime)
    }

    @Test
    fun from_recipeWithoutCookingTime_leavesTheFieldEmpty() {
        val recipe = Recipe(1, "Dal", "Handschriftlich", null, servings = 4, totalTimeMinutes = null)

        assertEquals("", RecipeForm.from(recipe, emptyList()).totalTime)
    }

    @Test
    fun errors_flagOnlyInvalidFields() {
        val errors = RecipeForm(title = "Chili", servings = "x").errors()

        assertTrue(errors.servings)
        assertFalse(errors.totalTime)
        assertFalse(errors.title)
        assertFalse(errors.page)
    }

    private val tofu = RecipeIngredientDraft(200.0, LineUnit.Grams, 7, "200 g Tofu")
    private val rice = RecipeIngredientDraft(185.0, LineUnit.Grams, 8, "1 Tasse Reis")

    @Test
    fun withLine_appendsOrReplacesAndRemoveLineDeletes() {
        val form = valid.withLine(null, tofu).withLine(null, rice).withLine(0, tofu.copy(amount = 300.0))

        assertEquals(listOf(tofu.copy(amount = 300.0), rice), form.completeLines())
        assertEquals(listOf(rice), form.removeLine(0).completeLines())
        assertEquals(listOf(tofu.copy(amount = 300.0), rice), form.toDraft()!!.lines)
    }

    @Test
    fun withScan_takesMetadataAndLinesButKeepsSource() {
        val scan = ScannedRecipe(
            title = "Linsen-Dal",
            servings = 4,
            totalTimeMinutes = 40,
            page = 112,
            activeTimeMinutes = 15,
            lines = listOf(
                ScannedLine("200 g rote Linsen", 200.0, LineUnit.Grams, ingredientId = 3, ingredientName = "Linsen", uncertain = false),
                ScannedLine("1 Bund Koriander", 1.0, LineUnit.Of(PieceUnit.BUNCH), ingredientId = null, ingredientName = "Koriander", uncertain = true),
            ),
        )

        val form = RecipeForm(source = "Ottolenghi").withScan(scan)

        assertEquals("Linsen-Dal", form.title)
        assertEquals("Ottolenghi", form.source)
        assertEquals("112", form.page)
        assertEquals("4", form.servings)
        assertEquals("40", form.totalTime)
        assertEquals("15", form.activeTime)
        assertEquals(RecipeFormLine(1.0, LineUnit.Of(PieceUnit.BUNCH), null, ingredientName = "Koriander", uncertain = true), form.lines[1])
    }

    @Test
    fun withScan_learnedAliasWinsOverClaudesMatchAndResolvesTheLine() {
        val scan = ScannedRecipe(
            title = "Hummus",
            servings = 2,
            totalTimeMinutes = 10,
            page = null,
            lines = listOf(
                ScannedLine("1 Dose Kichererbsen", 1.0, LineUnit.Of(PieceUnit.CAN), ingredientId = 3, ingredientName = "Kichererbsen, abgetropft", uncertain = false),
                ScannedLine("2 EL Tahin", 2.0, LineUnit.Of(PieceUnit.TBSP), ingredientId = null, ingredientName = "Tahin", uncertain = false, searchTerms = listOf("tahini")),
                ScannedLine("Salz", 2.0, LineUnit.Grams, ingredientId = null, ingredientName = "Salz", uncertain = false, searchTerms = listOf("salt")),
            ),
        )

        val form = RecipeForm().withScan(
            scan,
            aliases = mapOf("kichererbsen abgetropft" to 7L, "tahin" to 8L),
            units = listOf(
                IngredientUnit.perUnit(PieceUnit.CAN, 240.0, ingredientId = 7)!!,
                IngredientUnit.perUnit(PieceUnit.TBSP, 15.0, ingredientId = 8)!!,
            ).groupBy { it.ingredientId },
        )

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
                RecipeFormLine(200.0, LineUnit.Grams, ingredientId = 7, wording = "200 g Tofu", ingredientName = "Tofu"),
                RecipeFormLine(240.0, LineUnit.Grams, ingredientId = 3, wording = "1 Dose Kichererbsen", ingredientName = "Kichererbsen, abgetropft", confirmed = true),
                // Confirmed, but typed nothing: an existing line whose grams were changed.
                RecipeFormLine(185.0, LineUnit.Grams, ingredientId = 8, wording = "1 Tasse Reis", confirmed = true),
                RecipeFormLine(200.0, LineUnit.Grams, ingredientId = -1, wording = "200 g Räuchertofu", ingredientName = "Räuchertofu"),
                RecipeFormLine(110.0, LineUnit.Grams, ingredientId = -2, wording = "1 Zwiebel", ingredientName = "Zwiebel"),
            ),
        )

        assertEquals(
            listOf("Kichererbsen, abgetropft" to 3L, "Räuchertofu" to 21L),
            form.aliasesToLearn(createdIds = mapOf(-1L to 21L)),
        )
    }

    @Test
    fun withScan_takesSourceAndPageFromBook() {
        val scan = ScannedRecipe("Linsen-Dal", servings = 4, totalTimeMinutes = 40, page = null, lines = emptyList())

        val form = RecipeForm().withScan(scan, BookPage("Plenty", 113))

        assertEquals("Plenty", form.source)
        assertEquals("113", form.page)
    }

    @Test
    fun fieldErrors_ignoreTheLines() {
        val incompleteLine = RecipeFormLine(30.0, LineUnit.Grams, ingredientId = null, wording = "1 Bund Koriander")

        assertFalse(valid.withLine(null, incompleteLine).errors().fields)
        assertTrue(valid.copy(totalTime = "abc").errors().fields)
    }

    @Test
    fun unmatchedLine_blocksSaveAndIsLeftOutOfNutrition() {
        val form = valid.withLine(null, tofu).withLine(null, RecipeFormLine(30.0, LineUnit.Grams, ingredientId = null, wording = "1 Bund Koriander"))

        assertTrue(form.errors().lines)
        assertNull(form.toDraft())
        assertEquals(listOf(tofu), form.completeLines())
    }

    @Test
    fun lineWithoutWeight_blocksSave() {
        val form = valid.withLine(null, RecipeFormLine(0.0, LineUnit.Grams, ingredientId = 7, wording = "Salz"))

        assertTrue(form.errors().lines)
        assertNull(form.toDraft())
    }

    @Test
    fun resolvedLines_allowSave() {
        val form = valid.withLine(null, RecipeFormLine(30.0, LineUnit.Grams, ingredientId = null, wording = "1 Bund Koriander", uncertain = true))
            .withLine(0, RecipeIngredientDraft(25.0, LineUnit.Grams, 9, "1 Bund Koriander"))

        assertFalse(form.lines[0].uncertain)
        assertEquals(listOf(RecipeIngredientDraft(25.0, LineUnit.Grams, 9, "1 Bund Koriander")), form.toDraft()!!.lines)
    }

    @Test
    fun from_keepsLinesInStoredOrder() {
        val recipe = Recipe(1, "Bowl", "Buch", null, servings = 2, totalTimeMinutes = 20)
        val stored = listOf(
            RecipeLine(10, recipeId = 1, position = 0, amount = 200.0, unit = LineUnit.Grams, ingredientId = 7, wording = "200 g Tofu"),
            RecipeLine(11, recipeId = 1, position = 1, amount = 185.0, unit = LineUnit.Grams, ingredientId = 8, wording = "1 Tasse Reis"),
        )

        assertEquals(listOf(tofu, rice), RecipeForm.from(recipe, stored).completeLines())
    }

    private val smokedTofu = UsdaFood(172476, "Tofu, smoked", Nutrition(160.0, 16.0, 3.0, 1.0, 9.0, 1.0))

    private fun proposal(name: String, food: UsdaFood? = smokedTofu) = IngredientProposal(
        name, food?.let(NutritionSource::Usda), emptyList(), StoreSection.OTHER, PlantPoints.ONE,
    )

    private val scanned = valid.copy(
        lines = listOf(
            RecipeFormLine(200.0, LineUnit.Grams, null, wording = "200 g Räuchertofu", ingredientName = "Räuchertofu", searchTerms = listOf("tofu smoked")),
            RecipeFormLine(200.0, LineUnit.Grams, ingredientId = 7, wording = "200 g Tofu", ingredientName = "Tofu"),
            RecipeFormLine(100.0, LineUnit.Grams, null, wording = "100 g räuchertofu", ingredientName = " räuchertofu ", searchTerms = listOf("tofu")),
            RecipeFormLine(110.0, LineUnit.Grams, null, wording = "1 Zwiebel", ingredientName = "Zwiebel"),
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
        val edited = scanned.withLine(3, RecipeIngredientDraft(110.0, LineUnit.Grams, 9, "1 Zwiebel"))

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
            .withLine(null, RecipeFormLine(200.0, LineUnit.Grams, ingredientId = -1, wording = "200 g Räuchertofu"))

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
            .withLine(null, RecipeFormLine(200.0, LineUnit.Grams, ingredientId = -1, wording = "200 g Räuchertofu"))

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
            RecipeFormLine(200.0, LineUnit.Grams, ingredientId = 7, wording = "200 g Tofu"),
            RecipeFormLine(30.0, LineUnit.Grams, ingredientId = null, wording = "1 Bund Koriander", ingredientName = "Koriander"),
            RecipeFormLine(185.0, LineUnit.Grams, ingredientId = 8, wording = "1 Tasse Reis"),
            RecipeFormLine(200.0, LineUnit.Grams, ingredientId = -1, wording = "200 g Räuchertofu"),
            RecipeFormLine(0.0, LineUnit.Grams, ingredientId = 9, wording = "Salz"),
            RecipeFormLine(110.0, LineUnit.Grams, ingredientId = 10, wording = "1 Zwiebel", uncertain = true),
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
        val form = valid.withLine(null, RecipeFormLine(110.0, LineUnit.Grams, ingredientId = 10, wording = "1 Zwiebel", uncertain = true))

        assertTrue(form.errors().lines)
        assertNull(form.toDraft())
        assertFalse(form.withLine(0, RecipeIngredientDraft(110.0, LineUnit.Grams, 10, "1 Zwiebel")).errors().lines)
    }

    /** Tofu chilled, Reis and Salz dry goods, Zwiebel produce; Räuchertofu's proposal says other. */
    private val sections = mapOf(
        7L to StoreSection.DAIRY_CHILLED,
        8L to StoreSection.DRY_GOODS,
        9L to StoreSection.DRY_GOODS,
        10L to StoreSection.PRODUCE,
    )

    @Test
    fun checklistGroups_putLinesWithoutIngredientFirstThenEachSectionInRecipeOrder() {
        assertEquals(
            listOf(
                ChecklistGroup(null, listOf(1)),
                ChecklistGroup(StoreSection.PRODUCE, listOf(5)),
                ChecklistGroup(StoreSection.DAIRY_CHILLED, listOf(0)),
                ChecklistGroup(StoreSection.DRY_GOODS, listOf(2, 4)),
                ChecklistGroup(StoreSection.OTHER, listOf(3)),
            ),
            checklist.checklistGroups(sections),
        )
        assertEquals(listOf(1, 5, 0, 2, 4, 3), checklist.checklistOrder(sections))
    }

    @Test
    fun checklistGroups_putIngredientsOfUnknownSectionUnderOther() {
        val groups = checklist.checklistGroups(emptyMap())

        assertEquals(listOf(null, StoreSection.OTHER), groups.map { it.section })
        assertEquals(listOf(0, 2, 3, 4, 5), groups.last().lines)
    }

    @Test
    fun nextProblem_isTheFollowingProblemLineInTheGivenOrderWrappingAround() {
        val order = checklist.checklistOrder(sections)

        assertEquals(5, checklist.nextProblem(1, order))
        assertEquals(4, checklist.nextProblem(5, order))
        assertEquals(4, checklist.nextProblem(0, order))
        assertEquals(1, checklist.nextProblem(3, order))
    }

    @Test
    fun nextProblem_isNullWhenNoOtherProblemIsLeft() {
        val form = valid.copy(
            lines = listOf(
                RecipeFormLine(200.0, LineUnit.Grams, ingredientId = 7, wording = "200 g Tofu"),
                RecipeFormLine(0.0, LineUnit.Grams, ingredientId = 9, wording = "Salz"),
            ),
        )

        assertNull(form.nextProblem(1, form.checklistOrder(sections)))
    }

    @Test
    fun moveLine_movesDownPastTheTargetAndUpBeforeIt() {
        val words = { form: RecipeForm -> form.lines.map { it.wording } }

        assertEquals(
            listOf("1 Bund Koriander", "1 Tasse Reis", "200 g Räuchertofu", "Salz", "200 g Tofu", "1 Zwiebel"),
            words(checklist.moveLine(0, 4)),
        )
        assertEquals(
            listOf("200 g Tofu", "1 Bund Koriander", "Salz", "1 Tasse Reis", "200 g Räuchertofu", "1 Zwiebel"),
            words(checklist.moveLine(4, 2)),
        )
    }

    @Test
    fun moveLine_keepsTheOrderWithinOtherSectionsAndOfLinesWithoutIngredient() {
        // Salz above Reis: only the dry goods change order.
        val moved = checklist.moveLine(4, 2)

        assertEquals(
            listOf(
                ChecklistGroup(null, listOf(1)),
                ChecklistGroup(StoreSection.PRODUCE, listOf(5)),
                ChecklistGroup(StoreSection.DAIRY_CHILLED, listOf(0)),
                ChecklistGroup(StoreSection.DRY_GOODS, listOf(2, 3)),
                ChecklistGroup(StoreSection.OTHER, listOf(4)),
            ),
            moved.checklistGroups(sections),
        )
        assertEquals("Salz", moved.lines[2].wording)
    }

    @Test
    fun withIngredientIds_replacesTemporaryIds() {
        val draft = RecipeDraft("Bowl", "", null, 2, 20, listOf(tofu, tofu.copy(ingredientId = -1)))

        assertEquals(listOf(7L, 42L), draft.withIngredientIds(mapOf(-1L to 42L)).lines.map { it.ingredientId })
    }

    @Test
    fun effectiveServings_isThePositiveServings() {
        assertEquals(4, valid.effectiveServings())
        assertNull(valid.copy(servings = "x").effectiveServings())
        assertNull(RecipeForm().effectiveServings())
    }

    @Test
    fun isEmpty_untilSomethingIsEntered() {
        assertTrue(RecipeForm().isEmpty)
        assertFalse(RecipeForm().copy(title = "C").isEmpty)
        assertFalse(RecipeForm(servings = "4").isEmpty)
        assertFalse(RecipeForm().withLine(null, RecipeIngredientDraft(1.0, LineUnit.Grams, 1, "Salz")).isEmpty)
    }

    @Test
    fun withScan_deliversAmountUnitAndWording() {
        val scan = ScannedRecipe(
            title = "Snack",
            servings = 1,
            totalTimeMinutes = 5,
            page = null,
            lines = listOf(
                ScannedLine("4 Scheiben Toast", 4.0, LineUnit.Of(PieceUnit.SLICE), ingredientId = 5, ingredientName = "Toast", uncertain = false),
                ScannedLine(
                    "1 Handvoll Rucola", 20.0, LineUnit.Grams, ingredientId = null, ingredientName = "Rucola", uncertain = true,
                    wording = "1 Handvoll Rucola",
                ),
            ),
        )

        val lines = RecipeForm().withScan(scan, units = mapOf(5L to listOf(IngredientUnit.perUnit(PieceUnit.SLICE, 37.5, ingredientId = 5)!!))).lines

        assertEquals(listOf(4.0, 20.0), lines.map { it.amount })
        assertEquals(listOf(LineUnit.Of(PieceUnit.SLICE), LineUnit.Grams), lines.map { it.unit })
        assertEquals(listOf(null, "1 Handvoll Rucola"), lines.map { it.wording })
        assertEquals(listOf(false, false), lines.map { it.missingUnit })
        assertEquals(RecipeIngredientDraft(4.0, LineUnit.Of(PieceUnit.SLICE), 5, null), lines.first().toDraft())
    }

    @Test
    fun withScan_aUnitTheIngredientLacksMustBeConfirmed() {
        val scan = ScannedRecipe(
            title = "Toast",
            servings = 1,
            totalTimeMinutes = null,
            page = null,
            lines = listOf(
                ScannedLine("2 Scheiben Toast", 2.0, LineUnit.Of(PieceUnit.SLICE), 5, "Toast", uncertain = false, unitGrams = 37.5),
                ScannedLine("2 Scheiben Toast", 2.0, LineUnit.Of(PieceUnit.SLICE), 5, "Toast", uncertain = false, unitGrams = 40.0),
                ScannedLine("20 g Butter", 20.0, LineUnit.Grams, 6, "Butter", uncertain = false),
            ),
        )

        val form = valid.copy(lines = emptyList()).withScan(scan).copy(servings = "1")

        assertEquals(listOf(true, true, false), form.lines.map { it.missingUnit })
        assertEquals(37.5, form.lines[0].unitGrams)
        assertEquals(LineProblem.MISSING_UNIT, form.problem(form.lines[0]))
        assertNull(form.problem(form.lines[2]))
        assertNull(form.toDraft())

        // Defining it in the editor of one line resolves the other line in that unit, too.
        val defined = form.withUnitDefined(IngredientUnit.perUnit(PieceUnit.SLICE, 37.5, ingredientId = 5)!!)
        assertEquals(listOf(false, false, false), defined.lines.map { it.missingUnit })
    }

    @Test
    fun withProposals_marksAUnitTheProposalLacks() {
        val form = valid.copy(
            lines = listOf(
                RecipeFormLine(2.0, LineUnit.Of(PieceUnit.PIECE), null, ingredientName = "Ei"),
                RecipeFormLine(1.0, LineUnit.Of(PieceUnit.TSP), null, ingredientName = "Kurkuma", unitGrams = 3.0),
            ),
        )
        val foods = form.unmatchedFoods(ids())
        val proposals = mapOf(
            foods[0].id to proposal("Ei").copy(proposedUnits = listOfNotNull(IngredientUnit.perUnit(PieceUnit.PIECE, 60.0))),
            foods[1].id to proposal("Kurkuma"),
        )

        val lines = form.withProposals(foods, proposals).lines

        assertEquals(listOf(false, true), lines.map { it.missingUnit })
    }

    @Test
    fun newIngredientUnits_theProposedPieceWeightByTemporaryId() {
        val egg = IngredientProposal(
            name = "Ei", source = null, searchTerms = emptyList(), storeSection = StoreSection.DAIRY_CHILLED,
            plantPoints = PlantPoints.ZERO, proposedUnits = listOf(IngredientUnit(0, PieceUnit.PIECE, 55.0)),
        )

        val form = RecipeForm().withNewIngredient(-3, egg)

        assertEquals(mapOf(-3L to listOf(IngredientUnit(-3, PieceUnit.PIECE, 55.0))), form.newIngredientUnits())
    }

    @Test
    fun withNewIngredientUnit_addsTheUnitTheLineEditorDefined_createdWithTheIngredient() {
        val egg = IngredientProposal(
            name = "Ei", source = null, searchTerms = emptyList(), storeSection = StoreSection.DAIRY_CHILLED,
            plantPoints = PlantPoints.ZERO, proposedUnits = listOf(IngredientUnit(0, PieceUnit.PIECE, 55.0)),
        )
        val form = RecipeForm().withNewIngredient(-3, egg)
            .withNewIngredientUnit(IngredientUnit(-3, PieceUnit.PACKET, 60.0, packageGrams = 600.0, packageCount = 10))
            .withNewIngredientUnit(IngredientUnit(-3, PieceUnit.PACKET, 600.0))
            .withLine(null, RecipeFormLine(1.0, LineUnit.Of(PieceUnit.PACKET), -3))

        val units = listOf(IngredientUnit(-3, PieceUnit.PIECE, 55.0), IngredientUnit(-3, PieceUnit.PACKET, 600.0))
        assertEquals(mapOf(-3L to units), form.newIngredientUnits())
        assertEquals(units.map { it.copy(ingredientId = 0) }, form.newIngredientsToCreate().getValue(-3).units())
        assertEquals(form, form.withNewIngredientUnit(IngredientUnit(-9, PieceUnit.SLICE, 30.0)))
    }
}
