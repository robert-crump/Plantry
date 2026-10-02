package com.example.plantry.ui.shopping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.BuyQuantity
import com.example.plantry.data.ShoppingItem
import com.example.plantry.data.ShoppingList
import com.example.plantry.data.ShoppingListRepository
import com.example.plantry.data.WeekPlan
import com.example.plantry.ui.ingredient.label
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListViewModel(
    private val repository: ShoppingListRepository,
    private val clock: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val weekStart = MutableStateFlow(WeekPlan.startOf(clock()))

    /** Null until loaded. */
    val list: StateFlow<ShoppingList?> = weekStart.flatMapLatest(repository::observe)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Moves on to the new week if the app stayed open past Friday night. */
    fun refreshWeek() {
        weekStart.value = WeekPlan.startOf(clock())
    }

    fun setTicked(ingredientId: Long, ticked: Boolean) {
        viewModelScope.launch { repository.setTicked(weekStart.value, ingredientId, ticked) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    var staplesExpanded by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshWeek()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.shopping_title)) }) },
    ) { padding ->
        val current = list ?: return@Scaffold
        if (current.sections.isEmpty() && current.staples.isEmpty()) {
            Text(
                stringResource(R.string.shopping_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = padding) {
            current.sections.forEach { section ->
                item(key = section.storeSection.name) { SectionHeader(stringResource(section.storeSection.label)) }
                items(section.items, key = { it.ingredientId }) { item ->
                    ShoppingRow(item, onTickedChange = { viewModel.setTicked(item.ingredientId, it) })
                }
            }
            if (current.staples.isNotEmpty()) {
                item(key = "staples") {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.shopping_staples, current.staples.size))
                        },
                        trailingContent = {
                            Icon(if (staplesExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        },
                        modifier = Modifier.clickable { staplesExpanded = !staplesExpanded },
                    )
                }
                if (staplesExpanded) {
                    items(current.staples, key = { "staple:$it" }) { name ->
                        ListItem(headlineContent = { Text(name) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ShoppingRow(item: ShoppingItem, onTickedChange: (Boolean) -> Unit) {
    val ticked = item.ticked
    val color = if (ticked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val decoration = if (ticked) TextDecoration.LineThrough else null
    val needed = neededText(item)
    ListItem(
        leadingContent = { Checkbox(checked = ticked, onCheckedChange = onTickedChange) },
        headlineContent = { Text(item.name, color = color, textDecoration = decoration) },
        supportingContent = needed?.let { { Text(it) } },
        trailingContent = {
            Text(
                quantityText(item.quantity),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                textDecoration = decoration,
            )
        },
        modifier = Modifier.clickable { onTickedChange(!ticked) },
    )
}

@Composable
private fun quantityText(quantity: BuyQuantity): String = when (quantity) {
    is BuyQuantity.Pieces -> quantity.count.toString()
    is BuyQuantity.Packs -> pluralStringResource(R.plurals.shopping_packs, quantity.count, quantity.count)
    is BuyQuantity.Grams -> stringResource(R.string.shopping_grams, quantity.grams)
}

/** The amount the menu needs, beside a quantity in pieces or packs; null when buying by grams. */
@Composable
private fun neededText(item: ShoppingItem): String? = when (val quantity = item.quantity) {
    is BuyQuantity.Pieces -> stringResource(R.string.shopping_needed, item.neededGrams)
    is BuyQuantity.Packs -> stringResource(R.string.shopping_needed_of_packs, item.neededGrams, quantity.count * quantity.packSizeGrams)
    is BuyQuantity.Grams -> null
}
