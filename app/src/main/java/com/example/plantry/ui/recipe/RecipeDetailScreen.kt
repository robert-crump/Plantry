package com.example.plantry.ui.recipe

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.CookLogRepository
import com.example.plantry.data.CookingStats
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.Nutrient
import com.example.plantry.data.PlannedRepository
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeHighlight
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.RecipeNutrition
import com.example.plantry.data.RecipePhotoRepository
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.RecipeStats
import com.example.plantry.data.nutritionLines
import com.example.plantry.data.toDraft
import com.example.plantry.ui.cooklog.PlanDatePickerDialog
import com.example.plantry.ui.cooklog.formatPlannedDate
import com.example.plantry.ui.cooklog.label
import com.example.plantry.ui.cooklog.lastCookedLabel
import com.example.plantry.ui.currentLocale
import com.example.plantry.ui.theme.highlightGreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RecipeDetailUiState(
    val recipe: Recipe,
    val lines: List<RecipeIngredient>,
    val nutrition: RecipeNutrition,
    val highlights: Set<RecipeHighlight>,
    val cooking: CookingStats,
    /** The day it is planned for; null while it isn't on Geplant. */
    val plannedOn: LocalDate?,
)

class RecipeDetailViewModel(
    private val recipeId: Long,
    private val repository: RecipeRepository,
    ingredientRepository: IngredientRepository,
    cookLogRepository: CookLogRepository,
    private val plannedRepository: PlannedRepository,
    private val photos: RecipePhotoRepository,
    private val clock: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    /** The cookbook page photo, if the recipe has one. */
    val photo: StateFlow<ByteArray?> = photos.observe(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null until loaded, and after the recipe was deleted. */
    val state: StateFlow<RecipeDetailUiState?> = combine(
        repository.observeRecipe(recipeId),
        repository.observeLines(recipeId),
        ingredientRepository.observeIngredients(),
        cookLogRepository.observeStats(recipeId, clock),
        plannedRepository.observePlanned(recipeId),
    ) { recipe, lines, ingredients, cooking, planned ->
        if (recipe == null) return@combine null
        val byId = ingredients.associateBy { it.id }
        RecipeDetailUiState(
            recipe = recipe,
            lines = lines,
            nutrition = RecipeNutrition.calculate(
                nutritionLines(lines.map { it.toDraft() }, byId),
                recipe.ourServings,
            ),
            highlights = RecipeHighlight.of(RecipeStats.of(recipe, lines, byId), hasLines = lines.isNotEmpty()),
            cooking = cooking,
            plannedOn = planned?.plannedOn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun today(): LocalDate = clock()

    /** Puts the recipe on Geplant for [date], or moves it there. */
    fun plan(date: LocalDate) {
        viewModelScope.launch { plannedRepository.plan(recipeId, date) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(recipeId)
            photos.delete(recipeId)
            onDeleted()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    viewModel: RecipeDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    /** After "Planen", e.g. to ask for the reminder's notification permission. */
    onPlanned: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    val recipe = state?.recipe
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickPlanDate by rememberSaveable { mutableStateOf(false) }
    // Opening state of the Nährwerte rows; closed whenever the screen opens.
    var openNutrients by remember { mutableStateOf(emptySet<Nutrient>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (recipe != null) OverflowMenu(onEdit = onEdit, onDelete = { confirmDelete = true })
                },
            )
        },
    ) { padding ->
        val detail = state ?: return@Scaffold
        val current = detail.recipe
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (detail.highlights.isNotEmpty()) HighlightChips(detail.highlights)
            if (current.modified) {
                ListItem(
                    leadingContent = {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                stringResource(R.string.recipe_modified),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    },
                    headlineContent = { Text(stringResource(R.string.recipe_modified_hint)) },
                )
            }
            CookedSection(
                stats = detail.cooking,
                plannedOn = detail.plannedOn,
                today = viewModel.today(),
                onPlan = { pickPlanDate = true },
            )
            photo?.let { bytes ->
                ZoomablePhoto(
                    bytes,
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
            MetadataCard(current)

            DetailCard {
                SectionTitle(R.string.recipe_section_lines)
                if (detail.lines.isEmpty()) {
                    Text(
                        stringResource(R.string.recipe_lines_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                IngredientGrid(detail.lines)
            }

            if (detail.lines.isNotEmpty()) {
                DetailCard {
                    val allOpen = openNutrients.size == Nutrient.entries.size
                    Row(verticalAlignment = Alignment.Bottom) {
                        SectionTitle(R.string.recipe_section_nutrition, Modifier.weight(1f))
                        IconButton(onClick = { openNutrients = if (allOpen) emptySet() else Nutrient.entries.toSet() }) {
                            Icon(
                                if (allOpen) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                                stringResource(if (allOpen) R.string.nutrition_collapse_all else R.string.nutrition_expand_all),
                            )
                        }
                    }
                    NutrientList(
                        detail.nutrition,
                        expanded = openNutrients,
                        onToggle = { openNutrients = if (it in openNutrients) openNutrients - it else openNutrients + it },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (pickPlanDate) {
            val today = viewModel.today()
            PlanDatePickerDialog(
                // A day that has already passed can't be picked again, so moving starts from today.
                date = detail.plannedOn?.takeIf { it >= today } ?: today,
                today = today,
                onPick = { date ->
                    viewModel.plan(date)
                    onPlanned()
                },
                onDismiss = { pickPlanDate = false },
            )
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.recipe_delete_title)) },
                text = { Text(stringResource(R.string.recipe_delete_message, current.title)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        viewModel.delete(onDeleted = onBack)
                    }) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

/** "Bearbeiten" and "Löschen" behind the overflow icon. */
@Composable
private fun OverflowMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(bottom = 8.dp)) { content() }
    }
}

/** Quelle | Kochzeit, Portionen laut Buch | Unsere Portionen. */
@Composable
private fun MetadataCard(recipe: Recipe) {
    DetailCard {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetadataCell(R.string.recipe_source, Modifier.weight(1f)) { MetadataValue(sourceLabel(recipe)) }
                MetadataCell(R.string.recipe_cooking_time, Modifier.weight(1f)) {
                    val minutes = recipe.cookingTimeMinutes
                    if (minutes != null) {
                        MetadataValue(stringResource(R.string.recipe_minutes, minutes))
                    } else {
                        MissingCookingTimeChip()
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetadataCell(R.string.recipe_book_servings, Modifier.weight(1f)) {
                    MetadataValue(recipe.bookServings.toString())
                }
                MetadataCell(R.string.recipe_our_servings, Modifier.weight(1f)) {
                    MetadataValue(recipe.ourServings.toString())
                }
            }
        }
    }
}

@Composable
private fun MetadataCell(@StringRes label: Int, modifier: Modifier, value: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        value()
    }
}

@Composable
private fun MetadataValue(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

/** A green label per highlight the recipe earns, e.g. "Proteinreich", in [RecipeHighlight] order. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HighlightChips(highlights: Set<RecipeHighlight>) {
    val green = highlightGreen
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RecipeHighlight.entries.filter { it in highlights }.forEach { highlight ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = green.copy(alpha = 0.15f),
                contentColor = green,
            ) {
                Text(
                    stringResource(highlight.label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Timer icon and "Unbekannt", shaped like a chip but not tappable. */
@Composable
internal fun MissingCookingTimeChip(modifier: Modifier = Modifier) =
    TimerChip(stringResource(R.string.recipe_cooking_time_missing), modifier)

/** Timer icon and e.g. "25 Min.", or [MissingCookingTimeChip] without a time. */
@Composable
internal fun CookingTimeChip(minutes: Int?, modifier: Modifier = Modifier) =
    if (minutes != null) TimerChip(stringResource(R.string.recipe_minutes, minutes), modifier) else MissingCookingTimeChip(modifier)

@Composable
private fun TimerChip(text: String, modifier: Modifier) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.Timer, contentDescription = null, Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The lines' original wording, two per row in recipe order; long text wraps in its cell. */
@Composable
private fun IngredientGrid(lines: List<RecipeIngredient>) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        lines.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { line ->
                    Text(
                        line.originalText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a lone last line in the left column.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Last cooked and times cooked, with "Planen" on the right; once planned, the button shows the day. */
@Composable
private fun CookedSection(
    stats: CookingStats,
    plannedOn: LocalDate?,
    today: LocalDate,
    onPlan: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(lastCookedLabel(stats)) },
        supportingContent = if (stats.timesCooked > 0) {
            { Text(stringResource(R.string.cooked_times, stats.timesCooked)) }
        } else {
            null
        },
        trailingContent = {
            OutlinedButton(onClick = onPlan) {
                Icon(
                    if (plannedOn != null) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = null,
                    Modifier.size(ButtonDefaults.IconSize),
                )
                Text(
                    if (plannedOn != null) {
                        stringResource(R.string.planned_state, formatPlannedDate(plannedOn, today, currentLocale()))
                    } else {
                        stringResource(R.string.plan_action)
                    },
                    Modifier.padding(start = 8.dp),
                )
            }
        },
    )
}

@Composable
private fun SectionTitle(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
    )
}
