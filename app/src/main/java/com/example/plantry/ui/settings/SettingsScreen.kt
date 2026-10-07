package com.example.plantry.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.ui.rememberNotificationPermissionRequest
import com.example.plantry.data.claude.ClaudeFailure
import com.example.plantry.data.claude.ConnectionResult
import com.example.plantry.data.claude.ConnectionTester
import com.example.plantry.data.claude.ModelCatalog
import com.example.plantry.data.settings.ReminderKind
import com.example.plantry.data.settings.ReminderSetting
import com.example.plantry.data.settings.ScanModel
import com.example.plantry.data.settings.Settings as AppSettings
import com.example.plantry.data.settings.SettingsRepository
import com.example.plantry.data.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Running : ConnectionTestState
    data class Done(val result: ConnectionResult) : ConnectionTestState
}

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val tester: ConnectionTester,
    private val models: ModelCatalog = ModelCatalog.shared,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings

    /** The model each tier currently stands for, e.g. "Opus 5.5"; the built-in one until the API list is known. */
    private val _modelNames = MutableStateFlow(currentModelNames())
    val modelNames: StateFlow<Map<ScanModel, String>> = _modelNames.asStateFlow()

    init {
        refreshModelNames()
    }

    private fun currentModelNames() = ScanModel.entries.associateWith { models.peek(it).name }

    private fun refreshModelNames() {
        val key = repository.apiKey() ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ScanModel.entries.forEach { models.resolve(key, it) } }
            _modelNames.value = currentModelNames()
        }
    }

    private val _connectionTest = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTest: StateFlow<ConnectionTestState> = _connectionTest.asStateFlow()

    private var testJob: Job? = null

    fun saveApiKey(key: String): Boolean = repository.setApiKey(key).also {
        if (it) {
            resetTest()
            refreshModelNames()
        }
    }

    fun deleteApiKey() {
        repository.deleteApiKey()
        resetTest()
    }

    fun setScanModel(model: ScanModel) {
        repository.setScanModel(model)
        resetTest()
    }

    fun setCooldownDays(days: Int): Boolean = repository.setCooldownDays(days)

    fun setReminderEnabled(kind: ReminderKind, enabled: Boolean) = repository.setReminderEnabled(kind, enabled)

    fun setReminderTime(kind: ReminderKind, time: LocalTime) = repository.setReminderTime(kind, time)

    fun setThemeMode(mode: ThemeMode) = repository.setThemeMode(mode)

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
    val modelNames by viewModel.modelNames.collectAsStateWithLifecycle()
    val connectionTest by viewModel.connectionTest.collectAsStateWithLifecycle()
    var editingKey by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var choosingModel by rememberSaveable { mutableStateOf(false) }
    var choosingTheme by rememberSaveable { mutableStateOf(false) }
    var editingCooldown by rememberSaveable { mutableStateOf(false) }
    // The reminder whose time is being picked, and whether picking it is part of turning it on.
    var timeDialog by rememberSaveable { mutableStateOf<ReminderKind?>(null) }
    var turningOn by rememberSaveable { mutableStateOf(false) }
    // A reminder to turn on at a time once the notification permission is answered.
    var awaitingKind by rememberSaveable { mutableStateOf<ReminderKind?>(null) }
    var awaitingTime by rememberSaveable { mutableStateOf<LocalTime?>(null) }
    var showingKeyInfo by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notificationsOff = stringResource(R.string.notifications_off)
    val openSystemSettings = stringResource(R.string.notifications_open_settings)
    val requestNotifications = rememberNotificationPermissionRequest { granted ->
        val kind = awaitingKind
        val time = awaitingTime
        awaitingKind = null
        awaitingTime = null
        if (kind != null && time != null) {
            if (granted) {
                viewModel.setReminderTime(kind, time)
                viewModel.setReminderEnabled(kind, true)
            } else {
                scope.launch {
                    if (snackbar.showSnackbar(notificationsOff, openSystemSettings) == SnackbarResult.ActionPerformed) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader(R.string.settings_appearance)
            SettingsGroup(
                {
                    SettingsRow(
                        icon = Icons.Filled.Palette,
                        title = stringResource(R.string.settings_theme),
                        summary = stringResource(settings.themeMode.label),
                        onClick = { choosingTheme = true },
                    )
                },
            )

            SectionHeader(R.string.settings_claude)
            SettingsGroup(
                {
                    SettingsRow(
                        icon = Icons.Filled.Key,
                        title = stringResource(R.string.settings_api_key),
                        summary = settings.maskedApiKey ?: stringResource(R.string.settings_api_key_missing),
                        onClick = { editingKey = true },
                        trailing = {
                            IconButton(onClick = { showingKeyInfo = true }) {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = stringResource(R.string.settings_api_key_info_description),
                                )
                            }
                        },
                    )
                },
                {
                    SettingsRow(
                        icon = Icons.Filled.AutoAwesome,
                        title = stringResource(R.string.settings_scan_model),
                        summary = modelNames.getValue(settings.scanModel),
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

            SectionHeader(R.string.settings_suggestions)
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

            SectionHeader(R.string.settings_notifications)
            SettingsGroup(
                *ReminderKind.entries.map { kind ->
                    @Composable {
                        ReminderRow(
                            kind = kind,
                            setting = settings.reminder(kind),
                            onEditTime = {
                                turningOn = false
                                timeDialog = kind
                            },
                            onToggle = { on ->
                                if (on) {
                                    turningOn = true
                                    timeDialog = kind
                                } else {
                                    viewModel.setReminderEnabled(kind, false)
                                }
                            },
                        )
                    }
                }.toTypedArray(),
            )

            SectionHeader(R.string.settings_backup)
            BackupSection(backupViewModel, snackbar)
        }
    }

    if (editingKey) {
        ApiKeyDialog(
            onSave = { key -> viewModel.saveApiKey(key).also { saved -> if (saved) editingKey = false } },
            onDismiss = { editingKey = false },
            dismissLabel = R.string.action_cancel,
            onDelete = if (settings.hasApiKey) ({ confirmingDelete = true }) else null,
        )
    }

    if (choosingModel) {
        ChoiceDialog(
            title = stringResource(R.string.settings_scan_model),
            options = ScanModel.entries,
            selected = settings.scanModel,
            label = { modelNames.getValue(it) },
            description = { stringResource(it.description) },
            onSelect = {
                viewModel.setScanModel(it)
                choosingModel = false
            },
            onDismiss = { choosingModel = false },
        )
    }

    if (choosingTheme) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { stringResource(it.label) },
            onSelect = {
                viewModel.setThemeMode(it)
                choosingTheme = false
            },
            onDismiss = { choosingTheme = false },
        )
    }

    if (editingCooldown) {
        CooldownDialog(
            initialDays = settings.cooldownDays,
            onSave = { days -> viewModel.setCooldownDays(days).also { saved -> if (saved) editingCooldown = false } },
            onDismiss = { editingCooldown = false },
        )
    }

    timeDialog?.let { kind ->
        ReminderTimeDialog(
            title = stringResource(kind.title),
            initial = settings.reminder(kind).time,
            onSave = { time ->
                timeDialog = null
                if (turningOn) {
                    awaitingKind = kind
                    awaitingTime = time
                    requestNotifications()
                } else {
                    viewModel.setReminderTime(kind, time)
                }
            },
            onDismiss = { timeDialog = null },
        )
    }

    if (showingKeyInfo) {
        AlertDialog(
            onDismissRequest = { showingKeyInfo = false },
            text = { Text(stringResource(R.string.settings_api_key_info)) },
            confirmButton = {
                TextButton(onClick = { showingKeyInfo = false }) { Text(stringResource(R.string.action_ok)) }
            },
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
                    editingKey = false
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
internal fun SettingsGroup(vararg rows: @Composable () -> Unit, horizontalPadding: Dp = 16.dp) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
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
    icon: ImageVector?,
    title: String,
    /** Null for a row that only shows its value. */
    onClick: (() -> Unit)?,
    summary: String? = null,
    enabled: Boolean = true,
    summaryColor: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null,
) {
    val disabled = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it, color = if (enabled) summaryColor else disabled) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
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
        modifier = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier,
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
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    /** Null when nothing is chosen yet. */
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    description: (@Composable (T) -> String)? = null,
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
                            if (description != null) {
                                Text(
                                    description(option),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
 * With [onDelete] (a key is stored) a red "Schlüssel löschen" button sits bottom left.
 */
@Composable
fun ApiKeyDialog(
    onSave: (String) -> Boolean,
    onDismiss: () -> Unit,
    @StringRes dismissLabel: Int,
    @StringRes message: Int = R.string.settings_api_key_dialog_message,
    onDelete: (() -> Unit)? = null,
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
        // With a delete button all three share one full-width slot, so it can sit opposite the others.
        // On narrow screens it wraps onto its own line above Abbrechen/Speichern.
        confirmButton = {
            FlowRow(if (onDelete != null) Modifier.fillMaxWidth() else Modifier) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.settings_api_key_delete)) }
                }
                Row(
                    if (onDelete != null) Modifier.weight(1f) else Modifier,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(dismissLabel)) }
                    TextButton(onClick = { error = !onSave(key) }) { Text(stringResource(R.string.action_save)) }
                }
            }
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

/** The time is the summary; tapping the row edits it, the switch at the far right turns the reminder on or off. */
@Composable
private fun ReminderRow(kind: ReminderKind, setting: ReminderSetting, onEditTime: () -> Unit, onToggle: (Boolean) -> Unit) {
    SettingsRow(
        icon = kind.icon,
        title = stringResource(kind.title),
        summary = setting.time.format(TimeFormat),
        summaryColor = if (setting.enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
        onClick = onEditTime,
        trailing = { Switch(checked = setting.enabled, onCheckedChange = onToggle) },
    )
}

private val ReminderKind.title: Int
    get() = when (this) {
        ReminderKind.PROPOSAL -> R.string.settings_reminder_proposal
        ReminderKind.SHOPPING -> R.string.settings_reminder_shopping
        ReminderKind.COOKED -> R.string.settings_reminder
    }

private val ReminderKind.icon: ImageVector
    get() = when (this) {
        ReminderKind.PROPOSAL -> Icons.Filled.Lightbulb
        ReminderKind.SHOPPING -> Icons.Filled.ShoppingCart
        ReminderKind.COOKED -> Icons.Filled.Notifications
    }

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(title: String, initial: LocalTime, onSave: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = { onSave(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
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
