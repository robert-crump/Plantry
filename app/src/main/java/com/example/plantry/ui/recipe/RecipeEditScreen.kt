package com.example.plantry.ui.recipe

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.IngredientSuggestions
import com.example.plantry.data.RecipeNutrition
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.nutritionLines
import com.example.plantry.ui.ingredient.formatDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeEditUiState(
    val form: RecipeForm = RecipeForm(),
    val showErrors: Boolean = false,
    /** Null while no line is being edited. */
    val lineEditor: LineEditorState? = null,
    val saved: Boolean = false,
)

/** The line being edited in the dialog; a null [index] adds a new line. */
data class LineEditorState(
    val index: Int?,
    val form: RecipeLineForm = RecipeLineForm(),
    val showErrors: Boolean = false,
)

/** Edits the recipe with [recipeId], or creates a new one when it is null. */
class RecipeEditViewModel(
    private val recipeId: Long?,
    private val repository: RecipeRepository,
    ingredientRepository: IngredientRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeEditUiState())
    val state: StateFlow<RecipeEditUiState> = _state.asStateFlow()

    val isNew: Boolean get() = recipeId == null

    private val ingredients: StateFlow<List<Ingredient>> = ingredientRepository.observeIngredients()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Nutrition per portion of the lines as they are now, including a valid line in the open
     * editor, so the effect of a change shows before it is applied. Null without lines or valid
     * servings.
     */
    val preview: StateFlow<RecipeNutrition?> = combine(_state, ingredients) { state, ingredients ->
        val servings = state.form.effectiveServings() ?: return@combine null
        val editor = state.lineEditor
        val pending = editor?.form?.toDraft()
        val lines = if (pending == null) state.form.lines else state.form.withLine(editor.index, pending).lines
        if (lines.isEmpty()) return@combine null
        RecipeNutrition.calculate(nutritionLines(lines, ingredients.associateBy { it.id }), servings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Own-table matches for the ingredient field of the open line editor. */
    val suggestions: StateFlow<List<Ingredient>> = combine(_state, ingredients) { state, ingredients ->
        val form = state.lineEditor?.form
        if (form == null || form.ingredientId != null) {
            emptyList()
        } else {
            IngredientSuggestions.match(form.ingredientQuery, ingredients)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ingredient names by id, for displaying the lines. */
    val ingredientNames: StateFlow<Map<Long, String>> = ingredients
        .map { all -> all.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        if (recipeId != null) {
            viewModelScope.launch {
                val recipe = repository.getRecipe(recipeId) ?: return@launch
                val lines = repository.getLines(recipeId)
                _state.update { it.copy(form = RecipeForm.from(recipe, lines)) }
            }
        }
    }

    fun onFormChange(transform: RecipeForm.() -> RecipeForm) {
        _state.update { it.copy(form = it.form.transform()) }
    }

    fun addLine() {
        _state.update { it.copy(lineEditor = LineEditorState(index = null)) }
    }

    fun editLine(index: Int) {
        val line = _state.value.form.lines[index]
        val name = ingredientNames.value[line.ingredientId].orEmpty()
        _state.update { it.copy(lineEditor = LineEditorState(index, RecipeLineForm.from(line, name))) }
    }

    fun onLineFormChange(transform: RecipeLineForm.() -> RecipeLineForm) {
        _state.update { state ->
            state.copy(lineEditor = state.lineEditor?.let { it.copy(form = it.form.transform()) })
        }
    }

    fun applyLine() {
        _state.update { state ->
            val editor = state.lineEditor ?: return@update state
            val line = editor.form.toDraft()
            if (line == null) {
                state.copy(lineEditor = editor.copy(showErrors = true))
            } else {
                state.copy(form = state.form.withLine(editor.index, line), lineEditor = null)
            }
        }
    }

    fun dismissLineEditor() {
        _state.update { it.copy(lineEditor = null) }
    }

    fun removeLine(index: Int) {
        _state.update { it.copy(form = it.form.removeLine(index)) }
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
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val ingredientNames by viewModel.ingredientNames.collectAsStateWithLifecycle()
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
        bottomBar = {
            // Live protein preview, so the effect of every edit is visible right away.
            preview?.let { nutrition ->
                Surface(tonalElevation = 3.dp) {
                    ProteinIndicator(
                        nutrition.perPortion.protein,
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
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

            Text(
                stringResource(R.string.recipe_section_lines),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp),
            )
            form.lines.forEachIndexed { index, line ->
                ListItem(
                    headlineContent = { Text(line.originalText) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.recipe_line_amount,
                                formatDecimal(line.grams),
                                ingredientNames[line.ingredientId].orEmpty(),
                            ),
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeLine(index) }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.recipe_line_remove))
                        }
                    },
                    modifier = Modifier.clickable { viewModel.editLine(index) },
                )
            }
            TextButton(onClick = viewModel::addLine) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.recipe_line_add), Modifier.padding(start = 8.dp))
            }
        }

        state.lineEditor?.let { editor ->
            LineEditorDialog(
                editor = editor,
                suggestions = suggestions,
                proteinPerPortion = preview?.perPortion?.protein,
                onChange = viewModel::onLineFormChange,
                onApply = viewModel::applyLine,
                onDismiss = viewModel::dismissLineEditor,
            )
        }
    }
}

@Composable
private fun LineEditorDialog(
    editor: LineEditorState,
    suggestions: List<Ingredient>,
    proteinPerPortion: Double?,
    onChange: (RecipeLineForm.() -> RecipeLineForm) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val form = editor.form
    val errors = if (editor.showErrors) form.errors() else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (editor.index == null) R.string.recipe_line_new else R.string.recipe_line_edit))
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormField(
                    value = form.originalText,
                    onValueChange = { onChange { copy(originalText = it) } },
                    label = R.string.recipe_line_text,
                    hint = R.string.recipe_line_text_hint,
                    capitalize = true,
                )
                FormField(
                    value = form.grams,
                    onValueChange = { onChange { copy(grams = it) } },
                    label = R.string.recipe_line_grams,
                    error = if (errors?.grams == true) R.string.error_positive_decimal else null,
                    keyboardType = KeyboardType.Decimal,
                )
                FormField(
                    value = form.ingredientQuery,
                    onValueChange = { onChange { withIngredientQuery(it) } },
                    label = R.string.recipe_line_ingredient,
                    error = if (errors?.ingredient == true) R.string.error_ingredient_required else null,
                    capitalize = true,
                )
                suggestions.forEach { ingredient ->
                    ListItem(
                        headlineContent = { Text(ingredient.name) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onChange { withIngredient(ingredient) } },
                    )
                }
                if (form.ingredientId == null && form.ingredientQuery.isNotBlank() && suggestions.isEmpty()) {
                    Text(
                        stringResource(R.string.recipe_line_no_match),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                proteinPerPortion?.let { ProteinIndicator(it, Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text(stringResource(R.string.action_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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
    keyboardType: KeyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
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
            keyboardType = keyboardType,
            capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        ),
        modifier = modifier,
    )
}
