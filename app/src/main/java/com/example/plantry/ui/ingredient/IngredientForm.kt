package com.example.plantry.ui.ingredient

import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientDraft
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.IngredientSuggestions
import com.example.plantry.data.LabelSource
import com.example.plantry.data.MixContents
import com.example.plantry.data.MixPart
import com.example.plantry.data.Mixes
import com.example.plantry.data.Nutrient
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import com.example.plantry.data.openfoodfacts.OffProduct
import com.example.plantry.data.usda.UsdaFood
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Raw input of the ingredient detail screen. Decimals accept both "," and ".". */
data class IngredientForm(
    val name: String = "",
    val nutrition: Map<Nutrient, String> = Nutrient.entries.associateWith { "" },
    val storeSection: StoreSection = StoreSection.OTHER,
    val plantPoints: PlantPoints = PlantPoints.ZERO,
    /** One row per unit in [PieceUnit] order, each already validated by [UnitForm] in its dialog. */
    val units: List<IngredientUnit> = emptyList(),
    /** The package applied from a barcode scan; replaces the USDA reference on save. */
    val scannedLabel: LabelSource? = null,
    /** The USDA entry picked to replace the nutrition; replaces the source on save. */
    val pickedUsda: UsdaFood? = null,
    /** What the ingredient contains ("Enthält"): stored ones by id, then those added; while there are any, it is a mix. */
    val components: List<MixPart> = emptyList(),
    /** Null for none; already validated by [PriceForm] in its dialog. */
    val pricePerKg: Double? = null,
) {
    val isMix: Boolean get() = components.isNotEmpty()

    /**
     * The plant points of the mix: its components' own points, a component that is only
     * a name yet as one point (as it is created). Replaces [plantPoints] while [isMix].
     */
    fun componentPlantPoints(ingredients: Map<Long, Ingredient>): Double =
        components.sumOf { part -> part.id?.let { ingredients[it]?.plantPoints?.value ?: 0.0 } ?: PlantPoints.ONE.value }

    /** The units a row can still be given: those without a row, plus [editing]'s own. */
    fun availableUnits(editing: PieceUnit? = null): List<PieceUnit> =
        PieceUnit.entries.filter { unit -> unit == editing || units.none { it.unit == unit } }

    /** Puts [unit] in place of the row of [replacing] (null when adding), or of a row with the same unit. */
    fun withUnit(unit: IngredientUnit, replacing: PieceUnit? = null) =
        copy(units = (units.filter { it.unit != replacing && it.unit != unit.unit } + unit).sortedBy { it.unit })

    /** The grams of the Packung row, if any: a new row in package mode starts from them. */
    val packageGrams: Double? get() = units.find { it.unit == PieceUnit.PACKET }?.gramsPerUnit

    fun withoutUnit(unit: PieceUnit) = copy(units = units.filter { it.unit != unit })

    fun withNutrient(nutrient: Nutrient, value: String) = copy(nutrition = nutrition + (nutrient to value))

    /** Takes the values [product] has and keeps the others; the name stays as it is. */
    fun withScanned(product: OffProduct) = copy(
        nutrition = nutrition + product.nutrition.mapValues { formatDecimal(it.value) },
        scannedLabel = product.labelSource,
        pickedUsda = null,
    )

    /** Whether any nutrient holds something other than blank or 0, which picking a USDA entry would overwrite. */
    val hasNutrition: Boolean get() = nutrition.values.any { it.isNotBlank() && it.toDecimalOrNull() != 0.0 }

    /** Replaces every value with [food]'s; the name stays as it is. */
    fun withUsda(food: UsdaFood) = copy(
        nutrition = Nutrient.entries.associateWith { formatDecimal(food.nutrition[it]) },
        scannedLabel = null,
        pickedUsda = food,
    )

    fun errors() = IngredientFormErrors(
        name = name.isBlank(),
        nutrients = Nutrient.entries.filterTo(mutableSetOf()) { nutrient ->
            nutrition[nutrient].orEmpty().toDecimalOrNull().let { it == null || it < 0 }
        },
    )

    /** Returns the validated draft, or null if any field is invalid. */
    fun toDraft(): IngredientDraft? {
        if (errors().hasAny) return null
        return IngredientDraft(
            name = name,
            nutrition = Nutrition.of(nutrition.mapValues { it.value.toDecimalOrNull()!! }),
            storeSection = storeSection,
            plantPoints = plantPoints,
            units = units,
            scannedLabel = scannedLabel,
            pickedUsda = pickedUsda,
            components = components,
            pricePerKg = pricePerKg,
        )
    }

    companion object {
        fun from(ingredient: Ingredient, units: List<IngredientUnit> = emptyList(), components: List<MixPart> = emptyList()) = IngredientForm(
            name = ingredient.name,
            nutrition = Nutrient.entries.associateWith { formatDecimal(ingredient.nutrition[it]) },
            storeSection = ingredient.storeSection,
            plantPoints = ingredient.plantPoints,
            units = units.sortedBy { it.unit },
            components = components,
            pricePerKg = ingredient.price?.pricePerKg,
        )
    }
}

/** Raw input of the price dialog: € per kg (or l), 0 allowed. */
data class PriceForm(val perKg: String = "") {
    val isValid: Boolean get() = toPricePerKg() != null

    /** The price, or null while invalid. */
    fun toPricePerKg(): Double? = perKg.toDecimalOrNull()?.takeIf { it >= 0 }

    companion object {
        fun from(pricePerKg: Double?) = PriceForm(pricePerKg?.let(::formatDecimal).orEmpty())
    }
}

/**
 * The components being chosen in the Enthält dialog of the ingredient [mixId] (null while it is
 * new), with the ingredient field's [query].
 */
data class ComponentsForm(
    val mixId: Long?,
    val components: List<MixPart>,
    val query: String = "",
) {
    /** Ingredients matching [query] that can be added: not chosen yet, not a mix, not the mix itself. */
    fun suggestions(ingredients: List<Ingredient>, contents: MixContents): List<Ingredient> {
        val chosen = components.mapNotNullTo(mutableSetOf()) { it.id }
        val candidates = ingredients.filter { it.id !in chosen && Mixes.canContain(mixId, it.id, contents) }
        return IngredientSuggestions.match(query, candidates)
    }

    /** The name a new component would be created with; null when blank or already an ingredient or component. */
    fun newName(ingredients: List<Ingredient>): String? {
        val name = query.trim().takeIf { it.isNotEmpty() } ?: return null
        val taken = ingredients.map { it.name } + components.map { it.name }
        return name.takeIf { taken.none { it.trim().equals(name, ignoreCase = true) } }
    }

    fun withQuery(query: String) = copy(query = query)

    /** Adds [ingredient] and clears the field. */
    fun with(ingredient: Ingredient) = copy(components = components + MixPart(ingredient.id, ingredient.name), query = "")

    /** Adds a component to be created by [name] and clears the field. */
    fun withNew(name: String) = copy(components = components + MixPart(null, name), query = "")

    fun without(part: MixPart) = copy(components = components - part)
}

/**
 * Raw input of the dialog for one unit row: [grams] is the weight of one [unit], or of a package
 * holding [count] of them when [inPackage] ("750 g ÷ 20").
 */
data class UnitForm(
    val unit: PieceUnit? = null,
    val inPackage: Boolean = false,
    val grams: String = "",
    val count: String = "",
) {
    val gramsInvalid: Boolean get() = grams.toPositiveDecimalOrNull() == null

    val countInvalid: Boolean get() = inPackage && count.toPositiveWholeOrNull() == null

    val isValid: Boolean get() = unit != null && !gramsInvalid && !countInvalid

    /** The row, or null while invalid. */
    fun toUnit(): IngredientUnit? {
        val unit = unit ?: return null
        val grams = grams.toPositiveDecimalOrNull() ?: return null
        return if (inPackage) {
            IngredientUnit.ofPackage(unit, grams, count.toPositiveWholeOrNull() ?: return null)
        } else {
            IngredientUnit.perUnit(unit, grams)
        }
    }

    companion object {
        /** Reopens [row] in the mode it was entered in; a new row starts per unit. */
        fun from(row: IngredientUnit?) = when {
            row == null -> UnitForm()
            row.isPackage -> UnitForm(row.unit, inPackage = true, formatDecimal(row.packageGrams!!), row.packageCount.toString())
            else -> UnitForm(row.unit, inPackage = false, formatDecimal(row.gramsPerUnit))
        }

        /**
         * A new row of [unit]; with the ingredient's [packageGrams] known, it starts as that package
         * so only the count is left ("400 g ÷ __").
         */
        fun forNew(unit: PieceUnit, packageGrams: Double?) =
            if (packageGrams == null) UnitForm(unit) else UnitForm(unit, inPackage = true, grams = formatDecimal(packageGrams))
    }
}

data class IngredientFormErrors(
    val name: Boolean,
    val nutrients: Set<Nutrient>,
) {
    val hasAny: Boolean get() = name || nutrients.isNotEmpty()
}

/** Formats with the decimal separator of [locale] and without trailing zeros, e.g. 0.40 -> "0,4" (German). */
fun formatDecimal(value: Double, locale: Locale = Locale.getDefault()): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        .replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)

internal fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

internal fun String.toPositiveDecimalOrNull(): Double? = toDecimalOrNull()?.takeIf { it > 0 }

private fun String.toPositiveWholeOrNull(): Int? = trim().toIntOrNull()?.takeIf { it > 0 }
