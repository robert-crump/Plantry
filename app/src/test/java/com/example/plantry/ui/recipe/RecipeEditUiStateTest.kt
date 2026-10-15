package com.example.plantry.ui.recipe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeEditUiStateTest {

    private val loaded = RecipeForm(title = "Croque", bookServings = "2", ourServings = "2")

    @Test
    fun newRecipe_withEmptyForm_hasNoChanges() {
        assertFalse(RecipeEditUiState().hasChanges)
    }

    @Test
    fun newRecipe_withInput_hasChanges() {
        assertTrue(RecipeEditUiState(form = RecipeForm(title = "Croque")).hasChanges)
    }

    @Test
    fun savedRecipe_asLoaded_hasNoChanges() {
        assertFalse(RecipeEditUiState(form = loaded, loadedForm = loaded).hasChanges)
    }

    @Test
    fun savedRecipe_edited_hasChanges() {
        assertTrue(RecipeEditUiState(form = loaded.copy(title = "Croque Monsieur"), loadedForm = loaded).hasChanges)
    }

    @Test
    fun savedRecipe_editedBackToLoaded_hasNoChanges() {
        // Typing into "our servings" marks it edited, even when the value ends up as loaded.
        val retyped = loaded.copy(ourServingsEdited = true)
        assertFalse(RecipeEditUiState(form = retyped, loadedForm = loaded).hasChanges)
    }

    @Test
    fun scan_alwaysHasChanges() {
        assertTrue(RecipeEditUiState(scanning = true).hasChanges)
    }

    @Test
    fun skippedScan_withItsPhoto_hasChanges() {
        assertTrue(RecipeEditUiState(photo = ByteArray(1), photoChanged = true).hasChanges)
    }
}
