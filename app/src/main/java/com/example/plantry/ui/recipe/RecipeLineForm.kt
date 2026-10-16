package com.example.plantry.ui.recipe

import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientAliases
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.LineAmount
import com.example.plantry.data.LineUnit
import com.example.plantry.data.RecipeIngredientDraft
import com.example.plantry.data.ResolvedAmount
import com.example.plantry.ui.ingredient.formatDecimal
import com.example.plantry.ui.ingredient.toPositiveDecimalOrNull

/**
 * Raw input of the ingredient line editor: amount, unit, ingredient and an optional wording.
 * Typing into the ingredient field clears the selected ingredient until the user picks one of the
 * suggestions; a unit the newly picked ingredient lacks falls back to g.
 */
data class RecipeLineForm(
    val amount: String = "",
    val unit: LineUnit = LineUnit.Grams,
    val ingredientQuery: String = "",
    val ingredientId: Long? = null,
    /** The line as the book words it; blank shows the generated text. */
    val wording: String = "",
    /**
     * Claude's name for the food of a scanned line, which applying the line confirms as an alias of
     * the chosen ingredient; blank for a line entered by hand.
     */
    val scannedName: String = "",
    /** Of the selected ingredient, offered after g and ml. */
    val units: List<IngredientUnit> = emptyList(),
) {
    fun withAmount(amount: String) = copy(amount = amount)

    fun withUnit(unit: LineUnit) = copy(unit = unit)

    fun withWording(wording: String) = copy(wording = wording)

    fun withIngredientQuery(query: String) =
        copy(ingredientQuery = query, ingredientId = null, units = emptyList()).withAvailableUnit()

    /**
     * Selects the ingredient the typed text is a learned alias of, if none is selected yet; [names]
     * and [units] by ingredient id.
     */
    fun withAlias(
        aliases: Map<String, Long>,
        names: Map<Long, String>,
        units: Map<Long, List<IngredientUnit>> = emptyMap(),
    ): RecipeLineForm {
        if (ingredientId != null) return this
        val id = IngredientAliases.match(ingredientQuery, aliases) ?: return this
        return withIngredient(id, names[id] ?: return this, units[id].orEmpty())
    }

    fun withIngredient(ingredient: Ingredient, units: List<IngredientUnit>) = withIngredient(ingredient.id, ingredient.name, units)

    /** A negative [id] is a new ingredient proposed by Claude, see [RecipeForm.newIngredients]. */
    fun withIngredient(id: Long, name: String, units: List<IngredientUnit> = emptyList()) =
        copy(ingredientQuery = name, ingredientId = id, units = units).withAvailableUnit()

    /** g, ml, then the units of the selected ingredient. */
    fun unitOptions(): List<LineUnit> = LineUnit.ALWAYS + units.map { LineUnit.Of(it.unit) }

    /** The line worked out, once amount and ingredient are valid; for the calculation under the amount. */
    fun resolved(): ResolvedAmount? {
        val amount = amount.toPositiveDecimalOrNull() ?: return null
        if (ingredientId == null) return null
        return LineAmount.resolve(amount, unit, units, ingredientQuery, wording)
    }

    private fun withAvailableUnit() = if (unit in unitOptions()) this else copy(unit = LineUnit.Grams)

    fun errors() = RecipeLineFormErrors(
        amount = amount.toPositiveDecimalOrNull() == null,
        ingredient = ingredientId == null,
    )

    /** Returns the validated line, or null if any field is invalid. */
    fun toDraft(): RecipeIngredientDraft? {
        if (errors().hasAny) return null
        return RecipeIngredientDraft(
            amount = amount.toPositiveDecimalOrNull()!!,
            unit = unit,
            ingredientId = ingredientId!!,
            wording = wording.trim().ifEmpty { null },
        )
    }

    /** The validated line for the recipe form, marked as confirmed by the user. */
    fun toFormLine(): RecipeFormLine? = toDraft()?.let {
        RecipeFormLine(it.amount, it.unit, it.ingredientId, it.wording, ingredientName = scannedName, confirmed = true)
    }

    companion object {
        fun from(line: RecipeIngredientDraft, ingredientName: String, units: List<IngredientUnit> = emptyList()) =
            from(RecipeFormLine.from(line), ingredientName, units)

        /** An unmatched line starts with Claude's name for the food as the search term. */
        fun from(line: RecipeFormLine, ingredientName: String?, units: List<IngredientUnit> = emptyList()) = RecipeLineForm(
            amount = if (line.amount > 0.0) formatDecimal(line.amount) else "",
            unit = line.unit,
            ingredientQuery = ingredientName ?: line.ingredientName,
            ingredientId = line.ingredientId,
            wording = line.wording.orEmpty(),
            scannedName = line.ingredientName,
            units = units,
        )
    }
}

data class RecipeLineFormErrors(val amount: Boolean, val ingredient: Boolean) {
    val hasAny: Boolean get() = amount || ingredient
}
