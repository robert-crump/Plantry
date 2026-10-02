package com.example.plantry.ui.recipe

import androidx.annotation.StringRes
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
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
import com.example.plantry.data.RecipePhotoRepository
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.claude.ClaudeFailure
import com.example.plantry.data.claude.RecipeScanner
import com.example.plantry.data.claude.ScanResult
import com.example.plantry.data.settings.SettingsRepository
import com.example.plantry.ui.settings.message
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
    /** The photo of the cookbook page as compressed JPEG, stored or just taken. */
    val photo: ByteArray? = null,
    /** True once [photo] was taken or picked here, so saving stores it. */
    val photoChanged: Boolean = false,
    /** The last photo could not be read from the camera or gallery. */
    val photoUnreadable: Boolean = false,
    val scan: ScanState = ScanState.Done,
)

/** Reading the photo with Claude; the form shows once it is [Done] (or skipped). */
sealed interface ScanState {
    data object WaitingForPhoto : ScanState
    data object Reading : ScanState
    data class Failed(val reason: ClaudeFailure) : ScanState
    data object Done : ScanState
}

/** The line being edited in the dialog; a null [index] adds a new line. */
data class LineEditorState(
    val index: Int?,
    val form: RecipeLineForm = RecipeLineForm(),
    val showErrors: Boolean = false,
)

/**
 * Edits the recipe with [recipeId], or creates a new one when it is null. With [scan], the new
 * recipe is read from a photo by Claude first, then reviewed here.
 */
class RecipeEditViewModel(
    private val recipeId: Long?,
    scan: Boolean,
    private val repository: RecipeRepository,
    ingredientRepository: IngredientRepository,
    private val photos: RecipePhotoRepository,
    private val scanner: RecipeScanner,
    private val settings: SettingsRepository,
    private val compressPhoto: suspend (Uri) -> ByteArray?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RecipeEditUiState(scan = if (scan && recipeId == null) ScanState.WaitingForPhoto else ScanState.Done),
    )
    val state: StateFlow<RecipeEditUiState> = _state.asStateFlow()

    val isNew: Boolean get() = recipeId == null

    val isScan: Boolean = scan && recipeId == null

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
        val form = if (pending == null) state.form else state.form.withLine(editor.index, pending)
        val lines = form.completeLines()
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
                val photo = photos.get(recipeId)
                _state.update { it.copy(form = RecipeForm.from(recipe, lines), photo = it.photo ?: photo) }
            }
        }
    }

    /** Keeps the compressed photo; while scanning a new recipe, reads it right away. */
    fun onPhoto(uri: Uri) {
        viewModelScope.launch {
            val bytes = compressPhoto(uri)
            if (bytes == null) {
                _state.update { it.copy(photoUnreadable = true) }
                return@launch
            }
            _state.update { it.copy(photo = bytes, photoChanged = true, photoUnreadable = false) }
            if (_state.value.scan != ScanState.Done) readPhoto()
        }
    }

    /** Sends the photo to Claude; on failure the photo stays, so this can simply be retried. */
    fun readPhoto() {
        val photo = _state.value.photo ?: return
        if (_state.value.scan == ScanState.Reading) return
        val apiKey = settings.apiKey()
        if (apiKey == null) {
            _state.update { it.copy(scan = ScanState.Failed(ClaudeFailure.NO_API_KEY)) }
            return
        }
        _state.update { it.copy(scan = ScanState.Reading) }
        viewModelScope.launch {
            val result = scanner.scan(apiKey, settings.settings.value.scanModel, photo, ingredients.value)
            _state.update { state ->
                when (result) {
                    is ScanResult.Success -> state.copy(form = state.form.withScan(result.recipe), scan = ScanState.Done)
                    is ScanResult.Failure -> state.copy(scan = ScanState.Failed(result.reason))
                }
            }
        }
    }

    /** Gives up on reading and shows the empty form; the photo is kept. */
    fun skipScan() {
        _state.update { it.copy(scan = ScanState.Done) }
    }

    fun onFormChange(transform: RecipeForm.() -> RecipeForm) {
        _state.update { it.copy(form = it.form.transform()) }
    }

    fun addLine() {
        _state.update { it.copy(lineEditor = LineEditorState(index = null)) }
    }

    fun editLine(index: Int) {
        val line = _state.value.form.lines[index]
        val name = line.ingredientId?.let { ingredientNames.value[it] }
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
        val state = _state.value
        viewModelScope.launch {
            val id = recipeId?.also { repository.update(it, draft) } ?: repository.create(draft)
            if (state.photoChanged && state.photo != null) photos.save(id, state.photo)
            _state.update { it.copy(saved = true) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(
    viewModel: RecipeEditViewModel,
    onBack: () -> Unit,
    onCreateIngredient: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val ingredientNames by viewModel.ingredientNames.collectAsStateWithLifecycle()
    val photoSource = rememberPhotoSource(viewModel::onPhoto)
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    val form = state.form
    val errors = if (state.showErrors) form.errors() else null
    val showForm = state.scan == ScanState.Done

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                !viewModel.isNew -> R.string.recipe_edit
                                viewModel.isScan -> R.string.recipe_scan
                                else -> R.string.recipe_new
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (showForm) {
                        TextButton(onClick = viewModel::save) { Text(stringResource(R.string.action_save)) }
                    }
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
                .imePadding(),
        ) {
            // Fixed above the form, so the page stays in view while correcting the lines.
            PhotoSection(
                state = state,
                isScan = viewModel.isScan,
                photoSource = photoSource,
                onRead = viewModel::readPhoto,
                onSkipScan = viewModel::skipScan,
            )
            if (showForm) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RecipeFields(form, errors, viewModel::onFormChange)
                    Text(
                        stringResource(R.string.recipe_section_lines),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    if (errors?.lines == true) {
                        Text(
                            stringResource(R.string.error_lines_incomplete),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    form.lines.forEachIndexed { index, line ->
                        LineItem(
                            line = line,
                            ingredientName = line.ingredientId?.let { ingredientNames[it] },
                            onClick = { viewModel.editLine(index) },
                            onRemove = { viewModel.removeLine(index) },
                        )
                    }
                    TextButton(onClick = viewModel::addLine) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.recipe_line_add), Modifier.padding(start = 8.dp))
                    }
                }
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
                onCreateIngredient = onCreateIngredient,
            )
        }
    }
}

/** The page photo with its actions, and the progress or error of reading it. */
@Composable
private fun PhotoSection(
    state: RecipeEditUiState,
    isScan: Boolean,
    photoSource: PhotoSource,
    onRead: () -> Unit,
    onSkipScan: () -> Unit,
) {
    val photo = state.photo
    val scan = state.scan
    Column(Modifier.fillMaxWidth()) {
        if (photo != null) {
            ZoomablePhoto(
                photo,
                Modifier
                    .fillMaxWidth()
                    .height(if (scan == ScanState.Done) 240.dp else 420.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        } else if (isScan && scan != ScanState.Done) {
            Text(
                stringResource(R.string.scan_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
            )
        }

        when (scan) {
            ScanState.Reading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.scan_reading), style = MaterialTheme.typography.bodyMedium)
            }
            is ScanState.Failed -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    stringResource(scan.reason.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRead) { Text(stringResource(R.string.scan_retry)) }
                    TextButton(onClick = onSkipScan) { Text(stringResource(R.string.scan_skip)) }
                }
            }
            else -> Unit
        }
        if (state.photoUnreadable) {
            Text(
                stringResource(R.string.photo_unreadable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (scan != ScanState.Reading) {
            FlowRow(
                Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = photoSource.takePhoto) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Text(
                        stringResource(if (photo == null) R.string.photo_take else R.string.photo_retake),
                        Modifier.padding(start = 8.dp),
                    )
                }
                TextButton(onClick = photoSource.pickPhoto) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text(stringResource(R.string.photo_pick), Modifier.padding(start = 8.dp))
                }
            }
        }
        if (photo != null || (isScan && scan != ScanState.Done)) HorizontalDivider()
    }
}

@Composable
private fun RecipeFields(
    form: RecipeForm,
    errors: RecipeFormErrors?,
    onFormChange: (RecipeForm.() -> RecipeForm) -> Unit,
) {
    FormField(
        value = form.title,
        onValueChange = { onFormChange { copy(title = it) } },
        label = R.string.recipe_title,
        error = if (errors?.title == true) R.string.error_title_required else null,
        capitalize = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FormField(
            value = form.source,
            onValueChange = { onFormChange { copy(source = it) } },
            label = R.string.recipe_source,
            capitalize = true,
            modifier = Modifier.weight(2f),
        )
        FormField(
            value = form.page,
            onValueChange = { onFormChange { copy(page = it) } },
            label = R.string.recipe_page,
            error = if (errors?.page == true) R.string.error_positive_number else null,
            numeric = true,
            modifier = Modifier.weight(1f),
        )
    }
    FormField(
        value = form.bookServings,
        onValueChange = { onFormChange { withBookServings(it) } },
        label = R.string.recipe_book_servings,
        error = if (errors?.bookServings == true) R.string.error_positive_number else null,
        numeric = true,
    )
    FormField(
        value = form.ourServings,
        onValueChange = { onFormChange { withOurServings(it) } },
        label = R.string.recipe_our_servings,
        error = if (errors?.ourServings == true) R.string.error_positive_number else null,
        hint = R.string.recipe_our_servings_hint,
        numeric = true,
    )
    FormField(
        value = form.cookingTime,
        onValueChange = { onFormChange { copy(cookingTime = it) } },
        label = R.string.recipe_cooking_time_minutes,
        error = if (errors?.cookingTime == true) R.string.error_positive_number else null,
        numeric = true,
    )
}

/** One ingredient line; lines without an ingredient, and lines Claude was unsure about, stand out. */
@Composable
private fun LineItem(
    line: RecipeFormLine,
    ingredientName: String?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (container, label) = when {
        !line.complete -> colors.errorContainer to R.string.recipe_line_incomplete
        line.uncertain -> colors.tertiaryContainer to R.string.recipe_line_uncertain
        else -> Color.Transparent to null
    }
    ListItem(
        overlineContent = label?.let { { Text(stringResource(it)) } },
        headlineContent = { Text(line.originalText) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.recipe_line_amount,
                    if (line.grams > 0.0) formatDecimal(line.grams) else "?",
                    ingredientName ?: stringResource(R.string.recipe_line_no_ingredient),
                ),
            )
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, stringResource(R.string.recipe_line_remove))
            }
        },
        colors = ListItemDefaults.colors(containerColor = container),
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun LineEditorDialog(
    editor: LineEditorState,
    suggestions: List<Ingredient>,
    proteinPerPortion: Double?,
    onChange: (RecipeLineForm.() -> RecipeLineForm) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onCreateIngredient: () -> Unit,
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
                if (form.ingredientId == null) {
                    // The editor stays open; back from the new ingredient returns to it.
                    TextButton(onClick = onCreateIngredient) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.recipe_line_create_ingredient), Modifier.padding(start = 8.dp))
                    }
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
