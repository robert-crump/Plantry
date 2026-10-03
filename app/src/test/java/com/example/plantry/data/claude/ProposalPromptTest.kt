package com.example.plantry.data.claude

import com.example.plantry.data.BuyUnit
import com.example.plantry.data.Ingredient
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalPromptTest {

    private val lemon = Ingredient(
        id = 7, name = "Zitrone", fdcId = 167746, usdaDescription = "Lemons, raw, without peel",
        nutrition = Nutrition(29.0, 1.1, 9.3, 2.8, 0.3, 2.5), unitWeights = emptyList(), buyUnit = BuyUnit.PIECES,
        packSizeGrams = null, storeSection = StoreSection.PRODUCE, staple = false, plantPoints = PlantPoints.ONE,
        buyAsIngredientId = null, buyAsYieldFactor = null, reviewed = true,
    )

    @Test
    fun `propose prompt contains every calibrated rule`() {
        val prompt = ProposalPrompt.proposeSystem(emptyList())

        listOf(
            MatchingRules.USDA_ENTRY, MatchingRules.NAME, MatchingRules.STORE_SECTION,
            MatchingRules.STAPLE, MatchingRules.PLANT_POINTS, MatchingRules.BUY_AS,
        ).forEach { assertTrue(it.lines().first(), prompt.contains(it)) }
    }

    @Test
    fun `plant points follow the calibration`() {
        val rule = MatchingRules.PLANT_POINTS

        assertTrue(rule.contains("QUARTER for herbs (fresh or dried), spices,\ngarlic, ginger and chili"))
        assertTrue(rule.contains("ZERO for oils (incl. olive and coconut oil), refined grains"))
        assertTrue(rule.contains("Tomatenmark, Sojasauce ZERO"))
        assertTrue(rule.contains("Harissa, Za'atar QUARTER"))
    }

    @Test
    fun `plant points and store sections name only existing enum values`() {
        val words = Regex("[A-Z][A-Z_]+").findAll(MatchingRules.PLANT_POINTS + MatchingRules.STORE_SECTION)
            .map { it.value }.toSet()
        val known = PlantPoints.entries.map { it.name } + StoreSection.entries.map { it.name }

        assertTrue(words.toString(), known.containsAll(words))
        assertTrue(words.containsAll(known))
    }

    @Test
    fun `usda rules prefer canned beans and unenriched grains`() {
        val rule = MatchingRules.USDA_ENTRY

        assertTrue(rule.contains("\"canned, drained solids\""))
        assertTrue(rule.contains("unenriched rice, pasta and cornmeal"))
        assertTrue(rule.contains("Do not stretch a base food"))
    }

    @Test
    fun `table follows the rules and keeps one line per ingredient`() {
        val prompt = ProposalPrompt.proposeSystem(listOf(lemon))

        assertTrue(prompt.endsWith("The user's ingredient table (id, tab, German name):\n7\tZitrone\n"))
        assertTrue(prompt.indexOf(MatchingRules.BUY_AS) < prompt.indexOf("7\tZitrone"))
    }

    @Test
    fun `search prompts ask for the bought form`() {
        assertTrue(ProposalPrompt.SEARCH_TERMS_SYSTEM.endsWith(MatchingRules.SEARCH_FORMS))
        assertTrue(ProposalPrompt.SEARCH_TERMS_RULE.contains("beans and chickpeas canned unless the recipe says dried"))
        assertFalse(ProposalPrompt.SEARCH_TERMS_RULE.contains("\n"))
        assertFalse(ProposalPrompt.SEARCH_TERMS_RULE.endsWith("."))
    }
}
