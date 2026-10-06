package com.example.plantry.data.claude

import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import com.example.plantry.data.settings.ScanModel
import com.example.plantry.data.usda.UsdaFood
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Sends the real proposal prompt to Claude for foods the calibration settled. Skipped unless
 * ANTHROPIC_API_KEY is set: `ANTHROPIC_API_KEY=... ./gradlew testDebugUnitTest --tests '*LiveTest'`.
 */
class ProposalPromptLiveTest {

    private val apiKey: String? = System.getenv("ANTHROPIC_API_KEY")

    private fun usda(fdcId: Long, description: String) =
        UsdaFood(fdcId, description, Nutrition(0.0, 0.0, 0.0, 0.0, 0.0, 0.0))

    private fun food(id: Long, name: String, line: String, vararg candidates: UsdaFood) =
        FoodCandidates(NewFood(id, name, line, emptyList()), candidates.toList())

    @Test
    fun `known foods are classified as calibrated`() {
        assumeTrue("ANTHROPIC_API_KEY not set", !apiKey.isNullOrBlank())
        val foods = listOf(
            food(-1, "Knoblauch", "2 Knoblauchzehen", usda(1, "Garlic, raw")),
            food(-2, "Olivenöl", "3 EL Olivenöl", usda(2, "Oil, olive, salad or cooking")),
            food(
                -3, "Basmatireis", "200 g Basmatireis",
                usda(3, "Rice, white, long-grain, regular, enriched, raw"),
                usda(4, "Rice, white, long-grain, regular, raw, unenriched"),
                usda(5, "Rice, white, long-grain, regular, unenriched, cooked without salt"),
            ),
            food(
                -4, "Kichererbsen", "1 Dose Kichererbsen",
                usda(6, "Chickpeas (garbanzo beans, bengal gram), mature seeds, raw"),
                usda(7, "Chickpeas (garbanzo beans, bengal gram), mature seeds, canned, drained solids"),
            ),
            food(-5, "Tomatenmark", "2 EL Tomatenmark", usda(8, "Tomato products, canned, paste, without salt added")),
            food(-6, "Ingwer", "1 Stück Ingwer", usda(9, "Ginger root, raw")),
            food(-7, "Gnocchi", "500 g Gnocchi", usda(10, "Potatoes, flesh and skin, raw")),
        )

        val result = runBlocking { AnthropicIngredientProposer().propose(apiKey!!, ScanModel.DEFAULT, foods) }

        val proposals = (result as ClaudeResult.Success).value
        val points = foods.associate { it.food.name to proposals.getValue(it.food.id).plantPoints }
        assertEquals(
            mapOf(
                "Knoblauch" to PlantPoints.QUARTER, "Olivenöl" to PlantPoints.ZERO, "Basmatireis" to PlantPoints.ZERO,
                "Kichererbsen" to PlantPoints.ONE, "Tomatenmark" to PlantPoints.ZERO, "Ingwer" to PlantPoints.QUARTER,
                "Gnocchi" to PlantPoints.ZERO,
            ),
            points,
        )
        assertEquals(4L, proposals.getValue(-3).food?.fdcId)
        assertEquals(7L, proposals.getValue(-4).food?.fdcId)
        assertEquals(null, proposals.getValue(-7).food)
        assertEquals(StoreSection.DRY_GOODS, proposals.getValue(-2).storeSection)
    }
}
