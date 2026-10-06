package com.example.plantry.ui.cooklog

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.plantry.data.PlannedRepository
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeQuery
import com.example.plantry.data.RecipeRepository
import com.example.plantry.ui.FastScrollbar
import com.example.plantry.ui.currentLocale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

class CookingViewModel(
    private val repository: CookLogRepository,
    private val plannedRepository: PlannedRepository,
    recipeRepository: RecipeRepository,
    ingredientRepository: IngredientRepository,
) : ViewModel() {
    /** All recipes, for "Rezept loggen". */
    val recipes: StateFlow<List<Recipe>> = recipeRepository.observeRecipes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    private var seenPlans = plannedRepository.plans.value

    /** Whether something was planned (on another screen) since the last call. */
    fun takeNewPlans(): Boolean {
        val plans = plannedRepository.plans.value
        return (plans != seenPlans).also { seenPlans = plans }
    }

    fun delete(log: CookLog) {
        viewModelScope.launch { repository.delete(log.id) }
    }

    /** Puts a deleted entry back with its original id, so it keeps its place in the history. */
    fun restore(log: CookLog) {
        viewModelScope.launch { repository.restore(log) }
    }

    /** Logs a planned recipe on its planned day (today if still ahead) and takes it off Geplant; [onDone] gets what to undo. */
    fun done(item: PlannedItem, onDone: (Cooked) -> Unit) {
        viewModelScope.launch { plannedRepository.done(item.planned)?.let(onDone) }
    }

    /** "Rezept loggen": logs the recipe on [date] and takes it off Geplant; [onDone] gets what to undo. */
    fun log(recipeId: Long, date: LocalDate, onDone: (Cooked) -> Unit) {
        viewModelScope.launch { plannedRepository.cook(recipeId, date)?.let(onDone) }
    }

    fun undoCook(cooked: Cooked) {
        viewModelScope.launch { plannedRepository.undoCook(cooked) }
    }

    fun unplan(recipeId: Long) {
        viewModelScope.launch { plannedRepository.remove(recipeId) }
    }
}

/** A recipe just put on Geplant from the suggestions, to confirm with an undo snackbar. */
data class JustPlanned(val recipeId: Long, val title: String)

/**
 * The Kochen tab: Geplant on top as cards (hidden when empty), then the cooking history under
 * "Verlauf", newest first, in Monday–Sunday weeks with sticky headers, each row with its own date
 * badge. History rows show the recipe as it was when logged. Two FABs, shrinking to their icons
 * while scrolling down: "Rezept vorschlagen" and "Rezept loggen" (pick a recipe, then a day up to
 * today). [justPlanned] is shown once as a snackbar, then [onJustPlannedShown] clears it.
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
        showUndo(plannedMessage) { viewModel.unplan(planned.recipeId) }
    }

    // The snackbar sits at the bottom with the FAB lifted above it (the Scaffold would put it
    // above the FAB), so it is laid over the Scaffold instead of using its snackbarHost slot.
    var snackbarHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // Less the snackbar's own 12dp margin, so the FAB keeps its usual 16dp gap.
    val fabLift by animateDpAsState(
        with(density) { (snackbarHeight.toDp() - 12.dp).coerceAtLeast(0.dp) },
        label = "fab lift",
    )
    val listState = rememberLazyListState()
    var fabsExpanded by rememberSaveable { mutableStateOf(true) }
    // Only actual scrolling counts, so dragging a list too short to scroll leaves the FABs alone.
    val collapseFabs = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y < 0) fabsExpanded = false else if (consumed.y > 0) fabsExpanded = true
                return Offset.Zero
            }
        }
    }
    var picking by rememberSaveable { mutableStateOf(false) }
    var loggingRecipeId by rememberSaveable { mutableStateOf<Long?>(null) }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.cooking_title)) }) },
            floatingActionButton = {
                Column(
                    Modifier.padding(bottom = fabLift),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.suggest_action)) },
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = stringResource(R.string.suggest_action)) },
                        onClick = onSuggest,
                        expanded = fabsExpanded,
                    )
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.cook_log_action)) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cook_log_action)) },
                        onClick = { picking = true },
                        expanded = fabsExpanded,
                    )
                }
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
                val layoutDirection = LocalLayoutDirection.current
                // Room below the last row for both FABs. The top inset is a plain padding, not content
                // padding, so the sticky header pins below the app bar instead of behind it.
                val listPadding = PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                    bottom = padding.calculateBottomPadding() + 160.dp,
                )
                // Runs each time Kochen is shown: a new plan brings Geplant into view, otherwise the
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
                // One label per list item, in list order: "Geplant" over the planned section, the
                // month and year of each history entry (the "Verlauf" title and each week header
                // take their first entry's).
                val scrollLabels = remember(plannedItems, weeks, plannedLabel, locale) {
                    buildList {
                        if (plannedItems.isNotEmpty()) repeat(plannedItems.size + 1) { add(plannedLabel) }
                        list.firstOrNull()?.let { add(cookMonthYearLabel(it.cookedOn, locale)) }
                        weeks.values.forEach { week ->
                            add(cookMonthYearLabel(week.first().cookedOn, locale))
                            week.forEach { add(cookMonthYearLabel(it.cookedOn, locale)) }
                        }
                    }
                }
                Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).nestedScroll(collapseFabs)) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = listPadding,
                    ) {
                        if (plannedItems.isNotEmpty()) {
                            item(key = "planned-title") { SectionTitle(stringResource(R.string.planned_title)) }
                            items(plannedItems, key = { "planned-${it.recipeId}" }) { item ->
                                PlannedCard(
                                    item,
                                    onClick = { onRecipeClick(item.recipeId) },
                                    onDone = {
                                        viewModel.done(item) { cooked ->
                                            showUndo(loggedMessage) { viewModel.undoCook(cooked) }
                                        }
                                    },
                                    onRemove = { viewModel.unplan(item.recipeId) },
                                )
                            }
                        }
                        if (list.isNotEmpty()) {
                            item(key = "history-title") { SectionTitle(stringResource(R.string.planned_history_title)) }
                        }
                        weeks.forEach { (monday, week) ->
                            stickyHeader(key = "week-$monday") {
                                WeekHeader(cookWeekLabel(monday, today, locale, thisWeek, lastWeek))
                            }
                            items(week, key = { it.id }) { entry ->
                                HistoryRow(
                                    entry,
                                    // A deleted recipe has nothing to open.
                                    onClick = entry.recipeId?.let { id -> { onRecipeClick(id) } },
                                    onDelete = {
                                        viewModel.delete(entry)
                                        showUndo(deletedMessage) { viewModel.restore(entry) }
                                    },
                                )
                            }
                        }
                    }
                    FastScrollbar(
                        listState,
                        label = { scrollLabels.getOrNull(it) },
                        // Ends above the FABs.
                        modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(bottom = listPadding.calculateBottomPadding()),
                    )
                }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .onSizeChanged { snackbarHeight = it.height },
        )
    }
    if (picking) {
        val recipes by viewModel.recipes.collectAsStateWithLifecycle()
        LogRecipeDialog(
            recipes,
            onPick = { recipe ->
                picking = false
                loggingRecipeId = recipe.id
            },
            onDismiss = { picking = false },
        )
    }
    loggingRecipeId?.let { recipeId ->
        val today = LocalDate.now()
        CookDatePickerDialog(
            date = today,
            today = today,
            onPick = { date ->
                viewModel.log(recipeId, date) { cooked ->
                    showUndo(loggedMessage) { viewModel.undoCook(cooked) }
                }
            },
            onDismiss = { loggingRecipeId = null },
        )
    }
}

/** "Rezept loggen": a search field over all recipes, A–Z, narrowed as you type. */
@Composable
private fun LogRecipeDialog(recipes: List<Recipe>, onPick: (Recipe) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(recipes, query) { RecipeQuery.byTitle(recipes, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cook_log_action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.cook_log_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                )
                if (shown.isEmpty()) {
                    Text(
                        stringResource(R.string.cook_log_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(shown, key = { it.id }) { recipe ->
                            ListItem(
                                headlineContent = { Text(recipe.title) },
                                modifier = Modifier.clickable { onPick(recipe) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** A week's header in the history: a thin line, then e.g. "Diese Woche"; opaque, as it sticks at the top. */
@Composable
private fun WeekHeader(text: String) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider()
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
    }
}

/**
 * A gray card with a planned recipe: its planned day as a date badge, the name (wrapping), its
 * current stats, "Erledigt" and "Entfernen" (after asking).
 */
@Composable
private fun PlannedCard(
    item: PlannedItem,
    onClick: () -> Unit,
    onDone: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateBadge(item.planned.plannedOn)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.snapshot.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                RecipeStatsRow(item.snapshot.stats)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { confirmRemove = true }) { Text(stringResource(R.string.planned_remove)) }
            TextButton(onClick = onDone) { Text(stringResource(R.string.planned_done)) }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            text = { Text(stringResource(R.string.planned_remove_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onRemove()
                    },
                ) { Text(stringResource(R.string.planned_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** How far a row has to be dragged (of its width) to delete it. */
private const val DeleteFraction = 0.5f

/**
 * Dragging the row right to left past [DeleteFraction] of its width deletes the entry; a quick
 * flick alone doesn't. The background turns red and its icon grows once letting go will delete.
 */
@Suppress("DEPRECATION") // confirmValueChange is the only way to veto a dismissing flick.
@Composable
private fun HistoryRow(entry: CookLog, onClick: (() -> Unit)?, onDelete: () -> Unit) {
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // Not saveable on purpose: the list keeps saved state per key, so an entry brought back by
    // undo would return dismissed and be deleted again.
    val state = remember {
        lateinit var state: SwipeToDismissBoxState
        SwipeToDismissBoxState(
            SwipeToDismissBoxValue.Settled,
            density,
            // A flick targets the next anchor from any distance; only allow it past the threshold.
            confirmValueChange = {
                it == SwipeToDismissBoxValue.Settled || abs(state.requireOffset()) >= width * DeleteFraction
            },
            positionalThreshold = { it * DeleteFraction },
        ).also { state = it }
    }
    val armed = state.targetValue == SwipeToDismissBoxValue.EndToStart
    SwipeToDismissBox(
        state = state,
        modifier = Modifier.onSizeChanged { width = it.width },
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                if (armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "delete background",
            )
            val scale by animateFloatAsState(if (armed) 1.4f else 1f, label = "delete icon")
            Box(
                Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = if (armed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.scale(scale),
                )
            }
        },
        onDismiss = { onDelete() },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(MaterialTheme.colorScheme.surface)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateBadge(entry.cookedOn)
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
}

/** Day number over the abbreviated month in the device locale, e.g. "23" over "Okt". */
@Composable
private fun DateBadge(date: LocalDate) {
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
