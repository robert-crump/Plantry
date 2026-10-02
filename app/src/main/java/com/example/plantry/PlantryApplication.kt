package com.example.plantry

import android.app.Application
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.PlantryDatabase
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.usda.UsdaCatalog

class PlantryApplication : Application() {

    private val database by lazy { PlantryDatabase.create(this) }

    val recipeRepository: RecipeRepository by lazy { RecipeRepository(database.recipeDao()) }

    val ingredientRepository: IngredientRepository by lazy { IngredientRepository(database.ingredientDao()) }

    /** Parsed from the bundled asset on first access, so read it off the main thread. */
    val usdaCatalog: UsdaCatalog by lazy {
        assets.open(UsdaCatalog.ASSET_NAME).bufferedReader().useLines(UsdaCatalog::parse)
    }
}
