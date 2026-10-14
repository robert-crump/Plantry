package com.example.plantry.ui.recipe

import com.example.plantry.data.AmountParser
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientAliases
import com.example.plantry.data.ParsedAmount
import com.example.plantry.data.PieceWeight
import com.example.plantry.data.RecipeIngredientDraft
import com.example.plantry.ui.ingredient.formatDecimal
import com.example.plantry.ui.ingredient.toPositiveDecimalOrNull
import kotlin.math.roundToLong

/**
 * Raw input of the ingredient line editor. Typing into the ingredient field clears the selected
 * ingredient until the user picks one of the suggestions. While [gramsAuto], the grams follow the
 * amount the text starts with ([AmountParser]); typing grams stops that.
 */
data class RecipeLineForm(
    val originalText: String = "",
    val grams: String = "",
    val ingredientQuery: String = "",
    val ingredientId: Long? = null,
    /**
     * Claude's name for the food of a scanned line, which applying the line confirms as an alias of
     * the chosen ingredient; blank for a line entered by hand.
     */
    val scannedName: String = "",
    /** Of the selected ingredient, for amounts given in pieces. */
    val pieceWeight: PieceWeight? = null,
    /** The grams come from the text, not from the user; grams given here count as typed. */
    val gramsAuto: Boolean = grams.isBlank(),
) {
    fun withText(text: String) = copy(originalText = text).autoFilled()

    fun withGrams(grams: String) = copy(grams = grams, gramsAuto = false)

    fun withIngredientQuery(query: String) =
        copy(ingredientQuery = query, ingredientId = null, pieceWeight = null).autoFilled()

    /**
     * Selects the ingredient the typed text is a learned alias of, if none is selected yet; [names]
     * by ingredient id.
     */
    fun withAlias(
        aliases: Map<String, Long>,
        names: Map<Long, String>,
        pieceWeights: Map<Long, PieceWeight> = emptyMap(),
    ): RecipeLineForm {
        if (ingredientId != null) return this
        val id = IngredientAliases.match(ingredientQuery, aliases) ?: return this
        return withIngredient(id, names[id] ?: return this, pieceWeights[id])
    }

    fun withIngredient(ingredient: Ingredient) = withIngredient(ingredient.id, ingredient.name, ingredient.pieceWeight)

    /** A negative [id] is a new ingredient proposed by Claude, see [RecipeForm.newIngredients]. */
    fun withIngredient(id: Long, name: String, pieceWeight: PieceWeight? = null) =
        copy(ingredientQuery = name, ingredientId = id, pieceWeight = pieceWeight).autoFilled()

    /** The amount the grams were filled in from; null once the user typed grams or nothing parses. */
    fun autoAmount(): ParsedAmount? = if (gramsAuto) AmountParser.parse(originalText, pieceWeight) else null

    private fun autoFilled() = if (gramsAuto) copy(grams = autoAmount()?.let { formatDecimal(it.grams) } ?: "") else this

    fun errors() = RecipeLineFormErrors(
        grams = grams.toPositiveDecimalOrNull() == null,
        ingredient = ingredientId == null,
    )

    /**
     * Returns the validated line, or null if any field is invalid. A blank original text defaults
     * to the amount and ingredient, e.g. "300 g Süßkartoffel".
     */
    fun toDraft(): RecipeIngredientDraft? {
        if (errors().hasAny) return null
        val grams = grams.toPositiveDecimalOrNull()!!
        return RecipeIngredientDraft(
            originalText = originalText.trim().ifEmpty { "${formatDecimal(grams)} g ${ingredientQuery.trim()}" },
            grams = grams,
            ingredientId = ingredientId!!,
        )
    }

    /** The validated line for the recipe form, marked as confirmed by the user. */
    fun toFormLine(): RecipeFormLine? = toDraft()?.let {
        RecipeFormLine(it.originalText, it.grams, it.ingredientId, ingredientName = scannedName, confirmed = true)
    }

    companion object {
        fun from(line: RecipeIngredientDraft, ingredientName: String, pieceWeight: PieceWeight? = null) =
            from(RecipeFormLine.from(line), ingredientName, pieceWeight)

        /**
         * An unmatched line starts with Claude's name for the food as the search term. Grams the
         * line already has count as typed by the user, so they never change on their own.
         */
        fun from(line: RecipeFormLine, ingredientName: String?, pieceWeight: PieceWeight? = null): RecipeLineForm {
            val form = RecipeLineForm(
                originalText = line.originalText,
                grams = if (line.grams > 0.0) formatDecimal(line.grams) else "",
                ingredientQuery = ingredientName ?: line.ingredientName,
                ingredientId = line.ingredientId,
                scannedName = line.ingredientName,
                pieceWeight = pieceWeight,
            )
            return form.autoFilled()
        }
    }
}

/** How the grams were worked out, e.g. "4 × 37,5 g" or "150 g". */
fun ParsedAmount.calculation(): String {
    val perPiece = gramsPerPiece ?: return "${formatDecimal(grams)} g"
    return "${formatDecimal(count.rounded(100))} × ${formatDecimal(perPiece.rounded(10))} g"
}

private fun Double.rounded(steps: Int) = (this * steps).roundToLong().toDouble() / steps

data class RecipeLineFormErrors(val grams: Boolean, val ingredient: Boolean) {
    val hasAny: Boolean get() = grams || ingredient
}
