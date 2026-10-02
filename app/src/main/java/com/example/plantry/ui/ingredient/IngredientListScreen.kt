package com.example.plantry.ui.ingredient

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.Nutrition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class IngredientListUiState(
    val ingredients: List<Ingredient>,
    val unreviewedCount: Int,
    val onlyUnreviewed: Boolean,
)

class IngredientListViewModel(repository: IngredientRepository) : ViewModel() {

    private val onlyUnreviewed = MutableStateFlow(false)

    /** Null until the first emission, so the empty state doesn't flash on launch. */
    val state: StateFlow<IngredientListUiState?> =
        combine(repository.observeIngredients(), onlyUnreviewed) { all, filter ->
            IngredientListUiState(
                ingredients = if (filter) all.filterNot { it.reviewed } else all,
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
    onAddIngredient: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.ingredients_title)) }) },
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
                LazyColumn(Modifier.fillMaxSize()) {
                    items(current.ingredients, key = { it.id }) { ingredient ->
                        IngredientRow(ingredient, onClick = { onIngredientClick(ingredient.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun IngredientRow(ingredient: Ingredient, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(ingredient.name) },
        supportingContent = { Text(nutritionSummary(ingredient.nutrition)) },
        trailingContent = if (ingredient.reviewed) null else ({ UnreviewedBadge() }),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** E.g. "86 kcal · 1,6 g Protein je 100 g". */
@Composable
internal fun nutritionSummary(nutrition: Nutrition): String = stringResource(
    R.string.ingredient_summary,
    formatDecimal(Math.round(nutrition.kcal).toDouble()),
    formatDecimal(Math.round(nutrition.protein * 10) / 10.0),
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
