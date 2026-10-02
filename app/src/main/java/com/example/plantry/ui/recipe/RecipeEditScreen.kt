package com.example.plantry.ui.recipe

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeEditUiState(
    val form: RecipeForm = RecipeForm(),
    val showErrors: Boolean = false,
    val saved: Boolean = false,
)

/** Edits the recipe with [recipeId], or creates a new one when it is null. */
class RecipeEditViewModel(
    private val recipeId: Long?,
    private val repository: RecipeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeEditUiState())
    val state: StateFlow<RecipeEditUiState> = _state.asStateFlow()

    val isNew: Boolean get() = recipeId == null

    init {
        if (recipeId != null) {
            viewModelScope.launch {
                repository.getRecipe(recipeId)?.let { recipe ->
                    _state.update { it.copy(form = RecipeForm.from(recipe)) }
                }
            }
        }
    }

    fun onFormChange(transform: RecipeForm.() -> RecipeForm) {
        _state.update { it.copy(form = it.form.transform()) }
    }

    fun save() {
        val draft = _state.value.form.toDraft()
        if (draft == null) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            if (recipeId == null) repository.create(draft) else repository.update(recipeId, draft)
            _state.update { it.copy(saved = true) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(
    viewModel: RecipeEditViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    val form = state.form
    val errors = if (state.showErrors) form.errors() else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (viewModel.isNew) R.string.recipe_new else R.string.recipe_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FormField(
                value = form.title,
                onValueChange = { viewModel.onFormChange { copy(title = it) } },
                label = R.string.recipe_title,
                error = if (errors?.title == true) R.string.error_title_required else null,
                capitalize = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    value = form.source,
                    onValueChange = { viewModel.onFormChange { copy(source = it) } },
                    label = R.string.recipe_source,
                    capitalize = true,
                    modifier = Modifier.weight(2f),
                )
                FormField(
                    value = form.page,
                    onValueChange = { viewModel.onFormChange { copy(page = it) } },
                    label = R.string.recipe_page,
                    error = if (errors?.page == true) R.string.error_positive_number else null,
                    numeric = true,
                    modifier = Modifier.weight(1f),
                )
            }
            FormField(
                value = form.bookServings,
                onValueChange = { viewModel.onFormChange { withBookServings(it) } },
                label = R.string.recipe_book_servings,
                error = if (errors?.bookServings == true) R.string.error_positive_number else null,
                numeric = true,
            )
            FormField(
                value = form.ourServings,
                onValueChange = { viewModel.onFormChange { withOurServings(it) } },
                label = R.string.recipe_our_servings,
                error = if (errors?.ourServings == true) R.string.error_positive_number else null,
                hint = R.string.recipe_our_servings_hint,
                numeric = true,
            )
            FormField(
                value = form.cookingTime,
                onValueChange = { viewModel.onFormChange { copy(cookingTime = it) } },
                label = R.string.recipe_cooking_time_minutes,
                error = if (errors?.cookingTime == true) R.string.error_positive_number else null,
                numeric = true,
            )
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
    @StringRes error: Int? = null,
    @StringRes hint: Int? = null,
    numeric: Boolean = false,
    capitalize: Boolean = false,
) {
    val supporting = error ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = supporting?.let { { Text(stringResource(it)) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        ),
        modifier = modifier,
    )
}
