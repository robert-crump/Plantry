package com.example.plantry.ui.ingredient

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.plantry.R
import com.example.plantry.data.Nutrient
import com.example.plantry.data.Nutrition

/**
 * Asks for the nutrition per 100 g from the package, for an ingredient without a USDA entry; the
 * confirm button stays disabled until all values are valid. With [askName] it also asks for the
 * German name, starting from [initial]'s name.
 */
@Composable
fun LabelNutritionDialog(
    @StringRes confirmLabel: Int,
    onConfirm: (name: String, nutrition: Nutrition) -> Unit,
    onDismiss: () -> Unit,
    initial: LabelNutritionForm = LabelNutritionForm(),
    askName: Boolean = false,
) {
    var form by rememberSaveable(stateSaver = LabelNutritionFormSaver) { mutableStateOf(initial) }
    val invalid = form.invalid()
    val nutrition = form.toNutrition()
    val canConfirm = nutrition != null && (!askName || form.name.isNotBlank())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_nutrition_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (askName) {
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = { form = form.copy(name = it) },
                        label = { Text(stringResource(R.string.usda_create_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    stringResource(R.string.label_nutrition_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Nutrient.entries.forEach { nutrient ->
                    val error = nutrient in invalid
                    OutlinedTextField(
                        value = form.values[nutrient].orEmpty(),
                        onValueChange = { form = form.withValue(nutrient, it) },
                        label = { Text(stringResource(nutrient.label)) },
                        isError = error,
                        supportingText = if (error) {
                            { Text(stringResource(R.string.error_non_negative_decimal)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { nutrition?.let { onConfirm(form.name.trim(), it) } }, enabled = canConfirm) {
                Text(stringResource(confirmLabel))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Saves the name and the values in [Nutrient] order. */
private val LabelNutritionFormSaver = Saver<LabelNutritionForm, ArrayList<String>>(
    save = { form -> arrayListOf(form.name).apply { Nutrient.entries.forEach { add(form.values[it].orEmpty()) } } },
    restore = { saved -> LabelNutritionForm(saved[0], Nutrient.entries.withIndex().associate { (i, it) -> it to saved[i + 1] }) },
)
