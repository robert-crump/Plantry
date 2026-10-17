package com.example.plantry.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One ingredient a mix contains ("Enthält"), e.g. Beerenmischung -> Himbeeren. Only counts for
 * plant points; the mix keeps its own nutrition. Deleting either side removes the row.
 */
@Entity(
    tableName = "mix_components",
    primaryKeys = ["mixId", "componentId"],
    foreignKeys = [
        ForeignKey(entity = Ingredient::class, parentColumns = ["id"], childColumns = ["mixId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Ingredient::class, parentColumns = ["id"], childColumns = ["componentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("componentId")],
)
data class MixComponent(val mixId: Long, val componentId: Long)

/** The components of every mix, by mix id; ingredients without components are left out. */
typealias MixContents = Map<Long, List<Long>>

/** Rules for mixes and the plant points they count, see [MixComponent]. One level only: a component is never a mix. */
object Mixes {

    /** [components] grouped by mix. */
    fun contents(components: List<MixComponent>): MixContents = components.groupBy({ it.mixId }, { it.componentId })

    /** The distinct plants [ingredientIds] stand for: a mix by its components, anything else by itself. */
    fun plants(ingredientIds: Iterable<Long>, contents: MixContents): Set<Long> =
        ingredientIds.flatMapTo(mutableSetOf()) { contents[it]?.takeIf(List<Long>::isNotEmpty) ?: listOf(it) }

    /** Sum of [PlantPoints] over the distinct [plants] of [ingredientIds]; ids missing from [ingredients] count 0. */
    fun plantPoints(ingredientIds: Iterable<Long>, ingredients: Map<Long, Ingredient>, contents: MixContents): Double =
        plants(ingredientIds, contents).sumOf { ingredients[it]?.plantPoints?.value ?: 0.0 }

    /** Whether [candidate] may be added to the mix [mixId] (null for one not saved yet): neither itself nor a mix. */
    fun canContain(mixId: Long?, candidate: Long, contents: MixContents): Boolean =
        candidate != mixId && contents[candidate].isNullOrEmpty()

    /** Whether [ingredientId] may have components: not while it is a component of another mix. */
    fun canBeMix(ingredientId: Long?, contents: MixContents): Boolean =
        ingredientId == null || contents.values.none { ingredientId in it }
}
