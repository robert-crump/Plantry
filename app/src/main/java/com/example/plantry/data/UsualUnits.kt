package com.example.plantry.data

/** The unit each ingredient is usually measured in across the saved recipes, proposed for a new line. */
object UsualUnits {

    /** By ingredient id: the unit of most of its lines, on a tie the one of the latest line (highest id). */
    fun of(lines: List<RecipeLine>): Map<Long, LineUnit> =
        lines.groupBy { it.ingredientId }.mapValues { (_, own) ->
            own.groupBy { it.unit }
                .maxWith(compareBy({ it.value.size }, { it.value.maxOf(RecipeLine::id) }))
                .key
        }
}
