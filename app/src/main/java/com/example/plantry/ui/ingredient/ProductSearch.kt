package com.example.plantry.ui.ingredient

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.plantry.R
import com.example.plantry.data.Nutrient
import com.example.plantry.data.openfoodfacts.OffProduct
import com.example.plantry.data.openfoodfacts.OffSearch
import com.example.plantry.ui.LoadingIndicator

/** The product name search: what is typed, and the last search's result (null before the first). */
data class ProductSearchState(
    val query: String,
    val searching: Boolean = false,
    val result: OffSearch? = null,
)

/** Searches Open Food Facts by product name; picking a hit continues like a scanned barcode. */
@Composable
fun ProductSearchDialog(
    state: ProductSearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPick: (OffProduct) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.product_search_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.product_search_query)) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onSearch, enabled = state.query.isNotBlank()) {
                            Icon(Icons.Filled.Search, stringResource(R.string.action_search))
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    state.searching -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                    state.result is OffSearch.Offline -> Message(stringResource(R.string.barcode_offline))
                    state.result is OffSearch.Found && state.result.products.isEmpty() ->
                        Message(stringResource(R.string.product_search_none))
                    state.result is OffSearch.Found -> Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                        state.result.products.forEach { ProductRow(it, onClick = { onPick(it) }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSearch, enabled = state.query.isNotBlank() && !state.searching) {
                Text(stringResource(R.string.action_search))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "Räuchertofu (Taifun)" over "200 g · 198 kcal/100 g". */
@Composable
private fun ProductRow(product: OffProduct, onClick: () -> Unit) {
    val kcal = product.nutrition[Nutrient.KCAL]?.let { stringResource(R.string.product_search_kcal, it.toInt().toString()) }
    val details = listOfNotNull(product.quantity, kcal).joinToString(" · ")
    ListItem(
        headlineContent = { Text(product.productName) },
        supportingContent = details.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
