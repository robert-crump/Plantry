package com.example.plantry.ui.ingredient

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.BuyAsError
import com.example.plantry.data.BuyUnit
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.Nutrient
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IngredientDetailUiState(
    /** Null until the ingredient is loaded. */
    val ingredient: Ingredient? = null,
    val form: IngredientForm = IngredientForm(),
    val showErrors: Boolean = false,
    val buyAsError: BuyAsError? = null,
    val saved: Boolean = false,
)

class IngredientDetailViewModel(
    private val ingredientId: Long,
    private val repository: IngredientRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(IngredientDetailUiState())
    val state: StateFlow<IngredientDetailUiState> = _state.asStateFlow()

    /** Candidates for the buy-as link: every other ingredient. */
    val buyAsOptions: StateFlow<List<Ingredient>> = repository.observeIngredients()
        .map { all -> all.filter { it.id != ingredientId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val ingredient = repository.getIngredient(ingredientId) ?: return@launch
            _state.update { it.copy(ingredient = ingredient, form = IngredientForm.from(ingredient)) }
        }
    }

    fun onFormChange(transform: IngredientForm.() -> IngredientForm) {
        _state.update { it.copy(form = it.form.transform(), buyAsError = null) }
    }

    fun save() {
        val draft = _state.value.form.toDraft()
        if (draft == null) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            val error = repository.update(ingredientId, draft)
            _state.update { if (error == null) it.copy(saved = true) else it.copy(buyAsError = error) }
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
    val buyAsOptions by viewModel.buyAsOptions.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

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
                        TextButton(onClick = viewModel::save) { Text(stringResource(R.string.action_save)) }
                    }
                },
            )
        },
    ) { padding ->
        if (ingredient == null) return@Scaffold
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
                if (ingredient.fdcId != null && ingredient.usdaDescription != null) {
                    stringResource(R.string.ingredient_usda_source, ingredient.usdaDescription, ingredient.fdcId)
                } else {
                    stringResource(R.string.ingredient_no_usda_source)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle(R.string.ingredient_section_nutrition)
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

            SectionTitle(R.string.ingredient_section_units)
            Text(
                stringResource(R.string.ingredient_units_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            form.unitWeights.forEachIndexed { index, unit ->
                val error = errors?.unitWeights?.contains(index) == true
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(
                        value = unit.label,
                        onValueChange = { onChange { withUnitWeight(index, unit.copy(label = it)) } },
                        label = R.string.ingredient_unit_label,
                        error = if (error) R.string.error_unit_weight else null,
                        numeric = false,
                        modifier = Modifier.weight(2f),
                    )
                    FormField(
                        value = unit.grams,
                        onValueChange = { onChange { withUnitWeight(index, unit.copy(grams = it)) } },
                        label = R.string.ingredient_unit_grams,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onChange { removeUnitWeight(index) } }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.ingredient_unit_remove))
                    }
                }
            }
            TextButton(onClick = { onChange { addUnitWeight() } }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.ingredient_unit_add), Modifier.padding(start = 8.dp))
            }

            SectionTitle(R.string.ingredient_section_buy_unit)
            ChoiceRow(
                options = BuyUnit.entries,
                selected = form.buyUnit,
                label = { it.label },
                onSelect = { onChange { copy(buyUnit = it) } },
            )
            if (errors?.pieceWeightMissing == true) ErrorText(R.string.error_piece_weight_missing)
            if (form.buyUnit == BuyUnit.PACK) {
                FormField(
                    value = form.packSize,
                    onValueChange = { onChange { copy(packSize = it) } },
                    label = R.string.ingredient_pack_size,
                    error = if (errors?.packSize == true) R.string.error_positive_decimal else null,
                )
            }

            SectionTitle(R.string.ingredient_section_store_section)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoreSection.entries.forEach { section ->
                    FilterChip(
                        selected = form.storeSection == section,
                        onClick = { onChange { copy(storeSection = section) } },
                        label = { Text(stringResource(section.label)) },
                    )
                }
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.ingredient_staple)) },
                trailingContent = {
                    Switch(checked = form.staple, onCheckedChange = { onChange { copy(staple = it) } })
                },
            )

            SectionTitle(R.string.ingredient_section_plant_points)
            ChoiceRow(
                options = PlantPoints.entries,
                selected = form.plantPoints,
                label = { it.label },
                onSelect = { onChange { copy(plantPoints = it) } },
            )

            SectionTitle(R.string.ingredient_section_buy_as)
            BuyAsDropdown(
                options = buyAsOptions,
                selectedId = form.buyAsIngredientId,
                onSelect = { onChange { copy(buyAsIngredientId = it) } },
            )
            state.buyAsError?.let { error ->
                ErrorText(
                    when (error) {
                        BuyAsError.SELF_LINK -> R.string.error_buy_as_self
                        BuyAsError.CYCLE -> R.string.error_buy_as_cycle
                    },
                )
            }
            if (form.buyAsIngredientId != null) {
                FormField(
                    value = form.buyAsYieldFactor,
                    onValueChange = { onChange { copy(buyAsYieldFactor = it) } },
                    label = R.string.ingredient_buy_as_yield,
                    error = if (errors?.buyAsYieldFactor == true) R.string.error_positive_decimal else null,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuyAsDropdown(options: List<Ingredient>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val none = stringResource(R.string.ingredient_buy_as_none)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.id == selectedId }?.name ?: none,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(none) }, onClick = {
                onSelect(null)
                expanded = false
            })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.name) }, onClick = {
                    onSelect(option.id)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label(option))) }
        }
    }
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
private fun ErrorText(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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

private val Nutrient.label: Int
    get() = when (this) {
        Nutrient.KCAL -> R.string.nutrient_kcal
        Nutrient.PROTEIN -> R.string.nutrient_protein
        Nutrient.CARBS -> R.string.nutrient_carbs
        Nutrient.SUGAR -> R.string.nutrient_sugar
        Nutrient.FAT -> R.string.nutrient_fat
        Nutrient.FIBRE -> R.string.nutrient_fibre
    }

private val BuyUnit.label: Int
    get() = when (this) {
        BuyUnit.PIECES -> R.string.buy_unit_pieces
        BuyUnit.PACK -> R.string.buy_unit_pack
        BuyUnit.GRAMS -> R.string.buy_unit_grams
    }

private val StoreSection.label: Int
    get() = when (this) {
        StoreSection.PRODUCE -> R.string.store_section_produce
        StoreSection.DAIRY_CHILLED -> R.string.store_section_dairy_chilled
        StoreSection.DRY_GOODS -> R.string.store_section_dry_goods
        StoreSection.FROZEN -> R.string.store_section_frozen
        StoreSection.OTHER -> R.string.store_section_other
    }

private val PlantPoints.label: Int
    get() = when (this) {
        PlantPoints.ONE -> R.string.plant_points_one
        PlantPoints.QUARTER -> R.string.plant_points_quarter
        PlantPoints.ZERO -> R.string.plant_points_zero
    }
