package com.example.plantry.ui.recipe

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
import androidx.compose.material.icons.filled.FilterAlt
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
import com.example.plantry.data.RecipeHighlight
import com.example.plantry.data.RecipeListItem
import com.example.plantry.data.RecipeQuery
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.RecipeSort
import com.example.plantry.ui.FilterRow
import com.example.plantry.ui.FilterRows
import com.example.plantry.ui.HideOnScrollFab
import com.example.plantry.ui.rememberFabVisibility
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

    /** Null until the first emission, so the empty state doesn't flash on launch. */
    val state: StateFlow<RecipeListUiState?> = combine(
        recipeRepository.observeRecipes(),
        recipeRepository.observeAllLines(),
        combine(ingredientRepository.observeIngredients(), ingredientRepository.observeContents(), ::Pair),
        cookLogRepository.observeLastCooked(),
        combine(chips.filterNotNull(), sort, ::Pair),
    ) { recipes, lines, (ingredients, contents), lastCooked, (chips, sort) ->
        val byId = ingredients.associateBy { it.id }
        val active = chips.active.filter { it !is RecipeChip.WithIngredient || it.ingredientId in byId }
        val filter = RecipeFilter.of(active)
        RecipeListUiState(
            items = RecipeQuery.run(recipes, lines, byId, lastCooked, filter, sort, contents),
            recipeCount = recipes.size,
            filter = filter,
            activeChips = active,
            sort = sort,
            choosableIngredients = ingredients.sortedBy { it.name.lowercase() },
            ingredientNames = ingredients.associate { it.id to it.name },
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
    var sortPicks by remember { mutableIntStateOf(0) }
    val fab = rememberFabVisibility()
    LaunchedEffect(sortPicks, state?.sort, state?.filter) {
        listState.scrollToItem(0)
        fab.visible = true
    }
    var pickIngredients by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipes_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    state?.takeIf { it.recipeCount > 0 }?.let {
                        IngredientFilterButton(it.filter.ingredientIds.size) { pickIngredients = true }
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
        LazyColumn(Modifier.fillMaxSize().padding(padding).nestedScroll(fab.connection), state = listState) {
            // The filters scroll away with the list.
            item(key = "filters") { FilterBar(current, viewModel::toggleChip) }
            if (current.items.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.recipe_filter_no_match),
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

/** "Typ:", "Schnell:" and "Viel:", each chip in a fixed place, then "Mit:" while ingredients are chosen. */
@Composable
private fun FilterBar(state: RecipeListUiState, onToggle: (RecipeChip) -> Unit) {
    val active = state.activeChips
    val ingredients = active.filterIsInstance<RecipeChip.WithIngredient>()
    val rows = listOfNotNull(
        FilterRow(stringResource(R.string.filter_row_type)) {
            RecipeChip.DISHES.forEach { chip -> ToggleChip(stringResource(chip.dish.label), chip in active) { onToggle(chip) } }
        },
        FilterRow(stringResource(R.string.filter_row_quick)) {
            ToggleChip(stringResource(R.string.recipe_filter_easy), RecipeChip.EASY in active) { onToggle(RecipeChip.EASY) }
            ToggleChip(stringResource(R.string.recipe_filter_quick), RecipeChip.QUICK in active) { onToggle(RecipeChip.QUICK) }
        },
        FilterRow(stringResource(R.string.filter_row_much)) {
            RecipeChip.HIGHLIGHTS.forEach { chip ->
                ToggleChip(stringResource(chip.highlight.chipLabel), chip in active) { onToggle(chip) }
            }
        },
        if (ingredients.isEmpty()) null else FilterRow(stringResource(R.string.filter_row_with)) {
            ingredients.forEach { chip ->
                ToggleChip(state.ingredientNames[chip.ingredientId].orEmpty(), selected = true) { onToggle(chip) }
            }
        },
    )
    FilterRows(rows, Modifier.padding(top = 4.dp, bottom = 8.dp))
}

/** The "Viel:" chip's short name for the nutrient. */
private val RecipeHighlight.chipLabel: Int
    get() = when (this) {
        RecipeHighlight.HIGH_PROTEIN -> R.string.filter_protein
        RecipeHighlight.HIGH_CARB -> R.string.filter_carbs
        RecipeHighlight.HIGH_FIBRE -> R.string.filter_fibre
        RecipeHighlight.PLANT_POINT_POWER, RecipeHighlight.LOW_CALORIE -> label
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
