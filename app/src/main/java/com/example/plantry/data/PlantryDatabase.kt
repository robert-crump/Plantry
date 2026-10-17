package com.example.plantry.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.plantry.data.backup.BackupDao

/**
 * Restarted at version 1 with structured recipe lines: an older database is dropped, and the app
 * starts empty until a backup or the seed is imported. Every later schema change needs a migration.
 */
@Database(
    entities = [
        Recipe::class, Ingredient::class, IngredientUnit::class, RecipeIngredient::class, CookLog::class,
        IngredientAlias::class, PlannedRecipe::class, MixComponent::class,
    ],
    version = 2,
    autoMigrations = [
        // Adds the mix components ("Enthält").
        AutoMigration(from = 1, to = 2),
    ],
)
@TypeConverters(Converters::class)
abstract class PlantryDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao

    abstract fun ingredientDao(): IngredientDao

    abstract fun cookLogDao(): CookLogDao

    abstract fun plannedRecipeDao(): PlannedRecipeDao

    abstract fun backupDao(): BackupDao

    companion object {
        fun create(context: Context): PlantryDatabase =
            Room.databaseBuilder(context, PlantryDatabase::class.java, "plantry.db")
                // The schema before the restart had versions up to 20, so opening it is a downgrade.
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
