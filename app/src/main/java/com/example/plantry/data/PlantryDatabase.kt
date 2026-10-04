package com.example.plantry.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.DeleteTable
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import com.example.plantry.data.backup.BackupDao

@Database(
    entities = [Recipe::class, Ingredient::class, RecipeIngredient::class, CookLog::class, IngredientAlias::class],
    version = 9,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9, spec = PlantryDatabase.DropWeekPlan::class),
    ],
)
@TypeConverters(Converters::class)
abstract class PlantryDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao

    abstract fun ingredientDao(): IngredientDao

    abstract fun cookLogDao(): CookLogDao

    abstract fun backupDao(): BackupDao

    /** The week plan and the shopping list are gone; their data is not carried over. */
    @DeleteTable(tableName = "week_plan_slots")
    @DeleteTable(tableName = "shopping_ticks")
    class DropWeekPlan : AutoMigrationSpec

    companion object {
        fun create(context: Context): PlantryDatabase =
            Room.databaseBuilder(context, PlantryDatabase::class.java, "plantry.db").build()
    }
}
