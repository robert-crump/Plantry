package com.example.plantry.data

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** A recipe line's amount worked out, see [LineAmount.resolve]. */
data class ResolvedAmount(
    /** Null when the ingredient lacks the line's unit. */
    val grams: Double?,
    /** How [grams] come about, e.g. "2 × 37,5 g = 75 g" or "200 g"; null without [grams]. */
    val calculation: String?,
    /** The line as shown: its wording if it has one, else e.g. "2 Scheiben Vollkorntoast". */
    val text: String,
)

/**
 * Works out a recipe line from its amount, unit and the ingredient's units: its grams (never
 * stored), how they come about and the line as German text. ml count as g.
 */
object LineAmount {

    /** Null for a unit the ingredient lacks, i.e. without [gramsPerUnit]. */
    fun grams(amount: Double, unit: LineUnit, gramsPerUnit: Double?): Double? = when (unit) {
        LineUnit.Grams, LineUnit.Milliliters -> amount
        is LineUnit.Of -> gramsPerUnit?.let { amount * it }
    }

    /** The grams per [unit] among the ingredient's [units]; null for g and ml and for a unit it lacks. */
    fun gramsPerUnit(unit: LineUnit, units: List<IngredientUnit>): Double? =
        (unit as? LineUnit.Of)?.let { line -> units.find { it.unit == line.unit }?.gramsPerUnit }

    fun resolve(amount: Double, unit: LineUnit, gramsPerUnit: Double?, ingredientName: String, wording: String? = null) =
        ResolvedAmount(
            grams = grams(amount, unit, gramsPerUnit),
            calculation = calculation(amount, unit, gramsPerUnit),
            text = wording?.trim()?.takeIf { it.isNotEmpty() } ?: text(amount, unit, ingredientName),
        )

    fun resolve(amount: Double, unit: LineUnit, units: List<IngredientUnit>, ingredientName: String, wording: String? = null) =
        resolve(amount, unit, gramsPerUnit(unit, units), ingredientName, wording)

    /** "2 × 37,5 g = 75 g" for a counted unit, the grams for g and ml; null for a unit the ingredient lacks. */
    fun calculation(amount: Double, unit: LineUnit, gramsPerUnit: Double?): String? = when (unit) {
        LineUnit.Grams, LineUnit.Milliliters -> "${decimal(amount, 2)} g"
        is LineUnit.Of -> gramsPerUnit?.let { "${count(amount)} × ${decimal(it)} g = ${decimal(amount * it)} g" }
    }

    /** E.g. "2 Scheiben Vollkorntoast", "½ Dose Kichererbsen", "200 ml Hafermilch". */
    fun text(amount: Double, unit: LineUnit, ingredientName: String): String =
        "${amount(amount, unit)} ${word(unit, amount)} ${ingredientName.trim()}".trim()

    /** g and ml as decimals ("37,5"), counted units with fractions ("1½"). */
    fun amount(amount: Double, unit: LineUnit): String = if (unit is LineUnit.Of) count(amount) else decimal(amount, 2)

    /** "g", "ml", or the unit's singular up to 1 and its plural above ("1 Scheibe", "½ Scheibe", "2 Scheiben"). */
    fun word(unit: LineUnit, amount: Double = 1.0): String = when (unit) {
        LineUnit.Grams -> "g"
        LineUnit.Milliliters -> "ml"
        is LineUnit.Of -> if (amount <= 1.0) unit.unit.singular else unit.unit.plural
    }

    /** Whole numbers, and halves, thirds and quarters as "½" or "1¾"; anything else as a decimal. */
    private fun count(value: Double): String {
        val rounded = value.roundToLong()
        if (abs(value - rounded) < EPSILON) return rounded.toString()
        val whole = floor(value).toLong()
        val fraction = FRACTIONS.entries.find { abs(value - whole - it.key) < EPSILON }?.value ?: return decimal(value, 2)
        return if (whole == 0L) fraction else "$whole$fraction"
    }

    /** With a decimal comma and at most [digits] decimals, e.g. "37,5" or "100". */
    private fun decimal(value: Double, digits: Int = 1): String =
        BigDecimal.valueOf(value).setScale(digits, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

    private const val EPSILON = 0.001

    private val FRACTIONS = mapOf(1.0 / 4 to "¼", 1.0 / 3 to "⅓", 1.0 / 2 to "½", 2.0 / 3 to "⅔", 3.0 / 4 to "¾")
}
