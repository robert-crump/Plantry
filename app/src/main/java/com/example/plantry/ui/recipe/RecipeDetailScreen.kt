package com.example.plantry.ui.recipe

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.RecipeNutrition
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.nutritionLines
import com.example.plantry.data.toDraft
import com.example.plantry.ui.ingredient.formatDecimal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A recipe line with the name of its ingredient. */
data class RecipeLineItem(val line: RecipeIngredient, val ingredientName: String)

data class RecipeDetailUiState(
    val recipe: Recipe,
    val lines: List<RecipeLineItem>,
    val nutrition: RecipeNutrition,
)

class RecipeDetailViewModel(
    private val recipeId: Long,
    private val repository: RecipeRepository,
    ingredientRepository: IngredientRepository,
) : ViewModel() {
    /** Null until loaded, and after the recipe was deleted. */
    val state: StateFlow<RecipeDetailUiState?> = combine(
        repository.observeRecipe(recipeId),
        repository.observeLines(recipeId),
        ingredientRepository.observeIngredients(),
    ) { recipe, lines, ingredients ->
        if (recipe == null) return@combine null
        val byId = ingredients.associateBy { it.id }
        RecipeDetailUiState(
            recipe = recipe,
            lines = lines.map { RecipeLineItem(it, byId[it.ingredientId]?.name.orEmpty()) },
            nutrition = RecipeNutrition.calculate(
                nutritionLines(lines.map { it.toDraft() }, byId),
                recipe.ourServings,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(recipeId)
            onDeleted()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    viewModel: RecipeDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recipe = state?.recipe
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (recipe != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val detail = state ?: return@Scaffold
        val current = detail.recipe
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (current.modified) {
                ListItem(
                    leadingContent = {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                stringResource(R.string.recipe_modified),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    },
                    headlineContent = { Text(stringResource(R.string.recipe_modified_hint)) },
                )
            }
            DetailRow(stringResource(R.string.recipe_source), sourceLabel(current))
            DetailRow(stringResource(R.string.recipe_book_servings), current.bookServings.toString())
            DetailRow(stringResource(R.string.recipe_our_servings), current.ourServings.toString())
            DetailRow(
                stringResource(R.string.recipe_cooking_time),
                stringResource(R.string.recipe_minutes, current.cookingTimeMinutes),
            )

            SectionTitle(R.string.recipe_section_lines)
            if (detail.lines.isEmpty()) {
                Text(
                    stringResource(R.string.recipe_lines_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            detail.lines.forEach { item ->
                ListItem(
                    headlineContent = { Text(item.line.originalText) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.recipe_line_amount,
                                formatDecimal(item.line.grams),
                                item.ingredientName,
                            ),
                        )
                    },
                )
            }

            if (detail.lines.isNotEmpty()) {
                SectionTitle(R.string.recipe_section_nutrition)
                ProteinIndicator(
                    detail.nutrition.perPortion.protein,
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                NutrientList(detail.nutrition)
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.recipe_delete_title)) },
                text = { Text(stringResource(R.string.recipe_delete_message, current.title)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        viewModel.delete(onDeleted = onBack)
                    }) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    ListItem(overlineContent = { Text(label) }, headlineContent = { Text(value) })
}
