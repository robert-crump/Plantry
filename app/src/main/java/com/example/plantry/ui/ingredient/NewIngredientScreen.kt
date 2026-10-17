package com.example.plantry.ui.ingredient

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.NewIngredientFinder
import com.example.plantry.data.NewIngredientSeed
import com.example.plantry.data.claude.ClaudeFailure
import com.example.plantry.data.claude.ClaudeResult
import com.example.plantry.data.claude.ConnectionTester
import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.claude.NewFood
import com.example.plantry.data.openfoodfacts.OffLookup
import com.example.plantry.data.openfoodfacts.ProductLookup
import com.example.plantry.data.settings.SettingsRepository
import com.example.plantry.ui.settings.ApiKeyDialog
import com.example.plantry.ui.settings.ApiKeyEntry
import com.example.plantry.ui.settings.message
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where asking Claude for a new ingredient stands. */
sealed interface ClaudeStep {
    /** Asking for the name to propose an ingredient for. */
    data object Naming : ClaudeStep

    /** Waiting for the costs to be accepted, then a key: [gate] is the missing one. */
    data class Gated(val name: String, val gate: Gate) : ClaudeStep

    data class Proposing(val name: String) : ClaudeStep

    /** No USDA entry fits, so the user types the package's values in. */
    data class NoUsda(val proposal: IngredientProposal) : ClaudeStep

    data class Failed(val name: String, val reason: ClaudeFailure) : ClaudeStep

    enum class Gate { COSTS, KEY }
}

data class NewIngredientUiState(
    /** The scanned barcode's lookup; null when there is none to show. */
    val barcode: BarcodeLookup? = null,
    val claude: ClaudeStep? = null,
    /** Set once a start is complete; the screen hands it on, see [NewIngredientViewModel.onSeedTaken]. */
    val seed: NewIngredientSeed? = null,
)

class NewIngredientViewModel(
    private val products: ProductLookup,
    private val finder: NewIngredientFinder,
    private val settings: SettingsRepository,
    tester: ConnectionTester,
) : ViewModel() {

    private val _state = MutableStateFlow(NewIngredientUiState())
    val state: StateFlow<NewIngredientUiState> = _state.asStateFlow()

    /** The key entered while asking Claude waits for one; once it is stored, the proposal runs. */
    val keyEntry: ApiKeyEntry = ApiKeyEntry(settings, tester, viewModelScope) {
        keyEntry.reset()
        (_state.value.claude as? ClaudeStep.Gated)?.let { propose(it.name) }
    }

    private var lookupJob: Job? = null

    private var proposeJob: Job? = null

    fun lookUp(barcode: String) {
        lookupJob?.cancel()
        _state.update { it.copy(barcode = BarcodeLookup.Searching) }
        lookupJob = viewModelScope.launch { _state.update { it.copy(barcode = BarcodeLookup.Done(products.lookup(barcode))) } }
    }

    /** Ends the lookup, or closes the dialog showing its result. */
    fun dismissBarcode() {
        lookupJob?.cancel()
        _state.update { it.copy(barcode = null) }
    }

    fun askClaude() {
        _state.update { it.copy(claude = ClaudeStep.Naming) }
    }

    /**
     * Has Claude propose an ingredient named [name]: a USDA entry, store section, plant points and
     * units. Asks to accept the costs and for a key first, if missing.
     */
    fun propose(name: String) {
        val current = settings.settings.value
        val apiKey = settings.apiKey()
        val gate = when {
            !current.claudeCostsAccepted -> ClaudeStep.Gate.COSTS
            !current.hasApiKey || apiKey == null -> ClaudeStep.Gate.KEY
            else -> null
        }
        if (gate != null || apiKey == null) {
            _state.update { it.copy(claude = ClaudeStep.Gated(name, gate ?: ClaudeStep.Gate.KEY)) }
            return
        }
        val food = NewFood(id = -1, name = name.trim(), originalText = "", searchTerms = emptyList())
        _state.update { it.copy(claude = ClaudeStep.Proposing(food.name)) }
        proposeJob?.cancel()
        proposeJob = viewModelScope.launch {
            val result = finder.propose(apiKey, current.scanModel, listOf(food))
            _state.update { state ->
                when (result) {
                    is ClaudeResult.Success -> {
                        val proposal = result.value.getValue(food.id)
                        if (proposal.source == null) {
                            state.copy(claude = ClaudeStep.NoUsda(proposal))
                        } else {
                            state.copy(claude = null, seed = NewIngredientSeed.fromProposal(proposal))
                        }
                    }
                    is ClaudeResult.Failure -> state.copy(claude = ClaudeStep.Failed(food.name, result.reason))
                }
            }
        }
    }

    fun acceptClaudeCosts() {
        val gated = _state.value.claude as? ClaudeStep.Gated ?: return
        settings.acceptClaudeCosts()
        propose(gated.name)
    }

    /** Closes whatever step of asking Claude is open, and stops a running proposal. */
    fun dismissClaude() {
        proposeJob?.cancel()
        keyEntry.reset()
        _state.update { it.copy(claude = null) }
    }

    /** Hands on a start without asking Claude (barcode or typed in). */
    fun start(seed: NewIngredientSeed) {
        lookupJob?.cancel()
        _state.update { it.copy(barcode = null, claude = null, seed = seed) }
    }

    fun onSeedTaken() {
        _state.update { it.copy(seed = null) }
    }
}

/**
 * The three equal ways to start a new ingredient: scan a barcode, ask Claude, or type the values
 * in. Each ends on the ingredient's own screen, where the user finishes and saves it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewIngredientScreen(
    viewModel: NewIngredientViewModel,
    onBack: () -> Unit,
    onNew: (NewIngredientSeed) -> Unit,
    /** The name every way starts the new ingredient with, e.g. the one typed into a recipe line. */
    initialName: String = "",
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.seed) {
        state.seed?.let {
            viewModel.onSeedTaken()
            onNew(it)
        }
    }
    val scannerUnavailable = stringResource(R.string.barcode_unavailable)
    val context = LocalContext.current
    val scan = rememberBarcodeScanner(
        onScanned = viewModel::lookUp,
        onUnavailable = { Toast.makeText(context, scannerUnavailable, Toast.LENGTH_SHORT).show() },
    )
    var typing by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_ingredient_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChoiceCard(
                icon = Icons.Filled.QrCodeScanner,
                title = R.string.new_ingredient_choose_barcode,
                description = R.string.new_ingredient_choose_barcode_description,
                onClick = scan,
            )
            ChoiceCard(
                icon = Icons.Filled.AutoAwesome,
                title = R.string.new_ingredient_choose_claude,
                description = R.string.new_ingredient_choose_claude_description,
                onClick = viewModel::askClaude,
            )
            ChoiceCard(
                icon = Icons.Filled.Edit,
                title = R.string.new_ingredient_choose_manual,
                description = R.string.new_ingredient_choose_manual_description,
                onClick = { typing = true },
            )
        }

        if (typing) {
            LabelNutritionDialog(
                confirmLabel = R.string.action_create,
                onConfirm = { name, nutrition ->
                    typing = false
                    viewModel.start(NewIngredientSeed(name, nutrition))
                },
                onDismiss = { typing = false },
                initial = LabelNutritionForm(name = initialName),
                askName = true,
            )
        }
        when (val lookup = state.barcode) {
            BarcodeLookup.Searching -> BarcodeSearchingDialog(onDismiss = viewModel::dismissBarcode)
            is BarcodeLookup.Done -> {
                val product = (lookup.result as? OffLookup.Found)?.product
                // Not found or offline: the same dialog, empty, to type the label in.
                LabelNutritionDialog(
                    confirmLabel = R.string.action_create,
                    onConfirm = { name, nutrition ->
                        viewModel.start(
                            if (product != null) NewIngredientSeed.fromProduct(product, name, nutrition) else NewIngredientSeed(name, nutrition),
                        )
                    },
                    onDismiss = viewModel::dismissBarcode,
                    initial = (product?.let { LabelNutritionForm.from(it) } ?: LabelNutritionForm())
                        .let { if (initialName.isBlank()) it else it.copy(name = initialName) },
                    askName = true,
                    message = product?.let { foundText(it) } ?: failureText(lookup.result),
                    missing = product?.missing.orEmpty(),
                )
            }
            null -> Unit
        }
        ClaudeDialogs(state.claude, viewModel, initialName)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClaudeDialogs(step: ClaudeStep?, viewModel: NewIngredientViewModel, initialName: String) {
    when (step) {
        null -> Unit
        ClaudeStep.Naming -> ClaudeNameDialog(initialName, onPropose = viewModel::propose, onDismiss = viewModel::dismissClaude)
        is ClaudeStep.Gated -> when (step.gate) {
            ClaudeStep.Gate.COSTS -> AlertDialog(
                onDismissRequest = viewModel::dismissClaude,
                title = { Text(stringResource(R.string.claude_costs_title)) },
                text = { Text(stringResource(R.string.claude_costs_message)) },
                confirmButton = {
                    TextButton(onClick = viewModel::acceptClaudeCosts) { Text(stringResource(R.string.claude_costs_accept)) }
                },
                dismissButton = { TextButton(onClick = viewModel::dismissClaude) { Text(stringResource(R.string.action_cancel)) } },
            )
            ClaudeStep.Gate.KEY -> {
                val keyCheck by viewModel.keyEntry.check.collectAsStateWithLifecycle()
                ApiKeyDialog(
                    check = keyCheck,
                    onSave = viewModel.keyEntry::save,
                    onEdit = viewModel.keyEntry::reset,
                    onDismiss = viewModel::dismissClaude,
                    message = R.string.settings_api_key_claude_message,
                )
            }
        }
        is ClaudeStep.Proposing -> AlertDialog(
            onDismissRequest = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LoadingIndicator()
                    Text(stringResource(R.string.new_ingredient_proposing_one))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::dismissClaude) { Text(stringResource(R.string.action_cancel)) } },
        )
        is ClaudeStep.NoUsda -> LabelNutritionDialog(
            confirmLabel = R.string.action_create,
            onConfirm = { name, nutrition -> viewModel.start(NewIngredientSeed.fromProposal(step.proposal, name, nutrition)) },
            onDismiss = viewModel::dismissClaude,
            initial = LabelNutritionForm(name = step.proposal.name),
            askName = true,
            message = stringResource(R.string.new_ingredient_claude_no_usda),
        )
        is ClaudeStep.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismissClaude,
            title = { Text(stringResource(R.string.new_ingredient_claude_failed)) },
            text = { Text(stringResource(step.reason.message)) },
            confirmButton = { TextButton(onClick = { viewModel.propose(step.name) }) { Text(stringResource(R.string.scan_retry)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissClaude) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** The German name Claude proposes the ingredient for. */
@Composable
private fun ClaudeNameDialog(initialName: String, onPropose: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_ingredient_choose_claude)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.new_ingredient_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onPropose(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.new_ingredient_claude_propose))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ChoiceCard(icon: ImageVector, @StringRes title: Int, @StringRes description: Int, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
