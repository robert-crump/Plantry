package com.example.plantry.ui.cooklog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.PlannedRepository
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeQuery
import com.example.plantry.data.RecipeRepository
import com.example.plantry.ui.currentLocale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** What picking a recipe on [RecipeSearchScreen] is for. */
@Serializable
enum class RecipeSearchMode {
    /** "Kocheintrag": the day it was cooked, up to today. */
    Log,

    /** "Planen": the day to cook it, from today. */
    Plan,
}

class RecipeSearchViewModel(recipeRepository: RecipeRepository, plannedRepository: PlannedRepository) : ViewModel() {
    /** Null until loaded, so "Keine Rezepte gefunden" doesn't flash. */
    val recipes: StateFlow<List<Recipe>?> = recipeRepository.observeRecipes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The day each recipe on Geplant is planned for; null until loaded. */
    val plannedOn: StateFlow<Map<Long, LocalDate>?> = plannedRepository.observe()
        .map { all -> all.associate { it.recipeId to it.plannedOn } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * "Kocheintrag" and "Planen": a search field in the app bar over all recipes, A–Z, narrowed as you
 * type. The keyboard opens with the screen and hides when the list is scrolled. Tapping a recipe
 * asks for a day, up to today for [RecipeSearchMode.Log] and from today for [RecipeSearchMode.Plan];
 * picking one hands both to [onPicked]. When planning, recipes already on Geplant show their day,
 * and the picker starts there, since picking moves them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeSearchScreen(
    viewModel: RecipeSearchViewModel,
    mode: RecipeSearchMode,
    onBack: () -> Unit,
    onPicked: (recipe: Recipe, date: LocalDate) -> Unit,
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val plannedOn by viewModel.plannedOn.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var pickedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    // Dragging the list hides the keyboard, so browsing gets the whole screen.
    val hideKeyboard = remember(focusManager) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) focusManager.clearFocus()
                return Offset.Zero
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.recipe_search)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                query = ""
                                focusRequester.requestFocus()
                            },
                        ) {
                            Icon(Icons.Filled.Close, stringResource(R.string.recipe_search_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val all = recipes ?: return@Scaffold
        // Only planning needs the days, so logging doesn't wait for them.
        val planned = if (mode == RecipeSearchMode.Plan) plannedOn ?: return@Scaffold else emptyMap()
        val today = remember { LocalDate.now() }
        val locale = currentLocale()
        val shown = remember(all, query) { RecipeQuery.byTitle(all, query) }
        val modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
        if (shown.isEmpty()) {
            Text(
                stringResource(R.string.recipe_search_none),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.padding(16.dp),
            )
        } else {
            LazyColumn(modifier.nestedScroll(hideKeyboard)) {
                itemsIndexed(shown, key = { _, recipe -> recipe.id }) { index, recipe ->
                    if (index > 0) HorizontalDivider()
                    val day = planned[recipe.id]
                    ListItem(
                        headlineContent = { Text(recipe.title) },
                        supportingContent = day?.let {
                            { Text(stringResource(R.string.recipe_search_planned, formatPlannedDate(it, today, locale))) }
                        },
                        modifier = Modifier.clickable {
                            focusManager.clearFocus()
                            pickedId = recipe.id
                        },
                    )
                }
            }
        }
    }
    val picked = pickedId?.let { id -> recipes?.firstOrNull { it.id == id } }
    if (picked != null) {
        val today = LocalDate.now()
        val onPick = { date: LocalDate -> onPicked(picked, date) }
        val onDismiss = { pickedId = null }
        when (mode) {
            RecipeSearchMode.Log -> CookDatePickerDialog(date = today, today = today, onPick = onPick, onDismiss = onDismiss)
            // A day that has already passed can't be picked again, so moving starts from today.
            RecipeSearchMode.Plan -> PlanDatePickerDialog(
                date = plannedOn?.get(picked.id)?.takeIf { it >= today } ?: today,
                today = today,
                onPick = onPick,
                onDismiss = onDismiss,
            )
        }
    }
}
