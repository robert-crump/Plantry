package com.example.plantry.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One plant of a cooking log entry as it was when logged, mixes already resolved to their
 * components; plants worth no points are left out. Weekly plant points count these, so editing
 * or deleting a recipe or changing an ingredient's points later doesn't rewrite past weeks.
 * Deleting the entry deletes its plants; deleting the ingredient keeps the row with a null
 * [ingredientId].
 */
@Entity(
    tableName = "cook_log_plants",
    foreignKeys = [
        ForeignKey(entity = CookLog::class, parentColumns = ["id"], childColumns = ["cookLogId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Ingredient::class, parentColumns = ["id"], childColumns = ["ingredientId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("cookLogId"), Index("ingredientId")],
)
data class CookLogPlant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 0 until the entry is stored. */
    val cookLogId: Long = 0,
    /** Null once the ingredient was deleted. */
    val ingredientId: Long?,
    /** The ingredient's name when it was logged. */
    val name: String,
    /** The ingredient's [PlantPoints] value when it was logged. */
    val plantPoints: Double,
) {
    companion object {
        /** The plants of a recipe with the lines [ingredientIds]; ids missing from [ingredients] are skipped. */
        fun of(ingredientIds: Iterable<Long>, ingredients: Map<Long, Ingredient>, contents: MixContents): List<CookLogPlant> =
            Mixes.plants(ingredientIds.filter { it in ingredients }, contents)
                .mapNotNull { ingredients[it] }
                .filter { it.plantPoints.value > 0 }
                .sortedBy { it.id }
                .map { CookLogPlant(ingredientId = it.id, name = it.name, plantPoints = it.plantPoints.value) }

        /**
         * Fills `cook_log_plants` for every entry of an existing recipe, from the recipe as it is now,
         * the way [of] does; entries of deleted recipes get none. Run by the migration that added
         * the table.
         */
        const val BACKFILL_SQL = """
            INSERT INTO cook_log_plants (cookLogId, ingredientId, name, plantPoints)
            SELECT DISTINCT c.id, p.id, p.name, CASE p.plantPoints WHEN 'ONE' THEN 1.0 ELSE 0.25 END
            FROM cook_log c
            JOIN recipe_ingredients l ON l.recipeId = c.recipeId
            JOIN ingredients p ON p.id IN (SELECT componentId FROM mix_components WHERE mixId = l.ingredientId)
                OR (p.id = l.ingredientId AND NOT EXISTS (SELECT 1 FROM mix_components WHERE mixId = l.ingredientId))
            WHERE p.plantPoints IN ('ONE', 'QUARTER')
            ORDER BY c.id, p.id
        """
    }
}

/**
 * Plant points of the distinct plants among [plants], e.g. a week's entries: the same plant matches
 * by ingredient, by name once the ingredient is gone. A plant logged with different points counts
 * its highest.
 */
fun distinctPlantPoints(plants: Iterable<CookLogPlant>): Double =
    plants.groupBy { it.ingredientId ?: it.name }.values.sumOf { same -> same.maxOf { it.plantPoints } }
