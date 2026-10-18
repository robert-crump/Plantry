package com.example.plantry.ui.recipe

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.Nutrient
import com.example.plantry.data.PlannedRepository
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeHighlight
import com.example.plantry.data.RecipeNutrition
import com.example.plantry.data.RecipePhotoRepository
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.RecipeStats
import com.example.plantry.data.StoreSection
import com.example.plantry.data.nutritionLines
import com.example.plantry.data.resolve
import com.example.plantry.ui.HideOnScrollFab
import com.example.plantry.ui.ShowMoreToggle
import com.example.plantry.ui.collapsedCount
import com.example.plantry.ui.cooklog.PlanDatePickerDialog
import com.example.plantry.ui.cooklog.icon
import com.example.plantry.ui.cooklog.label
import com.example.plantry.ui.ingredient.label
import com.example.plantry.ui.rememberFabVisibility
import com.example.plantry.ui.theme.highlightGreen
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecipeDetailUiState(
    val recipe: Recipe,
    val lines: List<ShownLine>,
    val nutrition: RecipeNutrition,
    val stats: RecipeStats,
    val highlights: Set<RecipeHighlight>,
    /** The day it is planned for; null while it isn't on Geplant. */
    val plannedOn: LocalDate?,
)

/** A line as the recipe shows it, under its ingredient's store [section]. */
data class ShownLine(val ingredientId: Long, val text: String, val section: StoreSection)

class RecipeDetailViewModel(
    private val recipeId: Long,
    private val repository: RecipeRepository,
    ingredientRepository: IngredientRepository,
    private val plannedRepository: PlannedRepository,
    private val photoRepository: RecipePhotoRepository,
    private val compressPhoto: suspend (Uri) -> ByteArray?,
    private val clock: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    /** The photos of the cookbook pages, in order; null until read. */
    val photos: StateFlow<List<ByteArray>?> = photoRepository.observe(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null until loaded, and after the recipe was deleted. */
    val state: StateFlow<RecipeDetailUiState?> = combine(
        repository.observeRecipe(recipeId),
        repository.observeLines(recipeId),
        ingredientRepository.observeIngredients(),
        plannedRepository.observePlanned(recipeId),
        ingredientRepository.observeContents(),
    ) { recipe, lines, ingredients, planned, contents ->
        if (recipe == null) return@combine null
        val byId = ingredients.associateBy { it.id }
        val stats = RecipeStats.of(recipe, lines, byId, contents)
        RecipeDetailUiState(
            recipe = recipe,
            // Grouped by store section, in recipe order within each.
            lines = lines.map { line ->
                val ingredient = byId[line.ingredientId]
                ShownLine(line.ingredientId, line.resolve(ingredient?.name.orEmpty()).text, ingredient?.storeSection ?: StoreSection.OTHER)
            }.sortedBy { it.section },
            nutrition = RecipeNutrition.calculate(
                nutritionLines(lines, byId),
                recipe.servings,
            ),
            stats = stats,
            highlights = RecipeHighlight.of(stats, hasLines = lines.isNotEmpty()),
            plannedOn = planned?.plannedOn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun today(): LocalDate = clock()

    /** Puts the recipe on Geplant for [date], or moves it there. */
    fun plan(date: LocalDate) {
        viewModelScope.launch { plannedRepository.plan(recipeId, date) }
    }

    /**
     * Keeps the photo at [uri] as the last page; [onDone] is called once [photos] shows it, or with
     * false when it could not be read.
     */
    fun addPhoto(uri: Uri, onDone: (added: Boolean) -> Unit) {
        viewModelScope.launch {
            val bytes = compressPhoto(uri) ?: return@launch onDone(false)
            val before = photos.value?.size ?: 0
            photoRepository.add(recipeId, bytes)
            photos.first { it != null && it.size > before }
            onDone(true)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(recipeId)
            photoRepository.deleteAll(recipeId)
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
    /** With the ingredient id of a tapped ingredient line. */
    onIngredientClick: (Long) -> Unit = {},
    /** After "Planen", e.g. to ask for the reminder's notification permission. */
    onPlanned: () -> Unit = {},
    /**
     * Instead of planning here, gets the title and the day picked on "Planen", e.g. to plan it back
     * on Speiseplan when the recipe was opened from a suggestion.
     */
    onPlanPicked: ((title: String, date: LocalDate) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val recipe = state?.recipe
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickPlanDate by rememberSaveable { mutableStateOf(false) }
    // The photo open over the whole screen, by its index in the roll.
    var openPhoto by rememberSaveable { mutableStateOf<Int?>(null) }
    var choosingPhotoSource by rememberSaveable { mutableStateOf(false) }
    val photoRoll = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unreadable = stringResource(R.string.photo_unreadable)
    val photoSource = rememberPhotoSource { uri ->
        viewModel.addPhoto(uri) { added ->
            scope.launch {
                // The tile follows the new photo; scrolling to it shows the photo next to it.
                if (added) photoRoll.animateScrollToItem(viewModel.photos.value.orEmpty().size)
                else snackbar.showSnackbar(unreadable)
            }
        }
    }
    // Opening state of the Nährwerte rows; closed whenever the screen opens.
    var openNutrients by remember { mutableStateOf(emptySet<Nutrient>()) }
    val fab = rememberFabVisibility()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state != null) {
                HideOnScrollFab(fab) {
                    ExtendedFloatingActionButton(
                        onClick = { pickPlanDate = true },
                        icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                        text = { Text(stringResource(R.string.plan_action)) },
                    )
                }
            }
        },
    ) { padding ->
        val detail = state ?: return@Scaffold
        val current = detail.recipe
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(fab.connection)
                .verticalScroll(rememberScrollState()),
        ) {
            if (detail.highlights.isNotEmpty()) {
                HighlightTags(detail.highlights, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
            }
            photos?.let {
                PhotoRoll(
                    it,
                    onOpen = { index -> openPhoto = index },
                    modifier = Modifier.padding(top = 16.dp),
                    state = photoRoll,
                    onAdd = { choosingPhotoSource = true },
                )
            }
            // Without lines a 0 would say nothing.
            MetadataCard(current, plantPoints = detail.stats.roundedPlantPoints.takeIf { detail.lines.isNotEmpty() })

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
                IngredientList(detail.lines, onLineClick = { onIngredientClick(it.ingredientId) })
            }

            if (detail.lines.isNotEmpty()) {
                DetailCard {
                    SectionTitle(R.string.recipe_section_nutrition)
                    NutrientList(
                        detail.nutrition,
                        expanded = openNutrients,
                        onToggle = { openNutrients = if (it in openNutrients) openNutrients - it else openNutrients + it },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        val shown = photos.orEmpty()
        openPhoto?.takeIf { it in shown.indices }?.let { page ->
            PhotoViewer(shown, page, onPageChange = { openPhoto = it }, onClose = { openPhoto = null })
        }

        if (choosingPhotoSource) {
            PhotoSourceSheet(
                onTakePhoto = {
                    choosingPhotoSource = false
                    photoSource.takePhoto()
                },
                onPickPhoto = {
                    choosingPhotoSource = false
                    photoSource.pickPhoto()
                },
                onDismiss = { choosingPhotoSource = false },
            )
        }

        if (pickPlanDate) {
            val today = viewModel.today()
            PlanDatePickerDialog(
                // A day that has already passed can't be picked again, so moving starts from today.
                date = detail.plannedOn?.takeIf { it >= today } ?: today,
                today = today,
                onPick = { date ->
                    if (onPlanPicked != null) {
                        onPlanPicked(current.title, date)
                    } else {
                        viewModel.plan(date)
                        onPlanned()
                    }
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

/**
 * The same for every Art: Arbeitszeit | Gesamtzeit, Portionen | Pflanzenpunkte, Art in the left half,
 * then Quelle at full width. [plantPoints] null shows "—".
 */
@Composable
private fun MetadataCard(recipe: Recipe, plantPoints: Int?) {
    DetailCard {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetadataCell(R.string.recipe_active_time, Modifier.weight(1f)) { MinutesValue(recipe.activeTimeMinutes) }
                MetadataCell(R.string.recipe_total_time, Modifier.weight(1f)) { MinutesValue(recipe.totalTimeMinutes) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetadataCell(R.string.recipe_servings, Modifier.weight(1f)) { MetadataValue(recipe.servings.toString()) }
                MetadataCell(R.string.recipe_plant_points, Modifier.weight(1f)) {
                    MetadataValue(plantPoints?.toString() ?: stringResource(R.string.recipe_time_none))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetadataCell(R.string.recipe_dish, Modifier.weight(1f)) { MetadataValue(stringResource(recipe.dish.label)) }
                Spacer(Modifier.weight(1f))
            }
            MetadataCell(R.string.recipe_source, Modifier.fillMaxWidth()) { MetadataValue(sourceLabel(recipe)) }
        }
    }
}

/** A green tag per highlight, wrapping onto a second line when they don't fit. Not tappable. */
@Composable
private fun HighlightTags(highlights: Set<RecipeHighlight>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RecipeHighlight.entries.filter { it in highlights }.forEach { HighlightTag(it) }
    }
}

@Composable
private fun HighlightTag(highlight: RecipeHighlight) {
    val green = highlightGreen
    Surface(
        shape = MaterialTheme.shapes.small,
        color = green.copy(alpha = 0.12f),
        contentColor = green,
        border = BorderStroke(1.dp, green),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(highlight.icon, contentDescription = null, Modifier.size(18.dp))
            Text(stringResource(highlight.label), style = MaterialTheme.typography.labelLarge)
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

/** E.g. "25 Min.", or "—" without a time. */
@Composable
private fun MinutesValue(minutes: Int?) {
    MetadataValue(minutes?.let { stringResource(R.string.recipe_minutes, it) } ?: stringResource(R.string.recipe_time_none))
}

/**
 * Timer icon and only the Gesamtzeit, e.g. "60'", or "?" without one, even with an Arbeitszeit:
 * narrow enough to share a line with the stats. Screen readers hear "Gesamtzeit: 60 Min.".
 */
@Composable
internal fun CompactCookingTimeChip(totalMinutes: Int?, modifier: Modifier = Modifier) {
    val spoken = stringResource(
        R.string.recipe_total_time_spoken,
        totalMinutes?.let { stringResource(R.string.recipe_minutes, it) } ?: stringResource(R.string.recipe_cooking_time_missing),
    )
    TimerChip(
        totalMinutes?.let { stringResource(R.string.recipe_minutes_compact, it) } ?: stringResource(R.string.recipe_minutes_compact_missing),
        modifier.clearAndSetSemantics { contentDescription = spoken },
    )
}

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

/**
 * The lines one per line, under a header for each store section, see [ingredientListRows]. A long
 * list shows only its first lines, see [collapsedCount], and a toggle below them for the rest;
 * it starts collapsed whenever the screen opens. Each line is tappable.
 */
@Composable
private fun IngredientList(lines: List<ShownLine>, onLineClick: (ShownLine) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val collapsed = collapsedCount(lines.size)
    val hidden = lines.size - collapsed
    Column(
        Modifier
            .padding(vertical = 8.dp)
            .animateContentSize(),
    ) {
        val openLabel = stringResource(R.string.recipe_line_open)
        ingredientListRows(lines, if (expanded) lines.size else collapsed).forEach { row ->
            when (row) {
                is IngredientListRow.Header -> Text(
                    stringResource(row.section.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
                is IngredientListRow.Line -> Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(onClickLabel = openLabel) { onLineClick(row.line) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(row.line.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (hidden > 0) {
            ShowMoreToggle(
                expanded,
                pluralStringResource(R.plurals.recipe_lines_more, hidden, hidden),
                onToggle = { expanded = !expanded },
            )
        }
    }
}

/** A row of the ingredient list: a store section's header or one of its lines. */
internal sealed interface IngredientListRow {
    data class Header(val section: StoreSection) : IngredientListRow

    data class Line(val line: ShownLine) : IngredientListRow
}

/**
 * The first [shown] of [lines] (grouped by section), each section's first one after its header.
 * Without headers when all lines share one section, which a header would only repeat.
 */
internal fun ingredientListRows(lines: List<ShownLine>, shown: Int): List<IngredientListRow> {
    val headers = lines.distinctBy { it.section }.size > 1
    return buildList {
        lines.take(shown).forEachIndexed { index, line ->
            if (headers && (index == 0 || lines[index - 1].section != line.section)) add(IngredientListRow.Header(line.section))
            add(IngredientListRow.Line(line))
        }
    }
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
    )
}
