package com.example.plantry.ui.recipe

import androidx.annotation.StringRes
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.BookPage
import com.example.plantry.data.BookSession
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.IngredientAliases
import com.example.plantry.data.IngredientSuggestions
import com.example.plantry.data.RecipeNutrition
import com.example.plantry.data.NewIngredientFinder
import com.example.plantry.data.Nutrition
import com.example.plantry.data.RecipePhotoRepository
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.SourceSuggestions
import com.example.plantry.data.claude.ClaudeFailure
import com.example.plantry.data.claude.ClaudeResult
import com.example.plantry.data.claude.NewFood
import com.example.plantry.data.claude.NutritionSource
import com.example.plantry.data.claude.RecipeScanner
import com.example.plantry.data.claude.ScanResult
import com.example.plantry.data.usda.UsdaCatalog
import com.example.plantry.data.usda.UsdaFood
import androidx.compose.material.icons.filled.AutoAwesome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import com.example.plantry.data.settings.SettingsRepository
import com.example.plantry.ui.settings.message
import com.example.plantry.ui.settings.ApiKeyDialog
import com.example.plantry.ui.settings.ApiKeyEntry
import com.example.plantry.data.claude.ConnectionTester
import com.example.plantry.data.nutritionLines
import com.example.plantry.ui.ingredient.LabelNutritionDialog
import com.example.plantry.ui.ingredient.LabelNutritionForm
import com.example.plantry.ui.ingredient.formatDecimal
import com.example.plantry.ui.currentLocale
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
    /** Proposing new ingredients for the scanned lines that matched nothing. */
    val proposals: ProposalState = ProposalState.Idle,
    /** Null while no other USDA entry is being picked. */
    val usdaPicker: UsdaPickerState? = null,
    /** The new ingredient whose package values are being entered, if any. */
    val labelNutritionFor: Long? = null,
    /** A scan is reviewed in two steps: the recipe fields first, then (once true) the lines. */
    val linesStep: Boolean = false,
    /** The new recipe is being read from a photo (see [scan]) or that scan is being reviewed. */
    val scanning: Boolean = false,
    /** Starting a scan waits for "Ersetzen", because the form already has input. */
    val confirmReplace: Boolean = false,
    /** The sheet to take the page photo with the camera or pick it from the gallery is open. */
    val choosingPhotoSource: Boolean = false,
    /** Null unless a Claude feature waits for the costs to be accepted or a key to be entered. */
    val claudeGate: ClaudeGate? = null,
)

/** What uses Claude here; each waits until Claude is set up, see [RecipeEditViewModel.useClaude]. */
enum class ClaudeAction { SCAN, PROPOSE_LINE }

/** [action] waits for [step]: first accepting the costs, then entering a key. */
data class ClaudeGate(val action: ClaudeAction, val step: Step) {
    enum class Step { COSTS, KEY }
}

sealed interface ProposalState {
    data object Idle : ProposalState
    data object Loading : ProposalState
    data class Failed(val reason: ClaudeFailure) : ProposalState
}

/** Picking another USDA entry for the new ingredient [ingredientId]. */
data class UsdaPickerState(val ingredientId: Long, val query: String)

/** Reading the photo with Claude; the form shows once it is [Done] (or skipped). */
sealed interface ScanState {
    data object Reading : ScanState
    data class Failed(val reason: ClaudeFailure) : ScanState
    data object Done : ScanState
}

/** The line being edited in the dialog; a null [index] adds a new line. */
data class LineEditorState(
    val index: Int?,
    val form: RecipeLineForm = RecipeLineForm(),
    val showErrors: Boolean = false,
    /** Claude is proposing a new ingredient for the typed name. */
    val proposing: Boolean = false,
    val proposalFailure: ClaudeFailure? = null,
    /** Applying opens the next problem line, see [RecipeForm.nextProblem]. */
    val toNextProblem: Boolean = false,
)

/**
 * Edits the recipe with [recipeId], or creates a new one when it is null. A new recipe can instead
 * be read from a photo by Claude ([startScan]), then reviewed here.
 */
class RecipeEditViewModel(
    private val recipeId: Long?,
    private val repository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val photos: RecipePhotoRepository,
    private val scanner: RecipeScanner,
    private val newIngredientFinder: NewIngredientFinder,
    private val loadCatalog: suspend () -> UsdaCatalog,
    private val settings: SettingsRepository,
    private val bookSession: BookSession,
    private val compressPhoto: suspend (Uri) -> ByteArray?,
    tester: ConnectionTester,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeEditUiState())
    val state: StateFlow<RecipeEditUiState> = _state.asStateFlow()

    /** The key entered while a Claude feature waits; once it is stored, that feature runs. */
    val keyEntry: ApiKeyEntry = ApiKeyEntry(settings, tester, viewModelScope) {
        val action = _state.value.claudeGate?.action
        keyEntry.reset()
        action?.let(::useClaude)
    }

    val isNew: Boolean get() = recipeId == null

    /** Reading the photo; cancelled when the scan is left, so a late answer can't fill the form. */
    private var reading: Job? = null

    private val ingredients: StateFlow<List<Ingredient>> = ingredientRepository.observeIngredients()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Learned aliases, ingredient id by normalized wording. */
    private val aliases: StateFlow<Map<String, Long>> = ingredientRepository.observeAliases()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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
        val all = ingredients + state.form.previewIngredients()
        RecipeNutrition.calculate(nutritionLines(lines, all.associateBy { it.id }), servings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Matches for the ingredient field of the open line editor: own table and new ingredients, the
     * ingredient the typed wording is a learned alias of first.
     */
    val suggestions: StateFlow<List<Ingredient>> = combine(_state, ingredients, aliases) { state, ingredients, aliases ->
        val form = state.lineEditor?.form
        if (form == null || form.ingredientId != null) {
            emptyList()
        } else {
            val all = ingredients + state.form.previewIngredients()
            val aliased = IngredientAliases.match(form.ingredientQuery, aliases)?.let { id -> all.find { it.id == id } }
            val matches = IngredientSuggestions.match(form.ingredientQuery, all)
            if (aliased == null) matches else (listOf(aliased) + (matches - aliased)).take(IngredientSuggestions.DEFAULT_LIMIT)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ingredient names by id, including new ones by their temporary id, for displaying the lines. */
    val ingredientNames: StateFlow<Map<Long, String>> =
        combine(ingredients, _state.map { it.form.newIngredients }.distinctUntilChanged()) { all, new ->
            all.associate { it.id to it.name } + new.mapValues { it.value.proposal.name }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Sources of other recipes matching what is typed into the source field. */
    val sourceSuggestions: StateFlow<List<String>> = combine(
        _state.map { it.form.source }.distinctUntilChanged(),
        repository.observeRecipes().map { recipes -> recipes.map { it.source } },
    ) { query, sources -> SourceSuggestions.match(query, sources) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Search results of the USDA picker; null while the data is loading or no picker is open. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val usdaResults: StateFlow<List<UsdaFood>?> = _state
        .map { it.usdaPicker?.query }
        .distinctUntilChanged()
        .mapLatest { query -> query?.let { loadCatalog().search(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Temporary ids of new ingredients count down from -1, see [RecipeForm.newIngredients]. */
    private var lastTempId = 0L

    private fun newTempId() = --lastTempId

    private var saving = false

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

    /**
     * Runs [action] once the costs of Claude are accepted and a key is stored; asks for what is
     * missing first ([RecipeEditUiState.claudeGate]).
     */
    fun useClaude(action: ClaudeAction) {
        val current = settings.settings.value
        val step = when {
            !current.claudeCostsAccepted -> ClaudeGate.Step.COSTS
            !current.hasApiKey -> ClaudeGate.Step.KEY
            else -> null
        }
        _state.update { it.copy(claudeGate = step?.let { ClaudeGate(action, it) }) }
        if (step == null) {
            when (action) {
                ClaudeAction.SCAN -> startScan()
                ClaudeAction.PROPOSE_LINE -> proposeForLine()
            }
        }
    }

    fun acceptClaudeCosts() {
        val action = _state.value.claudeGate?.action ?: return
        settings.acceptClaudeCosts()
        useClaude(action)
    }

    fun dismissClaudeGate() {
        keyEntry.reset()
        _state.update { it.copy(claudeGate = null) }
    }

    /** Keeps the compressed photo of the page and reads the new recipe from it right away. */
    fun onPhoto(uri: Uri) {
        viewModelScope.launch {
            val bytes = compressPhoto(uri)
            if (bytes == null) {
                _state.update { it.copy(photoUnreadable = true) }
                return@launch
            }
            _state.update {
                it.copy(photo = bytes, photoChanged = true, photoUnreadable = false, scanning = true, linesStep = false, showErrors = false)
            }
            readPhoto()
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
        reading = viewModelScope.launch {
            val result = scanner.scan(apiKey, settings.settings.value.scanModel, photo, ingredients.value)
            _state.update { state ->
                when (result) {
                    is ScanResult.Success -> {
                        // Claude never reads the book, so the source always comes from the session.
                        val book = bookSession.defaults(BookPage(source = "", page = result.recipe.page))
                        state.copy(form = RecipeForm().withScan(result.recipe, book, aliases.value), scan = ScanState.Done)
                    }
                    is ScanResult.Failure -> state.copy(scan = ScanState.Failed(result.reason))
                }
            }
            if (result is ScanResult.Success) proposeNewIngredients()
        }
    }

    /**
     * Has Claude propose new ingredients for the lines without one. The form is usable meanwhile;
     * lines the user resolved in the meantime keep their ingredient.
     */
    fun proposeNewIngredients() {
        if (_state.value.proposals == ProposalState.Loading) return
        val foods = _state.value.form.unmatchedFoods(::newTempId)
        if (foods.isEmpty()) {
            _state.update { it.copy(proposals = ProposalState.Idle) }
            return
        }
        val apiKey = settings.apiKey()
        if (apiKey == null) {
            _state.update { it.copy(proposals = ProposalState.Failed(ClaudeFailure.NO_API_KEY)) }
            return
        }
        _state.update { it.copy(proposals = ProposalState.Loading) }
        viewModelScope.launch {
            val result = newIngredientFinder.propose(apiKey, settings.settings.value.scanModel, foods)
            _state.update { state ->
                when (result) {
                    is ClaudeResult.Success ->
                        state.copy(form = state.form.withProposals(foods, result.value), proposals = ProposalState.Idle)
                    is ClaudeResult.Failure -> state.copy(proposals = ProposalState.Failed(result.reason))
                }
            }
        }
    }

    /** Has Claude propose a new ingredient for the name typed into the open line editor. */
    fun proposeForLine() {
        val editor = _state.value.lineEditor ?: return
        val name = editor.form.ingredientQuery.trim()
        if (editor.proposing || name.isEmpty()) return
        val apiKey = settings.apiKey()
        if (apiKey == null) {
            _state.update { it.copy(lineEditor = editor.copy(proposalFailure = ClaudeFailure.NO_API_KEY)) }
            return
        }
        val food = NewFood(newTempId(), name, editor.form.originalText.trim(), searchTerms = emptyList())
        _state.update { it.copy(lineEditor = editor.copy(proposing = true, proposalFailure = null)) }
        viewModelScope.launch {
            val result = newIngredientFinder.propose(apiKey, settings.settings.value.scanModel, listOf(food))
            _state.update { state ->
                // Only the editor that asked takes the answer; a closed one leaves an unused proposal.
                val current = state.lineEditor?.takeIf { it.proposing }
                when (result) {
                    is ClaudeResult.Success -> {
                        val proposal = result.value.getValue(food.id)
                        state.copy(
                            form = state.form.withNewIngredient(food.id, proposal),
                            lineEditor = current?.copy(form = current.form.withIngredient(food.id, proposal.name), proposing = false),
                        )
                    }
                    is ClaudeResult.Failure ->
                        state.copy(lineEditor = current?.copy(proposing = false, proposalFailure = result.reason))
                }
            }
        }
    }

    fun confirmNewIngredient(id: Long) {
        _state.update { it.copy(form = it.form.confirmNewIngredient(id)) }
    }

    fun openUsdaPicker(id: Long) {
        val proposal = _state.value.form.newIngredients[id]?.proposal ?: return
        val query = proposal.searchTerms.firstOrNull() ?: proposal.food?.description?.substringBefore(',').orEmpty()
        _state.update { it.copy(usdaPicker = UsdaPickerState(id, query)) }
    }

    fun onUsdaQueryChange(query: String) {
        _state.update { state -> state.copy(usdaPicker = state.usdaPicker?.copy(query = query)) }
    }

    fun pickUsda(food: UsdaFood) {
        _state.update { state ->
            val picker = state.usdaPicker ?: return@update state
            state.copy(form = state.form.withNewIngredientFood(picker.ingredientId, food), usdaPicker = null)
        }
    }

    fun dismissUsdaPicker() {
        _state.update { it.copy(usdaPicker = null) }
    }

    /** Opens the package values for new ingredient [id]; replaces the USDA picker if it is open. */
    fun openLabelNutrition(id: Long) {
        _state.update { it.copy(usdaPicker = null, labelNutritionFor = id) }
    }

    fun saveLabelNutrition(nutrition: Nutrition) {
        _state.update { state ->
            val id = state.labelNutritionFor ?: return@update state
            state.copy(form = state.form.withNewIngredientLabel(id, nutrition), labelNutritionFor = null)
        }
    }

    fun dismissLabelNutrition() {
        _state.update { it.copy(labelNutritionFor = null) }
    }

    /**
     * Offers camera or gallery for the photo to read a new recipe from; asks first
     * ([RecipeEditUiState.confirmReplace]) when there is input the scan would replace.
     */
    fun startScan(confirmed: Boolean = false) {
        if (!isNew) return
        _state.update { state ->
            if (!confirmed && (!state.form.isEmpty || state.photo != null)) {
                state.copy(confirmReplace = true)
            } else {
                state.copy(choosingPhotoSource = true, confirmReplace = false)
            }
        }
    }

    fun dismissPhotoSource() {
        _state.update { it.copy(choosingPhotoSource = false) }
    }

    fun dismissReplace() {
        _state.update { it.copy(confirmReplace = false) }
    }

    /** Gives up on reading and goes back to the manual form; input and photo so far are kept. */
    fun skipScan() {
        reading?.cancel()
        _state.update { it.copy(scanning = false, scan = ScanState.Done) }
    }

    /** Leaves the recipe fields of a scan for its lines, once the fields are valid. */
    fun showLines() {
        _state.update { state ->
            if (state.form.errors().fields) state.copy(showErrors = true) else state.copy(linesStep = true, showErrors = false)
        }
    }

    fun showFields() {
        _state.update { it.copy(linesStep = false) }
    }

    fun onFormChange(transform: RecipeForm.() -> RecipeForm) {
        _state.update { it.copy(form = it.form.transform()) }
    }

    fun addLine() {
        _state.update { it.copy(lineEditor = LineEditorState(index = null)) }
    }

    fun editLine(index: Int) {
        _state.update { it.copy(lineEditor = lineEditor(it.form, index)) }
    }

    /** Opened on a problem line, applying it moves on to the next problem line. */
    private fun lineEditor(form: RecipeForm, index: Int): LineEditorState {
        val line = form.lines[index]
        val name = line.ingredientId?.let { ingredientNames.value[it] }
        return LineEditorState(index, RecipeLineForm.from(line, name), toNextProblem = form.problem(line) != null)
    }

    fun onLineFormChange(transform: RecipeLineForm.() -> RecipeLineForm) {
        _state.update { state ->
            state.copy(lineEditor = state.lineEditor?.let { it.copy(form = it.form.transform()) })
        }
    }

    fun applyLine() {
        _state.update { state ->
            val editor = state.lineEditor ?: return@update state
            // A typed wording the user confirmed before needs no picking.
            val line = editor.form.withAlias(aliases.value, ingredientNames.value).toFormLine()
            if (line == null || !state.form.isReady(line.ingredientId)) {
                state.copy(lineEditor = editor.copy(showErrors = true))
            } else {
                val form = state.form.withLine(editor.index, line)
                val next = editor.index?.takeIf { editor.toNextProblem }?.let(form::nextProblem)
                state.copy(form = form, lineEditor = next?.let { lineEditor(form, it) })
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
        if (saving) return
        saving = true
        val state = _state.value
        viewModelScope.launch {
            val newIds = ingredientRepository.createProposed(state.form.newIngredientsToCreate())
            val saved = draft.withIngredientIds(newIds)
            ingredientRepository.learnAliases(state.form.aliasesToLearn(newIds))
            val id = recipeId?.also { repository.update(it, saved) } ?: repository.create(saved)
            if (state.photoChanged && state.photo != null) photos.save(id, state.photo)
            if (state.scanning) bookSession.remember(saved.source, saved.page)
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
    val sourceSuggestions by viewModel.sourceSuggestions.collectAsStateWithLifecycle()
    val photoSource = rememberPhotoSource(viewModel::onPhoto)
    var showingScanInfo by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    val form = state.form
    val errors = if (state.showErrors) form.errors() else null
    val isScan = state.scanning
    val showForm = state.scan == ScanState.Done
    // A scan is reviewed in two steps, recipe fields then lines; otherwise both show at once.
    val showFields = showForm && !(isScan && state.linesStep)
    val showLines = showForm && (!isScan || state.linesStep)
    // Back steps out of the scan's photo and lines steps before it leaves the screen.
    val back = when {
        isScan && !showForm -> viewModel::skipScan
        isScan && state.linesStep -> viewModel::showFields
        else -> onBack
    }
    BackHandler(enabled = isScan && (!showForm || state.linesStep), onBack = back)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (viewModel.isNew) R.string.recipe_new else R.string.recipe_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (showLines) {
                        // Enabled once there are lines and none needs attention; field errors still show on a click.
                        TextButton(onClick = viewModel::save, enabled = form.errors().let { !it.lines && !it.noLines }) {
                            Text(stringResource(R.string.action_save))
                        }
                    } else if (showFields) {
                        TextButton(onClick = viewModel::showLines) { Text(stringResource(R.string.action_next)) }
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
            if (viewModel.isNew && !isScan) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { viewModel.useClaude(ClaudeAction.SCAN) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Text(stringResource(R.string.recipe_scan_start), Modifier.padding(start = 8.dp))
                    }
                    IconButton(onClick = { showingScanInfo = true }) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.recipe_scan_info_description))
                    }
                }
            }
            // A scan shows the photo only until it is read; the review works from the fields alone.
            if (!isScan || !showForm) {
                // Fixed above the form, so the page stays in view while correcting the lines.
                PhotoSection(
                    state = state,
                    onRead = viewModel::readPhoto,
                    onSkipScan = viewModel::skipScan,
                )
            }
            if (showForm) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showFields) {
                        RecipeFields(
                            form = form,
                            errors = errors,
                            sourceSuggestions = sourceSuggestions,
                            onFormChange = viewModel::onFormChange,
                            onDone = if (isScan) viewModel::showLines else null,
                        )
                        if (isScan) {
                            Button(onClick = viewModel::showLines, Modifier.align(Alignment.End).padding(top = 8.dp)) {
                                Text(stringResource(R.string.action_next))
                            }
                        }
                    }
                    if (showLines) {
                        Text(
                            stringResource(R.string.recipe_section_lines),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        val problems = form.lines.count { form.problem(it) != null }
                        if (form.lines.isEmpty()) {
                            Text(
                                stringResource(R.string.recipe_lines_required),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (problems > 0) {
                            Text(
                                pluralStringResource(R.plurals.recipe_lines_to_check, problems, problems),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (errors?.lines == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ProposalStatus(state.proposals, onRetry = viewModel::proposeNewIngredients)
                        // Problem lines first, so only the top of the list needs attention.
                        form.checklistOrder().forEach { index ->
                            val line = form.lines[index]
                            val newId = line.ingredientId?.takeIf { it in form.newIngredients }
                            LineItem(
                                line = line,
                                ingredientName = line.ingredientId?.let { ingredientNames[it] },
                                problem = form.problem(line),
                                onClick = { viewModel.editLine(index) },
                                onRemove = { viewModel.removeLine(index) },
                            )
                            if (newId != null && !form.isReady(newId)) {
                                NewIngredientMatch(
                                    newIngredient = form.newIngredients.getValue(newId),
                                    isError = errors?.lines == true,
                                    onConfirm = { viewModel.confirmNewIngredient(newId) },
                                    onChange = { viewModel.openUsdaPicker(newId) },
                                    onEnterLabel = { viewModel.openLabelNutrition(newId) },
                                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                                )
                            }
                        }
                        TextButton(onClick = viewModel::addLine) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(stringResource(R.string.recipe_line_add), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }

        if (state.choosingPhotoSource) {
            PhotoSourceSheet(
                onTakePhoto = {
                    viewModel.dismissPhotoSource()
                    photoSource.takePhoto()
                },
                onPickPhoto = {
                    viewModel.dismissPhotoSource()
                    photoSource.pickPhoto()
                },
                onDismiss = viewModel::dismissPhotoSource,
            )
        }

        if (showingScanInfo) {
            AlertDialog(
                onDismissRequest = { showingScanInfo = false },
                text = { Text(stringResource(R.string.scan_intro)) },
                confirmButton = {
                    TextButton(onClick = { showingScanInfo = false }) { Text(stringResource(R.string.action_ok)) }
                },
            )
        }

        if (state.confirmReplace) {
            AlertDialog(
                onDismissRequest = viewModel::dismissReplace,
                text = { Text(stringResource(R.string.recipe_scan_replace)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.startScan(confirmed = true) }) {
                        Text(stringResource(R.string.action_replace))
                    }
                },
                dismissButton = { TextButton(onClick = viewModel::dismissReplace) { Text(stringResource(R.string.action_cancel)) } },
            )
        }

        state.lineEditor?.let { editor ->
            LineEditorDialog(
                editor = editor,
                newIngredient = editor.form.ingredientId?.let { form.newIngredients[it] },
                hasNextProblem = editor.toNextProblem && editor.index?.let(form::nextProblem) != null,
                suggestions = suggestions,
                onChange = viewModel::onLineFormChange,
                onApply = viewModel::applyLine,
                onDismiss = viewModel::dismissLineEditor,
                onProposeIngredient = { viewModel.useClaude(ClaudeAction.PROPOSE_LINE) },
                onConfirmNew = viewModel::confirmNewIngredient,
                onChangeNew = viewModel::openUsdaPicker,
                onEnterLabelNew = viewModel::openLabelNutrition,
            )
        }

        // After the line editor, so it opens on top of it.
        state.usdaPicker?.let { picker ->
            val results by viewModel.usdaResults.collectAsStateWithLifecycle()
            UsdaPickerDialog(
                query = picker.query,
                results = results,
                onQueryChange = viewModel::onUsdaQueryChange,
                onPick = viewModel::pickUsda,
                onWithoutUsda = { viewModel.openLabelNutrition(picker.ingredientId) },
                onDismiss = viewModel::dismissUsdaPicker,
            )
        }

        state.labelNutritionFor?.let { id ->
            val source = form.newIngredients[id]?.proposal?.source
            LabelNutritionDialog(
                confirmLabel = R.string.action_apply,
                onConfirm = { _, nutrition -> viewModel.saveLabelNutrition(nutrition) },
                onDismiss = viewModel::dismissLabelNutrition,
                initial = (source as? NutritionSource.Label)?.let { LabelNutritionForm.from(it.nutrition) } ?: LabelNutritionForm(),
            )
        }

        // Last, so it opens on top of the line editor it may come from.
        state.claudeGate?.let { gate ->
            when (gate.step) {
                ClaudeGate.Step.COSTS -> AlertDialog(
                    onDismissRequest = viewModel::dismissClaudeGate,
                    title = { Text(stringResource(R.string.claude_costs_title)) },
                    text = { Text(stringResource(R.string.claude_costs_message)) },
                    confirmButton = {
                        TextButton(onClick = viewModel::acceptClaudeCosts) { Text(stringResource(R.string.claude_costs_accept)) }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissClaudeGate) { Text(stringResource(R.string.action_cancel)) }
                    },
                )
                ClaudeGate.Step.KEY -> {
                    val keyCheck by viewModel.keyEntry.check.collectAsStateWithLifecycle()
                    ApiKeyDialog(
                        check = keyCheck,
                        onSave = viewModel.keyEntry::save,
                        onEdit = viewModel.keyEntry::reset,
                        onDismiss = viewModel::dismissClaudeGate,
                        message = R.string.settings_api_key_claude_message,
                    )
                }
            }
        }
    }
}

/** Progress or failure of proposing new ingredients for the scanned lines. */
@Composable
private fun ProposalStatus(proposals: ProposalState, onRetry: () -> Unit) {
    when (proposals) {
        ProposalState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.new_ingredient_proposing), style = MaterialTheme.typography.bodyMedium)
        }
        is ProposalState.Failed -> Column {
            Text(
                stringResource(R.string.new_ingredient_failed, stringResource(proposals.reason.message)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.scan_retry)) }
        }
        ProposalState.Idle -> Unit
    }
}

/** Camera or gallery for the page photo a new recipe is read from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSourceSheet(onTakePhoto: () -> Unit, onPickPhoto: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.photo_take)) },
                leadingContent = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onTakePhoto),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.photo_pick)) },
                leadingContent = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onPickPhoto),
            )
        }
    }
}

/** The page photo, and the progress or error of reading it. */
@Composable
private fun PhotoSection(
    state: RecipeEditUiState,
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
            ScanState.Done -> Unit
        }
        if (state.photoUnreadable) {
            Text(
                stringResource(R.string.photo_unreadable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (photo != null) HorizontalDivider()
    }
}

/**
 * The recipe fields in input order; IME "Next" moves to the following field. The last one calls
 * [onDone] (a scan's next step) when given, else just closes the keyboard.
 */
@Composable
private fun RecipeFields(
    form: RecipeForm,
    errors: RecipeFormErrors?,
    sourceSuggestions: List<String>,
    onFormChange: (RecipeForm.() -> RecipeForm) -> Unit,
    onDone: (() -> Unit)?,
) {
    FormField(
        value = form.title,
        onValueChange = { onFormChange { copy(title = it) } },
        label = R.string.recipe_title,
        error = if (errors?.title == true) R.string.error_title_required else null,
        capitalize = true,
        imeAction = ImeAction.Next,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceField(
            value = form.source,
            suggestions = sourceSuggestions,
            onValueChange = { onFormChange { copy(source = it) } },
            modifier = Modifier.weight(2f),
        )
        FormField(
            value = form.page,
            onValueChange = { onFormChange { copy(page = it) } },
            label = R.string.recipe_page,
            error = if (errors?.page == true) R.string.error_positive_number else null,
            numeric = true,
            imeAction = ImeAction.Next,
            modifier = Modifier.weight(1f),
        )
    }
    FormField(
        value = form.bookServings,
        onValueChange = { onFormChange { withBookServings(it) } },
        label = R.string.recipe_book_servings,
        error = if (errors?.bookServings == true) R.string.error_positive_number else null,
        numeric = true,
        imeAction = ImeAction.Next,
    )
    FormField(
        value = form.ourServings,
        onValueChange = { onFormChange { withOurServings(it) } },
        label = R.string.recipe_our_servings,
        error = if (errors?.ourServings == true) R.string.error_positive_number else null,
        hint = R.string.recipe_our_servings_hint,
        numeric = true,
        imeAction = ImeAction.Next,
    )
    FormField(
        value = form.cookingTime,
        onValueChange = { onFormChange { copy(cookingTime = it) } },
        label = R.string.recipe_cooking_time_minutes,
        error = if (errors?.cookingTime == true) R.string.error_positive_number else null,
        hint = R.string.recipe_cooking_time_hint,
        numeric = true,
        imeAction = if (onDone != null) ImeAction.Next else ImeAction.Done,
        onImeAction = onDone,
    )
}

/** The source field, suggesting the sources of other recipes while typing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceField(
    value: String,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Opens on typing and closes on picking or dismissing, so a picked source stays quiet.
    var typing by remember { mutableStateOf(false) }
    val expanded = typing && suggestions.isNotEmpty()
    val focusManager = LocalFocusManager.current
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (!it) typing = false },
        modifier = modifier,
    ) {
        FormField(
            value = value,
            onValueChange = {
                typing = true
                onValueChange(it)
            },
            label = R.string.recipe_source,
            capitalize = true,
            imeAction = ImeAction.Next,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { typing = false }) {
            suggestions.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source) },
                    onClick = {
                        typing = false
                        onValueChange(source)
                        focusManager.moveFocus(FocusDirection.Next)
                    },
                )
            }
        }
    }
}

/**
 * One checklist row: original text, grams and the matched ingredient. A [problem] line stands out
 * and says what is missing; a resolved one shows a ✓.
 */
@Composable
private fun LineItem(
    line: RecipeFormLine,
    ingredientName: String?,
    problem: LineProblem?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (container, label) = when (problem) {
        LineProblem.NO_INGREDIENT -> colors.errorContainer to R.string.recipe_line_problem_ingredient
        LineProblem.NO_GRAMS -> colors.errorContainer to R.string.recipe_line_problem_grams
        LineProblem.NEW_INGREDIENT -> colors.tertiaryContainer to R.string.recipe_line_new_ingredient
        LineProblem.UNCERTAIN -> colors.tertiaryContainer to R.string.recipe_line_uncertain
        null -> Color.Transparent to null
    }
    // Not a ListItem: its leading icon is centred in a two-line row, here it sits on the first line.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (problem == null) {
            Icon(Icons.Filled.Check, stringResource(R.string.recipe_line_resolved), tint = colors.primary)
        }
        Column(Modifier.weight(1f)) {
            label?.let {
                Text(stringResource(it), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Text(line.originalText, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    R.string.recipe_line_amount,
                    if (line.grams > 0.0) formatDecimal(line.grams, currentLocale()) else "?",
                    ingredientName ?: stringResource(R.string.recipe_line_no_ingredient),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove, Modifier.align(Alignment.CenterVertically)) {
            Icon(Icons.Filled.Close, stringResource(R.string.recipe_line_remove))
        }
    }
}

@Composable
private fun LineEditorDialog(
    editor: LineEditorState,
    newIngredient: NewIngredient?,
    /** Applying moves on to another problem line, so the confirm button reads "Weiter". */
    hasNextProblem: Boolean,
    suggestions: List<Ingredient>,
    onChange: (RecipeLineForm.() -> RecipeLineForm) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onProposeIngredient: () -> Unit,
    onConfirmNew: (Long) -> Unit,
    onChangeNew: (Long) -> Unit,
    onEnterLabelNew: (Long) -> Unit,
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
                    imeAction = ImeAction.Next,
                )
                // With an ingredient already chosen, the keyboard's action on the grams applies the line.
                val gramsLast = form.ingredientId != null
                FormField(
                    value = form.grams,
                    onValueChange = { onChange { copy(grams = it) } },
                    label = R.string.recipe_line_grams,
                    error = if (errors?.grams == true) R.string.error_positive_decimal else null,
                    keyboardType = KeyboardType.Decimal,
                    imeAction = if (!gramsLast) ImeAction.Next else if (hasNextProblem) ImeAction.Go else ImeAction.Done,
                    onImeAction = if (gramsLast) onApply else null,
                )
                FormField(
                    value = form.ingredientQuery,
                    onValueChange = { onChange { withIngredientQuery(it) } },
                    label = R.string.recipe_line_ingredient,
                    error = if (errors?.ingredient == true) R.string.error_ingredient_required else null,
                    capitalize = true,
                    imeAction = if (hasNextProblem) ImeAction.Go else ImeAction.Done,
                    onImeAction = onApply,
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
                val ingredientId = form.ingredientId
                if (newIngredient != null && ingredientId != null) {
                    NewIngredientMatch(
                        newIngredient = newIngredient,
                        isError = editor.showErrors,
                        onConfirm = { onConfirmNew(ingredientId) },
                        onChange = { onChangeNew(ingredientId) },
                        onEnterLabel = { onEnterLabelNew(ingredientId) },
                    )
                }
                if (ingredientId == null) {
                    if (editor.proposing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.new_ingredient_proposing_one), style = MaterialTheme.typography.bodySmall)
                    } else if (form.ingredientQuery.isNotBlank()) {
                        TextButton(onClick = onProposeIngredient) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                            Text(
                                stringResource(R.string.recipe_line_propose_ingredient, form.ingredientQuery.trim()),
                                Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    editor.proposalFailure?.let { failure ->
                        Text(
                            stringResource(failure.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApply) {
                Text(stringResource(if (hasNextProblem) R.string.action_next else R.string.action_apply))
            }
        },
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
    imeAction: ImeAction = ImeAction.Default,
    /** Replaces the default IME action (moving focus on Next, closing the keyboard on Done). */
    onImeAction: (() -> Unit)? = null,
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
            imeAction = imeAction,
        ),
        keyboardActions = onImeAction?.let { action -> KeyboardActions { action() } } ?: KeyboardActions.Default,
        modifier = modifier,
    )
}
