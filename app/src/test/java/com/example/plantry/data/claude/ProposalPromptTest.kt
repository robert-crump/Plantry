package com.example.plantry.data.claude

import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalPromptTest {

    @Test
    fun `propose prompt contains every calibrated rule`() {
        val prompt = ProposalPrompt.PROPOSE_SYSTEM

        listOf(
            MatchingRules.USDA_ENTRY, MatchingRules.NAME, MatchingRules.STORE_SECTION, MatchingRules.PLANT_POINTS,
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
    fun `propose prompt and schema ask only for the kept attributes`() {
        val asked = ProposalPrompt.PROPOSE_SYSTEM + ProposalPrompt.proposeSchema().toString()

        listOf("unitWeights", "buyUnit", "packSize", "staple", "buyAs", "ingredient table (id").forEach {
            assertFalse(it, asked.contains(it))
        }
    }

    @Test
    fun `propose prompt and schema no longer ask for net or drained weights`() {
        val asked = ProposalPrompt.PROPOSE_SYSTEM + ProposalPrompt.proposeSchema().toString()

        listOf("netWeight", "drainedWeight", "Drained weight").forEach { assertFalse(it, asked.contains(it)) }
    }

    @Test
    fun `a can of a drained product weighs its drained grams`() {
        val rule = MatchingRules.UNITS

        assertTrue(rule.contains("the drained weight"))
        assertTrue(rule.contains("Kichererbsen (Dose) CAN 240"))
        assertTrue(rule.contains("Kokosmilch CAN 400"))
    }

    @Test
    fun `propose prompt and schema ask for a list of units with grams`() {
        assertTrue(ProposalPrompt.PROPOSE_SYSTEM.endsWith("Units: " + MatchingRules.UNITS))
        val rule = MatchingRules.UNITS
        assertTrue(rule.contains("Knoblauch CLOVE 3"))
        assertTrue(rule.contains("Kreuzkümmel TSP 2 and TBSP 6"))
        assertTrue(rule.contains("(Toast, sliced cheese, jars)"))
        val schema = ProposalPrompt.proposeSchema().toString()
        assertTrue(schema.contains("units"))
        assertTrue(schema.contains("grams"))
        listOf("CAN", "TSP", "TBSP", "CUP").forEach { assertTrue(it, schema.contains(it)) }
        listOf("SLICE", "PACKET", "pieceUnit", "pieceGrams", "NONE").forEach { assertFalse(it, schema.contains(it)) }
    }

    @Test
    fun `units rule names only the units Claude may propose`() {
        val words = Regex("\\b[A-Z]{2,}\\b").findAll(MatchingRules.UNITS).map { it.value }.toSet()
        val known = MatchingRules.PROPOSED_UNITS.map { it.name }

        assertTrue(words.toString(), known.containsAll(words))
        assertTrue(words.containsAll(known))
    }

    @Test
    fun `search prompts ask for the bought form`() {
        assertTrue(ProposalPrompt.SEARCH_TERMS_SYSTEM.endsWith(MatchingRules.SEARCH_FORMS))
        assertTrue(ProposalPrompt.SEARCH_TERMS_RULE.contains("beans and chickpeas canned unless the recipe says dried"))
        assertFalse(ProposalPrompt.SEARCH_TERMS_RULE.contains("\n"))
        assertFalse(ProposalPrompt.SEARCH_TERMS_RULE.endsWith("."))
    }
}
