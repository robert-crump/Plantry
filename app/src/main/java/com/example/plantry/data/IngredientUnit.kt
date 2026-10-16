package com.example.plantry.data

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * One unit an ingredient can be measured in besides g and ml, e.g. 1 Scheibe Vollkorntoast =
 * 37.5 g. Recipe lines in this unit get their grams from [gramsPerUnit] on every read, so changing
 * it changes every recipe that uses it.
 */
@Entity(
    tableName = "ingredient_units",
    primaryKeys = ["ingredientId", "unit"],
    foreignKeys = [
        ForeignKey(
            entity = Ingredient::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class IngredientUnit(
    val ingredientId: Long,
    val unit: PieceUnit,
    val gramsPerUnit: Double,
    /** The package it was entered from, e.g. 750 g with 20 Scheiben; both null when entered per unit. */
    val packageGrams: Double? = null,
    val packageCount: Int? = null,
) {
    /** Whether it was entered as a package ("750 g ÷ 20") rather than per unit ("1 Zehe = 4 g"). */
    val isPackage: Boolean get() = packageGrams != null && packageCount != null

    companion object {
        /** One [unit] weighs [grams]; null unless [grams] > 0. */
        fun perUnit(unit: PieceUnit, grams: Double, ingredientId: Long = 0): IngredientUnit? =
            if (grams > 0) IngredientUnit(ingredientId, unit, grams) else null

        /** A package of [grams] holds [count] of [unit], kept as entered; null unless both are positive. */
        fun ofPackage(unit: PieceUnit, grams: Double, count: Int, ingredientId: Long = 0): IngredientUnit? =
            if (grams > 0 && count >= 1) IngredientUnit(ingredientId, unit, grams / count, grams, count) else null
    }
}

/**
 * The unit of a recipe line: grams, millilitres (which count as grams) or one of the ingredient's
 * [IngredientUnit]s.
 */
sealed interface LineUnit {
    /** How the unit is stored and written to backups: "G", "ML" or the [PieceUnit]'s name. */
    val code: String

    data object Grams : LineUnit {
        override val code = "G"
    }

    data object Milliliters : LineUnit {
        override val code = "ML"
    }

    data class Of(val unit: PieceUnit) : LineUnit {
        override val code: String get() = unit.name
    }

    companion object {
        /** g and ml, which every ingredient has. */
        val ALWAYS: List<LineUnit> = listOf(Grams, Milliliters)

        fun fromCode(code: String): LineUnit? = when (code) {
            Grams.code -> Grams
            Milliliters.code -> Milliliters
            else -> PieceUnit.entries.find { it.name == code }?.let(::Of)
        }
    }
}
