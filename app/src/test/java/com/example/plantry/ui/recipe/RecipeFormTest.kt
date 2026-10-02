package com.example.plantry.ui.recipe

import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeDraft
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

        val form = RecipeForm.from(recipe).withBookServings("6")

        assertEquals("2", form.ourServings)
    }

    @Test
    fun toDraft_parsesValidInput() {
        val form = valid.copy(source = "Buch", page = " 12 ").withOurServings("")

        assertEquals(
            RecipeDraft("Chili", "Buch", page = 12, bookServings = 4, ourServings = null, cookingTimeMinutes = 45),
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
}
