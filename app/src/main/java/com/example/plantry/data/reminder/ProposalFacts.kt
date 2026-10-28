package com.example.plantry.data.reminder

import com.example.plantry.data.RecipePrice
import kotlin.math.roundToInt

/** One fact of the proposal notification's text, in whole numbers as shown. */
sealed interface ProposalFact {
    /** "🌱 +5 neu": the plant points the recipe adds to this week. */
    data class NewPlants(val points: Int) : ProposalFact

    /** "🌱 8 Pflanzen": the recipe's own plant points, when it adds none to this week. */
    data class Plants(val points: Int) : ProposalFact

    /** "32 g Eiweiß" per portion. */
    data class Protein(val grams: Int) : ProposalFact

    /** "ca. 2,40 €" per portion, rounded as the price estimate shows it (0 when below 10 cents). */
    data class Price(val euros: Double) : ProposalFact
}

/** The proposal notification's fact line, e.g. "🌱 +5 neu · 32 g Eiweiß · ca. 2,40 €". */
object ProposalFacts {

    /**
     * Always in the order plant points · protein · price. [newPlantPoints] are those the suggester
     * ranks on ([com.example.plantry.data.planner.WeekPlants.newPoints]); when they round to none,
     * the recipe's [plantPoints] stand in, and with none at all there is no plant fact. The price only
     * shows when its estimate has no gaps.
     */
    fun of(newPlantPoints: Double, plantPoints: Double, proteinPerPortion: Double, price: RecipePrice?): List<ProposalFact> {
        val new = newPlantPoints.roundToInt()
        val total = plantPoints.roundToInt()
        val plants = when {
            new > 0 -> ProposalFact.NewPlants(new)
            total > 0 -> ProposalFact.Plants(total)
            else -> null
        }
        val euros = price?.takeUnless { it.hasGaps }?.roundedPerPortion
        return listOfNotNull(plants, ProposalFact.Protein(proteinPerPortion.roundToInt()), euros?.let(ProposalFact::Price))
    }

    /** The facts as [text] words each, joined with " · ". */
    fun line(facts: List<ProposalFact>, text: (ProposalFact) -> String): String = facts.joinToString(SEPARATOR, transform = text)

    const val SEPARATOR = " · "
}
