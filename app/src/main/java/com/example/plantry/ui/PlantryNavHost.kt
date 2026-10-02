package com.example.plantry.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import kotlinx.serialization.Serializable

@Serializable
object RecipeListRoute

@Serializable
data class RecipeDetailRoute(val recipeId: Long)

/** A null [recipeId] creates a new recipe. */
@Serializable
data class RecipeEditRoute(val recipeId: Long? = null)

@Serializable
object IngredientListRoute

@Serializable
object UsdaSearchRoute

@Serializable
data class IngredientDetailRoute(val ingredientId: Long)

private enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    RECIPES(RecipeListRoute, R.string.nav_recipes, Icons.AutoMirrored.Filled.MenuBook),
    INGREDIENTS(IngredientListRoute, R.string.nav_ingredients, Icons.Filled.Kitchen),
}

@Composable
fun PlantryNavHost() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as PlantryApplication
    val recipeRepository = app.recipeRepository
    val ingredientRepository = app.ingredientRepository

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
            startDestination = RecipeListRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<RecipeListRoute> {
                RecipeListScreen(
                    viewModel = viewModel { RecipeListViewModel(recipeRepository) },
                    onRecipeClick = { navController.navigate(RecipeDetailRoute(it)) },
                    onAddRecipe = { navController.navigate(RecipeEditRoute()) },
                )
            }
            composable<RecipeDetailRoute> { entry ->
                val recipeId = entry.toRoute<RecipeDetailRoute>().recipeId
                RecipeDetailScreen(
                    viewModel = viewModel { RecipeDetailViewModel(recipeId, recipeRepository) },
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(RecipeEditRoute(recipeId)) },
                )
            }
            composable<RecipeEditRoute> { entry ->
                val recipeId = entry.toRoute<RecipeEditRoute>().recipeId
                RecipeEditScreen(
                    viewModel = viewModel { RecipeEditViewModel(recipeId, recipeRepository) },
                    onBack = { navController.popBackStack() },
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
        }
    }
}
