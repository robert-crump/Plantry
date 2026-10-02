package com.example.plantry.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One time a recipe was cooked; the only input for the planner's cooldown. */
@Entity(
    tableName = "cook_log",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class CookLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val cookedOn: LocalDate,
)

/** A [CookLog] entry with the title of its recipe, for the global history. */
data class CookLogEntry(
    @Embedded val log: CookLog,
    val recipeTitle: String,
)

/** How often and how recently a recipe was cooked. */
data class CookingStats(
    val timesCooked: Int,
    /** Null if the recipe was never cooked, which the planner treats as maximally overdue. */
    val daysSinceLastCooked: Long?,
) {
    companion object {
        fun from(cookedOn: List<LocalDate>, today: LocalDate) = CookingStats(
            timesCooked = cookedOn.size,
            daysSinceLastCooked = daysSinceLastCooked(cookedOn, today),
        )

        /**
         * Whole days between the latest of [cookedOn] and [today]: 0 if cooked today, 1 if
         * yesterday. Null if never cooked. Dates after [today] count as today.
         */
        fun daysSinceLastCooked(cookedOn: List<LocalDate>, today: LocalDate): Long? {
            val last = cookedOn.maxOrNull() ?: return null
            return ChronoUnit.DAYS.between(last, today).coerceAtLeast(0)
        }
    }
}
