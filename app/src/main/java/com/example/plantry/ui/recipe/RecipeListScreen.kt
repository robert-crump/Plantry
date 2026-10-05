package com.example.plantry.ui.recipe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.CookLogRepository
import com.example.plantry.data.CookingStats
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.IngredientSuggestions
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeFilter
import com.example.plantry.data.RecipeListItem
import com.example.plantry.data.RecipeQuery
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.RecipeSort
import com.example.plantry.ui.cooklog.lastCookedLabel
import com.example.plantry.ui.currentLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class RecipeListUiState(
    val items: List<RecipeListItem>,
    /** All recipes, unfiltered; 0 shows the empty state instead of the filters. */
    val recipeCount: Int,
    val filter: RecipeFilter,
    val sort: RecipeSort,
    /** The ingredients the filter offers: all but staples, A–Z. */
    val choosableIngredients: List<Ingredient>,
    /** The filter's ingredients, in the order of [choosableIngredients]. */
    val selectedIngredients: List<Ingredient>,
    val sources: List<String>,
)

/** [initialIngredientId] opens the list filtered by that ingredient. */
class RecipeListViewModel(
    recipeRepository: RecipeRepository,
    ingredientRepository: IngredientRepository,
    cookLogRepository: CookLogRepository,
    initialIngredientId: Long? = null,
) : ViewModel() {

    private val filter = MutableStateFlow(RecipeFilter(ingredientIds = setOfNotNull(initialIngredientId)))
    private val sort = MutableStateFlow(RecipeSort.TITLE)

    /** Null until the first emission, so the empty state doesn't flash on launch. */
    val state: StateFlow<RecipeListUiState?> = combine(
        recipeRepository.observeRecipes(),
        recipeRepository.observeAllLines(),
        ingredientRepository.observeIngredients(),
        cookLogRepository.observeLastCooked(),
        combine(filter, sort, ::Pair),
    ) { recipes, lines, ingredients, lastCooked, (filter, sort) ->
        val byId = ingredients.associateBy { it.id }
        val choosable = ingredients.filterNot { it.staple }.sortedBy { it.name.lowercase() }
        RecipeListUiState(
            items = RecipeQuery.run(recipes, lines, byId, lastCooked, filter, sort),
            recipeCount = recipes.size,
            filter = filter,
            sort = sort,
            choosableIngredients = choosable,
            selectedIngredients = ingredients.filter { it.id in filter.ingredientIds }.sortedBy { it.name.lowercase() },
            sources = RecipeQuery.sources(recipes),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSort(value: RecipeSort) {
        sort.value = value
    }

    fun toggleIngredient(id: Long) = filter.update { current ->
        val ids = current.ingredientIds
        current.copy(ingredientIds = if (id in ids) ids - id else ids + id)
    }

    fun setMaxCookingMinutes(minutes: Int?) = filter.update { it.copy(maxCookingMinutes = minutes) }

    fun setSource(source: String?) = filter.update { it.copy(source = source) }

    fun resetFilter() {
        filter.value = RecipeFilter()
    }
}

private val COOKING_TIME_OPTIONS = listOf(15, 30, 45, 60)

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
                    state?.takeIf { it.recipeCount > 0 }?.let { SortMenu(it.sort, viewModel::setSort) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRecipe) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.recipe_add))
            }
        },
    ) { padding ->
        val current = state ?: return@Scaffold
        if (current.recipeCount == 0) {
            CenteredMessage(stringResource(R.string.recipes_empty), Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            FilterBar(current, viewModel)
            if (current.items.isEmpty()) {
                CenteredMessage(stringResource(R.string.recipe_filter_no_match)) {
                    TextButton(onClick = viewModel::resetFilter) { Text(stringResource(R.string.recipe_filter_reset)) }
                }
            } else {
                val today = remember { LocalDate.now() }
                val ingredientCount = current.filter.ingredientIds.size
                LazyColumn(Modifier.fillMaxSize()) {
                    items(current.items, key = { it.recipe.id }) { item ->
                        RecipeRow(item, current.sort, ingredientCount, today, onClick = { onRecipeClick(item.recipe.id) })
                        HorizontalDivider()
                    }
                }
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(state: RecipeListUiState, viewModel: RecipeListViewModel) {
    var pickIngredients by rememberSaveable { mutableStateOf(false) }
    val filter = state.filter
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter.ingredientIds.isNotEmpty(),
            onClick = { pickIngredients = true },
            label = {
                Text(
                    if (filter.ingredientIds.isEmpty()) stringResource(R.string.recipe_filter_ingredients)
                    else pluralStringResource(R.plurals.recipe_filter_ingredients_count, filter.ingredientIds.size, filter.ingredientIds.size),
                )
            },
            trailingIcon = { DropdownIcon() },
        )
        ChoiceChip(
            label = stringResource(R.string.recipe_filter_cooking_time),
            selectedLabel = filter.maxCookingMinutes?.let { stringResource(R.string.recipe_filter_cooking_time_max, it) },
            options = COOKING_TIME_OPTIONS,
            optionLabel = { stringResource(R.string.recipe_filter_cooking_time_max, it) },
            onSelect = viewModel::setMaxCookingMinutes,
        )
        if (state.sources.isNotEmpty() || filter.source != null) {
            ChoiceChip(
                label = stringResource(R.string.recipe_filter_source),
                selectedLabel = filter.source,
                options = state.sources,
                optionLabel = { it },
                onSelect = viewModel::setSource,
            )
        }
    }
    if (state.selectedIngredients.isNotEmpty()) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.selectedIngredients.forEach { ingredient ->
                val remove = stringResource(R.string.recipe_filter_ingredient_remove, ingredient.name)
                InputChip(
                    selected = true,
                    onClick = { viewModel.toggleIngredient(ingredient.id) },
                    label = { Text(ingredient.name) },
                    trailingIcon = { Icon(Icons.Filled.Close, remove, Modifier.padding(start = 2.dp)) },
                )
            }
        }
    }
    if (pickIngredients) {
        IngredientFilterDialog(
            ingredients = state.choosableIngredients,
            selected = filter.ingredientIds,
            onToggle = viewModel::toggleIngredient,
            onDismiss = { pickIngredients = false },
        )
    }
}

/** A filter chip with a menu of [options] and "Alle"; [selectedLabel] is null while nothing is chosen. */
@Composable
private fun <T> ChoiceChip(
    label: String,
    selectedLabel: String?,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selectedLabel != null,
            onClick = { expanded = true },
            label = { Text(selectedLabel ?: label) },
            trailingIcon = { DropdownIcon() },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.recipe_filter_any)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
                leadingIcon = { if (selectedLabel == null) Icon(Icons.Filled.Check, contentDescription = null) },
            )
            options.forEach { option ->
                val text = optionLabel(option)
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    leadingIcon = { if (text == selectedLabel) Icon(Icons.Filled.Check, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun DropdownIcon() {
    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
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

@Composable
private fun RecipeRow(item: RecipeListItem, sort: RecipeSort, ingredientCount: Int, today: LocalDate, onClick: () -> Unit) {
    val details = listOfNotNull(
        if (ingredientCount > 0) pluralStringResource(R.plurals.recipe_list_matched, ingredientCount, item.matchedIngredients, ingredientCount) else null,
        sourceLabel(item.recipe),
        sortValueLabel(item, sort, today),
    )
    ListItem(
        headlineContent = { Text(item.recipe.title) },
        supportingContent = { Text(details.joinToString(" · ")) },
        trailingContent = item.recipe.cookingTimeMinutes?.let { minutes ->
            { Text(stringResource(R.string.recipe_minutes, minutes)) }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** The value a non-obvious sort orders by, so the order can be followed; null for A–Z and time. */
@Composable
private fun sortValueLabel(item: RecipeListItem, sort: RecipeSort, today: LocalDate): String? = when (sort) {
    RecipeSort.TITLE, RecipeSort.COOKING_TIME -> null
    RecipeSort.PROTEIN -> stringResource(R.string.recipe_list_protein, item.proteinPerPortion)
    RecipeSort.PLANT_POINTS -> stringResource(R.string.recipe_list_plant_points, formatPlantPoints(item.plantPoints, currentLocale()))
    RecipeSort.LAST_COOKED -> lastCookedLabel(CookingStats.from(listOfNotNull(item.lastCookedOn), today))
}

@Composable
private fun CenteredMessage(text: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action()
    }
}

private val RecipeSort.label: Int
    get() = when (this) {
        RecipeSort.TITLE -> R.string.recipe_sort_title
        RecipeSort.PROTEIN -> R.string.recipe_sort_protein
        RecipeSort.COOKING_TIME -> R.string.recipe_sort_cooking_time
        RecipeSort.PLANT_POINTS -> R.string.recipe_sort_plant_points
        RecipeSort.LAST_COOKED -> R.string.recipe_sort_last_cooked
    }

@Composable
internal fun sourceLabel(recipe: Recipe): String = when {
    recipe.source.isNotBlank() && recipe.page != null ->
        stringResource(R.string.recipe_source_with_page, recipe.source, recipe.page)
    recipe.source.isNotBlank() -> recipe.source
    recipe.page != null -> stringResource(R.string.recipe_page_only, recipe.page)
    else -> stringResource(R.string.recipe_no_source)
}
