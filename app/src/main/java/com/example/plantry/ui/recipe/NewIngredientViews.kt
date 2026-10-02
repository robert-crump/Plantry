package com.example.plantry.ui.recipe

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.plantry.R
import com.example.plantry.data.usda.UsdaFood
import com.example.plantry.ui.ingredient.nutritionSummary

/**
 * The USDA entry proposed for a new ingredient, e.g. "Räuchertofu → Tofu, smoked (USDA)", which the
 * user confirms or changes. The rest of the proposal is reviewed later on the ingredient screen.
 */
@Composable
fun NewIngredientMatch(
    newIngredient: NewIngredient,
    isError: Boolean,
    onConfirm: () -> Unit,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val proposal = newIngredient.proposal
    val food = proposal.food
    Column(modifier) {
        Text(
            if (food == null) {
                stringResource(R.string.new_ingredient_no_usda, proposal.name)
            } else {
                stringResource(R.string.new_ingredient_match, proposal.name, food.description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError && !newIngredient.ready) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            if (newIngredient.ready) {
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.new_ingredient_confirmed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            } else if (food != null) {
                FilledTonalButton(onClick = onConfirm) { Text(stringResource(R.string.new_ingredient_confirm)) }
            }
            TextButton(onClick = onChange) {
                Text(stringResource(if (food == null) R.string.new_ingredient_pick else R.string.new_ingredient_change))
            }
        }
    }
}

/** Searches the bundled USDA data for another entry; [results] is null while it is loading. */
@Composable
fun UsdaPickerDialog(
    query: String,
    results: List<UsdaFood>?,
    onQueryChange: (String) -> Unit,
    onPick: (UsdaFood) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_ingredient_picker_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.usda_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    results == null -> PickerHint(R.string.usda_search_loading)
                    results.isEmpty() -> PickerHint(R.string.usda_search_no_results)
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                        items(results, key = { it.fdcId }) { food ->
                            ListItem(
                                headlineContent = { Text(food.description) },
                                supportingContent = { Text(nutritionSummary(food.nutrition)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { onPick(food) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PickerHint(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
}
