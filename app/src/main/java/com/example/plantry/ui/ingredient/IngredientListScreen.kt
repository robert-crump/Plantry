package com.example.plantry.ui.ingredient

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.ui.AppBarSearchField
import com.example.plantry.ui.FastScrollbar
import com.example.plantry.ui.FilterRows
import com.example.plantry.ui.HideOnScrollFab
import com.example.plantry.ui.ScrollToTopOnChange
import com.example.plantry.ui.rememberFabVisibility
import com.example.plantry.ui.ToggleChip
import com.example.plantry.ui.currentLocale
import com.example.plantry.data.ChipSelection
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientChip
import com.example.plantry.data.IngredientFilters
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.IngredientSorting
import com.example.plantry.data.Nutrition
import com.example.plantry.data.RecipeQuery
import com.example.plantry.data.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.Locale

data class IngredientListUiState(
    /** The ingredients matching the filters, sorted by name. */
    val ingredients: List<Ingredient>,
    val totalCount: Int,
    /** Recipes using each ingredient (buy-as links followed); missing ids mean 0. */
    val recipeCounts: Map<Long, Int>,
    val chips: ChipSelection<IngredientChip>,
    /** The name search while it is open, blank before anything is typed; null while closed. */
    val search: String? = null,
) {
    val filtered: Boolean get() = chips.active.isNotEmpty() || !search.isNullOrBlank()
}

class IngredientListViewModel(
    repository: IngredientRepository,
    recipeRepository: RecipeRepository,
) : ViewModel() {

    private val chips = MutableStateFlow(ChipSelection<IngredientChip>())
    private val search = MutableStateFlow<String?>(null)

    /** Null until the first emission, so the empty state doesn't flash on launch. */
    val state: StateFlow<IngredientListUiState?> =
        combine(
            repository.observeIngredients(),
            recipeRepository.observeAllLines(),
            chips,
            search,
        ) { all, lines, chips, search ->
            val recipeCounts = RecipeQuery.recipeCounts(lines, all.associateBy { it.id })
            IngredientListUiState(
                ingredients = IngredientSorting.sortedByName(
                    IngredientFilters.apply(all, chips.active, search.orEmpty()),
                    Locale.getDefault(),
                ),
                totalCount = all.size,
                recipeCounts = recipeCounts,
                chips = chips,
                search = search,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleChip(chip: IngredientChip) = chips.update { it.toggle(chip) }

    fun openSearch() {
        search.update { it ?: "" }
    }

    fun setSearch(query: String) {
        search.value = query
    }

    fun closeSearch() {
        search.value = null
    }

    /** No chip on and the search closed; called each time the Zutaten tab is opened. */
    fun resetFilters() {
        chips.value = ChipSelection()
        search.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientListScreen(
    viewModel: IngredientListViewModel,
    onIngredientClick: (Long) -> Unit,
    onRecipesClick: (Long) -> Unit,
    onAddIngredient: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fab = rememberFabVisibility()
    val listState = rememberLazyListState()
    // A changed filter starts at the top, so a shrunk list isn't scrolled past its end.
    ScrollToTopOnChange(listState, listOf(state?.chips, state?.search)) { fab.visible = true }
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
                        AppBarSearchField(search, viewModel::setSearch, stringResource(R.string.ingredient_search), searchFocus)
                        LaunchedEffect(Unit) {
                            if (focusSearch) searchFocus.requestFocus()
                            focusSearch = false
                        }
                    } else {
                        Text(stringResource(R.string.ingredients_title))
                    }
                },
                navigationIcon = {
                    if (search != null) {
                        IconButton(onClick = viewModel::closeSearch) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (search == null) {
                        if ((state?.totalCount ?: 0) > 0) {
                            IconButton(onClick = { focusSearch = true; viewModel.openSearch() }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.ingredient_search))
                            }
                        }
                    } else if (search.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearch(""); searchFocus.requestFocus() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recipe_search_clear))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            HideOnScrollFab(fab) {
                ExtendedFloatingActionButton(
                    onClick = onAddIngredient,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.ingredient_fab)) },
                )
            }
        },
    ) { padding ->
        val current = state ?: return@Scaffold
        val locale = currentLocale()
        // One letter per row, in list order (the list is already sorted by name); none for the filters.
        val scrollLabels = remember(current.ingredients, locale) {
            listOf(null) + current.ingredients.map { IngredientSorting.indexLetter(it.name, locale) }
        }
        Box(Modifier.fillMaxSize().padding(padding).nestedScroll(fab.connection)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                // Room below the last row for the FAB.
                contentPadding = PaddingValues(bottom = FabClearance),
            ) {
                // The filters scroll away with the list.
                item(key = "filters") {
                    Column {
                        FilterRows(
                            listOf {
                                IngredientChip.entries.forEach { chip ->
                                    ToggleChip(stringResource(chip.label), selected = chip in current.chips.active) {
                                        viewModel.toggleChip(chip)
                                    }
                                }
                            },
                        )
                        CountRow(current)
                    }
                }
                if (current.ingredients.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(
                                if (current.totalCount > 0) R.string.ingredients_empty_filtered
                                else R.string.ingredients_empty,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
                items(current.ingredients, key = { it.id }) { ingredient ->
                    val recipeCount = current.recipeCounts[ingredient.id] ?: 0
                    IngredientRow(
                        ingredient,
                        amounts = chipAmounts(current.chips, ingredient.nutrition, locale),
                        recipeCount = recipeCount,
                        onClick = { onIngredientClick(ingredient.id) },
                        onRecipesClick = if (recipeCount > 0) {
                            { onRecipesClick(ingredient.id) }
                        } else {
                            null
                        },
                    )
                    HorizontalDivider()
                }
            }
            FastScrollbar(
                listState,
                label = { scrollLabels.getOrNull(it) },
                // Ends above the FAB.
                modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(bottom = FabClearance),
            )
        }
    }
}

private val FabClearance = 88.dp

/**
 * "212 Zutaten" or "90/213 Zutaten", while a chip is on with the grams from which an ingredient
 * counts for it, e.g. "90/213 Zutaten · ab 12 g Protein je 100 g".
 */
@Composable
private fun CountRow(state: IngredientListUiState) {
    val count = if (state.filtered) {
        pluralStringResource(R.plurals.ingredients_count_filtered, state.totalCount, state.ingredients.size, state.totalCount)
    } else {
        pluralStringResource(R.plurals.ingredients_count, state.totalCount, state.totalCount)
    }
    val locale = currentLocale()
    val thresholds = shownChips(state.chips).map { chip ->
        stringResource(R.string.filter_threshold, stringResource(chip.amountLabel, formatDecimal(IngredientFilters.min(chip), locale)))
    }
    Text(
        if (thresholds.isEmpty()) count else stringResource(R.string.filter_thresholds, count, thresholds.joinToString(", ")),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    )
}

internal val IngredientChip.label: Int
    get() = when (this) {
        IngredientChip.HIGH_PROTEIN -> R.string.filter_protein
        IngredientChip.HIGH_CARBS -> R.string.filter_carbs
        IngredientChip.HIGH_FIBRE -> R.string.filter_fibre
    }

/** The active chips in the order the chip row shows them, not the order they were switched on. */
private fun shownChips(chips: ChipSelection<IngredientChip>): List<IngredientChip> =
    IngredientChip.entries.filter { it in chips.active }

internal val IngredientChip.amountLabel: Int
    get() = when (this) {
        IngredientChip.HIGH_PROTEIN -> R.string.filter_amount_protein
        IngredientChip.HIGH_CARBS -> R.string.filter_amount_carbs
        IngredientChip.HIGH_FIBRE -> R.string.filter_amount_fibre
    }

/** The ingredient's whole grams per 100 g for each active chip, e.g. "24 g Protein · 11 g Ballaststoffe"; null without one. */
@Composable
private fun chipAmounts(chips: ChipSelection<IngredientChip>, nutrition: Nutrition, locale: Locale): String? =
    shownChips(chips).map { chip ->
        stringResource(chip.amountLabel, formatDecimal(Math.round(IngredientFilters.amount(chip, nutrition)).toDouble(), locale))
    }.takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** The name (ellipsized) over the chips' [amounts] if any, and the recipes button aligned right. */
@Composable
private fun IngredientRow(
    ingredient: Ingredient,
    amounts: String?,
    recipeCount: Int,
    onClick: () -> Unit,
    onRecipesClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                ingredient.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            amounts?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onRecipesClick != null) {
            RecipesButton(pluralStringResource(R.plurals.ingredient_recipe_count, recipeCount, recipeCount), onRecipesClick)
        }
    }
}

/** An outlined, tappable "N Rezepte ›" chip that opens the recipe list filtered by the ingredient. */
@Composable
private fun RecipesButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** E.g. "86 kcal · 1,6 g Protein je 100 g". */
@Composable
internal fun nutritionSummary(nutrition: Nutrition): String = stringResource(
    R.string.ingredient_summary,
    formatDecimal(Math.round(nutrition.kcal).toDouble(), currentLocale()),
    formatDecimal(Math.round(nutrition.protein * 10) / 10.0, currentLocale()),
)
