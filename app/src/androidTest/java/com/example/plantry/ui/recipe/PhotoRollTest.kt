package com.example.plantry.ui.recipe

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.plantry.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class PhotoRollTest {
    @get:Rule
    val compose = createComposeRule()

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun recipeWithoutPhotos_showsTheAddTileAlone() {
        var added = false
        compose.setContent { PhotoRoll(emptyList(), onOpen = {}, onAdd = { added = true }) }

        compose.onNodeWithText(resources.getString(R.string.photo_add)).assertIsDisplayed().performClick()

        assertTrue(added)
    }

    @Test
    fun deleteMark_namesItsPhoto() {
        var deleted: Int? = null
        compose.setContent { PhotoRoll(listOf(jpeg(Color.RED), jpeg(Color.BLUE)), onOpen = {}, onDelete = { deleted = it }) }

        compose.onAllNodesWithContentDescription(resources.getString(R.string.photo_delete))[1].performClick()

        assertEquals(1, deleted)
    }

    @Test
    fun viewer_swipesToTheNextPage_withDots() {
        var page by mutableIntStateOf(0)
        compose.setContent {
            PhotoViewer(listOf(jpeg(Color.RED), jpeg(Color.BLUE)), page = 0, onPageChange = { page = it }, onClose = {})
        }
        compose.onNodeWithContentDescription(resources.getString(R.string.photo_page, 1, 2)).assertIsDisplayed()

        compose.onAllNodesWithContentDescription(resources.getString(R.string.recipe_photo))[0].performTouchInput { swipeLeft() }

        compose.runOnIdle { assertEquals(1, page) }
        compose.onNodeWithContentDescription(resources.getString(R.string.photo_page, 2, 2)).assertIsDisplayed()
    }

    private fun jpeg(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(30, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }
}
