package com.example.plantry.ui.ingredient

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.MixContents
import com.example.plantry.data.MixPart
import com.example.plantry.data.Mixes
import com.example.plantry.data.Nutrient
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.NewIngredientSeed
import com.example.plantry.data.StoreSection
import com.example.plantry.data.openfoodfacts.OffLookup
import com.example.plantry.data.openfoodfacts.OffProduct
import com.example.plantry.data.openfoodfacts.ProductLookup
import com.example.plantry.data.usda.UsdaCatalog
import com.example.plantry.data.usda.UsdaFood
import com.example.plantry.ui.currentLocale
import com.example.plantry.ui.recipe.UsdaPickerDialog
import com.example.plantry.ui.settings.ChoiceDialog
import com.example.plantry.ui.settings.SettingsGroup
import com.example.plantry.ui.settings.SettingsRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

data class IngredientDetailUiState(
    /** Null until the ingredient is loaded. */
    val ingredient: Ingredient? = null,
    val form: IngredientForm = IngredientForm(),
    val showErrors: Boolean = false,
    /** Edit mode; the screen opens in view mode. */
    val editing: Boolean = false,
    /** Set while the dialog asking whether to drop unsaved edits is open. */
    val confirmDiscard: Boolean = false,
    /** Set when dropping the edits also leaves the screen, see [IngredientDetailViewModel.leave]. */
    val leaving: Boolean = false,
    /** Set while the delete dialog is open: the recipes that still use the ingredient, if any. */
    val deleteCheck: DeleteCheck? = null,
    /** Set once the screen is done, after a delete or when a new ingredient is dropped. */
    val closed: Boolean = false,
    /** A new ingredient, which exists only once it is saved; the screen then starts in edit mode. */
    val isNew: Boolean = false,
    /** The id of the new ingredient after it was saved. */
    val createdId: Long? = null,
    /** The scanned barcode's lookup; null when there is none to show. */
    val barcode: BarcodeLookup? = null,
    /** The stored units of [ingredient]; for a new one, those it started with. */
    val units: List<IngredientUnit> = emptyList(),
    /** The stored components of [ingredient] ("Enthält"); none for a new one. */
    val components: List<MixPart> = emptyList(),
    /** Set after removing or saving was refused: the recipes still using a unit the edit removed. */
    val unitInUse: List<String>? = null,
    /** The USDA picker's search while it is open. */
    val usdaQuery: String? = null,
    /** The picked USDA entry while the dialog asking to overwrite the values is open. */
    val usdaCompare: UsdaFood? = null,
)

data class DeleteCheck(val usedIn: List<String>)

/** All ingredients and what each mix contains: for choosing components and naming the mixes an ingredient is in. */
data class MixCatalog(val ingredients: List<Ingredient> = emptyList(), val contents: MixContents = emptyMap()) {
    val byId: Map<Long, Ingredient> by lazy { ingredients.associateBy { it.id } }

    /** The mixes containing [id], A–Z. */
    fun mixesContaining(id: Long?): List<Ingredient> =
        contents.filterValues { id != null && id in it }.keys.mapNotNull(byId::get).sortedBy { it.name.lowercase() }

    /** The stored components of [mixId], as the form holds them. */
    fun partsOf(mixId: Long): List<MixPart> =
        contents[mixId].orEmpty().mapNotNull(byId::get).map { MixPart(it.id, it.name) }
}

class IngredientDetailViewModel(
    private val ingredientId: Long,
    private val repository: IngredientRepository,
    private val products: ProductLookup,
    private val loadCatalog: suspend () -> UsdaCatalog,
    /** Set for an ingredient that doesn't exist yet; [ingredientId] is then unused. */
    private val seed: NewIngredientSeed? = null,
) : ViewModel() {

    private var lookupJob: Job? = null

    private val _state = MutableStateFlow(IngredientDetailUiState())
    val state: StateFlow<IngredientDetailUiState> = _state.asStateFlow()

    val catalog: StateFlow<MixCatalog> = combine(repository.observeIngredients(), repository.observeContents(), ::MixCatalog)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MixCatalog())

    /** Search results of the USDA picker; null while the data is loading or no picker is open. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val usdaResults: StateFlow<List<UsdaFood>?> = _state
        .map { it.usdaQuery }
        .distinctUntilChanged()
        .mapLatest { query -> query?.let { loadCatalog().search(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (seed != null) {
            val ingredient = seed.toIngredient()
            _state.update {
                it.copy(
                    ingredient = ingredient,
                    // As the starting point, so leaving it untouched asks nothing.
                    units = seed.units,
                    form = IngredientForm.from(ingredient, seed.units),
                    editing = true,
                    isNew = true,
                )
            }
        } else viewModelScope.launch {
            val ingredient = repository.getIngredient(ingredientId) ?: return@launch
            val units = repository.getUnits(ingredientId)
            val components = storedComponents()
            _state.update {
                it.copy(
                    ingredient = ingredient,
                    units = units,
                    components = components,
                    form = IngredientForm.from(ingredient, units, components),
                )
            }
        }
    }

    private suspend fun storedComponents(): List<MixPart> =
        MixCatalog(repository.observeIngredients().first(), repository.observeContents().first()).partsOf(ingredientId)

    fun onFormChange(transform: IngredientForm.() -> IngredientForm) {
        _state.update { it.copy(form = it.form.transform()) }
    }

    fun startEdit() {
        _state.update { it.copy(editing = true) }
    }

    private fun hasUnsavedEdits(): Boolean {
        val current = _state.value
        return current.editing &&
            current.ingredient?.let { IngredientForm.from(it, current.units, current.components) != current.form } == true
    }

    /** Leaves edit mode; with unsaved edits it asks first, see [discard]. */
    fun cancelEdit() {
        if (hasUnsavedEdits()) _state.update { it.copy(confirmDiscard = true) } else discard()
    }

    /** Leaves the screen, as when its tab is tapped again; with unsaved edits it asks first, see [discard]. */
    fun leave() {
        _state.update { if (hasUnsavedEdits()) it.copy(confirmDiscard = true, leaving = true) else it.copy(closed = true) }
    }

    fun dismissDiscard() {
        _state.update { it.copy(confirmDiscard = false, leaving = false) }
    }

    /** Drops the edits and returns to view mode, or leaves the screen for a new ingredient or after [leave]. */
    fun discard() {
        if (_state.value.isNew || _state.value.leaving) {
            _state.update { it.copy(closed = true) }
            return
        }
        _state.update {
            it.copy(
                form = it.ingredient?.let { ingredient -> IngredientForm.from(ingredient, it.units, it.components) } ?: it.form,
                editing = false,
                showErrors = false,
                confirmDiscard = false,
                barcode = null,
            )
        }
    }

    /** Saves the form and returns to view mode; invalid input stays in edit mode with its errors. */
    fun save() {
        val draft = _state.value.form.toDraft()
        if (draft == null) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        if (seed != null) {
            viewModelScope.launch {
                val id = repository.create(seed, draft)
                _state.update { it.copy(createdId = id) }
            }
            return
        }
        viewModelScope.launch {
            val usedIn = repository.update(ingredientId, draft)
            if (usedIn.isNotEmpty()) {
                _state.update { it.copy(unitInUse = usedIn) }
                return@launch
            }
            val saved = repository.getIngredient(ingredientId) ?: return@launch
            val units = repository.getUnits(ingredientId)
            val components = storedComponents()
            _state.update {
                it.copy(
                    ingredient = saved,
                    units = units,
                    components = components,
                    form = IngredientForm.from(saved, units, components),
                    editing = false,
                    showErrors = false,
                )
            }
        }
    }

    /**
     * Puts [unit] into the form in place of [replacing] (null when adding). Changing a stored row
     * to another unit removes the old one, which is refused while a recipe line uses it.
     */
    fun saveUnit(unit: IngredientUnit, replacing: PieceUnit?) {
        if (replacing == null || replacing == unit.unit) {
            onFormChange { withUnit(unit, replacing) }
            return
        }
        unlessInUse(replacing) { withUnit(unit, replacing) }
    }

    /** Removes the row of [unit] from the form, unless a recipe line uses it. */
    fun removeUnit(unit: PieceUnit) = unlessInUse(unit) { withoutUnit(unit) }

    private fun unlessInUse(unit: PieceUnit, transform: IngredientForm.() -> IngredientForm) {
        val current = _state.value
        if (current.isNew || current.units.none { it.unit == unit }) {
            onFormChange(transform)
            return
        }
        viewModelScope.launch {
            val usedIn = repository.recipesUsingUnit(ingredientId, unit)
            if (usedIn.isEmpty()) onFormChange(transform) else _state.update { it.copy(unitInUse = usedIn) }
        }
    }

    /** Closes the message about a unit still in use; the edit stays open. */
    fun dismissUnitInUse() {
        _state.update { it.copy(unitInUse = null) }
    }

    fun lookUp(barcode: String) {
        lookupJob?.cancel()
        _state.update { it.copy(barcode = BarcodeLookup.Searching) }
        lookupJob = viewModelScope.launch {
            val result = products.lookup(barcode)
            _state.update { it.copy(barcode = BarcodeLookup.Done(result)) }
        }
    }

    /** Writes the scanned values into the form; [save] still has to persist them. */
    fun applyScanned(product: OffProduct) {
        _state.update { it.copy(form = it.form.withScanned(product), barcode = null) }
    }

    /** Ends the lookup, or closes the dialog showing its result. */
    fun dismissBarcode() {
        lookupJob?.cancel()
        _state.update { it.copy(barcode = null) }
    }

    /** Opens the USDA picker, searching for the current entry's food (USDA is in English, so not by name). */
    fun openUsdaPicker() {
        val current = _state.value
        val description = current.form.pickedUsda?.description ?: current.ingredient?.usdaDescription
        _state.update { it.copy(usdaQuery = description?.substringBefore(',').orEmpty()) }
    }

    fun onUsdaQueryChange(query: String) {
        _state.update { it.copy(usdaQuery = query) }
    }

    fun dismissUsdaPicker() {
        _state.update { it.copy(usdaQuery = null) }
    }

    /**
     * Takes [food]'s values into the form. When that would overwrite values or a package label as
     * the source, it asks first, see [applyUsda].
     */
    fun pickUsda(food: UsdaFood) {
        val current = _state.value
        val form = current.form
        val replacesLabel = form.scannedLabel != null || (form.pickedUsda == null && current.ingredient?.labelSource != null)
        if (form.hasNutrition || replacesLabel) {
            _state.update { it.copy(usdaQuery = null, usdaCompare = food) }
        } else {
            _state.update { it.copy(usdaQuery = null, form = it.form.withUsda(food)) }
        }
    }

    /** Writes the asked-about USDA values into the form; [save] still has to persist them. */
    fun applyUsda() {
        _state.update { state -> state.usdaCompare?.let { state.copy(form = state.form.withUsda(it), usdaCompare = null) } ?: state }
    }

    fun dismissUsdaCompare() {
        _state.update { it.copy(usdaCompare = null) }
    }

    /** Opens the delete dialog, which either confirms or lists the recipes that block it. */
    fun requestDelete() {
        viewModelScope.launch {
            val usedIn = repository.recipesUsing(ingredientId)
            _state.update { it.copy(deleteCheck = DeleteCheck(usedIn)) }
        }
    }

    fun dismissDelete() {
        _state.update { it.copy(deleteCheck = null) }
    }

    fun delete() {
        viewModelScope.launch {
            val usedIn = repository.delete(ingredientId)
            _state.update {
                if (usedIn.isEmpty()) it.copy(deleteCheck = null, closed = true) else it.copy(deleteCheck = DeleteCheck(usedIn))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientDetailScreen(
    viewModel: IngredientDetailViewModel,
    onBack: () -> Unit,
    /** Called with the id once a new ingredient is saved. */
    onCreated: (Long) -> Unit = {},
    /** Set when the tab was tapped again: the screen closes, unsaved edits asking first. */
    leaveRequested: Boolean = false,
    /** Called when "Abbrechen" keeps the edits. */
    onLeaveCancelled: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    LaunchedEffect(leaveRequested) { if (leaveRequested) viewModel.leave() }
    LaunchedEffect(state.createdId) { state.createdId?.let(onCreated) }
    BackHandler(enabled = state.editing, onBack = viewModel::cancelEdit)
    val snackbar = remember { SnackbarHostState() }
    val scannerUnavailable = stringResource(R.string.barcode_unavailable)
    val scope = rememberCoroutineScope()
    val scan = rememberBarcodeScanner(
        onScanned = viewModel::lookUp,
        onUnavailable = { scope.launch { snackbar.showSnackbar(scannerUnavailable) } },
    )
    // Not found or offline: say so; the fields stay as they are and editable.
    val failure = (state.barcode as? BarcodeLookup.Done)?.result?.takeIf { it !is OffLookup.Found }
    val failureMessage = failure?.let { failureText(it) }
    LaunchedEffect(failure) {
        if (failureMessage != null) {
            // In the screen's scope: dismissing changes this effect's key, which would cancel it.
            scope.launch { snackbar.showSnackbar(failureMessage) }
            viewModel.dismissBarcode()
        }
    }

    val ingredient = state.ingredient
    val form = state.form
    val errors = if (state.showErrors) form.errors() else null
    val onChange = viewModel::onFormChange
    val editing = state.editing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ingredient?.name.orEmpty()) },
                navigationIcon = {
                    if (editing) {
                        IconButton(onClick = viewModel::cancelEdit) {
                            Icon(Icons.Filled.Close, stringResource(R.string.action_cancel))
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (ingredient != null) {
                        if (editing) {
                            if (!state.isNew) {
                                IconButton(onClick = viewModel::requestDelete) {
                                    Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                                }
                            }
                            IconButton(onClick = viewModel::save) {
                                Icon(Icons.Filled.Check, stringResource(R.string.action_save))
                            }
                        } else {
                            IconButton(onClick = viewModel::startEdit) {
                                Icon(Icons.Filled.Edit, stringResource(R.string.action_edit))
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (ingredient == null) return@Scaffold
        when (val lookup = state.barcode) {
            BarcodeLookup.Searching -> BarcodeSearchingDialog(onDismiss = viewModel::dismissBarcode)
            is BarcodeLookup.Done -> (lookup.result as? OffLookup.Found)?.let { found ->
                BarcodeCompareDialog(
                    product = found.product,
                    current = form,
                    onApply = { viewModel.applyScanned(found.product) },
                    onDismiss = viewModel::dismissBarcode,
                )
            }
            null -> Unit
        }
        state.usdaQuery?.let { query ->
            val results by viewModel.usdaResults.collectAsStateWithLifecycle()
            UsdaPickerDialog(
                query = query,
                results = results,
                onQueryChange = viewModel::onUsdaQueryChange,
                onPick = viewModel::pickUsda,
                onWithoutUsda = null,
                onDismiss = viewModel::dismissUsdaPicker,
            )
        }
        state.usdaCompare?.let { food ->
            NutritionCompareDialog(
                title = stringResource(R.string.usda_compare_title),
                intro = stringResource(R.string.ingredient_usda_source, food.description, food.fdcId),
                values = Nutrient.entries.associateWith { food.nutrition[it] },
                current = form,
                onApply = viewModel::applyUsda,
                onDismiss = viewModel::dismissUsdaCompare,
            )
        }
        if (state.confirmDiscard) {
            val keepEditing = {
                viewModel.dismissDiscard()
                onLeaveCancelled()
            }
            AlertDialog(
                onDismissRequest = keepEditing,
                title = { Text(stringResource(R.string.ingredient_discard_title)) },
                text = { Text(stringResource(R.string.ingredient_discard_message)) },
                confirmButton = { TextButton(onClick = viewModel::discard) { Text(stringResource(R.string.action_discard)) } },
                dismissButton = { TextButton(onClick = keepEditing) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
        state.unitInUse?.let { usedIn ->
            AlertDialog(
                onDismissRequest = viewModel::dismissUnitInUse,
                title = { Text(stringResource(R.string.ingredient_unit_in_use_title)) },
                text = {
                    Text(stringResource(R.string.ingredient_unit_in_use_message, ingredient.name, usedIn.joinToString("\n") { "• $it" }))
                },
                confirmButton = { TextButton(onClick = viewModel::dismissUnitInUse) { Text(stringResource(R.string.action_ok)) } },
            )
        }
        state.deleteCheck?.let { check ->
            DeleteDialog(
                name = ingredient.name,
                usedIn = check.usedIn,
                onConfirm = viewModel::delete,
                onDismiss = viewModel::dismissDelete,
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Field(
                editing = editing,
                value = form.name,
                onValueChange = { onChange { copy(name = it) } },
                label = R.string.ingredient_name,
                error = if (errors?.name == true) R.string.error_name_required else null,
                numeric = false,
                imeAction = ImeAction.Done,
            )
            // An unsaved pick or scan shows as the source already.
            val label = form.scannedLabel ?: ingredient.labelSource.takeIf { form.pickedUsda == null }
            val usdaFdcId = form.pickedUsda?.fdcId ?: ingredient.fdcId.takeIf { form.scannedLabel == null }
            val usdaDescription = form.pickedUsda?.description ?: ingredient.usdaDescription
            Text(
                if (label != null) {
                    stringResource(R.string.ingredient_label_source, label.labelProduct, label.labelBarcode)
                } else if (usdaFdcId != null && usdaDescription != null) {
                    stringResource(R.string.ingredient_usda_source, usdaDescription, usdaFdcId)
                } else {
                    stringResource(R.string.ingredient_no_usda_source)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (editing) {
                TextButton(onClick = viewModel::openUsdaPicker) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Text(stringResource(R.string.new_ingredient_pick), Modifier.padding(start = 8.dp))
                }
            }

            SectionTitle(R.string.ingredient_section_properties)
            PropertiesGroup(
                form = form,
                catalog = catalog,
                ingredientId = ingredient.id.takeUnless { state.isNew },
                onStoreSection = { onChange { copy(storeSection = it) } },
                onPlantPoints = { onChange { copy(plantPoints = it) } },
                onComponents = { onChange { copy(components = it) } },
                editable = editing,
            )

            SectionTitle(R.string.ingredient_section_units)
            UnitsGroup(
                form = form,
                editable = editing,
                onSave = viewModel::saveUnit,
                onRemove = viewModel::removeUnit,
            )

            SectionTitle(R.string.ingredient_section_nutrition)
            Nutrient.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { nutrient ->
                        Field(
                            editing = editing,
                            value = form.nutrition[nutrient].orEmpty(),
                            onValueChange = { onChange { withNutrient(nutrient, it) } },
                            label = nutrient.label,
                            error = if (errors?.nutrients?.contains(nutrient) == true) {
                                R.string.error_non_negative_decimal
                            } else {
                                null
                            },
                            imeAction = if (nutrient == Nutrient.entries.last()) ImeAction.Done else ImeAction.Next,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (editing) {
                TextButton(onClick = scan) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                    Text(stringResource(R.string.barcode_update), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun BarcodeCompareDialog(product: OffProduct, current: IngredientForm, onApply: () -> Unit, onDismiss: () -> Unit) =
    NutritionCompareDialog(
        title = stringResource(R.string.barcode_compare_title),
        intro = foundText(product),
        values = product.nutrition,
        current = current,
        onApply = onApply,
        onDismiss = onDismiss,
    )

/** Old → new per value; values missing from [values] keep the old one. The name is never touched. */
@Composable
private fun NutritionCompareDialog(
    title: String,
    intro: String,
    values: Map<Nutrient, Double>,
    current: IngredientForm,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = currentLocale()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(intro, style = MaterialTheme.typography.bodyMedium)
                Nutrient.entries.forEach { nutrient ->
                    val old = current.nutrition[nutrient].orEmpty().ifBlank { "–" }
                    val new = values[nutrient]
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(nutrient.label), Modifier.weight(1f))
                        Text(
                            if (new != null) {
                                stringResource(R.string.barcode_compare_change, old, formatDecimal(new, locale))
                            } else {
                                stringResource(R.string.barcode_compare_missing, old)
                            },
                            color = if (new != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text(stringResource(R.string.action_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DeleteDialog(name: String, usedIn: List<String>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (usedIn.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.ingredient_delete_blocked_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ingredient_delete_blocked_message,
                        name,
                        usedIn.joinToString("\n") { "• $it" },
                    ),
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.ingredient_delete_title)) },
            text = { Text(stringResource(R.string.ingredient_delete_message, name)) },
            confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private enum class PropertyDialog { STORE_SECTION, PLANT_POINTS, PLANT_POINTS_INFO, COMPONENTS }

/**
 * One line per property with its current value; when [editable], tapping opens a radio dialog that
 * applies on tap. A mix's plant points follow from its components and can't be chosen. Enthält is
 * left out for an ingredient contained in a mix (one level only), which shows Enthalten in instead.
 */
@Composable
private fun PropertiesGroup(
    form: IngredientForm,
    catalog: MixCatalog,
    /** Null while the ingredient is new. */
    ingredientId: Long?,
    onStoreSection: (StoreSection) -> Unit,
    onPlantPoints: (PlantPoints) -> Unit,
    onComponents: (List<MixPart>) -> Unit,
    editable: Boolean,
) {
    var dialog by rememberSaveable { mutableStateOf<PropertyDialog?>(null) }
    val containedIn = catalog.mixesContaining(ingredientId)
    val rows = buildList<@Composable () -> Unit> {
        add {
            SettingsRow(
                icon = null,
                title = stringResource(R.string.ingredient_section_store_section),
                summary = stringResource(form.storeSection.label),
                onClick = if (editable) ({ dialog = PropertyDialog.STORE_SECTION }) else null,
            )
        }
        add {
            SettingsRow(
                icon = null,
                title = stringResource(R.string.ingredient_section_plant_points),
                summary = if (form.isMix) {
                    stringResource(R.string.ingredient_plant_points_from_components, plantPointsLabel(form.componentPlantPoints(catalog.byId)))
                } else {
                    plantPointsLabel(form.plantPoints)
                },
                onClick = if (editable && !form.isMix) ({ dialog = PropertyDialog.PLANT_POINTS }) else null,
                trailing = {
                    IconButton(onClick = { dialog = PropertyDialog.PLANT_POINTS_INFO }) {
                        Icon(Icons.Filled.Info, stringResource(R.string.plant_points_info_description))
                    }
                },
            )
        }
        if (Mixes.canBeMix(ingredientId, catalog.contents)) {
            add {
                SettingsRow(
                    icon = null,
                    title = stringResource(R.string.ingredient_components),
                    summary = if (form.isMix) {
                        pluralStringResource(
                            R.plurals.ingredient_components_summary,
                            form.components.size,
                            form.components.size,
                            form.components.joinToString(", ") { it.name },
                        )
                    } else {
                        stringResource(R.string.ingredient_components_none)
                    },
                    onClick = if (editable) ({ dialog = PropertyDialog.COMPONENTS }) else null,
                )
            }
        }
        if (containedIn.isNotEmpty()) {
            add {
                SettingsRow(
                    icon = null,
                    title = stringResource(R.string.ingredient_contained_in),
                    summary = containedIn.joinToString(", ") { it.name },
                    onClick = null,
                )
            }
        }
    }
    SettingsGroup(*rows.toTypedArray(), horizontalPadding = 0.dp)

    val dismiss = { dialog = null }
    when (dialog) {
        PropertyDialog.STORE_SECTION -> ChoiceDialog(
            title = stringResource(R.string.ingredient_section_store_section),
            options = StoreSection.entries,
            selected = form.storeSection,
            label = { stringResource(it.label) },
            onSelect = { onStoreSection(it); dismiss() },
            onDismiss = dismiss,
        )
        PropertyDialog.PLANT_POINTS -> ChoiceDialog(
            title = stringResource(R.string.ingredient_section_plant_points),
            options = PlantPoints.entries,
            selected = form.plantPoints,
            label = { plantPointsLabel(it) },
            onSelect = { onPlantPoints(it); dismiss() },
            onDismiss = dismiss,
        )
        PropertyDialog.PLANT_POINTS_INFO -> PlantPointsInfoDialog(onDismiss = dismiss)
        PropertyDialog.COMPONENTS -> ComponentsDialog(
            initial = ComponentsForm(ingredientId, form.components),
            catalog = catalog,
            onConfirm = { onComponents(it); dismiss() },
            onDismiss = dismiss,
        )
        null -> Unit
    }
}

/**
 * One row per unit with its weight; when [editable], tapping a row edits it, its bin removes it,
 * and a button adds a unit that has no row yet.
 */
@Composable
private fun UnitsGroup(
    form: IngredientForm,
    editable: Boolean,
    /** The row, and the unit it replaces (null for a new row). */
    onSave: (IngredientUnit, PieceUnit?) -> Unit,
    onRemove: (PieceUnit) -> Unit,
) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    // The unit of the row being edited; null while adding.
    var editedUnit by rememberSaveable { mutableStateOf<PieceUnit?>(null) }
    Text(
        stringResource(R.string.ingredient_units_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (form.units.isEmpty()) {
        Text(stringResource(R.string.ingredient_no_units), style = MaterialTheme.typography.bodyLarge)
    } else {
        SettingsGroup(
            *form.units.map { row ->
                @Composable {
                    SettingsRow(
                        icon = null,
                        title = stringResource(row.unit.label),
                        summary = unitSummary(row),
                        onClick = if (editable) ({ editedUnit = row.unit; dialogOpen = true }) else null,
                        trailing = if (editable) {
                            {
                                IconButton(onClick = { onRemove(row.unit) }) {
                                    Icon(Icons.Filled.Delete, stringResource(R.string.ingredient_unit_remove, stringResource(row.unit.label)))
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }.toTypedArray(),
            horizontalPadding = 0.dp,
        )
    }
    if (editable && form.availableUnits().isNotEmpty()) {
        TextButton(onClick = { editedUnit = null; dialogOpen = true }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.ingredient_unit_add), Modifier.padding(start = 8.dp))
        }
    }
    if (dialogOpen) {
        val dismiss = { dialogOpen = false }
        UnitDialog(
            initial = form.units.find { it.unit == editedUnit },
            options = form.availableUnits(editedUnit),
            packageGrams = form.packageGrams,
            onConfirm = { onSave(it, editedUnit); dismiss() },
            onDismiss = dismiss,
        )
    }
}

/** "1 Zehe = 4 g", or "750 g ÷ 20 = 37,5 g" for a row entered as a package. */
@Composable
private fun unitSummary(row: IngredientUnit): String {
    val locale = currentLocale()
    val perUnit = formatDecimal(row.gramsPerUnit.roundTo2(), locale)
    return if (row.isPackage) {
        stringResource(R.string.ingredient_unit_summary_package, formatDecimal(row.packageGrams!!, locale), row.packageCount!!, perUnit)
    } else {
        stringResource(R.string.ingredient_unit_summary_single, stringResource(row.unit.label), perUnit)
    }
}

private fun Double.roundTo2(): Double = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP).toDouble()

/**
 * Unit (among [options]), then either the weight of one unit or a package weight with how many
 * units it holds. Reopens in the mode the row was entered in; OK stays disabled while invalid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDialog(
    /** Null when adding. */
    initial: IngredientUnit?,
    options: List<PieceUnit>,
    /** The ingredient's package, which a new row starts from, see [UnitForm.forNew]. */
    packageGrams: Double?,
    onConfirm: (IngredientUnit) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = remember { initial?.let(UnitForm::from) ?: UnitForm.forNew(options.first(), packageGrams) }
    var unit by rememberSaveable { mutableStateOf(start.unit) }
    var inPackage by rememberSaveable { mutableStateOf(start.inPackage) }
    var grams by rememberSaveable { mutableStateOf(start.grams) }
    var count by rememberSaveable { mutableStateOf(start.count) }
    val form = UnitForm(unit, inPackage, grams, count)
    val result = form.toUnit()
    var unitsExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.ingredient_unit_add else R.string.ingredient_unit_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = unitsExpanded, onExpandedChange = { unitsExpanded = it }) {
                    OutlinedTextField(
                        value = unit?.let { stringResource(it.label) }.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.ingredient_piece_unit)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitsExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = unitsExpanded, onDismissRequest = { unitsExpanded = false }) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.label)) },
                                onClick = {
                                    unit = option
                                    unitsExpanded = false
                                },
                            )
                        }
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to R.string.ingredient_unit_mode_single, true to R.string.ingredient_unit_mode_package)
                        .forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = inPackage == value,
                                onClick = { inPackage = value },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                            ) { Text(stringResource(label)) }
                        }
                }
                FormField(
                    value = grams,
                    onValueChange = { grams = it },
                    label = if (inPackage) R.string.ingredient_piece_package_grams_field else R.string.ingredient_piece_grams_field,
                    error = if (grams.isNotBlank() && form.gramsInvalid) R.string.error_positive_decimal else null,
                    imeAction = if (inPackage) ImeAction.Next else ImeAction.Done,
                )
                if (inPackage) {
                    FormField(
                        value = count,
                        onValueChange = { count = it },
                        label = R.string.ingredient_piece_count_field,
                        error = if (count.isNotBlank() && form.countInvalid) R.string.error_positive_number else null,
                        imeAction = ImeAction.Done,
                    )
                    if (result != null) {
                        Text(
                            stringResource(
                                R.string.ingredient_unit_summary_single,
                                stringResource(result.unit.label),
                                formatDecimal(result.gramsPerUnit.roundTo2(), currentLocale()),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { result?.let(onConfirm) }, enabled = result != null) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * The components as removable chips, and an ingredient field suggesting what can be added (see
 * [ComponentsForm.suggestions]); a name that is no ingredient yet can be added to be created on
 * save. Applies on OK.
 */
@Composable
private fun ComponentsDialog(
    initial: ComponentsForm,
    catalog: MixCatalog,
    onConfirm: (List<MixPart>) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember { mutableStateOf(initial) }
    val suggestions = form.suggestions(catalog.ingredients, catalog.contents)
    val newName = form.newName(catalog.ingredients)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ingredient_components)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.ingredient_components_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (form.components.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        form.components.forEach { part ->
                            InputChip(
                                selected = false,
                                onClick = { form = form.without(part) },
                                label = { Text(part.name) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        stringResource(R.string.ingredient_component_remove, part.name),
                                        Modifier.size(InputChipDefaults.IconSize),
                                    )
                                },
                            )
                        }
                    }
                }
                FormField(
                    value = form.query,
                    onValueChange = { form = form.withQuery(it) },
                    label = R.string.recipe_line_ingredient,
                    numeric = false,
                )
                suggestions.forEach { ingredient ->
                    ListItem(
                        headlineContent = { Text(ingredient.name) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { form = form.with(ingredient) },
                    )
                }
                if (newName != null) {
                    TextButton(onClick = { form = form.withNew(newName) }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.ingredient_component_create, newName), Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(form.components) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PlantPointsInfoDialog(onDismiss: () -> Unit) {
    val lines = listOf(
        PlantPoints.ONE to R.string.plant_points_info_one,
        PlantPoints.QUARTER to R.string.plant_points_info_quarter,
        PlantPoints.ZERO to R.string.plant_points_info_zero,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ingredient_section_plant_points)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.plant_points_info_intro))
                lines.forEach { (points, text) ->
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(plantPointsLabel(points) + ":") }
                            append(" ")
                            append(stringResource(text))
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp),
    )
}

/** An input in edit mode, the label with its value as plain text in view mode. */
@Composable
private fun Field(
    editing: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
    @StringRes error: Int? = null,
    numeric: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
) {
    if (editing) {
        FormField(value, onValueChange, label, modifier, error, numeric, imeAction)
    } else {
        Column(modifier.padding(vertical = 4.dp)) {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value.ifBlank { "–" }, style = MaterialTheme.typography.bodyLarge)
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
    numeric: Boolean = true,
    /** Next moves to the following field, Done closes the keyboard. */
    imeAction: ImeAction = ImeAction.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it)) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
            capitalization = if (numeric) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            imeAction = imeAction,
        ),
        modifier = modifier,
    )
}

internal val Nutrient.label: Int
    get() = when (this) {
        Nutrient.KCAL -> R.string.nutrient_kcal
        Nutrient.PROTEIN -> R.string.nutrient_protein
        Nutrient.CARBS -> R.string.nutrient_carbs
        Nutrient.SUGAR -> R.string.nutrient_sugar
        Nutrient.FAT -> R.string.nutrient_fat
        Nutrient.FIBRE -> R.string.nutrient_fibre
    }

/** The German singular, e.g. "Scheibe". */
internal val PieceUnit.label: Int
    get() = when (this) {
        PieceUnit.PIECE -> R.string.piece_unit_piece
        PieceUnit.SLICE -> R.string.piece_unit_slice
        PieceUnit.CLOVE -> R.string.piece_unit_clove
        PieceUnit.BUNCH -> R.string.piece_unit_bunch
        PieceUnit.STALK -> R.string.piece_unit_stalk
        PieceUnit.CAN -> R.string.piece_unit_can
        PieceUnit.PACKET -> R.string.piece_unit_packet
        PieceUnit.TSP -> R.string.piece_unit_tsp
        PieceUnit.TBSP -> R.string.piece_unit_tbsp
        PieceUnit.CUP -> R.string.piece_unit_cup
    }

internal val StoreSection.label: Int
    get() = when (this) {
        StoreSection.PRODUCE -> R.string.store_section_produce
        StoreSection.DAIRY_CHILLED -> R.string.store_section_dairy_chilled
        StoreSection.DRY_GOODS -> R.string.store_section_dry_goods
        StoreSection.FROZEN -> R.string.store_section_frozen
        StoreSection.OTHER -> R.string.store_section_other
    }
