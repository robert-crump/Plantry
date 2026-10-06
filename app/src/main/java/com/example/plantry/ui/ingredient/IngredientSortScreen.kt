package com.example.plantry.ui.ingredient

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoveDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.IngredientSorting
import com.example.plantry.data.SortGroup
import com.example.plantry.data.SortGroupItems
import com.example.plantry.data.SortView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IngredientSortUiState(
    val view: SortView,
    val groups: List<SortGroupItems>,
    val onlyUnreviewed: Boolean,
    val unreviewedCount: Int,
    val selected: Set<Long>,
) {
    val shownIds: List<Long> get() = groups.flatMap { group -> group.ingredients.map { it.id } }
    val canConfirm: Boolean get() = groups.any { group -> group.ingredients.any { !it.reviewed } }
}

/** The Sortieren screen locked to one [view]. */
class IngredientSortViewModel(
    private val repository: IngredientRepository,
    val view: SortView,
) : ViewModel() {

    /** Null follows the default: on whenever unreviewed ingredients exist. */
    private val onlyUnreviewed = MutableStateFlow<Boolean?>(null)
    private val selected = MutableStateFlow(emptySet<Long>())

    val state: StateFlow<IngredientSortUiState?> =
        combine(repository.observeIngredients(), onlyUnreviewed, selected) { all, filter, selected ->
            val unreviewed = all.count { !it.reviewed }
            val only = filter ?: (unreviewed > 0)
            IngredientSortUiState(
                view = view,
                groups = IngredientSorting.group(all, view, only),
                onlyUnreviewed = only,
                unreviewedCount = unreviewed,
                selected = selected,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleOnlyUnreviewed() {
        val current = state.value ?: return
        onlyUnreviewed.value = !current.onlyUnreviewed
        selected.value = emptySet()
    }

    fun toggle(id: Long) = selected.update { if (id in it) it - id else it + id }

    fun clearSelection() {
        selected.value = emptySet()
    }

    /** Moves the selected ingredients into [group] and saves the new value right away. */
    fun moveTo(group: SortGroup) {
        val ids = selected.value
        if (ids.isEmpty()) return
        selected.value = emptySet()
        viewModelScope.launch { repository.moveTo(ids, group) }
    }

    /** Marks every ingredient currently shown as reviewed. */
    fun confirmShown() {
        val ids = state.value?.shownIds ?: return
        selected.value = emptySet()
        viewModelScope.launch { repository.markReviewed(ids) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientSortScreen(viewModel: IngredientSortViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(viewModel.view.label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val current = state ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = current.onlyUnreviewed,
                    onClick = viewModel::toggleOnlyUnreviewed,
                    label = { Text(stringResource(R.string.sort_only_unreviewed, current.unreviewedCount)) },
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = viewModel::confirmShown, enabled = current.canConfirm) {
                    Text(stringResource(R.string.sort_confirm_all))
                }
            }
            if (current.selected.isNotEmpty()) {
                SelectionHint(current.selected.size, onClear = viewModel::clearSelection)
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(current.groups, key = { it.group.toString() }) { items ->
                    GroupCard(
                        items,
                        selected = current.selected,
                        moving = current.selected.isNotEmpty(),
                        onMoveHere = { viewModel.moveTo(items.group) },
                        onToggle = viewModel::toggle,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionHint(count: Int, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pluralStringResource(R.plurals.sort_selection_hint, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClear) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.sort_clear_selection))
        }
    }
}

/** A group: tapping its header while chips are selected moves them here. */
@Composable
private fun GroupCard(
    items: SortGroupItems,
    selected: Set<Long>,
    moving: Boolean,
    onMoveHere: () -> Unit,
    onToggle: (Long) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Surface(
                color = if (moving) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (moving) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().clickable(enabled = moving, onClick = onMoveHere),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        items.group.label(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (moving) {
                        Icon(Icons.Filled.MoveDown, contentDescription = stringResource(R.string.sort_move_here))
                    } else {
                        Text(
                            items.ingredients.size.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (items.ingredients.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items.ingredients.forEach { ingredient ->
                        FilterChip(
                            selected = ingredient.id in selected,
                            onClick = { onToggle(ingredient.id) },
                            label = { Text(ingredient.name) },
                        )
                    }
                }
            }
        }
    }
}

internal val SortView.label: Int
    get() = when (this) {
        SortView.PLANT_POINTS -> R.string.sort_view_plant_points
        SortView.STORE_SECTION -> R.string.sort_view_store_section
    }

@Composable
private fun SortGroup.label(): String = when (this) {
    is SortGroup.Section -> stringResource(section.label)
    is SortGroup.Points -> plantPointsLabel(points)
}
