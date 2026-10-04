package com.example.plantry.ui.recipe

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.CookLogRepository
import com.example.plantry.data.Cooked
import com.example.plantry.data.CookingStats
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.PlannedRepository
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.RecipeNutrition
import com.example.plantry.data.RecipePhotoRepository
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.nutritionLines
import com.example.plantry.data.toDraft
import com.example.plantry.ui.cooklog.CookDatePickerDialog
import com.example.plantry.ui.cooklog.cookDateLabel
import com.example.plantry.ui.cooklog.lastCookedLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RecipeDetailUiState(
    val recipe: Recipe,
    val lines: List<RecipeIngredient>,
    val nutrition: RecipeNutrition,
    val cooking: CookingStats,
    /** On Geplant. */
    val planned: Boolean,
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
        plannedRepository.observeIsPlanned(recipeId),
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
            cooking = cooking,
            planned = planned,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The date the "Gekocht" action logs; null means today. */
    private val _cookDate = MutableStateFlow<LocalDate?>(null)
    val cookDate: StateFlow<LocalDate?> = _cookDate.asStateFlow()

    /** The recipe just logged, until its undo snackbar is gone. */
    private val _justLogged = MutableStateFlow<Cooked?>(null)
    val justLogged: StateFlow<Cooked?> = _justLogged.asStateFlow()

    fun today(): LocalDate = clock()

    fun setCookDate(date: LocalDate) {
        _cookDate.value = date.takeIf { it != today() }
    }

    /**
     * Logs the recipe as cooked on the chosen date and takes it off Geplant, then resets the
     * date to today.
     */
    fun markCooked() {
        val date = _cookDate.value ?: today()
        viewModelScope.launch {
            _justLogged.value = plannedRepository.cook(recipeId, date)
            _cookDate.value = null
        }
    }

    fun plan() {
        viewModelScope.launch { plannedRepository.plan(recipeId) }
    }

    fun onUndoShown() {
        _justLogged.value = null
    }

    /** Deletes the log entry and puts the recipe back on Geplant if it was there. */
    fun undoCooked(cooked: Cooked) {
        viewModelScope.launch { plannedRepository.undoCook(cooked) }
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
    val cookDate by viewModel.cookDate.collectAsStateWithLifecycle()
    val justLogged by viewModel.justLogged.collectAsStateWithLifecycle()
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    val recipe = state?.recipe
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickCookDate by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val loggedMessage = stringResource(R.string.cooked_logged)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(justLogged) {
        val cooked = justLogged ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(loggedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoCooked(cooked)
        // Cleared only now: clearing it earlier would change the key and cancel this snackbar.
        viewModel.onUndoShown()
    }

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
                    if (recipe != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val detail = state ?: return@Scaffold
        val current = detail.recipe
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
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
                planned = detail.planned,
                cookDate = cookDate ?: viewModel.today(),
                today = viewModel.today(),
                onPickDate = { pickCookDate = true },
                onCooked = viewModel::markCooked,
                onPlan = {
                    viewModel.plan()
                    onPlanned()
                },
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
            DetailRow(stringResource(R.string.recipe_source), sourceLabel(current))
            DetailRow(stringResource(R.string.recipe_book_servings), current.bookServings.toString())
            DetailRow(stringResource(R.string.recipe_our_servings), current.ourServings.toString())
            current.cookingTimeMinutes?.let { minutes ->
                DetailRow(stringResource(R.string.recipe_cooking_time), stringResource(R.string.recipe_minutes, minutes))
            }

            SectionTitle(R.string.recipe_section_lines)
            if (detail.lines.isEmpty()) {
                Text(
                    stringResource(R.string.recipe_lines_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            IngredientGrid(detail.lines)

            if (detail.lines.isNotEmpty()) {
                SectionTitle(R.string.recipe_section_nutrition)
                ProteinIndicator(
                    detail.nutrition.perPortion.protein,
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                NutrientList(detail.nutrition)
            }
        }

        if (pickCookDate) {
            CookDatePickerDialog(
                date = cookDate ?: viewModel.today(),
                today = viewModel.today(),
                onPick = viewModel::setCookDate,
                onDismiss = { pickCookDate = false },
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

/** Last cooked, times cooked, the "Gekocht" action with its date chip, and "Planen". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CookedSection(
    stats: CookingStats,
    planned: Boolean,
    cookDate: LocalDate,
    today: LocalDate,
    onPickDate: () -> Unit,
    onCooked: () -> Unit,
    onPlan: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(lastCookedLabel(stats)) },
        supportingContent = if (stats.timesCooked > 0) {
            { Text(stringResource(R.string.cooked_times, stats.timesCooked)) }
        } else {
            null
        },
    )
    FlowRow(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = onPickDate,
            label = { Text(cookDateLabel(cookDate, today)) },
            leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
        )
        Button(onClick = onCooked) {
            Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.cooked_action), Modifier.padding(start = 8.dp))
        }
        // While planned, the button stays as a disabled "Geplant" marker.
        OutlinedButton(onClick = onPlan, enabled = !planned) {
            Icon(
                if (planned) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = null,
                Modifier.size(ButtonDefaults.IconSize),
            )
            Text(
                stringResource(if (planned) R.string.planned_state else R.string.plan_action),
                Modifier.padding(start = 8.dp),
            )
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

@Composable
private fun DetailRow(label: String, value: String) {
    ListItem(overlineContent = { Text(label) }, headlineContent = { Text(value) })
}
