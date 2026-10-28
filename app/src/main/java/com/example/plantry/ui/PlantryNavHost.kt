package com.example.plantry.ui

import androidx.annotation.StringRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.plantry.PlantryApplication
import com.example.plantry.R
import com.example.plantry.data.LabelSource
import com.example.plantry.data.NewIngredientSeed
import com.example.plantry.data.Nutrient
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import com.example.plantry.data.IngredientUnit
import com.example.plantry.ui.cooklog.CookingScreen
import com.example.plantry.ui.cooklog.CookingViewModel
import com.example.plantry.ui.cooklog.JustLogged
import com.example.plantry.ui.cooklog.JustPlanned
import com.example.plantry.ui.cooklog.RecipeSearchMode
import com.example.plantry.ui.cooklog.RecipeSearchScreen
import com.example.plantry.ui.cooklog.RecipeSearchViewModel
import com.example.plantry.ui.ingredient.IngredientDetailScreen
import com.example.plantry.ui.ingredient.IngredientDetailViewModel
import com.example.plantry.ui.ingredient.IngredientListScreen
import com.example.plantry.ui.ingredient.IngredientListViewModel
import com.example.plantry.ui.ingredient.NewIngredientScreen
import com.example.plantry.ui.ingredient.NewIngredientViewModel
import com.example.plantry.ui.recipe.RecipeDetailScreen
import com.example.plantry.ui.recipe.RecipeDetailViewModel
import com.example.plantry.ui.recipe.RecipeEditScreen
import com.example.plantry.ui.recipe.RecipeEditViewModel
import com.example.plantry.ui.recipe.RecipeListScreen
import com.example.plantry.ui.recipe.RecipeListViewModel
import com.example.plantry.ui.settings.BackupViewModel
import com.example.plantry.ui.settings.AboutScreen
import com.example.plantry.ui.settings.SettingsScreen
import com.example.plantry.ui.settings.SettingsViewModel
import com.example.plantry.ui.stats.StatsScreen
import com.example.plantry.ui.stats.StatsViewModel
import com.example.plantry.ui.suggest.SuggestionsScreen
import com.example.plantry.ui.suggest.SuggestionsViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
object CookingRoute

/**
 * [skipRecipeId]: left out of the first round, the recipe a notification's "Anderer Vorschlag" came
 * from; [proposalEventId] is that proposal's log row, which planning for today from here upgrades.
 */
@Serializable
data class SuggestionsRoute(val skipRecipeId: Long? = null, val proposalEventId: Long? = null)

/**
 * Saved-state keys with which "Vorschlag" and "Planen" hand the recipe and day (epoch day) to plan
 * back to the screen they were opened from, Speiseplan or Rezepte.
 */
private const val JUST_PLANNED_ID = "justPlannedId"
private const val JUST_PLANNED_TITLE = "justPlannedTitle"
private const val JUST_PLANNED_DAY = "justPlannedDay"

/** "Kocheintrag" and "Planen": a recipe to find, then the day it was cooked or is to be cooked. */
@Serializable
data class RecipeSearchRoute(val mode: RecipeSearchMode)

/** Saved-state keys with which "Kocheintrag" hands the picked recipe and day (epoch day) back to Speiseplan. */
private const val JUST_LOGGED_ID = "justLoggedId"
private const val JUST_LOGGED_DAY = "justLoggedDay"

/** Saved-state key set on the ingredient list when its tab is opened: back to the default filters. */
private const val RESET_INGREDIENT_FILTERS = "resetIngredientFilters"

/** The id of the ingredient just created from a recipe's line editor, handed back to it. */
private const val CREATED_INGREDIENT = "createdIngredient"

/** With [ingredientId], the list opens filtered by that ingredient. */
@Serializable
data class RecipeListRoute(val ingredientId: Long? = null)

/** Opened from a suggestion ([fromSuggestions]), "Planen" plans it back on Speiseplan, as the suggestion's own button does. */
@Serializable
data class RecipeDetailRoute(val recipeId: Long, val fromSuggestions: Boolean = false)

/** A null [recipeId] creates a new recipe. */
@Serializable
data class RecipeEditRoute(val recipeId: Long? = null)

@Serializable
object IngredientListRoute

/**
 * The three ways to a new ingredient: Barcode, Claude, Selbst eintragen. From a recipe's line
 * editor, [recipeLineName] is the typed name, and the created ingredient goes back to that editor.
 */
@Serializable
data class NewIngredientRoute(val recipeLineName: String? = null)

@Serializable
data class IngredientDetailRoute(val ingredientId: Long)

/** A new ingredient; [nutrition] is in [Nutrient] order. It exists only once the user saves it. */
@Serializable
data class IngredientCreateRoute(
    val name: String,
    val nutrition: List<Double>,
    val fdcId: Long? = null,
    val usdaDescription: String? = null,
    val labelProduct: String? = null,
    val labelBarcode: String? = null,
    val storeSection: StoreSection = StoreSection.OTHER,
    val plantPoints: PlantPoints = PlantPoints.ZERO,
    /** The starting units as [PieceUnit] names with the grams of one unit at the same index. */
    val unitNames: List<String> = emptyList(),
    val unitGrams: List<Double> = emptyList(),
    /** Saving returns the new ingredient's id to the recipe's line editor instead of showing it. */
    val forRecipe: Boolean = false,
) {
    constructor(seed: NewIngredientSeed, forRecipe: Boolean) : this(
        name = seed.name,
        nutrition = Nutrient.entries.map { seed.nutrition[it] },
        fdcId = seed.fdcId,
        usdaDescription = seed.usdaDescription,
        labelProduct = seed.labelSource?.labelProduct,
        labelBarcode = seed.labelSource?.labelBarcode,
        storeSection = seed.storeSection,
        plantPoints = seed.plantPoints,
        unitNames = seed.units.map { it.unit.name },
        unitGrams = seed.units.map { it.gramsPerUnit },
        forRecipe = forRecipe,
    )

    fun toSeed() = NewIngredientSeed(
        name = name,
        nutrition = Nutrition.of(Nutrient.entries.zip(nutrition).toMap()),
        fdcId = fdcId,
        usdaDescription = usdaDescription,
        labelSource = if (labelProduct != null && labelBarcode != null) LabelSource(labelProduct, labelBarcode) else null,
        storeSection = storeSection,
        plantPoints = plantPoints,
        units = unitNames.zip(unitGrams).mapNotNull { (unit, grams) -> IngredientUnit.perUnit(PieceUnit.valueOf(unit), grams) },
    )
}

/** [week]: the epoch day of a week to open on, from a Speiseplan week header; null for the current one. */
@Serializable
data class StatsRoute(val week: Long? = null)

@Serializable
object SettingsRoute

@Serializable
object AboutRoute

private enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    COOKING(CookingRoute, R.string.nav_cooking, Icons.Filled.CalendarMonth),
    RECIPES(RecipeListRoute(), R.string.nav_recipes, Icons.AutoMirrored.Filled.MenuBook),
    INGREDIENTS(IngredientListRoute, R.string.nav_ingredients, Icons.Filled.Kitchen),
    SETTINGS(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun PlantryNavHost(openRecipeId: Long? = null, otherSuggestionsThan: Long? = null, proposalEventId: Long? = null) {
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
    val currentTopLevel = currentEntry?.topLevel()
    // Every screen keeps the bottom bar, with the tab it was opened from selected.
    val backStack by navController.currentBackStack.collectAsStateWithLifecycle()
    val selectedTab = currentTopLevel ?: backStack.asReversed().firstNotNullOfOrNull { it.topLevel() }

    // Tapping the selected tab steps back to its first screen, closing each screen as its X or back
    // arrow would: an editor with unsaved changes asks first, and "Abbrechen" stops there.
    var returningToTab by rememberSaveable { mutableStateOf(false) }
    val leavesItself = currentDestination?.let { destination ->
        destination.hasRoute(RecipeEditRoute::class) ||
            destination.hasRoute(IngredientDetailRoute::class) ||
            destination.hasRoute(IngredientCreateRoute::class)
    } == true
    LaunchedEffect(returningToTab, currentEntry?.id) {
        when {
            !returningToTab -> Unit
            currentTopLevel != null -> returningToTab = false
            // Asked through leaveRequested; the screen pops itself.
            leavesItself -> Unit
            else -> navController.popBackStack()
        }
    }
    val leaveRequested = returningToTab && leavesItself
    val stopReturning = { returningToTab = false }

    // Checked on every resume; "Später" hides it until the app is restarted.
    val backupReminder by app.backupRepository.reminder.collectAsStateWithLifecycle()
    var backupReminderDismissed by rememberSaveable { mutableStateOf(false) }
    val showBackupReminder = backupReminder && !backupReminderDismissed && currentTopLevel != null
    val scope = rememberCoroutineScope()

    // A notification's recipe, on top of Speiseplan; if it is gone by now, Speiseplan stays.
    LaunchedEffect(openRecipeId) {
        if (openRecipeId != null && recipeRepository.getRecipe(openRecipeId) != null) {
            navController.navigate(RecipeDetailRoute(openRecipeId))
        }
    }
    // "Anderer Vorschlag": Vorschläge on top of Speiseplan, without the notification's recipe at first.
    LaunchedEffect(otherSuggestionsThan) {
        if (otherSuggestionsThan != null) navController.navigate(SuggestionsRoute(otherSuggestionsThan, proposalEventId))
    }
    LifecycleResumeEffect(Unit) {
        scope.launch { app.backupRepository.refreshReminder() }
        onPauseOrDispose {}
    }

    Scaffold(
        // Each screen handles its own insets; this Scaffold only adds the bottom bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (selectedTab != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { top ->
                        NavigationBarItem(
                            selected = top == selectedTab,
                            onClick = {
                                if (top == selectedTab) {
                                    if (currentTopLevel == null) returningToTab = true
                                    return@NavigationBarItem
                                }
                                returningToTab = false
                                navController.navigate(top.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                if (top == TopLevelDestination.INGREDIENTS) {
                                    navController.getBackStackEntry<IngredientListRoute>()
                                        .savedStateHandle[RESET_INGREDIENT_FILTERS] = true
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
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
                // The back swipe would otherwise shrink the screen and fade in the one below.
                predictivePopEnterTransition = { EnterTransition.None },
                predictivePopExitTransition = { ExitTransition.None },
                // The banner already took the status bar.
                modifier = if (showBackupReminder) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier,
            ) {
                composable<CookingRoute> { entry ->
                    val handle = entry.savedStateHandle
                    val justPlannedId by handle.getStateFlow<Long?>(JUST_PLANNED_ID, null).collectAsStateWithLifecycle()
                    val justPlannedTitle by handle.getStateFlow(JUST_PLANNED_TITLE, "").collectAsStateWithLifecycle()
                    val justPlannedDay by handle.getStateFlow(JUST_PLANNED_DAY, 0L).collectAsStateWithLifecycle()
                    val justLoggedId by handle.getStateFlow<Long?>(JUST_LOGGED_ID, null).collectAsStateWithLifecycle()
                    val justLoggedDay by handle.getStateFlow(JUST_LOGGED_DAY, 0L).collectAsStateWithLifecycle()
                    CookingScreen(
                        viewModel = viewModel {
                            CookingViewModel(cookLogRepository, app.plannedRepository, recipeRepository, ingredientRepository, settingsRepository)
                        },
                        onRecipeClick = { navController.navigate(RecipeDetailRoute(it)) },
                        onSuggest = { navController.navigate(SuggestionsRoute()) },
                        onPlan = { navController.navigate(RecipeSearchRoute(RecipeSearchMode.Plan)) },
                        onLog = { navController.navigate(RecipeSearchRoute(RecipeSearchMode.Log)) },
                        onStats = { week -> navController.navigate(StatsRoute(week?.toEpochDay())) },
                        justPlanned = justPlannedId?.let { JustPlanned(it, justPlannedTitle, LocalDate.ofEpochDay(justPlannedDay)) },
                        onJustPlannedShown = {
                            // Reset, not removed: removing would leave the collected flows on the old value.
                            handle[JUST_PLANNED_ID] = null
                            handle[JUST_PLANNED_TITLE] = ""
                            handle[JUST_PLANNED_DAY] = 0L
                        },
                        justLogged = justLoggedId?.let { JustLogged(it, LocalDate.ofEpochDay(justLoggedDay)) },
                        onJustLoggedShown = {
                            handle[JUST_LOGGED_ID] = null
                            handle[JUST_LOGGED_DAY] = 0L
                        },
                    )
                }
                composable<RecipeSearchRoute> { entry ->
                    val mode = entry.toRoute<RecipeSearchRoute>().mode
                    RecipeSearchScreen(
                        viewModel = viewModel { RecipeSearchViewModel(recipeRepository, app.plannedRepository) },
                        mode = mode,
                        onBack = { navController.popBackStack() },
                        onPicked = { recipe, date ->
                            when (mode) {
                                RecipeSearchMode.Log -> navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                                    // The day first: Speiseplan reacts to the id.
                                    handle[JUST_LOGGED_DAY] = date.toEpochDay()
                                    handle[JUST_LOGGED_ID] = recipe.id
                                }
                                RecipeSearchMode.Plan -> {
                                    askForNotifications()
                                    navController.previousBackStackEntry?.handOverPlan(recipe.id, recipe.title, date)
                                }
                            }
                            navController.popBackStack()
                        },
                    )
                }
                composable<SuggestionsRoute> { entry ->
                    val route = entry.toRoute<SuggestionsRoute>()
                    SuggestionsScreen(
                        viewModel = viewModel {
                            SuggestionsViewModel(
                                app.recipeSuggester,
                                app.plannedRepository,
                                recipeRepository,
                                ingredientRepository,
                                skipRecipeId = route.skipRecipeId,
                            )
                        },
                        onBack = { navController.popBackStack() },
                        onRecipeClick = { navController.navigate(RecipeDetailRoute(it, fromSuggestions = true)) },
                        onPlanned = { recipeId, title, date ->
                            askForNotifications()
                            scope.launch { app.proposalEventRepository.plannedFromSuggestions(route.proposalEventId, date) }
                            navController.previousBackStackEntry?.handOverPlan(recipeId, title, date)
                            navController.popBackStack()
                        },
                    )
                }
                composable<StatsRoute> { entry ->
                    StatsScreen(
                        viewModel = viewModel { StatsViewModel(cookLogRepository) },
                        onBack = { navController.popBackStack() },
                        onRecipeClick = { navController.navigate(RecipeDetailRoute(it)) },
                        onIngredientClick = { navController.navigate(IngredientDetailRoute(it)) },
                        openWeek = entry.toRoute<StatsRoute>().week?.let(LocalDate::ofEpochDay),
                    )
                }
                composable<RecipeListRoute> { entry ->
                    val ingredientId = entry.toRoute<RecipeListRoute>().ingredientId
                    val handle = entry.savedStateHandle
                    val justPlannedId by handle.getStateFlow<Long?>(JUST_PLANNED_ID, null).collectAsStateWithLifecycle()
                    val justPlannedTitle by handle.getStateFlow(JUST_PLANNED_TITLE, "").collectAsStateWithLifecycle()
                    val justPlannedDay by handle.getStateFlow(JUST_PLANNED_DAY, 0L).collectAsStateWithLifecycle()
                    RecipeListScreen(
                        viewModel = viewModel {
                            RecipeListViewModel(recipeRepository, ingredientRepository, cookLogRepository, app.plannedRepository, ingredientId)
                        },
                        onRecipeClick = { navController.navigate(RecipeDetailRoute(it)) },
                        onAddRecipe = { navController.navigate(RecipeEditRoute()) },
                        onPlan = { navController.navigate(RecipeSearchRoute(RecipeSearchMode.Plan)) },
                        justPlanned = justPlannedId?.let { JustPlanned(it, justPlannedTitle, LocalDate.ofEpochDay(justPlannedDay)) },
                        onJustPlannedShown = {
                            handle[JUST_PLANNED_ID] = null
                            handle[JUST_PLANNED_TITLE] = ""
                            handle[JUST_PLANNED_DAY] = 0L
                        },
                        onBack = if (ingredientId != null) ({ navController.popBackStack() }) else null,
                    )
                }
                composable<RecipeDetailRoute> { entry ->
                    val route = entry.toRoute<RecipeDetailRoute>()
                    val recipeId = route.recipeId
                    RecipeDetailScreen(
                        viewModel = viewModel {
                            RecipeDetailViewModel(
                                recipeId,
                                recipeRepository,
                                ingredientRepository,
                                app.plannedRepository,
                                app.recipePhotoRepository,
                                app.photoCompressor::compress,
                            )
                        },
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(RecipeEditRoute(recipeId)) },
                        onIngredientClick = { navController.navigate(IngredientDetailRoute(it)) },
                        onPlanned = askForNotifications,
                        onPlanPicked = if (route.fromSuggestions) {
                            { title, date ->
                                askForNotifications()
                                val suggestions = navController.getBackStackEntry<SuggestionsRoute>().toRoute<SuggestionsRoute>()
                                scope.launch { app.proposalEventRepository.plannedFromSuggestions(suggestions.proposalEventId, date) }
                                navController.getBackStackEntry<CookingRoute>().handOverPlan(recipeId, title, date)
                                // Vorschläge too, as after planning a suggestion from its card.
                                navController.popBackStack<CookingRoute>(inclusive = false)
                            }
                        } else {
                            null
                        },
                    )
                }
                composable<RecipeEditRoute> { entry ->
                    val route = entry.toRoute<RecipeEditRoute>()
                    val viewModel = viewModel {
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
                            app.connectionTester,
                        )
                    }
                    val handle = entry.savedStateHandle
                    val created by handle.getStateFlow<Long?>(CREATED_INGREDIENT, null).collectAsStateWithLifecycle()
                    LaunchedEffect(created) {
                        created?.let {
                            viewModel.useCreatedIngredient(it)
                            handle[CREATED_INGREDIENT] = null
                        }
                    }
                    RecipeEditScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onCreateIngredient = { name -> navController.navigate(NewIngredientRoute(recipeLineName = name)) },
                        leaveRequested = leaveRequested,
                        onLeaveCancelled = stopReturning,
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
                        onAddIngredient = { navController.navigate(NewIngredientRoute()) },
                    )
                }
                composable<NewIngredientRoute> { entry ->
                    val recipeLineName = entry.toRoute<NewIngredientRoute>().recipeLineName
                    NewIngredientScreen(
                        viewModel = viewModel {
                            NewIngredientViewModel(app.productLookup, app.productSearch, app.newIngredientFinder, settingsRepository, app.connectionTester)
                        },
                        onBack = { navController.popBackStack() },
                        onNew = { seed -> navController.navigate(IngredientCreateRoute(seed, forRecipe = recipeLineName != null)) },
                        initialName = recipeLineName.orEmpty(),
                    )
                }
                composable<IngredientCreateRoute> { entry ->
                    val route = entry.toRoute<IngredientCreateRoute>()
                    val seed = route.toSeed()
                    IngredientDetailScreen(
                        viewModel = viewModel { IngredientDetailViewModel(0, ingredientRepository, app.productLookup, app.loadUsdaCatalog, seed) },
                        onBack = { navController.popBackStack() },
                        onCreated = { id ->
                            if (route.forRecipe) {
                                navController.popBackStack<NewIngredientRoute>(inclusive = true)
                                navController.currentBackStackEntry?.savedStateHandle?.set(CREATED_INGREDIENT, id)
                            } else {
                                navController.navigate(IngredientDetailRoute(id)) {
                                    popUpTo<NewIngredientRoute> { inclusive = true }
                                }
                            }
                        },
                        leaveRequested = leaveRequested,
                        onLeaveCancelled = stopReturning,
                    )
                }
                composable<IngredientDetailRoute> { entry ->
                    val ingredientId = entry.toRoute<IngredientDetailRoute>().ingredientId
                    IngredientDetailScreen(
                        viewModel = viewModel { IngredientDetailViewModel(ingredientId, ingredientRepository, app.productLookup, app.loadUsdaCatalog) },
                        onBack = { navController.popBackStack() },
                        leaveRequested = leaveRequested,
                        onLeaveCancelled = stopReturning,
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        viewModel = viewModel { SettingsViewModel(settingsRepository, app.connectionTester) },
                        backupViewModel = viewModel { BackupViewModel(app.backupRepository, app.contentResolver) },
                        onAbout = { navController.navigate(AboutRoute) },
                    )
                }
                composable<AboutRoute> {
                    val settings by settingsRepository.settings.collectAsStateWithLifecycle()
                    AboutScreen(cooldownDays = settings.cooldownDays, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

/** Hands a recipe and day to plan back to Speiseplan or Rezepte, which plans it with an undo snackbar. */
private fun NavBackStackEntry.handOverPlan(recipeId: Long, title: String, date: LocalDate) {
    // Title and day first: the screen reacts to the id.
    savedStateHandle[JUST_PLANNED_TITLE] = title
    savedStateHandle[JUST_PLANNED_DAY] = date.toEpochDay()
    savedStateHandle[JUST_PLANNED_ID] = recipeId
}

/** The tab this entry is the first screen of; the recipe list filtered by an ingredient is a sub-screen. */
private fun NavBackStackEntry.topLevel(): TopLevelDestination? =
    TopLevelDestination.entries.firstOrNull { destination.hasRoute(it.route::class) }
        ?.takeUnless { destination.hasRoute(RecipeListRoute::class) && toRoute<RecipeListRoute>().ingredientId != null }

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
