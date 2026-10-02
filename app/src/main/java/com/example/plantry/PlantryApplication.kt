package com.example.plantry

import android.app.Application
import com.example.plantry.data.PlantryDatabase
import com.example.plantry.data.RecipeRepository

class PlantryApplication : Application() {

    val recipeRepository: RecipeRepository by lazy {
        RecipeRepository(PlantryDatabase.create(this).recipeDao())
    }
}
