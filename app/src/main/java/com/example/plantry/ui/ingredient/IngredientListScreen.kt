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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.ui.FastScrollbar
import com.example.plantry.ui.currentLocale
import com.example.plantry.data.Ingredient
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
    val ingredients: List<Ingredient>,
    /** Recipes using each ingredient (buy-as links followed); missing ids mean 0. */
    val recipeCounts: Map<Long, Int>,
    val unreviewedCount: Int,
    val onlyUnreviewed: Boolean,
)

class IngredientListViewModel(
    repository: IngredientRepository,
    recipeRepository: RecipeRepository,
) : ViewModel() {

    private val onlyUnreviewed = MutableStateFlow(false)

    /** Null until the first emission, so the empty state doesn't flash on launch. */
    val state: StateFlow<IngredientListUiState?> =
        combine(
            repository.observeIngredients(),
            recipeRepository.observeAllLines(),
            onlyUnreviewed,
        ) { all, lines, filter ->
            IngredientListUiState(
                ingredients = IngredientSorting.sortedByName(
                    if (filter) all.filterNot { it.reviewed } else all,
                    Locale.getDefault(),
                ),
                recipeCounts = RecipeQuery.recipeCounts(lines, all.associateBy { it.id }),
                unreviewedCount = all.count { !it.reviewed },
                onlyUnreviewed = filter,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleOnlyUnreviewed() = onlyUnreviewed.update { !it }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientListScreen(
    viewModel: IngredientListViewModel,
    onIngredientClick: (Long) -> Unit,
    onRecipesClick: (Long) -> Unit,
    onAddIngredient: () -> Unit,
    onOpenSort: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ingredients_title)) },
                actions = {
                    IconButton(onClick = onOpenSort) {
                        Icon(Icons.Filled.FactCheck, contentDescription = stringResource(R.string.sort_title))
                    }
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
            FilterChip(
                selected = current.onlyUnreviewed,
                onClick = viewModel::toggleOnlyUnreviewed,
                label = { Text(stringResource(R.string.ingredients_filter_unreviewed, current.unreviewedCount)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (current.ingredients.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(
                            if (current.onlyUnreviewed) R.string.ingredients_empty_unreviewed
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
