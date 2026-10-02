package com.example.plantry

import android.app.Application
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.PlantryDatabase
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.claude.AnthropicConnectionTester
import com.example.plantry.data.claude.ConnectionTester
import com.example.plantry.data.settings.KeystoreCipher
import com.example.plantry.data.settings.SettingsRepository
import com.example.plantry.data.settings.SharedPreferencesStorage
import com.example.plantry.data.usda.UsdaCatalog

class PlantryApplication : Application() {

    private val database by lazy { PlantryDatabase.create(this) }

    val recipeRepository: RecipeRepository by lazy { RecipeRepository(database.recipeDao()) }

    val ingredientRepository: IngredientRepository by lazy { IngredientRepository(database.ingredientDao()) }

    /** Parsed from the bundled asset on first access, so read it off the main thread. */
    val usdaCatalog: UsdaCatalog by lazy {
        assets.open(UsdaCatalog.ASSET_NAME).bufferedReader().useLines(UsdaCatalog::parse)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(
            SharedPreferencesStorage(getSharedPreferences(SharedPreferencesStorage.FILE_NAME, MODE_PRIVATE)),
            KeystoreCipher(),
        )
    }

    val connectionTester: ConnectionTester = AnthropicConnectionTester()
}
