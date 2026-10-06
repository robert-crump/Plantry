package com.example.plantry.ui.ingredient

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.DrainedWeight
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.Nutrient
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import com.example.plantry.data.openfoodfacts.OffLookup
import com.example.plantry.data.openfoodfacts.OffProduct
import com.example.plantry.data.openfoodfacts.ProductLookup
import com.example.plantry.ui.currentLocale
import com.example.plantry.ui.settings.ChoiceDialog
import com.example.plantry.ui.settings.SettingsGroup
import com.example.plantry.ui.settings.SettingsRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IngredientDetailUiState(
    /** Null until the ingredient is loaded. */
    val ingredient: Ingredient? = null,
    val form: IngredientForm = IngredientForm(),
    val showErrors: Boolean = false,
    val saved: Boolean = false,
    /** Set while the delete dialog is open: the recipes that still use the ingredient, if any. */
    val deleteCheck: DeleteCheck? = null,
    val deleted: Boolean = false,
    /** The scanned barcode's lookup; null when there is none to show. */
    val barcode: BarcodeLookup? = null,
)

data class DeleteCheck(val usedIn: List<String>)

class IngredientDetailViewModel(
    private val ingredientId: Long,
    private val repository: IngredientRepository,
    private val products: ProductLookup,
) : ViewModel() {

    private var lookupJob: Job? = null

    private val _state = MutableStateFlow(IngredientDetailUiState())
    val state: StateFlow<IngredientDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val ingredient = repository.getIngredient(ingredientId) ?: return@launch
            _state.update { it.copy(ingredient = ingredient, form = IngredientForm.from(ingredient)) }
        }
    }

    fun onFormChange(transform: IngredientForm.() -> IngredientForm) {
        _state.update { it.copy(form = it.form.transform()) }
    }

    fun save() {
        val draft = _state.value.form.toDraft()
        if (draft == null) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            repository.update(ingredientId, draft)
            _state.update { it.copy(saved = true) }
        }
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
                if (usedIn.isEmpty()) it.copy(deleteCheck = null, deleted = true) else it.copy(deleteCheck = DeleteCheck(usedIn))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientDetailScreen(
    viewModel: IngredientDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved, state.deleted) { if (state.saved || state.deleted) onBack() }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ingredient?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (ingredient != null) {
                        IconButton(onClick = viewModel::requestDelete) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                        }
                        TextButton(onClick = viewModel::save) { Text(stringResource(R.string.action_save)) }
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
            if (!ingredient.reviewed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnreviewedBadge()
                    Text(
                        stringResource(R.string.ingredient_reviewed_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FormField(
                value = form.name,
                onValueChange = { onChange { copy(name = it) } },
                label = R.string.ingredient_name,
                error = if (errors?.name == true) R.string.error_name_required else null,
                numeric = false,
            )
            Text(
                if (ingredient.labelSource != null) {
                    stringResource(R.string.ingredient_label_source, ingredient.labelSource.labelProduct, ingredient.labelSource.labelBarcode)
                } else if (ingredient.fdcId != null && ingredient.usdaDescription != null) {
                    stringResource(R.string.ingredient_usda_source, ingredient.usdaDescription, ingredient.fdcId)
                } else {
                    stringResource(R.string.ingredient_no_usda_source)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle(R.string.ingredient_section_properties)
            PropertiesGroup(
                form = form,
                onStoreSection = { onChange { copy(storeSection = it) } },
                onPlantPoints = { onChange { copy(plantPoints = it) } },
                onDrainedWeight = { onChange { copy(drainedWeight = it) } },
            )

            SectionTitle(
                if (form.drainedWeight != null) R.string.ingredient_section_nutrition_drained else R.string.ingredient_section_nutrition,
            )
            Nutrient.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { nutrient ->
                        FormField(
                            value = form.nutrition[nutrient].orEmpty(),
                            onValueChange = { onChange { withNutrient(nutrient, it) } },
                            label = nutrient.label,
                            error = if (errors?.nutrients?.contains(nutrient) == true) {
                                R.string.error_non_negative_decimal
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            TextButton(onClick = scan) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Text(stringResource(R.string.barcode_update), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Old → new per value; values the scan lacks keep the old one. The name is never touched. */
@Composable
private fun BarcodeCompareDialog(product: OffProduct, current: IngredientForm, onApply: () -> Unit, onDismiss: () -> Unit) {
    val locale = currentLocale()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.barcode_compare_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(foundText(product), style = MaterialTheme.typography.bodyMedium)
                Nutrient.entries.forEach { nutrient ->
                    val old = current.nutrition[nutrient].orEmpty().ifBlank { "–" }
                    val new = product.nutrition[nutrient]
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

private enum class PropertyDialog { STORE_SECTION, PLANT_POINTS, PLANT_POINTS_INFO, DRAINED_WEIGHT }

/** One line per property with its current value; tapping opens a radio dialog that applies on tap. */
@Composable
private fun PropertiesGroup(
    form: IngredientForm,
    onStoreSection: (StoreSection) -> Unit,
    onPlantPoints: (PlantPoints) -> Unit,
    onDrainedWeight: (DrainedWeight?) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<PropertyDialog?>(null) }
    SettingsGroup(
        {
            SettingsRow(
                icon = null,
                title = stringResource(R.string.ingredient_section_store_section),
                summary = stringResource(form.storeSection.label),
                onClick = { dialog = PropertyDialog.STORE_SECTION },
            )
        },
        {
            SettingsRow(
                icon = null,
                title = stringResource(R.string.ingredient_section_plant_points),
                summary = plantPointsLabel(form.plantPoints),
                onClick = { dialog = PropertyDialog.PLANT_POINTS },
                trailing = {
                    IconButton(onClick = { dialog = PropertyDialog.PLANT_POINTS_INFO }) {
                        Icon(Icons.Filled.Info, stringResource(R.string.plant_points_info_description))
                    }
                },
            )
        },
        {
            SettingsRow(
                icon = null,
                title = stringResource(R.string.ingredient_drained_weight),
                summary = form.drainedWeight?.let {
                    val locale = currentLocale()
                    stringResource(
                        R.string.ingredient_drained_weight_summary,
                        formatDecimal(it.drainedWeightGrams, locale),
                        formatDecimal(it.netWeightGrams, locale),
                    )
                } ?: stringResource(R.string.ingredient_not_drained),
                onClick = { dialog = PropertyDialog.DRAINED_WEIGHT },
            )
        },
        horizontalPadding = 0.dp,
    )

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
        PropertyDialog.DRAINED_WEIGHT -> DrainedWeightDialog(
            initial = form.drainedWeight,
            onConfirm = { onDrainedWeight(it); dismiss() },
            onDismiss = dismiss,
        )
        null -> Unit
    }
}

/** Net and drained weight from the can; OK stays disabled until both are empty or 0 < drained ≤ net. */
@Composable
private fun DrainedWeightDialog(initial: DrainedWeight?, onConfirm: (DrainedWeight?) -> Unit, onDismiss: () -> Unit) {
    var net by rememberSaveable { mutableStateOf(DrainedWeightForm.from(initial).net) }
    var drained by rememberSaveable { mutableStateOf(DrainedWeightForm.from(initial).drained) }
    val form = DrainedWeightForm(net, drained)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ingredient_drained_weight)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.ingredient_drained_weight_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FormField(
                    value = net,
                    onValueChange = { net = it },
                    label = R.string.ingredient_net_weight_field,
                    error = if (form.netInvalid) R.string.error_positive_decimal else null,
                )
                FormField(
                    value = drained,
                    onValueChange = { drained = it },
                    label = R.string.ingredient_drained_weight_field,
                    error = when (form.drainedError) {
                        DrainedWeightError.INVALID -> R.string.error_positive_decimal
                        DrainedWeightError.EXCEEDS_NET -> R.string.error_drained_exceeds_net
                        null -> null
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(form.toDrainedWeight()) }, enabled = form.isValid) {
                Text(stringResource(R.string.action_ok))
            }
        },
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

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
    @StringRes error: Int? = null,
    numeric: Boolean = true,
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

internal val StoreSection.label: Int
    get() = when (this) {
        StoreSection.PRODUCE -> R.string.store_section_produce
        StoreSection.DAIRY_CHILLED -> R.string.store_section_dairy_chilled
        StoreSection.DRY_GOODS -> R.string.store_section_dry_goods
        StoreSection.FROZEN -> R.string.store_section_frozen
        StoreSection.OTHER -> R.string.store_section_other
    }
