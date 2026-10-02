package com.example.plantry.ui.recipe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class RecipeListViewModel(repository: RecipeRepository) : ViewModel() {
    /** Null until the first emission, so the empty state doesn't flash on launch. */
    val recipes: StateFlow<List<Recipe>?> = repository.observeRecipes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    viewModel: RecipeListViewModel,
    onRecipeClick: (Long) -> Unit,
    onAddRecipe: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipes_title)) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, stringResource(R.string.cook_history_open))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRecipe) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.recipe_add))
            }
        },
    ) { padding ->
        val list = recipes ?: return@Scaffold
        if (list.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.recipes_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                items(list, key = { it.id }) { recipe ->
                    ListItem(
                        headlineContent = { Text(recipe.title) },
                        supportingContent = { Text(sourceLabel(recipe)) },
                        trailingContent = {
                            Text(stringResource(R.string.recipe_minutes, recipe.cookingTimeMinutes))
                        },
                        modifier = Modifier.clickable { onRecipeClick(recipe.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
internal fun sourceLabel(recipe: Recipe): String = when {
    recipe.source.isNotBlank() && recipe.page != null ->
        stringResource(R.string.recipe_source_with_page, recipe.source, recipe.page)
    recipe.source.isNotBlank() -> recipe.source
    recipe.page != null -> stringResource(R.string.recipe_page_only, recipe.page)
    else -> stringResource(R.string.recipe_no_source)
}
