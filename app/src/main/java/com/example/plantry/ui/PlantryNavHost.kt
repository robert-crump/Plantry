package com.example.plantry.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.plantry.PlantryApplication
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

@Composable
fun PlantryNavHost() {
    val navController = rememberNavController()
    val repository = (LocalContext.current.applicationContext as PlantryApplication).recipeRepository

    NavHost(navController, startDestination = RecipeListRoute) {
        composable<RecipeListRoute> {
            RecipeListScreen(
                viewModel = viewModel { RecipeListViewModel(repository) },
                onRecipeClick = { navController.navigate(RecipeDetailRoute(it)) },
                onAddRecipe = { navController.navigate(RecipeEditRoute()) },
            )
        }
        composable<RecipeDetailRoute> { entry ->
            val recipeId = entry.toRoute<RecipeDetailRoute>().recipeId
            RecipeDetailScreen(
                viewModel = viewModel { RecipeDetailViewModel(recipeId, repository) },
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(RecipeEditRoute(recipeId)) },
            )
        }
        composable<RecipeEditRoute> { entry ->
            val recipeId = entry.toRoute<RecipeEditRoute>().recipeId
            RecipeEditScreen(
                viewModel = viewModel { RecipeEditViewModel(recipeId, repository) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
