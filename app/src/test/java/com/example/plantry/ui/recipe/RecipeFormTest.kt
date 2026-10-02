package com.example.plantry.ui.recipe

import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeDraft
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.RecipeIngredientDraft
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

        assertEquals(listOf(tofu.copy(grams = 300.0), rice), form.lines)
        assertEquals(listOf(rice), form.removeLine(0).lines)
        assertEquals(listOf(tofu.copy(grams = 300.0), rice), form.toDraft()!!.lines)
    }

    @Test
    fun from_keepsLinesInStoredOrder() {
        val recipe = Recipe(1, "Bowl", "Buch", null, bookServings = 2, ourServings = 2, cookingTimeMinutes = 20)
        val stored = listOf(
            RecipeIngredient(10, recipeId = 1, position = 0, originalText = "200 g Tofu", grams = 200.0, ingredientId = 7),
            RecipeIngredient(11, recipeId = 1, position = 1, originalText = "1 Tasse Reis", grams = 185.0, ingredientId = 8),
        )

        assertEquals(listOf(tofu, rice), RecipeForm.from(recipe, stored).lines)
    }

    @Test
    fun effectiveServings_prefersOurServingsThenBookServings() {
        assertEquals(4, valid.effectiveServings())
        assertEquals(2, valid.withOurServings("2").effectiveServings())
        assertEquals(4, valid.withOurServings("x").effectiveServings())
        assertNull(RecipeForm().effectiveServings())
    }
}
