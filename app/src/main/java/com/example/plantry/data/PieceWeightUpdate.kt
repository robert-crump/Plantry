package com.example.plantry.data

import kotlin.math.abs

/**
 * The recipe lines a changed [PieceWeight] would give different grams, e.g. every "4 Scheiben Toast"
 * once a slice weighs less. The user is asked before they change, see [IngredientRepository.update].
 */
object PieceWeightUpdate {

    /**
     * True when saving [new] over [old] changes what a piece weighs, including setting it for the
     * first time. Removing it, or the same weight written differently (750 g / 20 → 1500 g / 40), is no change.
     */
    fun changed(old: PieceWeight?, new: PieceWeight?): Boolean {
        new ?: return false
        old ?: return true
        return old.pieceUnit != new.pieceUnit || !sameGrams(old.gramsPerPiece, new.gramsPerPiece)
    }

    /**
     * Each of [lines] (all of one ingredient) whose text parses to other grams with [pieceWeight],
     * with the parsed grams. Lines that don't parse, or already have those grams, are left out.
     */
    fun affectedLines(lines: List<RecipeIngredient>, pieceWeight: PieceWeight): List<RecipeIngredient> =
        lines.mapNotNull { line ->
            val grams = AmountParser.parse(line.originalText, pieceWeight)?.grams ?: return@mapNotNull null
            if (sameGrams(line.grams, grams)) null else line.copy(grams = grams)
        }

    /** Grams are entered to 0.1 g, so anything closer is the same. */
    private fun sameGrams(a: Double, b: Double) = abs(a - b) < 0.05
}

/** Recipe lines to give new grams after a piece weight changed, and in how many recipes they are. */
data class LineGramsUpdate(val lines: List<RecipeIngredient>) {
    val recipeCount: Int get() = lines.distinctBy { it.recipeId }.size
}
