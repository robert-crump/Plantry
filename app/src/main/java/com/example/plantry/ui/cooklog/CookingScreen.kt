package com.example.plantry.ui.cooklog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.CookLog
import com.example.plantry.data.CookLogEntry
import com.example.plantry.data.CookLogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class CookingViewModel(private val repository: CookLogRepository) : ViewModel() {
    /** Null until the first emission, so the empty state doesn't flash. */
    val entries: StateFlow<List<CookLogEntry>?> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(log: CookLog) {
        viewModelScope.launch { repository.delete(log.id) }
    }

    /** Puts a deleted entry back with its original id, so it keeps its place in the history. */
    fun restore(log: CookLog) {
        viewModelScope.launch { repository.restore(log) }
    }
}

/** The Kochen tab: the cooking history, newest first, under date headers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingScreen(
    viewModel: CookingViewModel,
    onRecipeClick: (Long) -> Unit,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.cook_history_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val today = LocalDate.now()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.cooking_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = entries ?: return@Scaffold
        if (list.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.cook_history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // The history is sorted by date, so grouping keeps the order.
            val byDate = list.groupBy { it.log.cookedOn }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                byDate.forEach { (date, dayEntries) ->
                    item(key = "date-$date") {
                        Text(
                            cookDateLabel(date, today),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(dayEntries, key = { it.log.id }) { entry ->
                        HistoryRow(
                            entry,
                            onClick = { onRecipeClick(entry.log.recipeId) },
                            onDelete = {
                                viewModel.delete(entry.log)
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    val result = snackbar.showSnackbar(
                                        deletedMessage,
                                        actionLabel = undoLabel,
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(entry.log)
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** Swiping the row away in either direction deletes the entry. */
@Composable
private fun HistoryRow(entry: CookLogEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    // Not saveable on purpose: the list keeps saved state per key, so an entry brought back by
    // undo would return dismissed and be deleted again.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                // The icon sits on the side the row uncovers.
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        onDismiss = { onDelete() },
    ) {
        ListItem(
            headlineContent = { Text(entry.recipeTitle, fontWeight = FontWeight.Bold) },
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}
