package com.example.plantry.ui.recipe

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.annotation.StringRes
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.LineAmount
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.LineUnit
import com.example.plantry.data.UsualUnits
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.IngredientAliases
import com.example.plantry.data.IngredientSuggestions
import com.example.plantry.data.Dish
import com.example.plantry.data.NewIngredientFinder
import com.example.plantry.data.Nutrition
import com.example.plantry.data.RecipePhotoRepository
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.SourceSuggestions
import com.example.plantry.data.StoreSection
import com.example.plantry.data.claude.ClaudeFailure
import com.example.plantry.data.claude.ClaudeResult
import com.example.plantry.data.claude.NutritionSource
import com.example.plantry.data.claude.RecipeScanner
import com.example.plantry.data.claude.ScanResult
import com.example.plantry.data.claude.StepsCondenser
import com.example.plantry.data.usda.UsdaCatalog
import com.example.plantry.data.usda.UsdaFood
import androidx.compose.material.icons.filled.AutoAwesome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import com.example.plantry.data.settings.SettingsRepository
import com.example.plantry.ui.DraggableColumn
import com.example.plantry.ui.LinearWavyProgressIndicator
import com.example.plantry.ui.settings.message
import com.example.plantry.ui.settings.ChoiceDialog
import com.example.plantry.ui.settings.SettingsGroup
import com.example.plantry.ui.settings.SettingsRow
import com.example.plantry.ui.settings.ApiKeyDialog
import com.example.plantry.ui.settings.ApiKeyEntry
import com.example.plantry.data.claude.ConnectionTester
import com.example.plantry.ui.ingredient.LabelNutritionDialog
import com.example.plantry.ui.ingredient.LabelNutritionForm
import com.example.plantry.ui.ingredient.label
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
    /** Closed without saving. */
    val closed: Boolean = false,
    /** The photo of the cookbook page a new recipe is read from, as compressed JPEG. */
    val photo: ByteArray? = null,
    /** True once [photo] was taken or picked here, so saving stores it. */
    val photoChanged: Boolean = false,
    /** The stored page photos of a saved recipe, in order. */
    val pages: List<ByteArray> = emptyList(),
    /** Indexes of [pages] removed here; saving deletes them, discarding keeps them. */
    val deletedPages: Set<Int> = emptySet(),
    /** The index of [pages] waiting for "Löschen", if any. */
    val confirmDeletePage: Int? = null,
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
    /** The form as loaded: the saved recipe, or the empty form of a new one. */
    val loadedForm: RecipeForm = RecipeForm(),
    /** Closing waits for "Verwerfen", because [hasChanges]. */
    val confirmDiscard: Boolean = false,
) {
    /**
     * Closing would lose something: the form differs from [loadedForm], a photo was taken for a
     * scan, or a page photo was removed.
     */
    val hasChanges: Boolean
        get() = scanning || photoChanged || deletedPages.isNotEmpty() || form != loadedForm

    /** The indexes of [pages] still shown, in order. */
    val shownPages: List<Int>
        get() = pages.indices.filterNot { it in deletedPages }
}

/** What uses Claude here; each waits until Claude is set up, see [RecipeEditViewModel.useClaude]. */
enum class ClaudeAction { SCAN, CONDENSE_STEPS }

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
    stepsCondenser: StepsCondenser,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeEditUiState())
    val state: StateFlow<RecipeEditUiState> = _state.asStateFlow()

    /** Kürzen on the Zubereitung field. */
    val steps = StepsAssistant(
        scope = viewModelScope,
        condenser = stepsCondenser,
        apiKey = settings::apiKey,
        model = { settings.settings.value.scanModel },
        text = { _state.value.form.steps },
        setText = { text -> _state.update { it.copy(form = it.form.copy(steps = text)) } },
    )

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

    private val storedUnits: StateFlow<Map<Long, List<IngredientUnit>>> = ingredientRepository.observeUnits()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Learned aliases, ingredient id by normalized wording. */
    private val aliases: StateFlow<Map<String, Long>> = ingredientRepository.observeAliases()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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

    /** Store sections of the stored ingredients by id; new ones have theirs in the form, see [RecipeForm.checklistGroups]. */
    val ingredientSections: StateFlow<Map<Long, StoreSection>> = ingredients
        .map { all -> all.associate { it.id to it.storeSection } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Units by ingredient id, including those of new ingredients by their temporary id. */
    val ingredientUnits: StateFlow<Map<Long, List<IngredientUnit>>> =
        combine(storedUnits, _state.map { it.form.newIngredients }.distinctUntilChanged()) { stored, new ->
            stored + new.mapValues { (id, ingredient) -> ingredient.proposal.units(id) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The unit proposed for each ingredient in a new line, see [UsualUnits]. */
    val usualUnits: StateFlow<Map<Long, LineUnit>> = repository.observeAllLines()
        .map(UsualUnits::of)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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
                val pages = photos.get(recipeId)
                val form = RecipeForm.from(recipe, lines)
                _state.update { it.copy(form = form, loadedForm = form, pages = pages) }
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
                ClaudeAction.CONDENSE_STEPS -> steps.condense()
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
            val result = scanner.scan(apiKey, settings.settings.value.scanModel, photo, ingredients.value, storedUnits.value)
            _state.update { state ->
                when (result) {
                    is ScanResult.Success -> {
                        // Claude never reads the book, so the source always comes from the session.
                        val book = bookSession.defaults(BookPage(source = "", page = result.recipe.page))
                        val form = RecipeForm().withScan(result.recipe, book, aliases.value, storedUnits.value)
                        state.copy(form = form, scan = ScanState.Done)
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

    /** Selects the ingredient just created from the open line editor; keeps the rest of the line. */
    fun useCreatedIngredient(id: Long) {
        viewModelScope.launch {
            val ingredient = ingredientRepository.getIngredient(id) ?: return@launch
            val units = ingredientRepository.getUnits(id)
            _state.update { state ->
                state.copy(lineEditor = state.lineEditor?.let { it.copy(form = it.form.withIngredient(ingredient, units)) })
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

    /** Asks before removing the page photo at [index] of [RecipeEditUiState.pages]. */
    fun requestDeletePage(index: Int) {
        _state.update { it.copy(confirmDeletePage = index) }
    }

    /** Removes the page asked about; the file goes only when saving. */
    fun deletePage() {
        _state.update { state ->
            val index = state.confirmDeletePage ?: return@update state
            state.copy(deletedPages = state.deletedPages + index, confirmDeletePage = null)
        }
    }

    fun dismissDeletePage() {
        _state.update { it.copy(confirmDeletePage = null) }
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

    /** Typing into the Zubereitung; a Kürzen failure shown below it is done with. */
    fun onStepsChange(text: String) {
        onFormChange { copy(steps = text) }
        steps.failureShown()
    }

    fun addLine() {
        _state.update { it.copy(lineEditor = LineEditorState(index = null, RecipeLineForm.newLine())) }
    }

    fun editLine(index: Int) {
        _state.update { it.copy(lineEditor = lineEditor(it.form, index)) }
    }

    /** Opened on a problem line, applying it moves on to the next problem line. */
    private fun lineEditor(form: RecipeForm, index: Int): LineEditorState {
        val line = form.lines[index]
        val name = line.ingredientId?.let { ingredientNames.value[it] }
        val units = line.ingredientId?.let { unitsOf(form)[it] }.orEmpty()
        return LineEditorState(index, RecipeLineForm.from(line, name, units), toNextProblem = form.problem(line) != null)
    }

    /** Units by ingredient id, those of the new ingredients in [form] by their temporary id. */
    private fun unitsOf(form: RecipeForm): Map<Long, List<IngredientUnit>> = storedUnits.value + form.newIngredientUnits()

    fun onLineFormChange(transform: RecipeLineForm.() -> RecipeLineForm) {
        _state.update { state ->
            state.copy(lineEditor = state.lineEditor?.let { it.copy(form = it.form.transform()) })
        }
    }

    /**
     * Takes the line into the recipe. A unit the ingredient lacks is saved to it right away, or for
     * a new ingredient kept with it until it is created on save.
     */
    fun applyLine() {
        val state = _state.value
        val editor = state.lineEditor ?: return
        // A typed wording the user confirmed before needs no picking.
        val lineForm = editor.form.withAlias(aliases.value, ingredientNames.value, unitsOf(state.form))
        val line = lineForm.toFormLine()
        if (line == null || !state.form.isReady(line.ingredientId)) {
            _state.update { it.copy(lineEditor = editor.copy(form = lineForm, showErrors = true)) }
            return
        }
        val newUnit = lineForm.newUnit()
        val withUnit = when {
            newUnit == null -> state.form
            newUnit.ingredientId > 0 -> state.form.also { viewModelScope.launch { ingredientRepository.addUnit(newUnit) } }
            else -> state.form.withNewIngredientUnit(newUnit)
        }
        // Other lines in the same unit no longer miss it.
        val form = (newUnit?.let(withUnit::withUnitDefined) ?: withUnit).withLine(editor.index, line)
        // In the order shown before applying: a line that got its ingredient leaves the top block, the next one there follows.
        val order = state.form.checklistOrder(ingredientSections.value)
        val next = editor.index?.takeIf { editor.toNextProblem }?.let { form.nextProblem(it, order) }
        // An added line is followed by the next one to add, until the user cancels.
        val nextEditor = if (editor.index == null) {
            LineEditorState(index = null, RecipeLineForm.newLine())
        } else {
            next?.let { index -> lineEditor(form, index) }
        }
        _state.update { it.copy(form = form, lineEditor = nextEditor) }
    }

    fun dismissLineEditor() {
        _state.update { it.copy(lineEditor = null) }
    }

    fun removeLine(index: Int) {
        _state.update { it.copy(form = it.form.removeLine(index)) }
    }

    /** Drops the line at [from] next to the one at [to] within its store section, see [RecipeForm.moveLine]. */
    fun moveLine(from: Int, to: Int) {
        _state.update { it.copy(form = it.form.moveLine(from, to)) }
    }

    /** Leaves the editor; with [RecipeEditUiState.hasChanges] it asks first, see [discard]. */
    fun close() {
        _state.update { if (it.hasChanges) it.copy(confirmDiscard = true) else it.copy(closed = true) }
    }

    fun dismissDiscard() {
        _state.update { it.copy(confirmDiscard = false) }
    }

    fun discard() {
        reading?.cancel()
        steps.cancel()
        _state.update { it.copy(confirmDiscard = false, closed = true) }
    }

    fun save() {
        val draft = _state.value.form.toDraft()
        if (draft == null) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        if (saving) return
        saving = true
        steps.cancel()
        val state = _state.value
        viewModelScope.launch {
            val newIds = ingredientRepository.createProposed(state.form.newIngredientsToCreate())
            val saved = draft.withIngredientIds(newIds)
            ingredientRepository.learnAliases(state.form.aliasesToLearn(newIds))
            val id = recipeId?.also { repository.update(it, saved) } ?: repository.create(saved)
            if (state.photoChanged && state.photo != null) photos.add(id, state.photo)
            if (state.deletedPages.isNotEmpty()) photos.delete(id, state.deletedPages)
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
    /** Opens the ways to a new ingredient, named as typed into the line editor. */
    onCreateIngredient: (String) -> Unit,
    /** Set when the tab was tapped again: the editor closes as with X. */
    leaveRequested: Boolean = false,
    /** Called when "Abbrechen" keeps the editor open. */
    onLeaveCancelled: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val ingredientNames by viewModel.ingredientNames.collectAsStateWithLifecycle()
    val ingredientUnits by viewModel.ingredientUnits.collectAsStateWithLifecycle()
    val usualUnits by viewModel.usualUnits.collectAsStateWithLifecycle()
    val ingredientSections by viewModel.ingredientSections.collectAsStateWithLifecycle()
    val sourceSuggestions by viewModel.sourceSuggestions.collectAsStateWithLifecycle()
    val stepsWork by viewModel.steps.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val condensedMessage = stringResource(R.string.recipe_steps_condensed)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(stepsWork.undoText) {
        if (stepsWork.undoText == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(condensedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.steps.undo() else viewModel.steps.undoExpired()
    }
    val photoSource = rememberPhotoSource(viewModel::onPhoto)
    var showingScanInfo by rememberSaveable { mutableStateOf(false) }
    // The page photo open over the whole screen, by its index among the shown ones.
    var openPage by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.saved, state.closed) { if (state.saved || state.closed) onBack() }
    LaunchedEffect(leaveRequested) { if (leaveRequested) viewModel.close() }

    val form = state.form
    val errors = if (state.showErrors) form.errors() else null
    val isScan = state.scanning
    val showForm = state.scan == ScanState.Done
    // A scan is reviewed in two steps, recipe fields then lines; otherwise both show at once.
    val showFields = showForm && !(isScan && state.linesStep)
    val showLines = showForm && (!isScan || state.linesStep)
    // System back steps out of the scan's photo and lines steps before it closes the editor; X always closes.
    BackHandler {
        when {
            isScan && !showForm -> viewModel.skipScan()
            isScan && state.linesStep -> viewModel.showFields()
            else -> viewModel.close()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
                    IconButton(onClick = viewModel::close) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close))
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
    ) { padding ->
        // The scan button and the photos scroll away with the form.
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
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
            val shownPages = state.shownPages
            if (shownPages.isNotEmpty()) {
                // "x" removes a page when saved.
                PhotoRoll(
                    shownPages.map { state.pages[it] },
                    onOpen = { openPage = it },
                    modifier = Modifier.padding(vertical = 8.dp),
                    onDelete = { viewModel.requestDeletePage(shownPages[it]) },
                )
                HorizontalDivider()
            }
            // A scan shows the photo only until it is read; the review works from the fields alone.
            if (!isScan || !showForm) {
                PhotoSection(
                    state = state,
                    onRead = viewModel::readPhoto,
                    onSkipScan = viewModel::skipScan,
                )
            }
            if (showForm) {
                Column(
                    Modifier.padding(16.dp),
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
                        val lineRow: @Composable (Int, Modifier) -> Unit = { index, actions ->
                            val line = form.lines[index]
                            val newId = line.ingredientId?.takeIf { it in form.newIngredients }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LineItem(
                                    line = line,
                                    ingredientName = line.ingredientId?.let { ingredientNames[it] },
                                    units = line.ingredientId?.let { ingredientUnits[it] }.orEmpty(),
                                    problem = form.problem(line),
                                    onClick = { viewModel.editLine(index) },
                                    onRemove = { viewModel.removeLine(index) },
                                    modifier = actions,
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
                        }
                        // Lines without an ingredient on top, then by store section as the recipe shows them.
                        val groups = form.checklistGroups(ingredientSections)
                        // Headers only for several sections, which one header would only repeat.
                        val headers = groups.count { it.section != null } > 1
                        groups.forEach { group ->
                            val section = group.section
                            if (section == null) {
                                group.lines.forEach { lineRow(it, Modifier) }
                                return@forEach
                            }
                            if (headers) {
                                Text(
                                    stringResource(section.label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                                )
                            }
                            // The order only counts within a section, so a line is dragged only there.
                            DraggableColumn(
                                items = group.lines,
                                onMove = { from, to -> viewModel.moveLine(group.lines[from], group.lines[to]) },
                                moveUpLabel = stringResource(R.string.recipe_line_move_up),
                                moveDownLabel = stringResource(R.string.recipe_line_move_down),
                                spacing = 8.dp,
                                content = lineRow,
                            )
                        }
                        TextButton(onClick = viewModel::addLine) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(stringResource(R.string.recipe_line_add), Modifier.padding(start = 8.dp))
                        }
                        Text(
                            stringResource(R.string.recipe_section_steps),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        StepsField(
                            value = form.steps,
                            work = stepsWork,
                            onValueChange = viewModel::onStepsChange,
                            onCondense = { viewModel.useClaude(ClaudeAction.CONDENSE_STEPS) },
                        )
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

        val shownPhotos = state.shownPages.map { state.pages[it] }
        openPage?.takeIf { it in shownPhotos.indices }?.let { page ->
            PhotoViewer(shownPhotos, page, onPageChange = { openPage = it }, onClose = { openPage = null })
        }

        if (state.confirmDeletePage != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDeletePage,
                text = { Text(stringResource(R.string.photo_delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = viewModel::deletePage) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = { TextButton(onClick = viewModel::dismissDeletePage) { Text(stringResource(R.string.action_cancel)) } },
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

        if (state.confirmDiscard) {
            val keepEditing = {
                viewModel.dismissDiscard()
                onLeaveCancelled()
            }
            AlertDialog(
                onDismissRequest = keepEditing,
                title = { Text(stringResource(R.string.recipe_discard_title)) },
                text = { Text(stringResource(R.string.recipe_discard_message)) },
                confirmButton = { TextButton(onClick = viewModel::discard) { Text(stringResource(R.string.action_discard)) } },
                dismissButton = { TextButton(onClick = keepEditing) { Text(stringResource(R.string.action_cancel)) } },
            )
        }

        state.lineEditor?.let { editor ->
            LineEditorDialog(
                editor = editor,
                newIngredient = editor.form.ingredientId?.let { form.newIngredients[it] },
                hasNextProblem = editor.toNextProblem &&
                    editor.index?.let { form.nextProblem(it, form.checklistOrder(ingredientSections)) } != null,
                suggestions = suggestions,
                ingredientUnits = ingredientUnits,
                usualUnits = usualUnits,
                onChange = viewModel::onLineFormChange,
                onApply = viewModel::applyLine,
                onDismiss = viewModel::dismissLineEditor,
                onCreateIngredient = { onCreateIngredient(editor.form.ingredientQuery.trim()) },
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
            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
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
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
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
 * The recipe fields in input order, Art first; every Art has all of them. IME "Next" moves to
 * the following field. The last one calls [onDone] (a scan's next step) when given, else just
 * closes the keyboard.
 */
@Composable
private fun RecipeFields(
    form: RecipeForm,
    errors: RecipeFormErrors?,
    sourceSuggestions: List<String>,
    onFormChange: (RecipeForm.() -> RecipeForm) -> Unit,
    onDone: (() -> Unit)?,
) {
    val lastImeAction = if (onDone != null) ImeAction.Next else ImeAction.Done
    DishRow(form.dish, onSelect = { onFormChange { withDish(it) } })
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
        value = form.servings,
        onValueChange = { onFormChange { copy(servings = it) } },
        label = R.string.recipe_servings,
        error = if (errors?.servings == true) R.string.error_positive_number else null,
        numeric = true,
        imeAction = ImeAction.Next,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FormField(
            value = form.activeTime,
            onValueChange = { onFormChange { copy(activeTime = it) } },
            label = R.string.recipe_active_time_minutes,
            error = when {
                errors?.activeTime != true -> null
                form.activeTime.trim().toIntOrNull()?.takeIf { it > 0 } != null -> R.string.error_active_time_exceeds_total
                else -> R.string.error_positive_number
            },
            hint = R.string.recipe_cooking_time_hint,
            numeric = true,
            imeAction = ImeAction.Next,
            modifier = Modifier.weight(1f),
        )
        FormField(
            value = form.totalTime,
            onValueChange = { onFormChange { copy(totalTime = it) } },
            label = R.string.recipe_total_time_minutes,
            error = if (errors?.totalTime == true) R.string.error_positive_number else null,
            hint = R.string.recipe_cooking_time_hint,
            numeric = true,
            imeAction = lastImeAction,
            onImeAction = onDone,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The Zubereitung, one step per line; numbering and empty lines are cleaned up on save. Below it
 * "Kürzen" once there is text, locked with progress while Claude works on it.
 */
@Composable
private fun StepsField(value: String, work: StepsWork, onValueChange: (String) -> Unit, onCondense: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = !work.running,
        textStyle = MaterialTheme.typography.bodyLarge,
        label = { Text(stringResource(R.string.recipe_section_steps)) },
        supportingText = { Text(stringResource(R.string.recipe_steps_hint)) },
        minLines = 3,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    when {
        work.running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.recipe_steps_condensing), style = MaterialTheme.typography.bodyMedium)
        }
        value.isNotBlank() -> TextButton(onClick = onCondense) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
            Text(stringResource(R.string.recipe_steps_condense), Modifier.padding(start = 8.dp))
        }
    }
    work.failure?.let { failure ->
        Text(
            stringResource(failure.message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** "Art" with the recipe's dish; tapping opens a radio dialog that applies on tap. */
@Composable
private fun DishRow(dish: Dish, onSelect: (Dish) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.recipe_dish)
    SettingsGroup(
        { SettingsRow(icon = null, title = title, summary = stringResource(dish.label), onClick = { choosing = true }) },
        horizontalPadding = 0.dp,
    )
    if (choosing) {
        ChoiceDialog(
            title = title,
            options = Dish.entries,
            selected = dish,
            label = { stringResource(it.label) },
            onSelect = { onSelect(it); choosing = false },
            onDismiss = { choosing = false },
        )
    }
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
 * One checklist row: the line's text, how its grams come about and the matched ingredient. A [problem] line stands out
 * and says what is missing.
 */
@Composable
private fun LineItem(
    line: RecipeFormLine,
    ingredientName: String?,
    units: List<IngredientUnit>,
    problem: LineProblem?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (container, label) = when (problem) {
        LineProblem.NO_INGREDIENT -> colors.errorContainer to R.string.recipe_line_problem_ingredient
        LineProblem.NO_GRAMS -> colors.errorContainer to R.string.recipe_line_problem_grams
        LineProblem.MISSING_UNIT -> colors.tertiaryContainer to R.string.recipe_line_missing_unit
        LineProblem.NEW_INGREDIENT -> colors.tertiaryContainer to R.string.recipe_line_new_ingredient
        LineProblem.UNCERTAIN -> colors.tertiaryContainer to R.string.recipe_line_uncertain
        null -> Color.Transparent to null
    }
    // Not a ListItem: its trailing icon is centred in a two-line row, here it sits on the first line.
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            label?.let {
                Text(stringResource(it), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            val resolved = LineAmount.resolve(line.amount, line.unit, units, ingredientName ?: line.ingredientName, line.wording)
            Text(resolved.text, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    R.string.recipe_line_amount,
                    resolved.calculation?.takeIf { line.amount > 0.0 } ?: "?",
                    ingredientName ?: stringResource(R.string.recipe_line_no_ingredient),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        // Up by the button's inner padding, so the icon is level with the first line of text.
        IconButton(onClick = onRemove, Modifier.offset(y = (-12).dp)) {
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
    ingredientUnits: Map<Long, List<IngredientUnit>>,
    usualUnits: Map<Long, LineUnit>,
    onChange: (RecipeLineForm.() -> RecipeLineForm) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onCreateIngredient: () -> Unit,
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
                // A new line asks for the ingredient first; the amount is there from the start, usable once one was picked.
                val adding = editor.index == null
                val amountEnabled = !adding || form.picked
                val ingredientFocus = remember { FocusRequester() }
                val amountFocus = remember { FocusRequester() }
                // Picking moves on to the amount, but opening a line that already has its ingredient doesn't.
                val initialIngredient = remember { form.ingredientId }
                LaunchedEffect(form.ingredientId) {
                    when {
                        form.ingredientId == null -> if (adding) ingredientFocus.requestFocus()
                        form.ingredientId != initialIngredient -> amountFocus.requestFocus()
                    }
                }
                IngredientField(
                    form = form,
                    suggestions = suggestions,
                    error = if (errors?.ingredient == true) R.string.error_ingredient_required else null,
                    onQueryChange = { onChange { withIngredientQuery(it) } },
                    onPick = { ingredient ->
                        onChange { withIngredient(ingredient, ingredientUnits[ingredient.id].orEmpty(), usualUnits[ingredient.id]) }
                    },
                    onCreate = onCreateIngredient,
                    modifier = Modifier.focusRequester(ingredientFocus),
                )
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(
                        value = form.amount,
                        onValueChange = { onChange { withAmount(it) } },
                        label = R.string.recipe_line_amount_field,
                        error = if (errors?.amount == true) R.string.error_positive_decimal else null,
                        keyboardType = KeyboardType.Decimal,
                        // Adding goes on to the next line from the amount; the wording is optional.
                        imeAction = if (adding) ImeAction.Done else ImeAction.Next,
                        onImeAction = if (adding) onApply else null,
                        enabled = amountEnabled,
                        modifier = Modifier
                            .weight(0.35f)
                            .focusRequester(amountFocus),
                    )
                    UnitField(
                        unit = form.unit,
                        options = form.unitOptions(),
                        onSelect = { onChange { withUnit(it) } },
                        enabled = amountEnabled,
                        modifier = Modifier.weight(0.65f),
                    )
                }
                form.resolved()?.calculation?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                form.missingUnit?.let { unit ->
                    MissingUnitFields(unit, form, errors, onChange)
                }
                FormField(
                    value = form.wording,
                    onValueChange = { onChange { withWording(it) } },
                    label = R.string.recipe_line_text,
                    hint = R.string.recipe_line_text_hint,
                    capitalize = true,
                    imeAction = if (hasNextProblem) ImeAction.Go else ImeAction.Done,
                    onImeAction = onApply,
                    enabled = amountEnabled,
                )
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

/**
 * The ingredient of a line: typing opens the matches as a menu over the dialog, so the dialog keeps its size, with
 * creating the typed name as a new ingredient last. Enter picks the first match.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientField(
    form: RecipeLineForm,
    suggestions: List<Ingredient>,
    @StringRes error: Int?,
    onQueryChange: (String) -> Unit,
    onPick: (Ingredient) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Opens on typing and closes on picking or dismissing, like the source field.
    var typing by remember { mutableStateOf(false) }
    val query = form.ingredientQuery.trim()
    val expanded = typing && form.ingredientId == null && query.isNotEmpty()
    val pick = { ingredient: Ingredient ->
        typing = false
        onPick(ingredient)
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (!it) typing = false },
        modifier = modifier,
    ) {
        FormField(
            value = form.ingredientQuery,
            onValueChange = {
                typing = true
                onQueryChange(it)
            },
            label = R.string.recipe_line_ingredient,
            error = error,
            capitalize = true,
            imeAction = ImeAction.Next,
            onImeAction = { suggestions.firstOrNull()?.let(pick) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { typing = false }) {
            suggestions.forEach { ingredient ->
                DropdownMenuItem(text = { Text(ingredient.name) }, onClick = { pick(ingredient) })
            }
            if (suggestions.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.recipe_line_create_ingredient, query)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    typing = false
                    onCreate()
                },
            )
        }
    }
}

/**
 * The grams of [unit], which the line's ingredient doesn't have yet: "1 Scheibe Vollkorntoast =
 * __ g", or for units that come in packages also "Packung ÷ Anzahl".
 */
@Composable
private fun MissingUnitFields(
    unit: PieceUnit,
    form: RecipeLineForm,
    errors: RecipeLineFormErrors?,
    onChange: (RecipeLineForm.() -> RecipeLineForm) -> Unit,
) {
    val definition = form.definition
    val inPackage = definition.inPackage && form.offersPackage
    val one = stringResource(R.string.recipe_line_unit_one, unit.singular, form.ingredientQuery.trim())
    val gramsError = if (errors?.unitGrams == true) R.string.error_positive_decimal else null
    Text(stringResource(R.string.recipe_line_unit_missing, unit.singular), style = MaterialTheme.typography.bodySmall)
    if (form.offersPackage) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to R.string.ingredient_unit_mode_single, true to R.string.ingredient_unit_mode_package)
                .forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = inPackage == value,
                        onClick = { onChange { withDefinition { copy(inPackage = value) } } },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(stringResource(label)) }
                }
        }
    }
    if (inPackage) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FormField(
                value = definition.grams,
                onValueChange = { onChange { withDefinition { copy(grams = it) } } },
                label = R.string.recipe_line_unit_package_grams,
                error = gramsError,
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f),
            )
            Text("÷")
            FormField(
                value = definition.count,
                onValueChange = { onChange { withDefinition { copy(count = it) } } },
                label = R.string.recipe_line_unit_package_count,
                error = if (errors?.unitCount == true) R.string.error_positive_number else null,
                numeric = true,
                imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f),
            )
        }
        form.newUnit()?.let {
            Text(
                "$one = ${LineAmount.calculation(it.gramsPerUnit, LineUnit.Grams, null)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$one =", Modifier.weight(1f))
            FormField(
                value = definition.grams,
                onValueChange = { onChange { withDefinition { copy(grams = it) } } },
                label = R.string.recipe_line_unit_grams,
                error = gramsError,
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
                modifier = Modifier.width(112.dp),
            )
        }
    }
}

/** The line's unit, picked from g, ml, the ingredient's units and all other units. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitField(
    unit: LineUnit,
    options: List<LineUnit>,
    onSelect: (LineUnit) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            // The cursor at the start, so a unit too long for the field loses its end, not its start.
            value = TextFieldValue(LineAmount.word(unit), selection = TextRange.Zero),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            label = { Text(stringResource(R.string.recipe_line_unit), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(LineAmount.word(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
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
    /** Shown below the field unless there is an [error]. */
    supportingText: String? = null,
    numeric: Boolean = false,
    capitalize: Boolean = false,
    keyboardType: KeyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    /** Replaces the default IME action (moving focus on Next, closing the keyboard on Done). */
    onImeAction: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val supporting = (error ?: hint)?.let { stringResource(it) } ?: supportingText
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        // The size of the label, also in a dialog's smaller text style: an empty field showing its
        // label is as high as a filled one. On one line, so a wrapped label doesn't grow it either.
        textStyle = MaterialTheme.typography.bodyLarge,
        label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        isError = error != null,
        supportingText = supporting?.let { { Text(it) } },
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
