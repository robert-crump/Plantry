package com.example.plantry

import android.app.Application
import com.example.plantry.data.BookSession
import com.example.plantry.data.CookLogRepository
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.NewIngredientFinder
import com.example.plantry.data.PhotoCompressor
import com.example.plantry.data.PlannedRepository
import com.example.plantry.data.PlantryDatabase
import com.example.plantry.data.RecipePhotoRepository
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.RecipeSnapshot
import com.example.plantry.data.backup.BackupRepository
import com.example.plantry.data.backup.FilePhotoStore
import com.example.plantry.data.claude.AnthropicConnectionTester
import com.example.plantry.data.claude.AnthropicIngredientProposer
import com.example.plantry.data.claude.AnthropicRecipeScanner
import com.example.plantry.data.claude.RecipeScanner
import com.example.plantry.data.planner.RecipeSuggester
import com.example.plantry.data.claude.ConnectionTester
import com.example.plantry.data.openfoodfacts.OpenFoodFactsClient
import com.example.plantry.data.openfoodfacts.ProductLookup
import com.example.plantry.data.settings.KeystoreCipher
import com.example.plantry.data.settings.SettingsRepository
import com.example.plantry.data.settings.SharedPreferencesStorage
import com.example.plantry.data.usda.UsdaCatalog
import com.example.plantry.reminder.CookReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

class PlantryApplication : Application() {

    /** Work that outlives any screen, such as keeping the reminder alarm in step. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        cookReminders.start(appScope)
    }

    private val database by lazy { PlantryDatabase.create(this) }

    val recipeRepository: RecipeRepository by lazy { RecipeRepository(database.recipeDao()) }

    val ingredientRepository: IngredientRepository by lazy { IngredientRepository(database.ingredientDao()) }

    val cookLogRepository: CookLogRepository by lazy {
        CookLogRepository(database.cookLogDao()) { RecipeSnapshot.load(it, database.recipeDao(), database.ingredientDao()) }
    }

    val plannedRepository: PlannedRepository by lazy {
        PlannedRepository(database.plannedRecipeDao(), cookLogRepository)
    }

    val cookReminders: CookReminders by lazy {
        CookReminders(this, settingsRepository, plannedRepository, recipeRepository, recipeSuggester)
    }

    val recipeSuggester: RecipeSuggester by lazy {
        RecipeSuggester(
            database.recipeDao(),
            database.ingredientDao(),
            database.cookLogDao(),
            cooldownDays = { settingsRepository.settings.value.cooldownDays },
        )
    }

    /** Parsed from the bundled asset on first access, so read it off the main thread. */
    val usdaCatalog: UsdaCatalog by lazy {
        assets.open(UsdaCatalog.ASSET_NAME).bufferedReader().useLines(UsdaCatalog::parse)
    }

    /** [usdaCatalog] off the main thread. */
    val loadUsdaCatalog: suspend () -> UsdaCatalog = { withContext(Dispatchers.Default) { usdaCatalog } }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(
            SharedPreferencesStorage(getSharedPreferences(SharedPreferencesStorage.FILE_NAME, MODE_PRIVATE)),
            KeystoreCipher(),
        )
    }

    private val photoStore by lazy { FilePhotoStore(filesDir.resolve(PHOTO_DIR)) }

    val recipePhotoRepository: RecipePhotoRepository by lazy { RecipePhotoRepository(photoStore) }

    val photoCompressor: PhotoCompressor by lazy { PhotoCompressor(contentResolver) }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(
            database.backupDao(),
            photoStore,
            settingsRepository,
            SharedPreferencesStorage(getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE)),
        )
    }

    /** The book of consecutive recipe scans. */
    val bookSession = BookSession()

    val connectionTester: ConnectionTester = AnthropicConnectionTester()

    val recipeScanner: RecipeScanner = AnthropicRecipeScanner()

    val productLookup: ProductLookup = OpenFoodFactsClient()

    val newIngredientFinder: NewIngredientFinder by lazy {
        NewIngredientFinder(AnthropicIngredientProposer(), loadUsdaCatalog)
    }

    private companion object {
        /** Recipe photos, one "<recipeId>.jpg" each. */
        const val PHOTO_DIR = "recipe_photos"
        const val BACKUP_PREFS = "backup"
    }
}
