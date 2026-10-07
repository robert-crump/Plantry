package com.example.plantry.data

/** One filter chip's value; chips sharing a non-null [group] exclude each other. */
interface FilterChoice {
    val group: Any?
}

/** The filter chips that are on, in the order they were switched on. */
data class ChipSelection<T : FilterChoice>(val active: List<T> = emptyList()) {

    /**
     * Switches [chip] off, or on: in the slot of the active chip of its group it replaces, else at
     * the end.
     */
    fun toggle(chip: T): ChipSelection<T> {
        if (chip in active) return ChipSelection(active - chip)
        val rival = chip.group?.let { group -> active.indexOfFirst { it.group == group } } ?: -1
        return ChipSelection(if (rival >= 0) active.toMutableList().apply { set(rival, chip) } else active + chip)
    }

    /** The active chips first, then the rest of [all] in their home order. */
    fun arranged(all: List<T>): List<T> = active + all.filterNot { it in active }
}
