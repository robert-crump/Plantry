package com.example.plantry.data.reminder

import com.example.plantry.data.PriceGap
import com.example.plantry.data.RecipePrice
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ProposalFactsTest {

    private fun price(perPortion: Double?, missing: List<PriceGap> = emptyList(), outdated: List<PriceGap> = emptyList()) =
        RecipePrice(perPortion, emptyList(), missing, outdated)

    private val complete = price(2.37)

    /** Words as the notification shows them, without Android resources. */
    private fun text(fact: ProposalFact) = when (fact) {
        is ProposalFact.NewPlants -> "🌱 +${fact.points} neu"
        is ProposalFact.Plants -> "🌱 ${fact.points} Pflanzen"
        is ProposalFact.Protein -> "${fact.grams} g Eiweiß"
        is ProposalFact.Price -> "ca. ${fact.euros}"
    }

    private fun line(facts: List<ProposalFact>) = ProposalFacts.line(facts, ::text)

    @Test
    fun allFacts_inOrderPlantsProteinPrice() {
        val facts = ProposalFacts.of(newPlantPoints = 5.0, plantPoints = 8.0, proteinPerPortion = 32.4, price = complete)
        assertEquals(
            listOf(ProposalFact.NewPlants(5), ProposalFact.Protein(32), ProposalFact.Price(2.4)),
            facts,
        )
        assertEquals("🌱 +5 neu · 32 g Eiweiß · ca. 2.4", line(facts))
    }

    @Test
    fun noNewPoints_showsTheRecipesTotal() {
        val facts = ProposalFacts.of(newPlantPoints = 0.0, plantPoints = 7.75, proteinPerPortion = 20.0, price = complete)
        assertEquals(ProposalFact.Plants(8), facts.first())
    }

    @Test
    fun newPointsRoundingToNone_showTheTotal() {
        val facts = ProposalFacts.of(newPlantPoints = 0.25, plantPoints = 3.0, proteinPerPortion = 20.0, price = complete)
        assertEquals(ProposalFact.Plants(3), facts.first())
    }

    @Test
    fun noPlantsAtAll_leavesThePlantFactOut() {
        val facts = ProposalFacts.of(newPlantPoints = 0.0, plantPoints = 0.0, proteinPerPortion = 20.0, price = complete)
        assertEquals("20 g Eiweiß · ca. 2.4", line(facts))
    }

    @Test
    fun missingPrice_leavesThePriceOutWithoutSeparator() {
        val facts = ProposalFacts.of(5.0, 8.0, 32.0, price(2.37, missing = listOf(PriceGap(1, "Reis"))))
        assertEquals("🌱 +5 neu · 32 g Eiweiß", line(facts))
    }

    @Test
    fun outdatedPrice_leavesThePriceOut() {
        val facts = ProposalFacts.of(5.0, 8.0, 32.0, price(2.37, outdated = listOf(PriceGap(1, "Reis", LocalDate.of(2023, 1, 1)))))
        assertEquals(listOf(ProposalFact.NewPlants(5), ProposalFact.Protein(32)), facts)
    }

    @Test
    fun noPricedLine_leavesThePriceOut() {
        assertEquals(2, ProposalFacts.of(5.0, 8.0, 32.0, price(null)).size)
        assertEquals(2, ProposalFacts.of(5.0, 8.0, 32.0, null).size)
    }

    @Test
    fun priceBelowTenCents_isZero() {
        assertEquals(ProposalFact.Price(0.0), ProposalFacts.of(5.0, 8.0, 32.0, price(0.04)).last())
    }
}
