package com.example.plantry.ui.ingredient

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.Nutrition
import com.example.plantry.data.usda.UsdaCatalog
import com.example.plantry.data.usda.UsdaFood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [results] is null while the catalog is still loading. */
data class UsdaSearchUiState(val query: String = "", val results: List<UsdaFood>? = null)

class UsdaSearchViewModel(
    loadCatalog: () -> UsdaCatalog,
    private val repository: IngredientRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val catalog = flow { emit(loadCatalog()) }.flowOn(Dispatchers.Default)

    val state: StateFlow<UsdaSearchUiState> = combine(catalog, query) { catalog, query ->
        UsdaSearchUiState(query, catalog.search(query))
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsdaSearchUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun create(food: UsdaFood, name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repository.createFromUsda(food, name)) }
    }

    /** Creates an ingredient without a USDA entry, from the package's values. */
    fun createFromLabel(name: String, nutrition: Nutrition, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repository.createFromLabel(name, nutrition)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsdaSearchScreen(
    viewModel: UsdaSearchViewModel,
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf<Long?>(null) }
    var withoutUsda by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.usda_search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.onQueryChange(it)
                },
                placeholder = { Text(stringResource(R.string.usda_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // Always offered: a generic USDA hit may not match a branded product's label.
            TextButton(onClick = { withoutUsda = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Filled.EditNote, contentDescription = null)
                Text(stringResource(R.string.usda_search_without), Modifier.padding(start = 8.dp))
            }
            val results = state.results
            when {
                results == null -> CenteredHint(R.string.usda_search_loading)
                results.isEmpty() && state.query.isNotBlank() -> CenteredHint(R.string.usda_search_no_results)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.fdcId }) { food ->
                        ListItem(
                            headlineContent = { Text(food.description) },
                            supportingContent = { Text(nutritionSummary(food.nutrition)) },
                            modifier = Modifier.clickable { picked = food.fdcId },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        val pickedFood = state.results?.firstOrNull { it.fdcId == picked }
        if (pickedFood != null) {
            CreateIngredientDialog(
                food = pickedFood,
                onDismiss = { picked = null },
                onCreate = { name -> viewModel.create(pickedFood, name, onCreated) },
            )
        }
        if (withoutUsda) {
            LabelNutritionDialog(
                confirmLabel = R.string.action_create,
                onConfirm = { name, nutrition -> viewModel.createFromLabel(name, nutrition, onCreated) },
                onDismiss = { withoutUsda = false },
                initial = LabelNutritionForm(name = query.trim()),
                askName = true,
            )
        }
    }
}

@Composable
private fun CreateIngredientDialog(food: UsdaFood, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.usda_create_title)) },
        text = {
            Column {
                Text(food.description, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.usda_create_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CenteredHint(@StringRes text: Int) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
