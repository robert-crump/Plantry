package com.example.plantry.ui.cooklog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.CookLog
import com.example.plantry.data.CookLogRepository
import com.example.plantry.data.Cooked
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.PlannedItem
import com.example.plantry.data.PlannedRepository
import com.example.plantry.data.RecipeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class CookingViewModel(
    private val repository: CookLogRepository,
    private val plannedRepository: PlannedRepository,
    recipeRepository: RecipeRepository,
    ingredientRepository: IngredientRepository,
    private val clock: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    /** Null until the first emission, so the empty state doesn't flash. */
    val entries: StateFlow<List<CookLog>?> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Geplant, with each recipe's current stats; null until loaded. */
    val planned: StateFlow<List<PlannedItem>?> = combine(
        plannedRepository.observe(),
        recipeRepository.observeRecipes(),
        recipeRepository.observeAllLines(),
        ingredientRepository.observeIngredients(),
    ) { planned, recipes, lines, ingredients ->
        PlannedItem.of(planned, recipes, lines, ingredients.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun today(): LocalDate = clock()

    fun delete(log: CookLog) {
        viewModelScope.launch { repository.delete(log.id) }
    }

    /** Puts a deleted entry back with its original id, so it keeps its place in the history. */
    fun restore(log: CookLog) {
        viewModelScope.launch { repository.restore(log) }
    }

    /** Logs a planned recipe as cooked on [date] and takes it off Geplant; [onDone] gets what to undo. */
    fun cook(item: PlannedItem, date: LocalDate, onDone: (Cooked) -> Unit) {
        viewModelScope.launch { plannedRepository.cook(item.recipeId, date)?.let(onDone) }
    }

    fun undoCook(cooked: Cooked) {
        viewModelScope.launch { plannedRepository.undoCook(cooked) }
    }

    fun unplan(recipeId: Long) {
        viewModelScope.launch { plannedRepository.remove(recipeId) }
    }

    /** Puts a removed entry back with its original date. */
    fun replan(item: PlannedItem) {
        viewModelScope.launch { plannedRepository.restore(item.planned) }
    }
}

/** A recipe just put on Geplant from the suggestions, to confirm with an undo snackbar. */
data class JustPlanned(val recipeId: Long, val title: String)

/**
 * The Kochen tab: Geplant on top (hidden when empty), then the cooking history, newest first,
 * under date headers. History rows show the recipe as it was when logged. [justPlanned] is shown
 * once as a snackbar, then [onJustPlannedShown] clears it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingScreen(
    viewModel: CookingViewModel,
    onRecipeClick: (Long) -> Unit,
    onSuggest: () -> Unit,
    justPlanned: JustPlanned?,
    onJustPlannedShown: () -> Unit,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val planned by viewModel.planned.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.cook_history_deleted)
    val loggedMessage = stringResource(R.string.cooked_logged)
    val removedMessage = stringResource(R.string.planned_removed)
    val undoLabel = stringResource(R.string.action_undo)
    val plannedMessage = stringResource(R.string.suggest_planned, justPlanned?.title.orEmpty())
    val today = viewModel.today()

    /** Replaces any snackbar still showing; [onUndo] runs if its action is tapped. */
    fun showUndo(message: String, onUndo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    LaunchedEffect(justPlanned) {
        val planned = justPlanned ?: return@LaunchedEffect
        onJustPlannedShown()
        showUndo(plannedMessage) { viewModel.unplan(planned.recipeId) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.cooking_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.suggest_action)) },
                icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                onClick = onSuggest,
            )
        },
    ) { padding ->
        val list = entries ?: return@Scaffold
        val plannedItems = planned ?: return@Scaffold
        if (list.isEmpty() && plannedItems.isEmpty()) {
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
            val byDate = list.groupBy { it.cookedOn }
            val layoutDirection = LocalLayoutDirection.current
            // Room below the last row for the FAB.
            val listPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection),
                bottom = padding.calculateBottomPadding() + 88.dp,
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
                if (plannedItems.isNotEmpty()) {
                    item(key = "planned-title") { SectionTitle(stringResource(R.string.planned_title)) }
                    items(plannedItems, key = { "planned-${it.recipeId}" }) { item ->
                        PlannedRow(
                            item,
                            today = today,
                            onClick = { onRecipeClick(item.recipeId) },
                            onCooked = { date ->
                                viewModel.cook(item, date) { cooked ->
                                    showUndo(loggedMessage) { viewModel.undoCook(cooked) }
                                }
                            },
                            onRemove = {
                                viewModel.unplan(item.recipeId)
                                showUndo(removedMessage) { viewModel.replan(item) }
                            },
                        )
                        HorizontalDivider()
                    }
                    if (list.isNotEmpty()) {
                        item(key = "history-title") { SectionTitle(stringResource(R.string.planned_history_title)) }
                    }
                }
                byDate.forEach { (date, dayEntries) ->
                    item(key = "date-$date") {
                        Text(
                            cookDateLabel(date, today),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(dayEntries, key = { it.id }) { entry ->
                        HistoryRow(
                            entry,
                            // A deleted recipe has nothing to open.
                            onClick = entry.recipeId?.let { id -> { onRecipeClick(id) } },
                            onDelete = {
                                viewModel.delete(entry)
                                showUndo(deletedMessage) { viewModel.restore(entry) }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

/**
 * A planned recipe with its current stats, "Gekocht" with a date chip (today unless another day
 * was picked), and "Entfernen".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlannedRow(
    item: PlannedItem,
    today: LocalDate,
    onClick: () -> Unit,
    onCooked: (LocalDate) -> Unit,
    onRemove: () -> Unit,
) {
    // Saved as epoch day; null means today, so the chip follows the date across midnight.
    var pickedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val cookDate = pickedDay?.let(LocalDate::ofEpochDay) ?: today
    Column(Modifier.clickable(onClick = onClick).padding(bottom = 8.dp)) {
        ListItem(
            headlineContent = { Text(item.snapshot.title, fontWeight = FontWeight.Bold) },
            trailingContent = { RecipeStatsRow(item.snapshot.stats) },
        )
        FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { pickDate = true },
                label = { Text(cookDateLabel(cookDate, today)) },
                leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
            )
            Button(onClick = { onCooked(cookDate) }) {
                Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.cooked_action), Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.planned_remove)) }
        }
    }
    if (pickDate) {
        CookDatePickerDialog(
            date = cookDate,
            today = today,
            onPick = { date -> pickedDay = date.takeIf { it != today }?.toEpochDay() },
            onDismiss = { pickDate = false },
        )
    }
}

/** Swiping the row away in either direction deletes the entry. */
@Composable
private fun HistoryRow(entry: CookLog, onClick: (() -> Unit)?, onDelete: () -> Unit) {
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
            headlineContent = { Text(entry.title, fontWeight = FontWeight.Bold) },
            trailingContent = { RecipeStatsRow(entry.stats) },
            modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        )
    }
}
