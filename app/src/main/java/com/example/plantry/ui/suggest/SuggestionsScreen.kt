package com.example.plantry.ui.suggest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.plantry.R
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientRepository
import com.example.plantry.data.MixContents
import com.example.plantry.data.PlannedRepository
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeLine
import com.example.plantry.data.resolve
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.RecipeSnapshot
import com.example.plantry.data.planner.RecipeSuggester
import com.example.plantry.data.planner.SuggestionRounds
import com.example.plantry.data.planner.WeekPlants
import com.example.plantry.data.planner.plantPointsGoalReachedWith
import com.example.plantry.ui.cooklog.PlanDatePickerDialog
import com.example.plantry.ui.cooklog.PlantPointsScore
import com.example.plantry.ui.cooklog.RecipeStatsRow
import com.example.plantry.ui.cooklog.WeekPlantPointsScore
import com.example.plantry.ui.recipe.CompactCookingTimeChip
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/** A suggested recipe as its card shows it. */
data class SuggestionCard(
    val recipeId: Long,
    val snapshot: RecipeSnapshot,
    val totalTimeMinutes: Int?,
    /** The lines as shown in recipe order, e.g. "1 Dose Kichererbsen, 200 g Reis". */
    val ingredients: String,
    /** The plant points the recipe would add to this week, see [WeekPlants.newPoints]. */
    val newPlantPoints: Double,
) {
    companion object {
        /** Lines are matched to [recipe] by id; without [week] all of the recipe's plants are new. */
        fun of(
            recipe: Recipe,
            lines: List<RecipeLine>,
            ingredients: Map<Long, Ingredient>,
            contents: MixContents = emptyMap(),
            week: WeekPlants = WeekPlants(emptySet(), ingredients, contents),
        ): SuggestionCard {
            val recipeLines = lines.filter { it.recipeId == recipe.id }
            return SuggestionCard(
                recipeId = recipe.id,
                snapshot = RecipeSnapshot.of(recipe, recipeLines, ingredients, contents),
                totalTimeMinutes = recipe.totalTimeMinutes,
                ingredients = recipeLines.sortedBy { it.position }
                    .joinToString(", ") { it.resolve(ingredients[it.ingredientId]?.name.orEmpty()).text },
                newPlantPoints = week.newPoints(recipeLines.map { it.ingredientId }),
            )
        }
    }
}

/**
 * One round of the suggestions screen: the main dishes and, at times, the plant-points boost below
 * them, with this week's plant points as they were when the round was drawn.
 */
data class Suggestions(val cards: List<SuggestionCard>, val boost: SuggestionCard? = null, val weekPoints: Double = 0.0) {
    /** Whether [card] would take this week to the plant-points goal. */
    fun reachesGoal(card: SuggestionCard): Boolean = plantPointsGoalReachedWith(weekPoints, card.newPlantPoints)
}

class SuggestionsViewModel(
    private val suggester: RecipeSuggester,
    private val plannedRepository: PlannedRepository,
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val clock: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    // Lives as long as the screen, so recipes already shown stay excluded until it is left.
    private val rounds = SuggestionRounds { count, exclude -> suggester.suggest(clock(), count, exclude) }
    private val boostRounds = SuggestionRounds(count = 1) { count, exclude -> suggester.boost(clock(), count, exclude) }
    private var loading: Job? = null

    private val _suggestions = MutableStateFlow<Suggestions?>(null)

    /** Null until the first round is loaded, so the empty message doesn't flash. */
    val suggestions: StateFlow<Suggestions?> = _suggestions.asStateFlow()

    init {
        reroll()
    }

    /** Replaces all cards, the boost card too; ignored while a round is still loading. */
    fun reroll() {
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            val planned = plannedRepository.observe().first().mapTo(mutableSetOf()) { it.recipeId }
            val ids = rounds.next(planned)
            val boostId = boostRounds.next(planned).firstOrNull()
            val recipes = recipeRepository.observeRecipes().first().associateBy { it.id }
            val lines = recipeRepository.observeAllLines().first()
            val ingredients = ingredientRepository.observeIngredients().first().associateBy { it.id }
            val contents = ingredientRepository.observeContents().first()
            val week = suggester.week(clock())
            fun card(id: Long) = recipes[id]?.let { SuggestionCard.of(it, lines, ingredients, contents, week) }
            _suggestions.value = Suggestions(ids.mapNotNull(::card), boostId?.let(::card), week.points)
        }
    }

    fun today(): LocalDate = clock()
}

/**
 * Up to three suggested recipes, on the weekend while the week is short of its plant points one more
 * below them to boost those; "Neue Vorschläge" replaces them all. The top bar shows this week's plant
 * points, e.g. "22/30", and each card the ones it would add, e.g. "+4", green when they would reach
 * the goal. Tapping a card asks for the day to cook it; picking one hands it and the day to
 * [onPlanned], which puts it on Geplant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsScreen(
    viewModel: SuggestionsViewModel,
    onBack: () -> Unit,
    onPlanned: (recipeId: Long, title: String, date: LocalDate) -> Unit,
) {
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    var confirmId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.suggest_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    suggestions?.let { WeekPlantPointsScore(it.weekPoints, Modifier.padding(end = 16.dp)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.suggest_reroll), style = MaterialTheme.typography.titleMedium) },
                icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                onClick = viewModel::reroll,
                modifier = Modifier.padding(8.dp),
            )
        },
    ) { padding ->
        val current = suggestions ?: return@Scaffold
        if (current.cards.isEmpty() && current.boost == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.suggest_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                // Room below the last card for the FAB.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(current.cards, key = { it.recipeId }) { card ->
                    SuggestionCardView(card, current.reachesGoal(card), onClick = { confirmId = card.recipeId })
                }
                current.boost?.let { card ->
                    item(key = "boost") {
                        Text(
                            stringResource(R.string.suggest_boost),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    item(key = card.recipeId) {
                        SuggestionCardView(card, current.reachesGoal(card), onClick = { confirmId = card.recipeId })
                    }
                }
            }
        }
    }

    val confirming = confirmId?.let { id -> suggestions?.let { it.cards + listOfNotNull(it.boost) }?.firstOrNull { it.recipeId == id } }
    if (confirming != null) {
        PlanDatePickerDialog(
            date = viewModel.today(),
            today = viewModel.today(),
            onPick = { date -> onPlanned(confirming.recipeId, confirming.snapshot.title, date) },
            onDismiss = { confirmId = null },
        )
    }
}

/** The title row ends with the plant points the recipe adds to this week, green when they [reachGoal]. */
@Composable
private fun SuggestionCardView(card: SuggestionCard, reachGoal: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.snapshot.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                val points = card.newPlantPoints.roundToInt()
                PlantPointsScore(
                    text = "+$points",
                    description = stringResource(
                        if (points == 1) R.string.suggest_new_plant_points_one else R.string.suggest_new_plant_points_other,
                        points,
                    ),
                    reached = reachGoal,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecipeStatsRow(card.snapshot.stats, Modifier.weight(1f))
                CompactCookingTimeChip(card.totalTimeMinutes, Modifier.padding(start = 8.dp))
            }
            if (card.ingredients.isNotEmpty()) {
                Text(
                    card.ingredients,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
