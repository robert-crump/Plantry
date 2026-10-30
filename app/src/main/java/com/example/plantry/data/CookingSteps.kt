package com.example.plantry.data

import androidx.room.TypeConverter

/** The "Zubereitung" of a recipe: plain-text steps in order, each on one line. */
object CookingSteps {

    /** "1.", "2)", "3 ." and the like at the start of a line, as pasted numbered text has. */
    private val NUMBERING = Regex("""^\d+\s*[.)]\s*""")

    /**
     * One line is one step: lines are trimmed, leading numbering is stripped (so pasted numbered
     * text isn't numbered twice), and empty lines are dropped.
     */
    fun parse(text: String): List<String> = text.lines()
        .map { it.trim().replaceFirst(NUMBERING, "").trim() }
        .filter { it.isNotEmpty() }

    /** The text [parse] reads back as [steps]: one step per line. */
    fun text(steps: List<String>): String = steps.joinToString("\n")

    /**
     * Stores [Recipe.steps] as one text, a step per line; only on that field, so other string
     * lists, such as query results, stay as Room maps them.
     */
    class Converter {
        @TypeConverter
        fun toText(steps: List<String>): String = text(steps)

        @TypeConverter
        fun fromText(text: String): List<String> = if (text.isEmpty()) emptyList() else text.split("\n")
    }
}
