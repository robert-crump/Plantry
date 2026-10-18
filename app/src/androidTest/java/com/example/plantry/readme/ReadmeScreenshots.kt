package com.example.plantry.readme

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.plantry.MainActivity
import com.example.plantry.PlantryApplication
import com.example.plantry.R
import com.example.plantry.data.settings.ThemeMode
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Screenshots for the README (#92), taken from the emulator fixture the debug build seeds after
 * `pm clear`. Run through `./gradlew readmeScreenshots`, which also clears the app's data, hides
 * notification icons and copies the PNGs to `docs/screenshots/`.
 * [EmulatorOnlyRule] skips it without that argument and refuses real devices.
 */
@RunWith(AndroidJUnit4::class)
class ReadmeScreenshots {
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EmulatorOnlyRule()).around(compose)

    private lateinit var context: Context
    private lateinit var screenshots: ReadmeScreenshotCapture
    private lateinit var locales: LocaleManager
    private lateinit var previousLocales: LocaleList

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        screenshots = ReadmeScreenshotCapture.cleared(context)
        val settings = (context.applicationContext as PlantryApplication).settingsRepository
        settings.setThemeMode(ThemeMode.LIGHT)
        // The one-time hint under Geplant isn't what the screen usually looks like.
        settings.setSwipeHintSeen()
        // German dates to go with the German UI, whatever the emulator's language.
        locales = context.getSystemService(LocaleManager::class.java)
        previousLocales = locales.applicationLocales
        locales.applicationLocales = LocaleList.forLanguageTags("de-DE")
    }

    @After
    fun tearDown() {
        if (::locales.isInitialized) locales.applicationLocales = previousLocales
    }

    @Test
    fun captureReadmeScreenshots() {
        captureCooking()
        captureSuggestions()
        captureRecipes()
        captureIngredient()
    }

    /** Speiseplan: Geplant and the cooking history. */
    private fun captureCooking() {
        launchCooking().use {
            screenshots.capture(compose, "cooking")
        }
    }

    /** Zufallsrezept, opened from Speiseplan's action menu. */
    private fun captureSuggestions() {
        launchCooking().use {
            compose.onAllNodesWithContentDescription(string(R.string.cook_actions_open)).onFirst().performClick()
            waitForText(string(R.string.suggest_action))
            compose.onAllNodesWithText(string(R.string.suggest_action), useUnmergedTree = true).onFirst().performClick()
            waitForText(string(R.string.suggest_reroll))
            screenshots.capture(compose, "suggestions")
        }
    }

    /** The recipe list, then one recipe's detail. */
    private fun captureRecipes() {
        launchCooking().use {
            // The bottom bar's tab: Speiseplan has no other "Rezepte" text.
            compose.onAllNodesWithText(string(R.string.nav_recipes)).onFirst().performClick()
            waitForText(RECIPE)
            screenshots.capture(compose, "recipes")

            openFromList(RECIPE)
            waitForContentDescription(string(R.string.action_more))
            screenshots.capture(compose, "recipe")
        }
    }

    /** One ingredient's detail, opened from the ingredient list. */
    private fun captureIngredient() {
        launchCooking().use {
            compose.onAllNodesWithText(string(R.string.nav_ingredients)).onFirst().performClick()
            waitForText(INGREDIENT)
            openFromList(INGREDIENT)
            waitForText(string(R.string.ingredient_section_nutrition))
            screenshots.capture(compose, "ingredient")
        }
    }

    /** Speiseplan, once the seeder has imported the fixture: today's planned recipe is on it. */
    private fun launchCooking(): ActivityScenario<MainActivity> {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText(PLANNED_TODAY, SEED_TIMEOUT_MS)
        return scenario
    }

    private fun openFromList(text: String) {
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
        compose.onAllNodesWithText(text).onFirst().performClick()
    }

    /** Searches the unmerged tree: the FAB menu's and extended FABs' texts are only there. */
    private fun waitForText(text: String, timeoutMs: Long = UI_TIMEOUT_MS) {
        compose.waitUntil(timeoutMs) {
            compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForContentDescription(description: String) {
        compose.waitUntil(UI_TIMEOUT_MS) {
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        // From app/src/debug/assets/emulator-fixture.json; its Geplant moves to start today.
        const val PLANNED_TODAY = "Kichererbsen-Curry"
        const val RECIPE = "Linsen-Dal mit Spinat"
        const val INGREDIENT = "Brokkoli"

        const val SEED_TIMEOUT_MS = 60_000L
        const val UI_TIMEOUT_MS = 30_000L
    }
}
