package com.example.plantry.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.plantry.R
import com.example.plantry.data.claude.ClaudeFailure
import com.example.plantry.data.claude.ConnectionResult
import com.example.plantry.data.claude.ConnectionTester
import com.example.plantry.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where testing a typed key stands. */
sealed interface KeyCheck {
    data object Idle : KeyCheck
    data object Testing : KeyCheck
    data class Failed(val reason: ClaudeFailure) : KeyCheck
    data object Saved : KeyCheck
}

/**
 * Tests a typed key with one tiny call before storing it. A key problem (see
 * [ClaudeFailure.isKeyProblem]) keeps it out; after a passing problem it can be saved anyway.
 */
class ApiKeyEntry(
    private val repository: SettingsRepository,
    private val tester: ConnectionTester,
    private val scope: CoroutineScope,
    private val onSaved: () -> Unit = {},
) {
    private val _check = MutableStateFlow<KeyCheck>(KeyCheck.Idle)
    val check: StateFlow<KeyCheck> = _check.asStateFlow()

    private var testing: Job? = null

    /** With [force], stores [key] untested. */
    fun save(key: String, force: Boolean = false) {
        val trimmed = key.trim()
        if (trimmed.isEmpty() || _check.value == KeyCheck.Testing) return
        if (force) {
            store(trimmed)
            return
        }
        _check.value = KeyCheck.Testing
        testing = scope.launch {
            when (val result = tester.test(trimmed, repository.settings.value.scanModel)) {
                ConnectionResult.Success -> store(trimmed)
                is ConnectionResult.Failure -> _check.value = KeyCheck.Failed(result.reason)
            }
        }
    }

    /** Back to [KeyCheck.Idle], e.g. when the key is edited or the dialog closes. */
    fun reset() {
        testing?.cancel()
        _check.value = KeyCheck.Idle
    }

    private fun store(key: String) {
        repository.setApiKey(key)
        _check.value = KeyCheck.Saved
        onSaved()
    }
}

/**
 * Asks for the Anthropic API key; saving tests it first, see [ApiKeyEntry]. With [onDelete] (a key
 * is stored) a red "Schlüssel löschen" button sits bottom left.
 */
@Composable
fun ApiKeyDialog(
    check: KeyCheck,
    onSave: (key: String, force: Boolean) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    @StringRes message: Int = R.string.settings_api_key_dialog_message,
    onDelete: (() -> Unit)? = null,
) {
    var key by rememberSaveable { mutableStateOf("") }
    var blank by rememberSaveable { mutableStateOf(false) }
    val testing = check == KeyCheck.Testing
    val failure = (check as? KeyCheck.Failed)?.reason

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
                        blank = false
                        onEdit()
                    },
                    label = { Text(stringResource(R.string.settings_api_key)) },
                    singleLine = true,
                    enabled = !testing,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    isError = blank || failure != null,
                    supportingText = when {
                        blank -> ({ Text(stringResource(R.string.settings_api_key_required)) })
                        failure != null -> ({ Text(stringResource(failure.message)) })
                        testing -> ({ Text(stringResource(R.string.settings_api_key_testing)) })
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        // With a delete button all share one full-width slot, so it can sit opposite the others.
        // On narrow screens it wraps onto its own line above Abbrechen/Speichern.
        confirmButton = {
            FlowRow(if (onDelete != null) Modifier.fillMaxWidth() else Modifier) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        enabled = !testing,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.settings_api_key_delete)) }
                }
                Row(
                    if (onDelete != null) Modifier.weight(1f) else Modifier,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    // After a passing problem, saving again would only repeat it; editing the key brings "Speichern" back.
                    if (failure != null && !failure.isKeyProblem) {
                        TextButton(onClick = { onSave(key, true) }) { Text(stringResource(R.string.settings_api_key_save_anyway)) }
                    } else if (testing) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).size(24.dp))
                    } else {
                        TextButton(onClick = { if (key.isBlank()) blank = true else onSave(key, false) }) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }
            }
        },
    )
}
