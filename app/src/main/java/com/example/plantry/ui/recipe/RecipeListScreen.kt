package com.example.plantry.ui.recipe

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterAlt as OutlinedFilterAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.ChipSelection
import com.example.plantry.data.CookLogRepository
import com.example.plantry.data.Dish
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.IngredientSuggestions
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeChip
import com.example.plantry.data.RecipeFilter
import com.example.plantry.data.RecipeListItem
import com.example.plantry.data.RecipeQuery
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.RecipeSort
import com.example.plantry.ui.AppBarSearchField
import com.example.plantry.ui.FilterRow
import com.example.plantry.ui.FilterRows
import com.example.plantry.ui.HideOnScrollFab
import com.example.plantry.ui.ScrollToTopOnChange
import com.example.plantry.ui.rememberFabVisibility
import com.example.plantry.ui.rememberHideKeyboardOnScroll
import com.example.plantry.ui.ToggleChip
import com.example.plantry.ui.cooklog.RecipeStatsRow
import com.example.plantry.ui.cooklog.label
import com.example.plantry.ui.cooklog.lastCookedLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RecipeListUiState(
    val items: List<RecipeListItem>,
    /** All recipes, unfiltered; 0 shows the empty state instead of the filters. */
    val recipeCount: Int,
    val filter: RecipeFilter,
    /** The chips that are on, in the order they were switched on; deleted ingredients left out. */
    val activeChips: List<RecipeChip>,
    val sort: RecipeSort,
    /** The ingredients the filter offers: all of them, A–Z. */
    val choosableIngredients: List<Ingredient>,
    val ingredientNames: Map<Long, String>,
    /** The title search while it is open, blank before anything is typed; null while closed. */
    val search: String?,
)

/**
 * Opens with [RecipeChip.defaults]; [initialIngredientId] instead opens the list filtered by only
 * that ingredient, so it shows as many recipes as the ingredient's count promised.
 */
class RecipeListViewModel(
    recipeRepository: RecipeRepository,
    ingredientRepository: IngredientRepository,
    cookLogRepository: CookLogRepository,
    initialIngredientId: Long? = null,
) : ViewModel() {

    /** Null until the defaults are known; they are set once, later only taps change the chips. */
    private val chips = MutableStateFlow(
        initialIngredientId?.let { ChipSelection(listOf<RecipeChip>(RecipeChip.WithIngredient(it))) },
    )
    private val sort = MutableStateFlow(RecipeSort.TITLE)
    private val search = MutableStateFlow<String?>(null)

    /** Null until the first emission, so the empty state doesn't flash on launch. */
    val state: StateFlow<RecipeListUiState?> = combine(
        recipeRepository.observeRecipes(),
        recipeRepository.observeAllLines(),
        combine(ingredientRepository.observeIngredients(), ingredientRepository.observeContents(), ::Pair),
        cookLogRepository.observeLastCooked(),
        combine(chips.filterNotNull(), sort, search, ::Triple),
    ) { recipes, lines, (ingredients, contents), lastCooked, (chips, sort, search) ->
        val byId = ingredients.associateBy { it.id }
        val active = chips.active.filter { it !is RecipeChip.WithIngredient || it.ingredientId in byId }
        val filter = RecipeFilter.of(active, search)
        RecipeListUiState(
            items = RecipeQuery.run(recipes, lines, byId, lastCooked, filter, sort, contents),
            recipeCount = recipes.size,
            filter = filter,
            activeChips = active,
            sort = sort,
            choosableIngredients = ingredients.sortedBy { it.name.lowercase() },
            ingredientNames = ingredients.associate { it.id to it.name },
            search = search,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSort(value: RecipeSort) {
        sort.value = value
    }

    init {
        if (chips.value == null) {
            viewModelScope.launch {
                val recipes = recipeRepository.observeRecipes().first()
                chips.update { it ?: ChipSelection(RecipeChip.defaults(recipes)) }
            }
        }
    }

    fun toggleChip(chip: RecipeChip) = chips.update { it?.toggle(chip) }

    fun openSearch() {
        search.update { it ?: "" }
    }

    fun setSearch(query: String) {
        search.value = query
    }

    /** Back to the list as it was: the chips were only set aside. */
    fun closeSearch() {
        search.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    viewModel: RecipeListViewModel,
    onRecipeClick: (Long) -> Unit,
    onAddRecipe: () -> Unit,
    /** Set when opened as a sub-screen (filtered from the ingredient list); shows a back arrow. */
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // Picking a sort, even the current one, jumps to the top; keyed on the sort too so it
    // runs after the list has been reordered (the keyed rows would otherwise keep their place).
    // A changed filter starts at the top too, so a shrunk list isn't scrolled past its end.
    var sortPicks by rememberSaveable { mutableIntStateOf(0) }
    val fab = rememberFabVisibility()
    ScrollToTopOnChange(listState, listOf(sortPicks, state?.sort, state?.filter)) { fab.visible = true }
    var pickIngredients by rememberSaveable { mutableStateOf(false) }
    val search = state?.search
    val searchFocus = remember { FocusRequester() }
    // Only tapping the search icon opens the keyboard, not coming back to an open search.
    var focusSearch by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = search != null) { viewModel.closeSearch() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (search != null) {
                        AppBarSearchField(search, viewModel::setSearch, stringResource(R.string.recipe_search), searchFocus)
                        LaunchedEffect(Unit) {
                            if (focusSearch) searchFocus.requestFocus()
                            focusSearch = false
                        }
                    } else {
                        Text(stringResource(R.string.recipes_title))
                    }
                },
                navigationIcon = {
                    if (search != null) {
                        IconButton(onClick = viewModel::closeSearch) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    } else if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    state?.takeIf { it.recipeCount > 0 }?.let {
                        if (search == null) {
                            // Not on the list for one ingredient: searching would set that ingredient aside.
                            if (onBack == null) {
                                IconButton(onClick = { focusSearch = true; viewModel.openSearch() }) {
                                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.recipe_search))
                                }
                            }
                            IngredientFilterButton(it.filter.ingredientIds.size) { pickIngredients = true }
                        } else if (search.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearch(""); searchFocus.requestFocus() }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recipe_search_clear))
                            }
                        }
                        SortMenu(it.sort) { option -> viewModel.setSort(option); sortPicks++ }
                    }
                },
            )
        },
        floatingActionButton = {
            HideOnScrollFab(fab) {
                ExtendedFloatingActionButton(
                    onClick = onAddRecipe,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.recipe_fab)) },
                )
            }
        },
    ) { padding ->
        val current = state ?: return@Scaffold
        if (current.recipeCount == 0) {
            CenteredMessage(stringResource(R.string.recipes_empty), Modifier.padding(padding))
            return@Scaffold
        }
        val today = remember { LocalDate.now() }
        val ingredientCount = current.filter.ingredientIds.size
        val hideKeyboard = rememberHideKeyboardOnScroll()
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).nestedScroll(fab.connection).nestedScroll(hideKeyboard),
            state = listState,
        ) {
            // The filters scroll away with the list.
            item(key = "filters") { FilterBar(current, viewModel::toggleChip) }
            if (current.items.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(if (current.search != null) R.string.recipe_search_none else R.string.recipe_filter_no_match),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            items(current.items, key = { it.recipe.id }) { item ->
                RecipeRow(item, current.sort, ingredientCount, today, onClick = { onRecipeClick(item.recipe.id) })
                HorizontalDivider()
            }
        }
        if (pickIngredients) {
            IngredientFilterDialog(
                ingredients = current.choosableIngredients,
                selected = current.filter.ingredientIds,
                onToggle = { viewModel.toggleChip(RecipeChip.WithIngredient(it)) },
                onDismiss = { pickIngredients = false },
            )
        }
    }
}

/** Opens the ingredient filter; filled with a count badge while ingredients are chosen. */
@Composable
private fun IngredientFilterButton(chosen: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (chosen > 0) Badge { Text("$chosen") } }) {
            Icon(
                if (chosen > 0) Icons.Filled.FilterAlt else Icons.Outlined.OutlinedFilterAlt,
                contentDescription = stringResource(R.string.recipe_filter_ingredients),
            )
        }
    }
}

@Composable
private fun SortMenu(sort: RecipeSort, onSort: (RecipeSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.recipe_sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RecipeSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        onSort(option)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option == sort) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                )
            }
        }
    }
}

/**
 * Dishes, the two times and the nutrients, each chip in a fixed place, then the chosen
 * ingredients; under them "256 Rezepte" or "54/256 Rezepte". While searching only the number of
 * matches, since the chips don't apply.
 */
@Composable
private fun FilterBar(state: RecipeListUiState, onToggle: (RecipeChip) -> Unit) {
    if (state.search != null) {
        CountLine(pluralStringResource(R.plurals.recipes_count, state.items.size, state.items.size))
        return
    }
    val active = state.activeChips
    val ingredients = active.filterIsInstance<RecipeChip.WithIngredient>()
    val rows = listOfNotNull<FilterRow>(
        {
            RecipeChip.DISHES.forEach { chip -> ToggleChip(stringResource(chip.dish.label), chip in active) { onToggle(chip) } }
        },
        {
            ToggleChip(stringResource(R.string.recipe_filter_easy), RecipeChip.EASY in active) { onToggle(RecipeChip.EASY) }
            ToggleChip(stringResource(R.string.recipe_filter_quick), RecipeChip.QUICK in active) { onToggle(RecipeChip.QUICK) }
        },
        {
            RecipeChip.HIGHLIGHTS.forEach { chip ->
                ToggleChip(stringResource(chip.highlight.label), chip in active) { onToggle(chip) }
            }
        },
        if (ingredients.isEmpty()) {
            null
        } else {
            {
                ingredients.forEach { chip ->
                    ToggleChip(state.ingredientNames[chip.ingredientId].orEmpty(), selected = true) { onToggle(chip) }
                }
            }
        },
    )
    Column(Modifier.padding(top = 4.dp)) {
        FilterRows(rows)
        CountLine(
            if (active.isNotEmpty()) {
                pluralStringResource(R.plurals.recipes_count_filtered, state.recipeCount, state.items.size, state.recipeCount)
            } else {
                pluralStringResource(R.plurals.recipes_count, state.recipeCount, state.recipeCount)
            },
        )
    }
}

@Composable
private fun CountLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun IngredientFilterDialog(
    ingredients: List<Ingredient>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = if (query.isBlank()) ingredients else IngredientSuggestions.match(query, ingredients, limit = Int.MAX_VALUE)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipe_filter_ingredients_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.recipe_filter_ingredients_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (shown.isEmpty()) {
                    Text(
                        stringResource(R.string.recipe_filter_ingredients_none),
                        Modifier.padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.id }) { ingredient ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(ingredient.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = ingredient.id in selected, onCheckedChange = { onToggle(ingredient.id) })
                            Text(ingredient.name)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.recipe_filter_done)) } },
    )
}

/**
 * The name on up to two lines, then the stats with the cooking time on the right. A third line
 * shows how many of the filter's ingredients the recipe uses (from two on) and, sorted by last
 * cooked, when.
 */
@Composable
private fun RecipeRow(item: RecipeListItem, sort: RecipeSort, ingredientCount: Int, today: LocalDate, onClick: () -> Unit) {
    val details = listOfNotNull(
        if (ingredientCount > 1) pluralStringResource(R.plurals.recipe_list_matched, ingredientCount, item.matchedIngredients, ingredientCount) else null,
        if (sort == RecipeSort.LAST_COOKED) lastCookedLabel(item.lastCookedOn, today) else null,
    )
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            item.recipe.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecipeStatsRow(item.stats, Modifier.weight(1f), highlights = item.highlights)
            CompactCookingTimeChip(item.recipe.totalTimeMinutes, Modifier.padding(start = 8.dp))
        }
        if (details.isNotEmpty()) {
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val RecipeSort.label: Int
    get() = when (this) {
        RecipeSort.TITLE -> R.string.recipe_sort_title
        RecipeSort.PROTEIN -> R.string.recipe_sort_protein
        RecipeSort.CARBS -> R.string.recipe_sort_carbs
        RecipeSort.TOTAL_TIME -> R.string.recipe_total_time
        RecipeSort.ACTIVE_TIME -> R.string.recipe_active_time
        RecipeSort.PLANT_POINTS -> R.string.recipe_sort_plant_points
        RecipeSort.LAST_COOKED -> R.string.recipe_sort_last_cooked
    }

internal val Dish.label: Int
    get() = when (this) {
        Dish.MAIN -> R.string.dish_main
        Dish.BREAKFAST -> R.string.dish_breakfast
        Dish.SNACK -> R.string.dish_snack
    }

@Composable
internal fun sourceLabel(recipe: Recipe): String = when {
    recipe.source.isNotBlank() && recipe.page != null ->
        stringResource(R.string.recipe_source_with_page, recipe.source, recipe.page)
    recipe.source.isNotBlank() -> recipe.source
    recipe.page != null -> stringResource(R.string.recipe_page_only, recipe.page)
    else -> stringResource(R.string.recipe_no_source)
}
