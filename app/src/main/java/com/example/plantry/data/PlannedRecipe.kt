package com.example.plantry.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow

/**
 * A recipe on Geplant: meant to be cooked on [plannedOn] (today or later when planned), not yet
 * confirmed. A recipe is planned at most once; deleting it removes the entry.
 */
@Entity(
    tableName = "planned_recipes",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlannedRecipe(
    @PrimaryKey val recipeId: Long,
    val plannedOn: LocalDate,
) {
    /** Entries whose planned day is more than [MAX_AGE_DAYS] days before [today] are gone from Geplant. */
    fun isCurrent(today: LocalDate): Boolean = ChronoUnit.DAYS.between(plannedOn, today) <= MAX_AGE_DAYS

    companion object {
        const val MAX_AGE_DAYS = 7L
    }
}

@Dao
interface PlannedRecipeDao {

    /** Planning a recipe again (e.g. to move it, or one that dropped off) replaces its date. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(planned: PlannedRecipe)

    @Query("SELECT * FROM planned_recipes WHERE recipeId = :recipeId")
    suspend fun get(recipeId: Long): PlannedRecipe?

    @Query("DELETE FROM planned_recipes WHERE recipeId = :recipeId")
    suspend fun delete(recipeId: Long)

    /** Earliest planned day first. */
    @Query("SELECT * FROM planned_recipes ORDER BY plannedOn, recipeId")
    fun observeAll(): Flow<List<PlannedRecipe>>
}
