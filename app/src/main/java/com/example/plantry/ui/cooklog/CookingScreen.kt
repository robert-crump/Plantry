package com.example.plantry.ui.cooklog

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.CookLog
import com.example.plantry.data.CookLogRepository
import com.example.plantry.data.Cooked
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.PlannedItem
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.PlannedRepository
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.settings.SettingsRepository
import com.example.plantry.ui.FabMenu
import com.example.plantry.ui.FabMenuAction
import com.example.plantry.ui.FabScrim
import com.example.plantry.ui.FastScrollbar
import com.example.plantry.ui.SnackbarBelowFab
import com.example.plantry.ui.currentLocale
import com.example.plantry.ui.theme.highlightGreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

class CookingViewModel(
    private val repository: CookLogRepository,
    private val plannedRepository: PlannedRepository,
    recipeRepository: RecipeRepository,
    ingredientRepository: IngredientRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    /** Null until the first emission, so the empty state doesn't flash. */
    val entries: StateFlow<List<CookLog>?> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Each history week's plant points, keyed by its Monday, see [weeklyPlantPoints]. */
    val weeklyPlantPoints: StateFlow<Map<LocalDate, Double>> = combine(
        repository.observeHistory(),
        repository.observePlants(),
        ::weeklyPlantPoints,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Geplant, with each recipe's current stats; null until loaded. */
    val planned: StateFlow<List<PlannedItem>?> = combine(
        plannedRepository.observe(),
        recipeRepository.observeRecipes(),
        recipeRepository.observeAllLines(),
        combine(ingredientRepository.observeIngredients(), ingredientRepository.observeContents(), ::Pair),
    ) { planned, recipes, lines, (ingredients, contents) ->
        PlannedItem.of(planned, recipes, lines, ingredients.associateBy { it.id }, contents)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether to explain swiping Geplant cards, until the first swipe. */
    val showSwipeHint: StateFlow<Boolean> = settingsRepository.settings.map { !it.swipeHintSeen }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun swipeHintSeen() = settingsRepository.setSwipeHintSeen()

    private var seenPlans = plannedRepository.plans.value

    /** Whether something was planned (on another screen) since the last call. */
    fun takeNewPlans(): Boolean {
        val plans = plannedRepository.plans.value
        return (plans != seenPlans).also { seenPlans = plans }
    }

    /** Takes the [planned] recipes off Geplant and deletes the history [entries]; confirmed, so no undo. */
    fun delete(planned: List<PlannedItem>, entries: List<CookLog>) {
        viewModelScope.launch {
            planned.forEach { plannedRepository.remove(it.recipeId) }
            entries.forEach { repository.delete(it.id) }
        }
    }

    /** Logs each planned recipe on its planned day (today if still ahead) and takes it off Geplant; confirmed, so no undo. */
    fun done(items: List<PlannedItem>) {
        viewModelScope.launch { items.forEach { plannedRepository.done(it.planned) } }
    }

    /** "Kocheintrag": logs the recipe on [date] and takes it off Geplant; [onDone] gets what to undo. */
    fun log(recipeId: Long, date: LocalDate, onDone: (Cooked) -> Unit) {
        viewModelScope.launch { plannedRepository.cook(recipeId, date)?.let(onDone) }
    }

    fun undoCook(cooked: Cooked) {
        viewModelScope.launch { plannedRepository.undoCook(cooked) }
    }

    /**
     * "Vorschlag" and "Planen": puts the recipe on Geplant for [date], or moves it there; [onDone]
     * gets the entry it replaced, for [undoPlan]. Counts as seen, since Speiseplan is already showing.
     */
    fun plan(recipeId: Long, date: LocalDate, onDone: (PlannedRecipe?) -> Unit) {
        viewModelScope.launch {
            val previous = plannedRepository.plan(recipeId, date)
            seenPlans = plannedRepository.plans.value
            onDone(previous)
        }
    }

    fun undoPlan(recipeId: Long, previous: PlannedRecipe?) {
        viewModelScope.launch { plannedRepository.undoPlan(recipeId, previous) }
    }
}

/** What the selection bar asks to confirm. */
private enum class SelectionAction { DONE, DELETE }

/** A recipe and day picked on "Vorschlag" or "Planen", to plan with an undo snackbar. */
data class JustPlanned(val recipeId: Long, val title: String, val date: LocalDate)

/** A recipe and day picked on the "Kocheintrag" screen, to log with an undo snackbar. */
data class JustLogged(val recipeId: Long, val date: LocalDate)

/**
 * The Speiseplan tab: Geplant on top as cards (hidden when empty), then the cooking history under
 * "Verlauf", newest first, in Monday–Sunday weeks with sticky headers showing the week's plant
 * points, each row with its own date badge. History rows show the recipe as it was when logged. A "+" FAB, hidden while scrolling
 * down, opens "Planen" ([onPlan], pick a recipe, then a day from today), "Vorschlag" and
 * "Kocheintrag" ([onLog], pick a recipe, then a day up to today). [justPlanned] is planned once with
 * an undo snackbar, then [onJustPlannedShown] clears it; [justLogged] is logged once, then
 * [onJustLoggedShown] clears it. A long press selects a card or row, like Gmail: taps then toggle,
 * and a bar on top offers "Gekocht" (Geplant only) and "Löschen", each confirmed in a dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingScreen(
    viewModel: CookingViewModel,
    onRecipeClick: (Long) -> Unit,
    onSuggest: () -> Unit,
    onPlan: () -> Unit,
    onLog: () -> Unit,
    justPlanned: JustPlanned?,
    onJustPlannedShown: () -> Unit,
    justLogged: JustLogged?,
    onJustLoggedShown: () -> Unit,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val planned by viewModel.planned.collectAsStateWithLifecycle()
    val weeklyPlantPoints by viewModel.weeklyPlantPoints.collectAsStateWithLifecycle()
    val showSwipeHint by viewModel.showSwipeHint.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val loggedMessage = stringResource(R.string.cooked_logged)
    val undoLabel = stringResource(R.string.action_undo)
    val plannedMessage = stringResource(R.string.suggest_planned, justPlanned?.title.orEmpty())

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
        viewModel.plan(planned.recipeId, planned.date) { previous ->
            scope.launch { listState.scrollToItem(0) }
            showUndo(plannedMessage) { viewModel.undoPlan(planned.recipeId, previous) }
        }
    }

    LaunchedEffect(justLogged) {
        val logged = justLogged ?: return@LaunchedEffect
        onJustLoggedShown()
        viewModel.log(logged.recipeId, logged.date) { cooked ->
            showUndo(loggedMessage) { viewModel.undoCook(cooked) }
        }
    }

    // Ids of the selected Geplant recipes and history entries; whatever has disappeared since is ignored.
    var selectedPlanned by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var selectedLogs by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val selectedItems = planned.orEmpty().filter { it.recipeId in selectedPlanned }
    val selectedEntries = entries.orEmpty().filter { it.id in selectedLogs }
    val selectionCount = selectedItems.size + selectedEntries.size
    val selecting = selectionCount > 0
    var confirm by rememberSaveable { mutableStateOf<SelectionAction?>(null) }
    fun clearSelection() {
        selectedPlanned = emptySet()
        selectedLogs = emptySet()
    }
    BackHandler(enabled = selecting, onBack = ::clearSelection)

    var fabVisible by rememberSaveable { mutableStateOf(true) }
    var fabMenuOpen by rememberSaveable { mutableStateOf(false) }

    fun togglePlanned(recipeId: Long) {
        fabMenuOpen = false
        selectedPlanned = if (recipeId in selectedPlanned) selectedPlanned - recipeId else selectedPlanned + recipeId
    }

    fun toggleLog(id: Long) {
        fabMenuOpen = false
        selectedLogs = if (id in selectedLogs) selectedLogs - id else selectedLogs + id
    }

    if (selecting) {
        when (confirm) {
            SelectionAction.DONE -> ConfirmDialog(
                title = selectedItems.singleOrNull()?.let { stringResource(R.string.planned_done_title, it.snapshot.title) }
                    ?: pluralStringResource(R.plurals.planned_done_several_title, selectionCount, selectionCount),
                confirmLabel = stringResource(R.string.planned_done_action),
                onConfirm = {
                    viewModel.done(selectedItems)
                    confirm = null
                    clearSelection()
                },
                onDismiss = { confirm = null },
            )
            SelectionAction.DELETE -> ConfirmDialog(
                title = (selectedItems.map { it.snapshot.title } + selectedEntries.map { it.title }).singleOrNull()
                    ?.let { stringResource(R.string.cook_delete_one_title, it) }
                    ?: pluralStringResource(R.plurals.cook_delete_title, selectionCount, selectionCount),
                text = stringResource(R.string.cook_delete_message),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = {
                    viewModel.delete(selectedItems, selectedEntries)
                    confirm = null
                    clearSelection()
                },
                onDismiss = { confirm = null },
            )
            null -> {}
        }
    }
    // Only actual scrolling counts, so dragging a list too short to scroll leaves the FAB alone.
    // Any scrolling closes the menu.
    val hideFab = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y != 0f) fabMenuOpen = false
                if (consumed.y < 0) fabVisible = false else if (consumed.y > 0) fabVisible = true
                return Offset.Zero
            }
        }
    }
    SnackbarBelowFab(snackbar) { fabLift ->
        Scaffold(
            topBar = {
                if (selecting) {
                    SelectionTopBar(
                        selectionCount,
                        onClose = ::clearSelection,
                        onDone = if (selectedEntries.isEmpty()) ({ confirm = SelectionAction.DONE }) else null,
                        onDelete = { confirm = SelectionAction.DELETE },
                    )
                } else {
                    TopAppBar(title = { Text(stringResource(R.string.cooking_title)) })
                }
            },
            floatingActionButton = {
                FabMenu(
                    expanded = fabMenuOpen,
                    onExpandedChange = { fabMenuOpen = it },
                    visible = fabVisible && !selecting,
                    actions = listOf(
                        FabMenuAction(R.string.plan_action, Icons.Filled.CalendarMonth, onPlan),
                        FabMenuAction(R.string.suggest_action, Icons.Filled.AutoAwesome, onSuggest),
                        FabMenuAction(R.string.cook_log_action, Icons.Filled.EditNote, onLog),
                    ),
                    modifier = Modifier.padding(bottom = fabLift),
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
                    FabScrim(fabMenuOpen, onDismiss = { fabMenuOpen = false })
                }
            } else {
                val layoutDirection = LocalLayoutDirection.current
                // Room below the last row for the FAB. The top inset is a plain padding, not content
                // padding, so the sticky header pins below the app bar instead of behind it.
                val listPadding = PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                    bottom = padding.calculateBottomPadding() + 88.dp,
                )
                // Runs each time Speiseplan is shown: a new plan brings Geplant into view, otherwise the
                // scroll position is kept.
                LaunchedEffect(Unit) {
                    if (viewModel.takeNewPlans()) listState.scrollToItem(0)
                }
                val plannedLabel = stringResource(R.string.planned_title)
                val thisWeek = stringResource(R.string.cook_week_this)
                val lastWeek = stringResource(R.string.cook_week_last)
                val locale = currentLocale()
                // Refreshed with the list, so the week headers catch up when a new day starts.
                val today = remember(list) { LocalDate.now() }
                val weeks = remember(list) { groupByWeek(list) { it.cookedOn } }
                val swipeHint = showSwipeHint && plannedItems.isNotEmpty()
                // One label per list item, in list order: "Geplant" over the planned section (title,
                // hint and cards), the month and year of each history entry (the "Verlauf" title and
                // each week header take their first entry's).
                val scrollLabels = remember(plannedItems, swipeHint, weeks, plannedLabel, locale) {
                    buildList {
                        if (plannedItems.isNotEmpty()) {
                            repeat(plannedItems.size + if (swipeHint) 2 else 1) { add(plannedLabel) }
                        }
                        list.firstOrNull()?.let { add(cookMonthYearLabel(it.cookedOn, locale)) }
                        weeks.values.forEach { week ->
                            add(cookMonthYearLabel(week.first().cookedOn, locale))
                            week.forEach { add(cookMonthYearLabel(it.cookedOn, locale)) }
                        }
                    }
                }
                Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).nestedScroll(hideFab)) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = listPadding,
                    ) {
                        if (plannedItems.isNotEmpty()) {
                            item(key = "planned-title") { SectionTitle(stringResource(R.string.planned_title)) }
                            if (swipeHint) item(key = "planned-hint") { SwipeHint() }
                            items(plannedItems, key = { "planned-${it.recipeId}" }) { item ->
                                PlannedCard(
                                    item,
                                    selected = item.recipeId in selectedPlanned,
                                    swipeEnabled = !selecting,
                                    onClick = { if (selecting) togglePlanned(item.recipeId) else onRecipeClick(item.recipeId) },
                                    onLongClick = { togglePlanned(item.recipeId) },
                                    onSwiped = viewModel::swipeHintSeen,
                                    onDone = { viewModel.done(listOf(item)) },
                                )
                            }
                        }
                        if (list.isNotEmpty()) {
                            item(key = "history-title") { SectionTitle(stringResource(R.string.planned_history_title)) }
                        }
                        weeks.forEach { (monday, week) ->
                            stickyHeader(key = "week-$monday") {
                                WeekHeader(cookWeekLabel(monday, today, locale, thisWeek, lastWeek), weeklyPlantPoints[monday])
                            }
                            items(week, key = { it.id }) { entry ->
                                HistoryRow(
                                    entry,
                                    selected = entry.id in selectedLogs,
                                    // A deleted recipe has nothing to open.
                                    onClick = if (selecting) ({ toggleLog(entry.id) }) else entry.recipeId?.let { id -> { onRecipeClick(id) } },
                                    onLongClick = { toggleLog(entry.id) },
                                )
                            }
                        }
                    }
                    FastScrollbar(
                        listState,
                        label = { scrollLabels.getOrNull(it) },
                        // Ends above the FAB.
                        modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(bottom = listPadding.calculateBottomPadding()),
                    )
                    FabScrim(fabMenuOpen, onDismiss = { fabMenuOpen = false })
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/**
 * A week's header in the history: a thin line, then e.g. "Diese Woche" and, at the end, a leaf with
 * the week's [plantPoints] against the goal, e.g. "23/30", green and bold once reached (none while
 * still loading). Opaque, as it sticks at the top.
 */
@Composable
private fun WeekHeader(text: String, plantPoints: Double?) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider()
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Text(text, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.weight(1f))
            if (plantPoints != null) WeekPlantPointsScore(plantPoints)
        }
    }
}

/** "Wischen zum Abhaken · Gedrückt halten zum Auswählen" under the Geplant title, until the first swipe. */
@Composable
private fun SwipeHint() {
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Icon(Icons.Filled.Swipe, contentDescription = null, Modifier.size(16.dp), tint = color)
        Text(stringResource(R.string.planned_swipe_hint), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** The bar replacing the app bar while entries are selected; [onDone] is null unless only Geplant entries are. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onClose: () -> Unit, onDone: (() -> Unit)?, onDelete: () -> Unit) {
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.cook_selected, count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.cook_selection_clear)) }
        },
        actions = {
            if (onDone != null) {
                IconButton(onClick = onDone) { Icon(Icons.Filled.Check, stringResource(R.string.planned_done_action)) }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * A gray card with a planned recipe: its planned day as a date badge (a check while [selected]),
 * the name (wrapping) and its current stats. Swiped either way, unless [swipeEnabled] is off, it
 * asks whether the recipe was cooked and logs it ([onDone]) or springs back; [onSwiped] runs on
 * each swipe.
 */
@Composable
internal fun PlannedCard(
    item: PlannedItem,
    selected: Boolean,
    swipeEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwiped: () -> Unit,
    onDone: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    // Held at the edge from the swipe until the dialog is cancelled, or after "Gekocht" until the card is gone.
    var held by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    if (asking) {
        ConfirmDialog(
            title = stringResource(R.string.planned_done_title, item.snapshot.title),
            confirmLabel = stringResource(R.string.planned_done_action),
            onConfirm = {
                asking = false
                onDone()
            },
            onDismiss = {
                asking = false
                held = false
            },
        )
    }
    DoneSwipeBox(
        held = held,
        enabled = swipeEnabled,
        onSwipe = {
            onSwiped()
            held = true
            asking = true
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        shape = shape,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .semantics { this.selected = selected }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                DateBadge(item.planned.plannedOn, selected)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        item.snapshot.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    RecipeStatsRow(item.snapshot.stats)
                }
            }
        }
    }
}

/** How far the finger has to drag (of the card's width) to act. */
private const val SwipeFraction = 0.6f

/** How far the card moves per finger movement, so it feels heavy. */
private const val SwipeResistance = 0.6f

/**
 * [content] dragged either way follows the finger at [SwipeResistance] over a green background with
 * a check centred in the uncovered strip. Let go after the finger travelled [SwipeFraction] of its
 * width, it slides to the edge and calls [onSwipe], then stays there while [held]; let go earlier,
 * it springs back. A flick counts for nothing. Crossing the threshold, either way, ticks and pops
 * the icon.
 */
@Composable
private fun DoneSwipeBox(
    held: Boolean,
    enabled: Boolean,
    onSwipe: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Not saveable on purpose: a card coming back has the same key and must start settled. Set right
    // away while dragging, so a quick swipe is judged by where it really ended.
    var offset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    fun settle(to: Float, then: () -> Unit = {}) {
        settling?.cancel()
        settling = scope.launch {
            animate(offset, to) { value, _ -> offset = value }
            then()
        }
    }
    var width by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val armed by remember { derivedStateOf { width > 0 && abs(offset) >= width * SwipeFraction * SwipeResistance } }
    val direction by remember { derivedStateOf { sign(offset) } }
    LaunchedEffect(Unit) {
        snapshotFlow { armed }
            .drop(1)
            .collect { if (dragging) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    }
    LaunchedEffect(held) {
        if (!held && offset != 0f) settle(0f)
    }
    val dragState = rememberDraggableState { delta ->
        offset = (offset + delta * SwipeResistance).coerceIn(-width.toFloat(), width.toFloat())
    }
    Box(
        modifier
            .onSizeChanged { width = it.width }
            .draggable(
                dragState,
                Orientation.Horizontal,
                enabled = enabled && !held,
                onDragStarted = {
                    settling?.cancel()
                    dragging = true
                },
                onDragStopped = {
                    dragging = false
                    if (armed) settle(sign(offset) * width, onSwipe) else settle(0f)
                },
            ),
    ) {
        if (direction != 0f) {
            val scale by animateFloatAsState(if (armed) 1.2f else 1f, label = "swipe icon")
            val right = direction > 0
            Box(Modifier.matchParentSize().clip(shape).background(highlightGreen)) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    // The surface colour contrasts with both the light and the dark green.
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .align(if (right) Alignment.CenterStart else Alignment.CenterEnd)
                        // Read while placing, so dragging doesn't recompose.
                        .offset {
                            val inset = ((abs(offset) - SwipeIconSize.toPx()) / 2).roundToInt()
                            IntOffset(if (right) inset else -inset, 0)
                        }
                        .size(SwipeIconSize)
                        .scale(scale),
                )
            }
        }
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }) { content() }
    }
}

private val SwipeIconSize = 24.dp

/** A history entry, its date badge a check while [selected]; [onClick] is null when there is nothing to open. */
@Composable
private fun HistoryRow(entry: CookLog, selected: Boolean, onClick: (() -> Unit)?, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .semantics { this.selected = selected }
            .combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DateBadge(entry.cookedOn, selected)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RecipeStatsRow(entry.stats)
        }
    }
}

/** Day number over the abbreviated month in the device locale, e.g. "23" over "Okt"; a check in a circle while [selected]. */
@Composable
private fun DateBadge(date: LocalDate, selected: Boolean) {
    if (selected) {
        Box(
            Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        return
    }
    Column(
        Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val color = MaterialTheme.colorScheme.onSecondaryContainer
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium.copy(lineHeight = 18.sp),
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            cookMonthLabel(date, currentLocale()),
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp),
            color = color,
        )
    }
}
