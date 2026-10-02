package com.example.plantry.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.plantry.PlantryApplication
import com.example.plantry.R
import com.example.plantry.ui.cooklog.CookHistoryScreen
import com.example.plantry.ui.cooklog.CookHistoryViewModel
import com.example.plantry.ui.ingredient.IngredientDetailScreen
import com.example.plantry.ui.ingredient.IngredientDetailViewModel
import com.example.plantry.ui.ingredient.IngredientListScreen
import com.example.plantry.ui.ingredient.IngredientListViewModel
import com.example.plantry.ui.ingredient.UsdaSearchScreen
import com.example.plantry.ui.ingredient.UsdaSearchViewModel
import com.example.plantry.ui.recipe.RecipeDetailScreen
import com.example.plantry.ui.recipe.RecipeDetailViewModel
import com.example.plantry.ui.recipe.RecipeEditScreen
import com.example.plantry.ui.recipe.RecipeEditViewModel
import com.example.plantry.ui.recipe.RecipeListScreen
import com.example.plantry.ui.recipe.RecipeListViewModel
import com.example.plantry.ui.settings.ApiKeyDialog
import com.example.plantry.ui.settings.SettingsScreen
import com.example.plantry.ui.settings.SettingsViewModel
import com.example.plantry.ui.week.WeekPlanScreen
import com.example.plantry.ui.week.WeekPlanViewModel
import kotlinx.serialization.Serializable

@Serializable
object WeekPlanRoute

@Serializable
object RecipeListRoute

@Serializable
data class RecipeDetailRoute(val recipeId: Long)

/** A null [recipeId] creates a new recipe. */
@Serializable
data class RecipeEditRoute(val recipeId: Long? = null)

@Serializable
object CookHistoryRoute

@Serializable
object IngredientListRoute

@Serializable
object UsdaSearchRoute

@Serializable
data class IngredientDetailRoute(val ingredientId: Long)

@Serializable
object SettingsRoute

private enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    WEEK(WeekPlanRoute, R.string.nav_week, Icons.Filled.CalendarMonth),
    RECIPES(RecipeListRoute, R.string.nav_recipes, Icons.AutoMirrored.Filled.MenuBook),
    INGREDIENTS(IngredientListRoute, R.string.nav_ingredients, Icons.Filled.Kitchen),
    SETTINGS(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun PlantryNavHost() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as PlantryApplication
    val recipeRepository = app.recipeRepository
    val ingredientRepository = app.ingredientRepository
    val settingsRepository = app.settingsRepository
    val cookLogRepository = app.cookLogRepository
    val weekPlanRepository = app.weekPlanRepository

    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { top ->
        currentDestination?.hasRoute(top.route::class) == true
    }

    Scaffold(
        // Each screen handles its own insets; this Scaffold only adds the bottom bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentTopLevel != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { top ->
                        NavigationBarItem(
                            selected = top == currentTopLevel,
                            onClick = {
                                navController.navigate(top.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(top.icon, contentDescription = null) },
                            label = { Text(stringResource(top.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController,
            startDestination = WeekPlanRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<WeekPlanRoute> {
                WeekPlanScreen(
                    viewModel = viewModel { WeekPlanViewModel(weekPlanRepository, recipeRepository) },
                    onRecipeClick = { navController.navigate(RecipeDetailRoute(it)) },
                )
            }
            composable<RecipeListRoute> {
                RecipeListScreen(
                    viewModel = viewModel { RecipeListViewModel(recipeRepository) },
                    onRecipeClick = { navController.navigate(RecipeDetailRoute(it)) },
                    onAddRecipe = { navController.navigate(RecipeEditRoute()) },
                    onOpenHistory = { navController.navigate(CookHistoryRoute) },
                )
            }
            composable<RecipeDetailRoute> { entry ->
                val recipeId = entry.toRoute<RecipeDetailRoute>().recipeId
                RecipeDetailScreen(
                    viewModel = viewModel { RecipeDetailViewModel(recipeId, recipeRepository, ingredientRepository, cookLogRepository, weekPlanRepository) },
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(RecipeEditRoute(recipeId)) },
                )
            }
            composable<RecipeEditRoute> { entry ->
                val recipeId = entry.toRoute<RecipeEditRoute>().recipeId
                RecipeEditScreen(
                    viewModel = viewModel { RecipeEditViewModel(recipeId, recipeRepository, ingredientRepository) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<CookHistoryRoute> {
                CookHistoryScreen(
                    viewModel = viewModel { CookHistoryViewModel(cookLogRepository) },
                    onBack = { navController.popBackStack() },
                    onRecipeClick = { navController.navigate(RecipeDetailRoute(it)) },
                )
            }
            composable<IngredientListRoute> {
                IngredientListScreen(
                    viewModel = viewModel { IngredientListViewModel(ingredientRepository) },
                    onIngredientClick = { navController.navigate(IngredientDetailRoute(it)) },
                    onAddIngredient = { navController.navigate(UsdaSearchRoute) },
                )
            }
            composable<UsdaSearchRoute> {
                UsdaSearchScreen(
                    viewModel = viewModel { UsdaSearchViewModel({ app.usdaCatalog }, ingredientRepository) },
                    onBack = { navController.popBackStack() },
                    onCreated = { id ->
                        navController.navigate(IngredientDetailRoute(id)) {
                            popUpTo<UsdaSearchRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<IngredientDetailRoute> { entry ->
                val ingredientId = entry.toRoute<IngredientDetailRoute>().ingredientId
                IngredientDetailScreen(
                    viewModel = viewModel { IngredientDetailViewModel(ingredientId, ingredientRepository) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(viewModel = viewModel { SettingsViewModel(settingsRepository, app.connectionTester) })
            }
        }
    }

    // Asked on every launch while no key is stored; "Später" skips it until the next launch.
    val settings by settingsRepository.settings.collectAsStateWithLifecycle()
    var keyPromptDismissed by rememberSaveable { mutableStateOf(false) }
    if (!settings.hasApiKey && !keyPromptDismissed) {
        ApiKeyDialog(
            onSave = settingsRepository::setApiKey,
            onDismiss = { keyPromptDismissed = true },
            dismissLabel = R.string.settings_api_key_later,
            message = R.string.settings_api_key_first_launch_message,
        )
    }
}
