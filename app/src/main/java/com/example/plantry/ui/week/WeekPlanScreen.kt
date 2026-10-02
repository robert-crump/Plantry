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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.SlotRef
import com.example.plantry.data.WeekPlan
import com.example.plantry.data.WeekPlanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    recipeRepository: RecipeRepository,
    private val clock: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val weekStart = MutableStateFlow(WeekPlan.startOf(clock()))

    /** Null until loaded. */
    val state: StateFlow<WeekPlanUiState?> = weekStart.flatMapLatest { start ->
        combine(repository.observeWeek(start), repository.observeRolloverCount(start), ::WeekPlanUiState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recipes: StateFlow<List<Recipe>> = recipeRepository.observeRecipes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d. MMM", Locale.GERMAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekPlanScreen(
    viewModel: WeekPlanViewModel,
    onRecipeClick: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    /** Position of the slot the recipe picker fills, or null while it is closed. */
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshWeek()
        onPauseOrDispose {}
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
            )
        },
    ) { padding ->
        val current = state ?: return@Scaffold
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = padding) {
            if (current.rolloverCount > 0) {
                item {
                    RolloverCard(current.rolloverCount, onRollover = viewModel::rollover)
                }
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
                    trailingContent = {
                        Text(stringResource(R.string.recipe_minutes, recipe.cookingTimeMinutes))
                    },
                    modifier = Modifier.clickable { onPick(recipe.id) },
                )
            }
        }
    }
}
