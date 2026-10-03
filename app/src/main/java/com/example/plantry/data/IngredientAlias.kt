package com.example.plantry.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A recipe-line wording the user once confirmed for an ingredient, e.g. "kichererbsen abgetropft"
 * -> Kichererbsen (Dose). Later scans and manual entry match it directly. A wording points to one
 * ingredient; confirming it for another one replaces the alias.
 */
@Entity(
    tableName = "ingredient_aliases",
    foreignKeys = [
        ForeignKey(
            entity = Ingredient::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("ingredientId")],
)
data class IngredientAlias(
    /** Normalized, see [IngredientAliases.normalize]. */
    @PrimaryKey val wording: String,
    val ingredientId: Long,
)

object IngredientAliases {

    private val SEPARATOR = Regex("[\\s,;.:()/-]+")

    /** Lower case, punctuation and repeated spaces dropped: "Kichererbsen, abgetropft" -> "kichererbsen abgetropft". */
    fun normalize(wording: String): String =
        wording.lowercase().split(SEPARATOR).filter { it.isNotEmpty() }.joinToString(" ")

    /** The ingredient [wording] was confirmed for, by normalized wording in [aliases]. */
    fun match(wording: String, aliases: Map<String, Long>): Long? =
        normalize(wording).takeIf { it.isNotEmpty() }?.let(aliases::get)

    /** [learned] (wording -> ingredient id) as aliases; blank wordings are skipped, the last one wins. */
    fun of(learned: List<Pair<String, Long>>): List<IngredientAlias> = learned
        .mapNotNull { (wording, id) -> normalize(wording).takeIf { it.isNotEmpty() }?.let { it to id } }
        .toMap()
        .map { (wording, id) -> IngredientAlias(wording, id) }
}
