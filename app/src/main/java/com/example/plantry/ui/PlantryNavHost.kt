package com.example.plantry.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.example.plantry.data.SortView
import com.example.plantry.ui.cooklog.CookingScreen
import com.example.plantry.ui.cooklog.CookingViewModel
import com.example.plantry.ui.cooklog.JustPlanned
import com.example.plantry.ui.ingredient.IngredientDetailScreen
import com.example.plantry.ui.ingredient.IngredientDetailViewModel
import com.example.plantry.ui.ingredient.IngredientListScreen
import com.example.plantry.ui.ingredient.IngredientListViewModel
import com.example.plantry.ui.ingredient.IngredientSortScreen
import com.example.plantry.ui.ingredient.IngredientSortViewModel
import com.example.plantry.ui.ingredient.UsdaSearchScreen
import com.example.plantry.ui.ingredient.UsdaSearchViewModel
import com.example.plantry.ui.recipe.RecipeDetailScreen
import com.example.plantry.ui.recipe.RecipeDetailViewModel
import com.example.plantry.ui.recipe.RecipeEditScreen
import com.example.plantry.ui.recipe.RecipeEditViewModel
import com.example.plantry.ui.recipe.RecipeListScreen
import com.example.plantry.ui.recipe.RecipeListViewModel
import com.example.plantry.ui.settings.ApiKeyDialog
import com.example.plantry.ui.settings.BackupViewModel
import com.example.plantry.ui.settings.SettingsScreen
import com.example.plantry.ui.settings.SettingsViewModel
import com.example.plantry.ui.suggest.SuggestionsScreen
import com.example.plantry.ui.suggest.SuggestionsViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object CookingRoute

@Serializable
object SuggestionsRoute

/** Saved-state keys with which the suggestions hand a planned recipe back to Kochen. */
private const val JUST_PLANNED_ID = "justPlannedId"
private const val JUST_PLANNED_TITLE = "justPlannedTitle"

/** Saved-state key set on the ingredient list when its tab is opened: back to the default filters. */
private const val RESET_INGREDIENT_FILTERS = "resetIngredientFilters"

/** With [ingredientId], the list opens filtered by that ingredient. */
@Serializable
data class RecipeListRoute(val ingredientId: Long? = null)

@Serializable
data class RecipeDetailRoute(val recipeId: Long)

/** A null [recipeId] creates a new recipe. */
@Serializable
data class RecipeEditRoute(val recipeId: Long? = null)

@Serializable
object IngredientListRoute

@Serializable
data class IngredientSortRoute(val view: SortView)

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
    COOKING(CookingRoute, R.string.nav_cooking, Icons.Filled.Restaurant),
    RECIPES(RecipeListRoute(), R.string.nav_recipes, Icons.AutoMirrored.Filled.MenuBook),
    INGREDIENTS(IngredientListRoute, R.string.nav_ingredients, Icons.Filled.Kitchen),
    SETTINGS(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun PlantryNavHost(openRecipeId: Long? = null) {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as PlantryApplication
    val recipeRepository = app.recipeRepository
    val ingredientRepository = app.ingredientRepository
    val settingsRepository = app.settingsRepository
    val cookLogRepository = app.cookLogRepository
    // Hoisted here: the suggestions screen is left right after planning.
    val askForNotifications = rememberFirstPlanReminderOffer(settingsRepository)

    val currentEntry = navController.currentBackStackEntryAsState().value
    val currentDestination = currentEntry?.destination
    // The recipe list filtered from the ingredient list is a sub-screen: back arrow, no bottom bar.
    val filteredRecipeList = currentDestination?.hasRoute(RecipeListRoute::class) == true &&
        currentEntry.toRoute<RecipeListRoute>().ingredientId != null
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { top ->
        currentDestination?.hasRoute(top.route::class) == true
    }?.takeUnless { filteredRecipeList }

    // Checked on every resume; "Später" hides it until the app is restarted.
    val backupReminder by app.backupRepository.reminder.collectAsStateWithLifecycle()
    var backupReminderDismissed by rememberSaveable { mutableStateOf(false) }
    val showBackupReminder = backupReminder && !backupReminderDismissed && currentTopLevel != null
    val scope = rememberCoroutineScope()

    val unreviewedCount by remember {
        ingredientRepository.observeIngredients().map { all -> all.count { !it.reviewed } }
    }.collectAsStateWithLifecycle(0)
    // A notification's recipe, on top of Kochen; if it is gone by now, Kochen stays.
    LaunchedEffect(openRecipeId) {
        if (openRecipeId != null && recipeRepository.getRecipe(openRecipeId) != null) {
            navController.navigate(RecipeDetailRoute(openRecipeId))
        }
    }
    LifecycleResumeEffect(Unit) {
        scope.launch { app.backupRepository.refreshReminder() }
        onPauseOrDispose {}
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
                                // Not on a detail round trip, which doesn't go through the tab.
                                if (top == TopLevelDestination.INGREDIENTS && currentTopLevel != top) {
                                    navController.getBackStackEntry<IngredientListRoute>()
                                        .savedStateHandle[RESET_INGREDIENT_FILTERS] = true
                                }
                            },
                            icon = {
                                if (top == TopLevelDestination.INGREDIENTS && unreviewedCount > 0) {
                                    BadgedBox(badge = { Badge { Text(unreviewedCount.toString()) } }) {
                                        Icon(top.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(top.icon, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(top.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
            if (showBackupReminder) {
                BackupReminderBanner(
                    onExport = {
                        navController.navigate(SettingsRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onDismiss = { backupReminderDismissed = true },
                )
            }
            NavHost(
                navController,
                startDestination = CookingRoute,
                // The banner already took the status bar.
                modifier = if (showBackupReminder) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier,
            ) {
                composable<CookingRoute> { entry ->
                    val handle = entry.savedStateHandle
                    val justPlannedId by handle.getStateFlow<Long?>(JUST_PLANNED_ID, null).collectAsStateWithLifecycle()
                    val justPlannedTitle by handle.getStateFlow(JUST_PLANNED_TITLE, "").collectAsStateWithLifecycle()
                    CookingScreen(
                        viewModel = viewModel {
                            CookingViewModel(cookLogRepository, app.plannedRepository, recipeRepository, ingredientRepository, settingsRepository)
                        },
                        onRecipeClick = { navController.navigate(RecipeDetailRoute(it)) },
                        onSuggest = { navController.navigate(SuggestionsRoute) },
                        justPlanned = justPlannedId?.let { JustPlanned(it, justPlannedTitle) },
                        onJustPlannedShown = {
                            // Reset, not removed: removing would leave the collected flows on the old value.
                            handle[JUST_PLANNED_ID] = null
                            handle[JUST_PLANNED_TITLE] = ""
                        },
                    )
                }
                composable<SuggestionsRoute> {
                    SuggestionsScreen(
                        viewModel = viewModel {
                            SuggestionsViewModel(app.recipeSuggester, app.plannedRepository, recipeRepository, ingredientRepository)
                        },
                        onBack = { navController.popBackStack() },
                        onPlanned = { recipeId, title ->
                            askForNotifications()
                            navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                                // The title first: Kochen reacts to the id.
                                handle[JUST_PLANNED_TITLE] = title
                                handle[JUST_PLANNED_ID] = recipeId
                            }
                            navController.popBackStack()
                        },
                    )
                }
                composable<RecipeListRoute> { entry ->
                    val ingredientId = entry.toRoute<RecipeListRoute>().ingredientId
                    RecipeListScreen(
                        viewModel = viewModel {
                            RecipeListViewModel(recipeRepository, ingredientRepository, cookLogRepository, ingredientId)
                        },
                        onRecipeClick = { navController.navigate(RecipeDetailRoute(it)) },
                        onAddRecipe = { navController.navigate(RecipeEditRoute()) },
                        onBack = if (ingredientId != null) ({ navController.popBackStack() }) else null,
                    )
                }
                composable<RecipeDetailRoute> { entry ->
                    val recipeId = entry.toRoute<RecipeDetailRoute>().recipeId
                    RecipeDetailScreen(
                        viewModel = viewModel {
                            RecipeDetailViewModel(
                                recipeId,
                                recipeRepository,
                                ingredientRepository,
                                cookLogRepository,
                                app.plannedRepository,
                                app.recipePhotoRepository,
                            )
                        },
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(RecipeEditRoute(recipeId)) },
                        onPlanned = askForNotifications,
                    )
                }
                composable<RecipeEditRoute> { entry ->
                    val route = entry.toRoute<RecipeEditRoute>()
                    RecipeEditScreen(
                        viewModel = viewModel {
                            RecipeEditViewModel(
                                route.recipeId,
                                recipeRepository,
                                ingredientRepository,
                                app.recipePhotoRepository,
                                app.recipeScanner,
                                app.newIngredientFinder,
                                app.loadUsdaCatalog,
                                settingsRepository,
                                app.bookSession,
                                app.photoCompressor::compress,
                            )
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<IngredientListRoute> { entry ->
                    val viewModel = viewModel { IngredientListViewModel(ingredientRepository, recipeRepository) }
                    val handle = entry.savedStateHandle
                    val resetFilters by handle.getStateFlow(RESET_INGREDIENT_FILTERS, false).collectAsStateWithLifecycle()
                    LaunchedEffect(resetFilters) {
                        if (resetFilters) {
                            viewModel.resetFilters()
                            handle[RESET_INGREDIENT_FILTERS] = false
                        }
                    }
                    IngredientListScreen(
                        viewModel = viewModel,
                        onIngredientClick = { navController.navigate(IngredientDetailRoute(it)) },
                        onRecipesClick = { navController.navigate(RecipeListRoute(ingredientId = it)) },
                        onAddIngredient = { navController.navigate(UsdaSearchRoute) },
                        onOpenSort = { navController.navigate(IngredientSortRoute(it)) },
                    )
                }
                composable<IngredientSortRoute> { entry ->
                    val view = entry.toRoute<IngredientSortRoute>().view
                    IngredientSortScreen(
                        viewModel = viewModel { IngredientSortViewModel(ingredientRepository, view) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<UsdaSearchRoute> {
                    UsdaSearchScreen(
                        viewModel = viewModel { UsdaSearchViewModel({ app.usdaCatalog }, ingredientRepository, app.productLookup) },
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
                        viewModel = viewModel { IngredientDetailViewModel(ingredientId, ingredientRepository, app.productLookup) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        viewModel = viewModel { SettingsViewModel(settingsRepository, app.connectionTester) },
                        backupViewModel = viewModel { BackupViewModel(app.backupRepository, app.contentResolver) },
                    )
                }
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

@Composable
private fun BackupReminderBanner(onExport: () -> Unit, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
        ) {
            Text(stringResource(R.string.backup_reminder), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_reminder_later)) }
                TextButton(onClick = onExport) { Text(stringResource(R.string.backup_export)) }
            }
        }
    }
}
