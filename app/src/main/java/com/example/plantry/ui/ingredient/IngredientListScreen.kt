package com.example.plantry.ui.ingredient

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.ui.FastScrollbar
import com.example.plantry.ui.currentLocale
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientFilters
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.IngredientSorting
import com.example.plantry.data.Nutrition
import com.example.plantry.data.RecipeQuery
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.ReviewFilter
import com.example.plantry.data.SortView
import com.example.plantry.data.UsageFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

data class IngredientListUiState(
    /** The ingredients matching the filters, sorted by name. */
    val ingredients: List<Ingredient>,
    val totalCount: Int,
    /** Recipes using each ingredient (buy-as links followed); missing ids mean 0. */
    val recipeCounts: Map<Long, Int>,
    val review: ReviewFilter,
    val usage: UsageFilter,
) {
    val filtered: Boolean get() = review != ReviewFilter.ALL || usage != UsageFilter.ALL
}

class IngredientListViewModel(
    repository: IngredientRepository,
    recipeRepository: RecipeRepository,
) : ViewModel() {

    private val review = MutableStateFlow(IngredientFilters.DEFAULT_REVIEW)
    private val usage = MutableStateFlow(IngredientFilters.DEFAULT_USAGE)

    /** Null until the first emission, so the empty state doesn't flash on launch. */
    val state: StateFlow<IngredientListUiState?> =
        combine(
            repository.observeIngredients(),
            recipeRepository.observeAllLines(),
            review,
            usage,
        ) { all, lines, review, usage ->
            val recipeCounts = RecipeQuery.recipeCounts(lines, all.associateBy { it.id })
            IngredientListUiState(
                ingredients = IngredientSorting.sortedByName(
                    IngredientFilters.apply(all, recipeCounts, review, usage),
                    Locale.getDefault(),
                ),
                totalCount = all.size,
                recipeCounts = recipeCounts,
                review = review,
                usage = usage,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setReview(filter: ReviewFilter) {
        review.value = filter
    }

    fun setUsage(filter: UsageFilter) {
        usage.value = filter
    }

    /** Back to the defaults; called each time the Zutaten tab is opened. */
    fun resetFilters() {
        review.value = IngredientFilters.DEFAULT_REVIEW
        usage.value = IngredientFilters.DEFAULT_USAGE
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientListScreen(
    viewModel: IngredientListViewModel,
    onIngredientClick: (Long) -> Unit,
    onRecipesClick: (Long) -> Unit,
    onAddIngredient: () -> Unit,
    onOpenSort: (SortView) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ingredients_title)) },
                actions = {
                    SortMenu(onOpenSort)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddIngredient) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ingredient_add))
            }
        },
    ) { padding ->
        val current = state ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            FilterRow(current, onReviewChange = viewModel::setReview, onUsageChange = viewModel::setUsage)
            if (current.ingredients.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(
                            if (current.totalCount > 0) R.string.ingredients_empty_filtered
                            else R.string.ingredients_empty,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val listState = rememberLazyListState()
                val locale = currentLocale()
                // One letter per row, in list order (the list is already sorted by name).
                val scrollLabels = remember(current.ingredients, locale) {
                    current.ingredients.map { IngredientSorting.indexLetter(it.name, locale) }
                }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        // Room below the last row for the FAB.
                        contentPadding = PaddingValues(bottom = FabClearance),
                    ) {
                        items(current.ingredients, key = { it.id }) { ingredient ->
                            val recipeCount = current.recipeCounts[ingredient.id] ?: 0
                            IngredientRow(
                                ingredient,
                                recipeCount = recipeCount,
                                onClick = { onIngredientClick(ingredient.id) },
                                onRecipesClick = if (RecipeQuery.canFilterBy(ingredient, recipeCount)) {
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
    }
}

private val FabClearance = 88.dp
private val FilterButtonWidth = 140.dp

/** The count ("212 Zutaten" or "90/213 Zutaten") and the Prüfstatus and Verwendung dropdowns. */
@Composable
private fun FilterRow(
    state: IngredientListUiState,
    onReviewChange: (ReviewFilter) -> Unit,
    onUsageChange: (UsageFilter) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    ) {
        Text(
            if (state.filtered) {
                pluralStringResource(R.plurals.ingredients_count_filtered, state.totalCount, state.ingredients.size, state.totalCount)
            } else {
                pluralStringResource(R.plurals.ingredients_count, state.totalCount, state.totalCount)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Wraps rather than ellipsizes on narrow phones with three-digit counts.
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        FilterDropdown(
            name = stringResource(R.string.ingredients_filter_review),
            selected = state.review,
            options = ReviewFilter.entries,
            label = { stringResource(it.label) },
            onSelect = onReviewChange,
        )
        FilterDropdown(
            name = stringResource(R.string.ingredients_filter_usage),
            selected = state.usage,
            options = UsageFilter.entries,
            label = { stringResource(it.label) },
            onSelect = onUsageChange,
        )
    }
}

private val ReviewFilter.label: Int
    get() = when (this) {
        ReviewFilter.ALL -> R.string.filter_all
        ReviewFilter.REVIEWED -> R.string.filter_reviewed
        ReviewFilter.UNREVIEWED -> R.string.filter_unreviewed
    }

private val UsageFilter.label: Int
    get() = when (this) {
        UsageFilter.ALL -> R.string.filter_all
        UsageFilter.USED -> R.string.filter_used
        UsageFilter.UNUSED -> R.string.filter_unused
    }

/**
 * A fixed-width outlined button showing [selected]; tapping it opens a menu of [options].
 * TalkBack reads "[name]: [selected]".
 */
@Composable
private fun <T> FilterDropdown(
    name: String,
    selected: T,
    options: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = label(selected)
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(start = 12.dp, end = 0.dp),
            modifier = Modifier
                .width(FilterButtonWidth)
                .semantics(mergeDescendants = true) { contentDescription = "$name: $selectedLabel" },
        ) {
            Text(
                selectedLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    trailingIcon = if (option == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** One line: name (ellipsized), unreviewed badge, and the recipes button aligned right. */
@Composable
private fun IngredientRow(
    ingredient: Ingredient,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                ingredient.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!ingredient.reviewed) UnreviewedBadge()
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

@Composable
internal fun UnreviewedBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            stringResource(R.string.ingredient_unreviewed),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** The ⋮ menu: each entry opens the Sortieren screen locked to that grouping. */
@Composable
private fun SortMenu(onOpenSort: (SortView) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortView.entries.forEach { view ->
                DropdownMenuItem(
                    text = { Text(stringResource(view.label)) },
                    onClick = {
                        expanded = false
                        onOpenSort(view)
                    },
                )
            }
        }
    }
}
