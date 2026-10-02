package com.example.plantry.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Recipe::class, Ingredient::class, RecipeIngredient::class, CookLog::class],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
@TypeConverters(Converters::class)
abstract class PlantryDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao

    abstract fun ingredientDao(): IngredientDao

    abstract fun cookLogDao(): CookLogDao

    companion object {
        fun create(context: Context): PlantryDatabase =
            Room.databaseBuilder(context, PlantryDatabase::class.java, "plantry.db").build()
    }
}
