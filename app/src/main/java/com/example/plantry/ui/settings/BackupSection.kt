package com.example.plantry.ui.settings

import android.content.ContentResolver
import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.backup.BackupFile
import com.example.plantry.data.backup.BackupRepository
import com.example.plantry.data.backup.InvalidBackupException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class BackupUiState(
    val busy: Boolean = false,
    /** A parsed file waiting for the user to confirm that it replaces all data. */
    val pendingImport: BackupFile? = null,
    @StringRes val message: Int? = null,
)

class BackupViewModel(
    private val repository: BackupRepository,
    private val contentResolver: ContentResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun nextFileName(): String = repository.nextFileName()

    fun export(uri: Uri) = run {
        repository.export { json ->
            val stream = contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No stream for $uri")
            stream.bufferedWriter().use { it.write(json) }
        }
        R.string.backup_exported
    }

    fun read(uri: Uri) = run {
        val stream = contentResolver.openInputStream(uri) ?: throw IOException("No stream for $uri")
        val file = repository.read(stream.bufferedReader().use { it.readText() })
        _state.update { it.copy(pendingImport = file) }
        null
    }

    fun confirmImport() {
        val file = _state.value.pendingImport ?: return
        _state.update { it.copy(pendingImport = null) }
        run {
            repository.import(file)
            R.string.backup_imported
        }
    }

    fun cancelImport() = _state.update { it.copy(pendingImport = null) }

    fun messageShown() = _state.update { it.copy(message = null) }

    /** Runs [action] while busy and reports its message, or the matching German error. */
    private fun run(action: suspend () -> Int?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val message = try {
                action()
            } catch (e: InvalidBackupException) {
                when (e.reason) {
                    InvalidBackupException.Reason.NOT_A_BACKUP -> R.string.backup_error_invalid
                    InvalidBackupException.Reason.NEWER_VERSION -> R.string.backup_error_newer
                }
            } catch (_: IOException) {
                R.string.backup_error_io
            } catch (_: SecurityException) {
                R.string.backup_error_io
            } catch (_: SQLiteException) {
                R.string.backup_error_import
            }
            _state.update { it.copy(busy = false, message = message) }
        }
    }
}

@Composable
fun BackupSection(viewModel: BackupViewModel, snackbarHostState: SnackbarHostState) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_TYPE)) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::read)
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.backup_export)) },
        supportingContent = { Text(stringResource(R.string.backup_export_hint)) },
        leadingContent = { Icon(Icons.Filled.Upload, contentDescription = null) },
        modifier = Modifier.clickable(enabled = !state.busy) { exportLauncher.launch(viewModel.nextFileName()) },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.backup_import)) },
        supportingContent = { Text(stringResource(R.string.backup_import_hint)) },
        leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
        // Some file managers don't know the JSON type, so any file can be picked.
        modifier = Modifier.clickable(enabled = !state.busy) { importLauncher.launch(arrayOf(MIME_TYPE, "*/*")) },
    )

    state.pendingImport?.let { file ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = {
                Text(stringResource(R.string.backup_import_confirm_message, file.recipes.size, file.ingredients.size))
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmImport) { Text(stringResource(R.string.backup_import_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelImport) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    val message = state.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(resources.getString(message))
            viewModel.messageShown()
        }
    }
}

private const val MIME_TYPE = "application/json"
