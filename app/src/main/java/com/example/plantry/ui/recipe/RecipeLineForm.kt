package com.example.plantry.ui.recipe

import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientAliases
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.LineAmount
import com.example.plantry.data.LineUnit
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.RecipeIngredientDraft
import com.example.plantry.data.ResolvedAmount
import com.example.plantry.ui.ingredient.UnitForm
import com.example.plantry.ui.ingredient.formatDecimal
import com.example.plantry.ui.ingredient.toPositiveDecimalOrNull

/**
 * Raw input of the ingredient line editor: amount, unit, ingredient and an optional wording.
 * Typing into the ingredient field clears the selected ingredient until the user picks one of the
 * suggestions. Any unit can be picked; one the ingredient lacks ([missingUnit]) needs its grams
 * ([definition]) before the line can be applied, and applying saves it to the ingredient.
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
    /** The grams of [missingUnit]; its unit is ignored, as the line's unit decides it. */
    val definition: UnitForm = UnitForm(),
) {
    fun withAmount(amount: String) = copy(amount = amount)

    fun withUnit(unit: LineUnit) = copy(unit = unit)

    fun withWording(wording: String) = copy(wording = wording)

    fun withDefinition(transform: UnitForm.() -> UnitForm) = copy(definition = definition.transform())

    fun withIngredientQuery(query: String) = copy(ingredientQuery = query, ingredientId = null, units = emptyList())

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
        copy(ingredientQuery = name, ingredientId = id, units = units)

    /** g, ml, the units of the selected ingredient, then all other units. */
    fun unitOptions(): List<LineUnit> {
        val own = units.map { it.unit }
        return LineUnit.ALWAYS + (own + PieceUnit.entries.filter { it !in own }).map(LineUnit::Of)
    }

    /** The line's unit, once an ingredient is selected that doesn't have it yet. */
    val missingUnit: PieceUnit?
        get() {
            if (ingredientId == null) return null
            val unit = (unit as? LineUnit.Of)?.unit ?: return null
            return unit.takeIf { units.none { it.unit == unit } }
        }

    /** Whether [missingUnit] can be entered as a package and a count ("750 g ÷ 20"). */
    val offersPackage: Boolean get() = missingUnit in PACKAGE_UNITS

    /** [missingUnit] as defined, or null while there is none or its grams are invalid. */
    fun newUnit(): IngredientUnit? {
        val unit = missingUnit ?: return null
        return definition.copy(unit = unit, inPackage = definition.inPackage && offersPackage).toUnit()?.copy(ingredientId = ingredientId!!)
    }

    /** The line worked out, once amount and ingredient are valid; for the calculation under the amount. */
    fun resolved(): ResolvedAmount? {
        val amount = amount.toPositiveDecimalOrNull() ?: return null
        if (ingredientId == null) return null
        return LineAmount.resolve(amount, unit, units + listOfNotNull(newUnit()), ingredientQuery, wording)
    }

    fun errors() = RecipeLineFormErrors(
        amount = amount.toPositiveDecimalOrNull() == null,
        ingredient = ingredientId == null,
        unitGrams = missingUnit != null && definition.gramsInvalid,
        unitCount = missingUnit != null && offersPackage && definition.countInvalid,
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
        /** The units that come in packages of several, e.g. 20 Scheiben in 750 g Toast. */
        val PACKAGE_UNITS = setOf(PieceUnit.PIECE, PieceUnit.SLICE, PieceUnit.PACKET)

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

data class RecipeLineFormErrors(
    val amount: Boolean,
    val ingredient: Boolean,
    /** The grams of a unit the ingredient lacks are missing or invalid. */
    val unitGrams: Boolean = false,
    /** The package count of that unit is missing or invalid. */
    val unitCount: Boolean = false,
) {
    val hasAny: Boolean get() = amount || ingredient || unitGrams || unitCount
}
