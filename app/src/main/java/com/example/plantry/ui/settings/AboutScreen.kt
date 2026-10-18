package com.example.plantry.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.plantry.R
import com.example.plantry.data.IngredientChip
import com.example.plantry.data.IngredientFilters
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.RecipeHighlight
import com.example.plantry.data.RecipeNutrition
import com.example.plantry.data.planner.MealPlanner
import com.example.plantry.data.planner.WeeklyPlantPointsGoal
import com.example.plantry.ui.cooklog.icon
import com.example.plantry.ui.cooklog.label
import com.example.plantry.ui.currentLocale
import com.example.plantry.ui.ingredient.amountLabel
import com.example.plantry.ui.ingredient.formatDecimal
import com.example.plantry.ui.ingredient.label
import com.example.plantry.ui.ingredient.plantPointsLabel

private const val OPEN_FOOD_FACTS_URL = "https://world.openfoodfacts.org"
private const val USDA_URL = "https://fdc.nal.usda.gov/"

/**
 * What Plantry is for and the rules behind its numbers. Every threshold comes from its constant and
 * the cooldown from the settings, so the text can't drift from what the app does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(cooldownDays: Int, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsGroup({ Paragraphs(stringResource(R.string.about_intro, WeeklyPlantPointsGoal)) })

            AboutHeader(R.string.about_how)
            SettingsGroup(
                {
                    Paragraphs(
                        stringResource(R.string.about_step_recipes),
                        stringResource(R.string.about_step_suggestions),
                        stringResource(R.string.about_step_reminder),
                        stringResource(R.string.about_step_history),
                    )
                },
            )

            AboutHeader(R.string.about_plant_points)
            SettingsGroup({ Paragraphs(stringResource(R.string.plant_points_info_intro)) }, *plantPointRows())
            AboutHint(stringResource(R.string.about_plant_points_rules, WeeklyPlantPointsGoal))

            AboutHeader(R.string.about_recipe_tags)
            SettingsGroup(
                *RecipeHighlight.entries.map { highlight ->
                    @Composable {
                        AboutRow(stringResource(highlight.label), highlight.threshold()) {
                            Icon(highlight.icon, contentDescription = null)
                        }
                    }
                }.toTypedArray(),
            )

            AboutHeader(R.string.about_ingredient_filters)
            SettingsGroup(
                *IngredientChip.entries.map { chip ->
                    @Composable {
                        AboutRow(stringResource(chip.label), chip.threshold()) {
                            Icon(chip.highlight.icon, contentDescription = null)
                        }
                    }
                }.toTypedArray(),
            )

            AboutHeader(R.string.about_suggestions)
            SettingsGroup(
                {
                    Paragraphs(
                        listOf(
                            AnnotatedString(stringResource(R.string.about_suggestions_intro, MealPlanner.DEFAULT_POOL_SIZE)),
                            labeled(
                                R.string.about_suggestions_cooldown_label,
                                pluralStringResource(R.plurals.about_suggestions_cooldown, cooldownDays, cooldownDays),
                            ),
                            labeled(
                                R.string.about_suggestions_protein_label,
                                stringResource(R.string.about_suggestions_protein, RecipeNutrition.PROTEIN_MIN.toInt()),
                            ),
                            labeled(R.string.about_suggestions_plants_label, stringResource(R.string.about_suggestions_plants)),
                            AnnotatedString(stringResource(R.string.about_suggestions_never_excluded)),
                            labeled(
                                R.string.about_suggestions_boost_label,
                                stringResource(R.string.about_suggestions_boost, WeeklyPlantPointsGoal),
                            ),
                        ),
                    )
                },
            )

            AboutHeader(R.string.about_data)
            val uriHandler = LocalUriHandler.current
            SettingsGroup(
                {
                    Paragraphs(
                        listOf(
                            labeled(R.string.about_data_claude_label, stringResource(R.string.about_data_claude)),
                            labeled(R.string.about_data_off_label, stringResource(R.string.about_data_off)),
                            labeled(R.string.about_data_usda_label, stringResource(R.string.about_data_usda)),
                            labeled(R.string.about_data_local_label, stringResource(R.string.about_data_local)),
                        ),
                    )
                },
                {
                    SettingsRow(
                        icon = null,
                        title = stringResource(R.string.about_source_off),
                        summary = stringResource(R.string.about_source_off_summary),
                        onClick = { uriHandler.openUri(OPEN_FOOD_FACTS_URL) },
                        trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                    )
                },
                {
                    SettingsRow(
                        icon = null,
                        title = stringResource(R.string.about_source_usda),
                        summary = stringResource(R.string.about_source_usda_summary),
                        onClick = { uriHandler.openUri(USDA_URL) },
                        trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                    )
                },
            )

            Text(
                stringResource(R.string.about_version, appVersion()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}

/** The 1 / ¼ / 0 rows, worded as in the ingredient screen's info dialog. */
private fun plantPointRows(): Array<@Composable () -> Unit> = listOf(
    PlantPoints.ONE to R.string.plant_points_info_one,
    PlantPoints.QUARTER to R.string.plant_points_info_quarter,
    PlantPoints.ZERO to R.string.plant_points_info_zero,
).map { (points, text) -> @Composable { AboutRow(plantPointsLabel(points), stringResource(text)) } }.toTypedArray()

@Composable
private fun perPortion(@StringRes amountLabel: Int, grams: Int): String = stringResource(
    R.string.about_at_least_per_portion,
    stringResource(amountLabel, formatDecimal(grams.toDouble(), currentLocale())),
)

@Composable
private fun RecipeHighlight.threshold(): String =
    when (this) {
        RecipeHighlight.PLANT_POINT_POWER -> stringResource(R.string.about_at_least_plant_points, RecipeHighlight.PLANT_POINTS_MIN)
        RecipeHighlight.HIGH_PROTEIN -> perPortion(R.string.filter_amount_protein, RecipeHighlight.PROTEIN_MIN)
        RecipeHighlight.HIGH_CARB -> perPortion(R.string.filter_amount_carbs, RecipeHighlight.CARBS_MIN)
        RecipeHighlight.HIGH_FIBRE -> perPortion(R.string.filter_amount_fibre, RecipeHighlight.FIBRE_MIN)
        RecipeHighlight.LOW_CALORIE -> stringResource(R.string.about_at_most_kcal_per_portion, RecipeHighlight.KCAL_MAX)
    }

@Composable
private fun IngredientChip.threshold(): String {
    val amount = stringResource(amountLabel, formatDecimal(IngredientFilters.min(this), currentLocale()))
    return stringResource(
        if (this == IngredientChip.HIGH_FIBRE) R.string.about_fibre_eu else R.string.about_at_least_per_100g,
        amount,
    )
}

/** The recipe tag of the same nutrient, whose icon the chip borrows. */
private val IngredientChip.highlight: RecipeHighlight
    get() = when (this) {
        IngredientChip.HIGH_PROTEIN -> RecipeHighlight.HIGH_PROTEIN
        IngredientChip.HIGH_CARBS -> RecipeHighlight.HIGH_CARB
        IngredientChip.HIGH_FIBRE -> RecipeHighlight.HIGH_FIBRE
    }

@Composable
private fun AboutHeader(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
    )
}

@Composable
private fun AboutHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp),
    )
}

/** A row that only shows a value, like [SettingsRow] without a click. */
@Composable
private fun AboutRow(title: String, summary: String, icon: (@Composable () -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = icon,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun Paragraphs(vararg texts: String) {
    Paragraphs(texts.map { AnnotatedString(it) })
}

@Composable
private fun Paragraphs(texts: List<AnnotatedString>) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        texts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** "**Label:** text". */
@Composable
private fun labeled(@StringRes label: Int, text: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(label) + ":") }
    append(" ")
    append(text)
}
