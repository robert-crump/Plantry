package com.example.plantry.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.claude.ClaudeFailure
import com.example.plantry.data.claude.ConnectionResult
import com.example.plantry.data.claude.ConnectionTester
import com.example.plantry.data.settings.ScanModel
import com.example.plantry.data.settings.Settings
import com.example.plantry.data.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Running : ConnectionTestState
    data class Done(val result: ConnectionResult) : ConnectionTestState
}

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val tester: ConnectionTester,
) : ViewModel() {

    val settings: StateFlow<Settings> = repository.settings

    private val _connectionTest = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTest: StateFlow<ConnectionTestState> = _connectionTest.asStateFlow()

    private var testJob: Job? = null

    fun saveApiKey(key: String): Boolean = repository.setApiKey(key).also { if (it) resetTest() }

    fun deleteApiKey() {
        repository.deleteApiKey()
        resetTest()
    }

    fun setScanModel(model: ScanModel) {
        repository.setScanModel(model)
        resetTest()
    }

    fun setCooldownDays(days: Int): Boolean = repository.setCooldownDays(days)

    fun testConnection() {
        val key = repository.apiKey() ?: return
        val model = settings.value.scanModel
        testJob?.cancel()
        _connectionTest.value = ConnectionTestState.Running
        testJob = viewModelScope.launch {
            _connectionTest.value = ConnectionTestState.Done(tester.test(key, model))
        }
    }

    private fun resetTest() {
        testJob?.cancel()
        _connectionTest.value = ConnectionTestState.Idle
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, backupViewModel: BackupViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val connectionTest by viewModel.connectionTest.collectAsStateWithLifecycle()
    var editingKey by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var choosingModel by rememberSaveable { mutableStateOf(false) }
    var editingCooldown by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader(R.string.settings_claude)
            SettingsGroup(
                {
                    SettingsRow(
                        icon = Icons.Filled.Key,
                        title = stringResource(R.string.settings_api_key),
                        summary = settings.maskedApiKey ?: stringResource(R.string.settings_api_key_missing),
                        onClick = { editingKey = true },
                        trailing = if (settings.hasApiKey) {
                            {
                                IconButton(onClick = { confirmingDelete = true }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_api_key_delete))
                                }
                            }
                        } else {
                            null
                        },
                    )
                },
                {
                    SettingsRow(
                        icon = Icons.Filled.AutoAwesome,
                        title = stringResource(R.string.settings_scan_model),
                        summary = stringResource(settings.scanModel.label),
                        onClick = { choosingModel = true },
                    )
                },
                {
                    ConnectionRow(
                        state = connectionTest,
                        enabled = settings.hasApiKey && connectionTest != ConnectionTestState.Running,
                        onClick = viewModel::testConnection,
                    )
                },
            )
            SectionHint(R.string.settings_api_key_hint)

            SectionHeader(R.string.settings_week_plan)
            SettingsGroup(
                {
                    SettingsRow(
                        icon = Icons.Filled.EventRepeat,
                        title = stringResource(R.string.settings_cooldown),
                        summary = pluralStringResource(
                            R.plurals.settings_cooldown_days,
                            settings.cooldownDays,
                            settings.cooldownDays,
                        ),
                        onClick = { editingCooldown = true },
                    )
                },
            )
            SectionHint(R.string.settings_cooldown_hint)

            SectionHeader(R.string.settings_backup)
            BackupSection(backupViewModel, snackbar)
        }
    }

    if (editingKey) {
        ApiKeyDialog(
            onSave = { key -> viewModel.saveApiKey(key).also { saved -> if (saved) editingKey = false } },
            onDismiss = { editingKey = false },
            dismissLabel = R.string.action_cancel,
        )
    }

    if (choosingModel) {
        ChoiceDialog(
            title = stringResource(R.string.settings_scan_model),
            options = ScanModel.entries,
            selected = settings.scanModel,
            label = { stringResource(it.label) },
            description = { stringResource(it.description) },
            onSelect = {
                viewModel.setScanModel(it)
                choosingModel = false
            },
            onDismiss = { choosingModel = false },
        )
    }

    if (editingCooldown) {
        CooldownDialog(
            initialDays = settings.cooldownDays,
            onSave = { days -> viewModel.setCooldownDays(days).also { saved -> if (saved) editingCooldown = false } },
            onDismiss = { editingCooldown = false },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.settings_api_key_delete_title)) },
            text = { Text(stringResource(R.string.settings_api_key_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteApiKey()
                    confirmingDelete = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
    )
}

@Composable
private fun SectionHint(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp),
    )
}

private val GroupOuterCorner = 24.dp
private val GroupInnerCorner = 4.dp

/** Rounded rows separated by a small gap; only the outer corners of the first and last row are large. */
@Composable
internal fun SettingsGroup(vararg rows: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEachIndexed { index, row ->
            val top = if (index == 0) GroupOuterCorner else GroupInnerCorner
            val bottom = if (index == rows.lastIndex) GroupOuterCorner else GroupInnerCorner
            Surface(
                shape = RoundedCornerShape(top, top, bottom, bottom),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                row()
            }
        }
    }
}

@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
    summaryColor: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null,
) {
    val disabled = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it, color = if (enabled) summaryColor else disabled) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = trailing,
        colors = if (enabled) {
            ListItemDefaults.colors(containerColor = Color.Transparent)
        } else {
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = disabled,
                leadingIconColor = disabled,
            )
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

private const val DISABLED_ALPHA = 0.38f

/** The test runs from the row; its result replaces the summary. */
@Composable
private fun ConnectionRow(state: ConnectionTestState, enabled: Boolean, onClick: () -> Unit) {
    val result = (state as? ConnectionTestState.Done)?.result
    SettingsRow(
        icon = Icons.Filled.NetworkCheck,
        title = stringResource(R.string.settings_connection_test),
        summary = when (result) {
            null -> stringResource(R.string.settings_connection_hint)
            ConnectionResult.Success -> stringResource(R.string.settings_connection_success)
            is ConnectionResult.Failure -> stringResource(result.reason.message)
        },
        summaryColor = when (result) {
            null -> Color.Unspecified
            ConnectionResult.Success -> MaterialTheme.colorScheme.primary
            is ConnectionResult.Failure -> MaterialTheme.colorScheme.error
        },
        enabled = enabled,
        onClick = onClick,
        trailing = if (state == ConnectionTestState.Running) {
            { CircularProgressIndicator(Modifier.size(24.dp)) }
        } else {
            null
        },
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    description: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null, modifier = Modifier.padding(12.dp))
                        Column {
                            Text(label(option), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                description(option),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Asks for the Anthropic API key. Used on first launch (dismiss = "Später") and in Settings.
 * [onSave] returns false when the key was rejected, which keeps the dialog open with an error.
 */
@Composable
fun ApiKeyDialog(
    onSave: (String) -> Boolean,
    onDismiss: () -> Unit,
    @StringRes dismissLabel: Int,
    @StringRes message: Int = R.string.settings_api_key_dialog_message,
) {
    var key by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_api_key_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(message))
                OutlinedTextField(
                    value = key,
                    onValueChange = {
                        key = it
                        error = false
                    },
                    label = { Text(stringResource(R.string.settings_api_key)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    isError = error,
                    supportingText = if (error) ({ Text(stringResource(R.string.settings_api_key_required)) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { error = !onSave(key) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(dismissLabel)) }
        },
    )
}

/** [onSave] returns false when the number of days was rejected, which keeps the dialog open with an error. */
@Composable
private fun CooldownDialog(initialDays: Int, onSave: (Int) -> Boolean, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initialDays.toString()) }
    var error by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_cooldown)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = false
                },
                label = { Text(stringResource(R.string.settings_cooldown_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = error,
                supportingText = {
                    Text(
                        stringResource(
                            R.string.settings_cooldown_range,
                            SettingsRepository.COOLDOWN_RANGE.first,
                            SettingsRepository.COOLDOWN_RANGE.last,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { error = !(text.trim().toIntOrNull()?.let(onSave) ?: false) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val ScanModel.label: Int
    get() = when (this) {
        ScanModel.OPUS -> R.string.scan_model_opus
        ScanModel.SONNET -> R.string.scan_model_sonnet
    }

private val ScanModel.description: Int
    get() = when (this) {
        ScanModel.OPUS -> R.string.scan_model_opus_description
        ScanModel.SONNET -> R.string.scan_model_sonnet_description
    }

/** German message for each failure; reused by the scan screen (#5). */
val ClaudeFailure.message: Int
    get() = when (this) {
        ClaudeFailure.INVALID_KEY -> R.string.claude_error_invalid_key
        ClaudeFailure.NO_CREDIT -> R.string.claude_error_no_credit
        ClaudeFailure.PERMISSION_DENIED -> R.string.claude_error_permission
        ClaudeFailure.MODEL_NOT_FOUND -> R.string.claude_error_model_not_found
        ClaudeFailure.RATE_LIMITED -> R.string.claude_error_rate_limited
        ClaudeFailure.OVERLOADED -> R.string.claude_error_overloaded
        ClaudeFailure.NETWORK -> R.string.claude_error_network
        ClaudeFailure.UNKNOWN -> R.string.claude_error_unknown
        ClaudeFailure.NO_API_KEY -> R.string.claude_error_no_api_key
        ClaudeFailure.REFUSED -> R.string.claude_error_refused
        ClaudeFailure.TRUNCATED -> R.string.claude_error_truncated
        ClaudeFailure.BAD_RESPONSE -> R.string.claude_error_bad_response
        ClaudeFailure.NOT_A_RECIPE -> R.string.claude_error_not_a_recipe
    }
