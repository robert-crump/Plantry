package com.example.plantry.ui.cooklog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
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
        recipeRepository.observeAllLines(),
        ingredientRepository.observeIngredients(),
        ingredientRepository.observeContents(),
    ) { entries, lines, ingredients, contents ->
        weeklyPlantPoints(entries, lines, ingredients.associateBy { it.id }, contents)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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

    fun delete(log: CookLog) {
        viewModelScope.launch { repository.delete(log.id) }
    }

    /** Undoes [delete]: the entry comes back with its id and the recipe as it was when logged. */
    fun restore(log: CookLog) {
        viewModelScope.launch { repository.restore(log) }
    }

    /** Logs a planned recipe on its planned day (today if still ahead) and takes it off Geplant; [onDone] gets what to undo. */
    fun done(item: PlannedItem, onDone: (Cooked) -> Unit) {
        viewModelScope.launch { plannedRepository.done(item.planned)?.let(onDone) }
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

    /** Takes the recipe off Geplant; [onDone] gets the entry, for [restorePlan]. */
    fun unplan(recipeId: Long, onDone: (PlannedRecipe) -> Unit) {
        viewModelScope.launch { plannedRepository.remove(recipeId)?.let(onDone) }
    }

    /** Undoes [unplan]: back on Geplant for the same day, which the evening reminder sees again. */
    fun restorePlan(planned: PlannedRecipe) {
        viewModelScope.launch { plannedRepository.restore(planned) }
    }
}

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
 * [onJustLoggedShown] clears it.
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

    var fabVisible by rememberSaveable { mutableStateOf(true) }
    var fabMenuOpen by rememberSaveable { mutableStateOf(false) }
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
            topBar = { TopAppBar(title = { Text(stringResource(R.string.cooking_title)) }) },
            floatingActionButton = {
                FabMenu(
                    expanded = fabMenuOpen,
                    onExpandedChange = { fabMenuOpen = it },
                    visible = fabVisible,
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
                                val doneMessage = stringResource(R.string.planned_logged, item.snapshot.title)
                                val removedMessage = stringResource(R.string.planned_removed, item.snapshot.title)
                                PlannedCard(
                                    item,
                                    onClick = { onRecipeClick(item.recipeId) },
                                    onSwiped = viewModel::swipeHintSeen,
                                    onDone = {
                                        viewModel.done(item) { cooked ->
                                            showUndo(doneMessage) { viewModel.undoCook(cooked) }
                                        }
                                    },
                                    onRemove = {
                                        viewModel.unplan(item.recipeId) { removed ->
                                            showUndo(removedMessage) { viewModel.restorePlan(removed) }
                                        }
                                    },
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
                                val deletedMessage = stringResource(R.string.cook_history_deleted, entry.title)
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

/** "Nach links wischen zum Entfernen, …" under the Geplant title, until the first swipe. */
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

/**
 * A gray card with a planned recipe: its planned day as a date badge, the name (wrapping) and its
 * current stats. Swiped right it is logged as cooked ([onDone]), swiped left taken off Geplant
 * ([onRemove]); [onSwiped] runs on either swipe.
 */
@Composable
private fun PlannedCard(
    item: PlannedItem,
    onClick: () -> Unit,
    onSwiped: () -> Unit,
    onDone: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    SwipeActionBox(
        onSwipe = {
            onSwiped()
            if (it == SwipeAction.DONE) onDone() else onRemove()
        },
        doneEnabled = true,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        shape = shape,
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
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
        }
    }
}

/** Swiping a row right to left deletes it, left to right (Geplant only) logs it as cooked. */
private enum class SwipeAction { DELETE, DONE }

/** How far a row has to be dragged (of its width) to act. */
private const val SwipeFraction = 0.4f

/**
 * Like Gmail: [content] dragged right to left past [SwipeFraction] of its width, or flicked, slides
 * off and calls [onSwipe] with [SwipeAction.DELETE], left to right (if [doneEnabled]) with
 * [SwipeAction.DONE]; the caller acts at once and offers an undo. The background is red (delete) or
 * green (done) from the first pixel, its icon centred in the uncovered strip; crossing the
 * threshold, either way, ticks and pops the icon.
 */
@Composable
private fun SwipeActionBox(
    onSwipe: (SwipeAction) -> Unit,
    doneEnabled: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    // Not saveable on purpose: a row brought back by undo has the same key and must start settled.
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, density, positionalThreshold = { it * SwipeFraction }) }
    val armed = state.targetValue != SwipeToDismissBoxValue.Settled
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue != SwipeToDismissBoxValue.Settled }
            .drop(1)
            .collect { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    }
    val done = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = doneEnabled,
        onDismiss = { onSwipe(if (it == SwipeToDismissBoxValue.StartToEnd) SwipeAction.DONE else SwipeAction.DELETE) },
        backgroundContent = {
            val scale by animateFloatAsState(if (armed) 1.2f else 1f, label = "swipe icon")
            Box(
                Modifier.fillMaxSize().clip(shape).background(if (done) highlightGreen else MaterialTheme.colorScheme.error),
            ) {
                Icon(
                    if (done) Icons.Filled.Check else Icons.Filled.Delete,
                    contentDescription = null,
                    // The surface colour contrasts with both the light and the dark green.
                    tint = if (done) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .align(if (done) Alignment.CenterStart else Alignment.CenterEnd)
                        // Read while placing, so dragging doesn't recompose.
                        .offset {
                            val strip = abs(runCatching { state.requireOffset() }.getOrDefault(0f))
                            val inset = ((strip - SwipeIconSize.toPx()) / 2).roundToInt()
                            IntOffset(if (done) inset else -inset, 0)
                        }
                        .size(SwipeIconSize)
                        .scale(scale),
                )
            }
        },
    ) { content() }
}

private val SwipeIconSize = 24.dp

/** Dragging the row right to left past [SwipeFraction] of its width deletes the entry ([onDelete]). */
@Composable
private fun HistoryRow(entry: CookLog, onClick: (() -> Unit)?, onDelete: () -> Unit) {
    SwipeActionBox(onSwipe = { onDelete() }, doneEnabled = false) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(MaterialTheme.colorScheme.surface)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
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
