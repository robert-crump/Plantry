package com.example.plantry.ui.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeEditUiStateTest {

    private val loaded = RecipeForm(title = "Croque", servings = "2")

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
        val retyped = loaded.copy(servings = "3").copy(servings = "2")
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

    @Test
    fun savedRecipe_withItsPages_hasNoChanges() {
        assertFalse(RecipeEditUiState(form = loaded, loadedForm = loaded, pages = pages).hasChanges)
    }

    @Test
    fun removedPage_hasChanges_andIsNoLongerShown() {
        val state = RecipeEditUiState(form = loaded, loadedForm = loaded, pages = pages, deletedPages = setOf(1))

        assertTrue(state.hasChanges)
        assertEquals(listOf(0, 2), state.shownPages)
    }

    private val pages = List(3) { ByteArray(1) { _ -> it.toByte() } }
}
