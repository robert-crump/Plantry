package com.example.plantry.ui.cooklog

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.plantry.R
import com.example.plantry.data.PlannedItem
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.RecipeSnapshot
import com.example.plantry.data.RecipeStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PlannedCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val item = PlannedItem(PlannedRecipe(1, LocalDate.of(2026, 10, 18)), RecipeSnapshot("Linsencurry", RecipeStats(5.0, 20.0, 40.0)))
    private val question = resources.getString(R.string.planned_done_title, "Linsencurry")
    private val gekocht = resources.getString(R.string.planned_done_action)

    private var done = 0
    private var swiped = 0
    private var longClicked = false

    private fun setCard(selected: Boolean = false, swipeEnabled: Boolean = true) {
        compose.setContent {
            PlannedCard(
                item,
                selected = selected,
                swipeEnabled = swipeEnabled,
                onClick = {},
                onLongClick = { longClicked = true },
                onSwiped = { swiped++ },
                onDone = { done++ },
            )
        }
    }

    @Test
    fun fullSwipeRight_asks_thenLogs() {
        setCard()

        compose.onNodeWithText("Linsencurry").performTouchInput { swipeRight() }
        compose.onNodeWithText(question).assertExists()
        compose.onNodeWithText(gekocht).performClick()

        compose.runOnIdle { assertEquals(1, done) }
        compose.onNodeWithText(question).assertDoesNotExist()
    }

    @Test
    fun fullSwipeLeft_cancelled_logsNothing() {
        setCard()

        compose.onNodeWithText("Linsencurry").performTouchInput { swipeLeft() }
        compose.onNodeWithText(resources.getString(R.string.action_cancel)).performClick()

        compose.onNodeWithText(question).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, done)
            assertEquals(1, swiped)
        }
    }

    @Test
    fun shortSwipe_springsBack_withoutAsking() {
        setCard()

        // Half the width: past the old 40 %, short of the 60 % now needed.
        compose.onNodeWithText("Linsencurry").performTouchInput {
            swipeRight(startX = left + width * 0.1f, endX = left + width * 0.6f)
        }

        compose.onNodeWithText(question).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, swiped) }
    }

    @Test
    fun swipeDisabled_whileSelecting() {
        setCard(selected = true, swipeEnabled = false)

        compose.onNodeWithText("Linsencurry").performTouchInput { swipeRight() }

        compose.onNodeWithText(question).assertDoesNotExist()
    }

    @Test
    fun longPress_selects() {
        setCard()

        compose.onNodeWithText("Linsencurry").performTouchInput { longClick() }

        compose.runOnIdle { assertTrue(longClicked) }
    }

    @Test
    fun selected_isAnnounced() {
        setCard(selected = true)

        compose.onNodeWithText("Linsencurry").assertIsSelected()
    }
}
