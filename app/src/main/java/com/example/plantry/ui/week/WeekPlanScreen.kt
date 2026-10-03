package com.example.plantry.ui.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.SlotRef
import com.example.plantry.data.WeekPlan
import com.example.plantry.data.WeekPlanRepository
import com.example.plantry.data.WeekSummary
import com.example.plantry.data.planner.WeekPlanner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WeekPlanUiState(
    val plan: WeekPlan,
    /** Uncooked recipes of last week that one tap would copy into this (still empty) week. */
    val rolloverCount: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WeekPlanViewModel(
    private val repository: WeekPlanRepository,
    private val planner: WeekPlanner,
    recipeRepository: RecipeRepository,
    ingredientRepository: IngredientRepository,
    private val clock: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val weekStart = MutableStateFlow(WeekPlan.startOf(clock()))

    /** Null until loaded. */
    val state: StateFlow<WeekPlanUiState?> = weekStart.flatMapLatest { start ->
        combine(repository.observeWeek(start), repository.observeRolloverCount(start), ::WeekPlanUiState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recipes: StateFlow<List<Recipe>> = recipeRepository.observeRecipes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Totals of the current menu, cooked or not; null until loaded. */
    val summary: StateFlow<WeekSummary?> = combine(
        state,
        recipes,
        recipeRepository.observeAllLines(),
        ingredientRepository.observeIngredients(),
    ) { current, all, lines, ingredients ->
        current ?: return@combine null
        val recipesById = all.associateBy { it.id }
        val menu = current.plan.slots.mapNotNull { planned -> planned?.let { recipesById[it.slot.recipeId] } }
        WeekSummary.of(menu, lines, ingredients.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Moves on to the new week if the app stayed open past Friday night. */
    fun refreshWeek() {
        weekStart.value = WeekPlan.startOf(clock())
    }

    fun pick(position: Int, recipeId: Long) {
        viewModelScope.launch { repository.pick(weekStart.value, position, recipeId) }
    }

    fun remove(position: Int) {
        viewModelScope.launch { repository.remove(SlotRef(weekStart.value, position)) }
    }

    fun setDone(position: Int, done: Boolean) {
        viewModelScope.launch { repository.setDone(SlotRef(weekStart.value, position), done) }
    }

    fun rollover() {
        viewModelScope.launch { repository.rollover(weekStart.value) }
    }

    /** Set when a suggestion found no recipe to add, until its snackbar is gone. */
    private val _noSuggestion = MutableStateFlow(false)
    val noSuggestion: StateFlow<Boolean> = _noSuggestion.asStateFlow()

    /** Fills the empty slots with suggestions; manual picks stay. */
    fun suggestWeek() {
        viewModelScope.launch {
            if (planner.suggestWeek(weekStart.value, clock()) == 0) _noSuggestion.value = true
        }
    }

    /** Replaces the recipe at [position] with another suggestion. */
    fun swapSuggestion(position: Int) {
        viewModelScope.launch {
            if (!planner.swap(SlotRef(weekStart.value, position), clock())) _noSuggestion.value = true
        }
    }

    fun noSuggestionShown() {
        _noSuggestion.value = false
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d. MMM", Locale.GERMAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekPlanScreen(
    viewModel: WeekPlanViewModel,
    onRecipeClick: (Long) -> Unit,
    onOpenShopping: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val noSuggestion by viewModel.noSuggestion.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val noSuggestionMessage = stringResource(R.string.week_suggest_none)
    /** Position of the slot the recipe picker fills, or null while it is closed. */
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshWeek()
        onPauseOrDispose {}
    }

    LaunchedEffect(noSuggestion) {
        if (!noSuggestion) return@LaunchedEffect
        snackbar.showSnackbar(noSuggestionMessage)
        // Cleared only now: clearing it earlier would change the key and cancel this snackbar.
        viewModel.noSuggestionShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.week_title))
                        state?.plan?.weekStart?.let { start ->
                            Text(
                                stringResource(
                                    R.string.week_range,
                                    start.format(dayFormat),
                                    start.plusDays(6).format(dayFormat),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenShopping) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = stringResource(R.string.week_shopping))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state?.plan?.slots?.any { it == null } == true) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.week_suggest)) },
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                    onClick = viewModel::suggestWeek,
                )
            }
        },
    ) { padding ->
        val current = state ?: return@Scaffold
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = padding) {
            if (current.rolloverCount > 0) {
                item {
                    RolloverCard(current.rolloverCount, onRollover = viewModel::rollover)
                }
            }
            summary?.let { totals ->
                item(key = "summary") { SummaryCard(totals) }
            }
            current.plan.slots.forEachIndexed { position, planned ->
                item(key = position) {
                    if (planned == null) {
                        EmptySlot(onPick = { picking = position })
                    } else {
                        FilledSlot(
                            planned,
                            onClick = { onRecipeClick(planned.slot.recipeId) },
                            onDoneChange = { viewModel.setDone(position, it) },
                            onSuggestOther = { viewModel.swapSuggestion(position) },
                            onReplace = { picking = position },
                            onRemove = { viewModel.remove(position) },
                        )
                    }
                    HorizontalDivider()
                }
            }
        }

        picking?.let { position ->
            val replacing = current.plan.slots[position]?.slot?.recipeId
            RecipePickerSheet(
                // A recipe is on the menu at most once; the replaced one may stay.
                recipes = recipes.filter { it.id !in current.plan.recipeIds || it.id == replacing },
                onPick = { recipeId ->
                    viewModel.pick(position, recipeId)
                    picking = null
                },
                onDismiss = { picking = null },
            )
        }
    }
}

@Composable
private fun RolloverCard(count: Int, onRollover: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            pluralStringResource(R.plurals.week_rollover_message, count, count),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        TextButton(onClick = onRollover, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.week_rollover_action))
        }
    }
}

@Composable
private fun SummaryCard(summary: WeekSummary) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            SummaryValue(
                value = formatPlantPoints(summary.plantPoints),
                label = stringResource(R.string.week_summary_plant_points),
                modifier = Modifier.weight(1f),
            )
            SummaryValue(
                value = summary.distinctIngredients.toString(),
                label = stringResource(R.string.week_summary_ingredients),
                modifier = Modifier.weight(1f),
            )
            SummaryValue(
                value = summary.averageProteinPerPortion
                    ?.let { stringResource(R.string.week_summary_protein_value, it) }
                    ?: "–",
                label = stringResource(R.string.week_summary_protein),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryValue(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Plant points come in quarters: "12", "12,25", "12,5". */
internal fun formatPlantPoints(points: Double): String =
    DecimalFormat("0.##", DecimalFormatSymbols(Locale.GERMAN)).format(points)

@Composable
private fun EmptySlot(onPick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
        headlineContent = {
            Text(stringResource(R.string.week_slot_pick), color = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier.clickable(onClick = onPick),
    )
}

@Composable
private fun FilledSlot(
    planned: PlannedRecipe,
    onClick: () -> Unit,
    onDoneChange: (Boolean) -> Unit,
    onSuggestOther: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
) {
    val done = planned.slot.done
    ListItem(
        leadingContent = { Checkbox(checked = done, onCheckedChange = onDoneChange) },
        headlineContent = {
            Text(
                planned.recipeTitle,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = if (done) {
            { Text(stringResource(R.string.week_slot_done)) }
        } else {
            null
        },
        trailingContent = {
            Row {
                if (!done) {
                    IconButton(onClick = onSuggestOther) {
                        Icon(Icons.Filled.Shuffle, stringResource(R.string.week_slot_suggest_other))
                    }
                }
                IconButton(onClick = onReplace) {
                    Icon(Icons.Filled.SwapHoriz, stringResource(R.string.week_slot_replace))
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, stringResource(R.string.week_slot_remove))
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipePickerSheet(
    recipes: List<Recipe>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.week_picker_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (recipes.isEmpty()) {
            Text(
                stringResource(R.string.week_picker_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn {
            items(recipes, key = { it.id }) { recipe ->
                ListItem(
                    headlineContent = { Text(recipe.title) },
                    trailingContent = recipe.cookingTimeMinutes?.let { minutes ->
                        { Text(stringResource(R.string.recipe_minutes, minutes)) }
                    },
                    modifier = Modifier.clickable { onPick(recipe.id) },
                )
            }
        }
    }
}
