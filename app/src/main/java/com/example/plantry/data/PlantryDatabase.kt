package com.example.plantry.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Recipe::class], version = 1)
abstract class PlantryDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao

    companion object {
        fun create(context: Context): PlantryDatabase =
            Room.databaseBuilder(context, PlantryDatabase::class.java, "plantry.db").build()
    }
}
