package com.example.plantry.ui.suggest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.plantry.data.PlannedRepository
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.RecipeRepository
import com.example.plantry.data.RecipeSnapshot
import com.example.plantry.data.planner.RecipeSuggester
import com.example.plantry.data.planner.SuggestionRounds
import com.example.plantry.ui.cooklog.RecipeStatsRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A suggested recipe as its card shows it. */
data class SuggestionCard(
    val recipeId: Long,
    val snapshot: RecipeSnapshot,
    val cookingTimeMinutes: Int?,
    /** The lines' original wording in recipe order, e.g. "1 Dose Kichererbsen, 2 TL Zucker". */
    val ingredients: String,
) {
    companion object {
        /** Lines are matched to [recipe] by id. */
        fun of(recipe: Recipe, lines: List<RecipeIngredient>, ingredients: Map<Long, Ingredient>): SuggestionCard {
            val recipeLines = lines.filter { it.recipeId == recipe.id }
            return SuggestionCard(
                recipeId = recipe.id,
                snapshot = RecipeSnapshot.of(recipe, recipeLines, ingredients),
                cookingTimeMinutes = recipe.cookingTimeMinutes,
                ingredients = recipeLines.sortedBy { it.position }.joinToString(", ") { it.originalText },
            )
        }
    }
}

class SuggestionsViewModel(
    suggester: RecipeSuggester,
    private val plannedRepository: PlannedRepository,
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    clock: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    // Lives as long as the screen, so recipes already shown stay excluded until it is left.
    private val rounds = SuggestionRounds { count, exclude -> suggester.suggest(clock(), count, exclude) }
    private var loading: Job? = null

    private val _cards = MutableStateFlow<List<SuggestionCard>?>(null)

    /** Null until the first round is loaded, so the empty message doesn't flash. */
    val cards: StateFlow<List<SuggestionCard>?> = _cards.asStateFlow()

    init {
        reroll()
    }

    /** Replaces all cards; ignored while a round is still loading. */
    fun reroll() {
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            val planned = plannedRepository.observe().first().mapTo(mutableSetOf()) { it.recipeId }
            val ids = rounds.next(planned)
            val recipes = recipeRepository.observeRecipes().first().associateBy { it.id }
            val lines = recipeRepository.observeAllLines().first()
            val ingredients = ingredientRepository.observeIngredients().first().associateBy { it.id }
            _cards.value = ids.mapNotNull { id -> recipes[id]?.let { SuggestionCard.of(it, lines, ingredients) } }
        }
    }

    /** Puts the recipe on Geplant, then calls [onDone]. */
    fun plan(card: SuggestionCard, onDone: () -> Unit) {
        viewModelScope.launch {
            plannedRepository.plan(card.recipeId)
            onDone()
        }
    }
}

/**
 * Up to three suggested recipes; "Neue Vorschläge" replaces them all. Tapping a card asks whether to
 * cook it; yes puts it on Geplant and hands it to [onPlanned].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsScreen(
    viewModel: SuggestionsViewModel,
    onBack: () -> Unit,
    onPlanned: (recipeId: Long, title: String) -> Unit,
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
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
        val list = cards ?: return@Scaffold
        if (list.isEmpty()) {
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
                items(list, key = { it.recipeId }) { card ->
                    SuggestionCardView(card, onClick = { confirmId = card.recipeId })
                }
            }
        }
    }

    val confirming = confirmId?.let { id -> cards?.firstOrNull { it.recipeId == id } }
    if (confirming != null) {
        AlertDialog(
            onDismissRequest = { confirmId = null },
            title = { Text(stringResource(R.string.suggest_confirm_title)) },
            text = { Text(confirming.snapshot.title) },
            confirmButton = {
                TextButton(onClick = {
                    confirmId = null
                    viewModel.plan(confirming) { onPlanned(confirming.recipeId, confirming.snapshot.title) }
                }) { Text(stringResource(R.string.action_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmId = null }) { Text(stringResource(R.string.action_no)) }
            },
        )
    }
}

@Composable
private fun SuggestionCardView(card: SuggestionCard, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(card.snapshot.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RecipeStatsRow(card.snapshot.stats)
                card.cookingTimeMinutes?.let { minutes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = stringResource(R.string.recipe_cooking_time),
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(stringResource(R.string.recipe_minutes, minutes), style = MaterialTheme.typography.bodySmall)
                    }
                }
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
