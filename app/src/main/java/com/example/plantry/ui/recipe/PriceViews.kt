package com.example.plantry.ui.recipe

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.plantry.R
import com.example.plantry.data.RecipePrice
import com.example.plantry.ui.formatEuros
import com.example.plantry.ui.formatPriceDate

/** "ca. 2,40 €", "< 0,10 €" or "—" without a price; an info icon follows while the estimate has gaps. */
@Composable
internal fun PricePerPortion(price: RecipePrice) {
    val rounded = price.roundedPerPortion
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            when {
                rounded == null -> stringResource(R.string.recipe_time_none)
                rounded == 0.0 -> stringResource(R.string.recipe_price_below)
                else -> stringResource(R.string.recipe_price_value, formatEuros(rounded))
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (price.hasGaps) {
            Icon(
                Icons.Outlined.Info,
                stringResource(R.string.recipe_price_gaps),
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * How the estimate comes about: the whole recipe's cost, each priced ingredient's share, and the
 * ingredients without a price or with an outdated one. Tapping an ingredient opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PriceSheet(price: RecipePrice, servings: Int, onIngredientClick: (Long) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.recipe_price), style = MaterialTheme.typography.titleLarge)
                Text(
                    if (price.perPortion == null) {
                        stringResource(R.string.recipe_price_none)
                    } else {
                        pluralStringResource(R.plurals.recipe_price_total, servings, formatEuros(price.total), servings)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (price.costs.isNotEmpty()) {
                SheetSection(R.string.recipe_price_costs)
                price.costs.forEach { cost ->
                    IngredientItem(cost.name, trailing = "${formatEuros(cost.cost)} €") { onIngredientClick(cost.ingredientId) }
                }
            }
            if (price.missing.isNotEmpty()) {
                SheetSection(R.string.recipe_price_missing)
                Text(
                    stringResource(R.string.recipe_price_missing_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                price.missing.forEach { gap -> IngredientItem(gap.name) { onIngredientClick(gap.ingredientId) } }
            }
            if (price.outdated.isNotEmpty()) {
                SheetSection(R.string.recipe_price_outdated)
                price.outdated.forEach { gap ->
                    val date = gap.priceDate?.let { stringResource(R.string.recipe_price_outdated_date, formatPriceDate(it)) }
                    IngredientItem(gap.name, trailing = date) { onIngredientClick(gap.ingredientId) }
                }
            }
        }
    }
}

@Composable
private fun SheetSection(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun IngredientItem(name: String, trailing: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(name) },
        trailingContent = trailing?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.recipe_line_open), onClick = onClick),
    )
}
