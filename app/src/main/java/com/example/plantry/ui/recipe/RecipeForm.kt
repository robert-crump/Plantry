package com.example.plantry.ui.recipe

import com.example.plantry.data.BookPage
import com.example.plantry.data.BookSession
import com.example.plantry.data.CookingSteps
import com.example.plantry.data.Dish
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientAliases
import com.example.plantry.data.Nutrition
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeDraft
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.LineUnit
import com.example.plantry.data.RecipeLine
import com.example.plantry.data.RecipeIngredientDraft
import com.example.plantry.data.StoreSection
import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.claude.NewFood
import com.example.plantry.data.claude.NutritionSource
import com.example.plantry.data.claude.ProposalParser
import com.example.plantry.data.claude.ScannedRecipe
import com.example.plantry.data.toDraft
import com.example.plantry.data.usda.UsdaFood

/**
 * Raw text input of the recipe edit screen; every [Dish] has the same fields. Only title, servings
 * and at least one line are required.
 */
data class RecipeForm(
    val dish: Dish = Dish.MAIN,
    val title: String = "",
    val source: String = "",
    val page: String = "",
    val servings: String = "",
    val activeTime: String = "",
    val totalTime: String = "",
    /** Ingredient lines are validated in the line editor, so they are kept parsed. */
    val lines: List<RecipeFormLine> = emptyList(),
    /** The Zubereitung as typed, one step per line; see [CookingSteps.parse]. */
    val steps: String = "",
    /**
     * Ingredients Claude proposed, by temporary negative id; lines refer to them by that id. Those
     * a line uses are created on save.
     */
    val newIngredients: Map<Long, NewIngredient> = emptyMap(),
) {
    /** Nothing entered yet, so a scan replaces nothing. */
    val isEmpty: Boolean get() = this == RecipeForm()

    /** A snack without servings starts at 1, e.g. one bar; 20 rice cakes are typed in. */
    fun withDish(value: Dish) = copy(
        dish = value,
        servings = if (value == Dish.SNACK && servings.isBlank()) "1" else servings,
    )

    /** Replaces the line at [index], or appends [line] when [index] is null. */
    fun withLine(index: Int?, line: RecipeIngredientDraft) = withLine(index, RecipeFormLine.from(line))

    fun withLine(index: Int?, line: RecipeFormLine) =
        copy(lines = if (index == null) lines + line else lines.toMutableList().also { it[index] = line })

    /**
     * Takes what Claude read, with source and page from [book] (see [BookSession.defaults]); by
     * default the source is kept, since a page photo rarely shows the book. A line whose food name
     * is a learned alias (see [IngredientAliases]) gets that ingredient, whatever Claude matched.
     * Each line comes in with Claude's amount and unit; one whose ingredient lacks the unit (by
     * [units], by ingredient id) is marked [RecipeFormLine.missingUnit].
     */
    fun withScan(
        scan: ScannedRecipe,
        book: BookPage = BookPage(source, scan.page),
        aliases: Map<String, Long> = emptyMap(),
        units: Map<Long, List<IngredientUnit>> = emptyMap(),
    ) = RecipeForm(
        title = scan.title,
        source = book.source,
        page = book.page?.toString().orEmpty(),
        servings = scan.servings?.toString().orEmpty(),
        activeTime = scan.activeTimeMinutes?.toString().orEmpty(),
        totalTime = scan.totalTimeMinutes?.toString().orEmpty(),
        lines = scan.lines.map {
            val aliased = IngredientAliases.match(it.ingredientName, aliases)
            val ingredientId = aliased ?: it.ingredientId
            RecipeFormLine(
                amount = it.amount,
                unit = it.unit,
                ingredientId = ingredientId,
                wording = it.wording,
                ingredientName = it.ingredientName,
                uncertain = it.uncertain,
                searchTerms = if (aliased != null) emptyList() else it.searchTerms,
                unitGrams = it.unitGrams,
                missingUnit = ingredientId != null && it.unit.lackedBy(units[ingredientId].orEmpty()),
            )
        },
    )

    /**
     * The foods of lines without an ingredient, one per distinct name (several lines may use the
     * same food), each with a fresh id from [newId].
     */
    fun unmatchedFoods(newId: () -> Long): List<NewFood> = lines
        .filter { it.ingredientId == null && it.ingredientName.isNotBlank() }
        .groupBy { it.ingredientName.normalizedName() }
        .values
        .map { group ->
            NewFood(
                id = newId(),
                name = group.first().ingredientName.trim(),
                originalText = group.first().wording.orEmpty(),
                searchTerms = ProposalParser.cleanTerms(group.flatMap { it.searchTerms }),
            )
        }

    /**
     * Adds the proposals for [foods] and assigns them to the lines still without an ingredient; a
     * line whose unit the proposal lacks is marked [RecipeFormLine.missingUnit].
     */
    fun withProposals(foods: List<NewFood>, proposals: Map<Long, IngredientProposal>): RecipeForm {
        val idsByName = foods.filter { it.id in proposals }.associate { it.name.normalizedName() to it.id }
        return copy(
            lines = lines.map { line ->
                if (line.ingredientId != null) return@map line
                val id = idsByName[line.ingredientName.normalizedName()] ?: return@map line
                line.copy(ingredientId = id, missingUnit = line.unit.lackedBy(proposals.getValue(id).units(id)))
            },
            newIngredients = newIngredients + proposals.mapValues { NewIngredient(it.value) },
        )
    }

    /** The lines in [unit]'s ingredient and unit no longer miss it. */
    fun withUnitDefined(unit: IngredientUnit) = copy(
        lines = lines.map {
            if (it.ingredientId == unit.ingredientId && it.unit == LineUnit.Of(unit.unit)) it.copy(missingUnit = false) else it
        },
    )

    fun withNewIngredient(id: Long, proposal: IngredientProposal) =
        copy(newIngredients = newIngredients + (id to NewIngredient(proposal)))

    /** Gives new ingredient [unit]'s ingredient id the unit, created with it on save. */
    fun withNewIngredientUnit(unit: IngredientUnit) =
        updateNewIngredient(unit.ingredientId) { it.copy(proposal = it.proposal.withUnit(unit)) }

    /** Confirms the proposed nutrition source; without one there is nothing to confirm. */
    fun confirmNewIngredient(id: Long) =
        updateNewIngredient(id) { if (it.proposal.source == null) it else it.copy(confirmed = true) }

    /** The user picked [food] instead of the proposed entry, which also confirms it. */
    fun withNewIngredientFood(id: Long, food: UsdaFood) = withNewIngredientSource(id, NutritionSource.Usda(food))

    /** The user typed in the package's values per 100 g instead of a USDA entry, which also confirms it. */
    fun withNewIngredientLabel(id: Long, nutrition: Nutrition) = withNewIngredientSource(id, NutritionSource.Label(nutrition))

    private fun withNewIngredientSource(id: Long, source: NutritionSource) =
        updateNewIngredient(id) { NewIngredient(it.proposal.copy(source = source), confirmed = true) }

    private fun updateNewIngredient(id: Long, transform: (NewIngredient) -> NewIngredient): RecipeForm {
        val existing = newIngredients[id] ?: return this
        return copy(newIngredients = newIngredients + (id to transform(existing)))
    }

    /** Whether [ingredientId] is a stored ingredient or a new one whose nutrition source is confirmed. */
    fun isReady(ingredientId: Long?): Boolean = when {
        ingredientId == null -> false
        ingredientId > 0 -> true
        else -> newIngredients[ingredientId]?.ready == true
    }

    /** What still needs the user's attention on [line], or null once it is resolved (✓). */
    fun problem(line: RecipeFormLine): LineProblem? = when {
        line.ingredientId == null -> LineProblem.NO_INGREDIENT
        !isReady(line.ingredientId) -> LineProblem.NEW_INGREDIENT
        line.amount <= 0.0 -> LineProblem.NO_GRAMS
        line.missingUnit -> LineProblem.MISSING_UNIT
        line.uncertain -> LineProblem.UNCERTAIN
        else -> null
    }

    /**
     * The checklist as the edit screen shows it: the lines without an ingredient on top, then the
     * others by store section, each group in recipe order. [sections] maps stored ingredient ids to
     * their section, new ingredients have theirs from the proposal; an unknown one counts as OTHER.
     */
    fun checklistGroups(sections: Map<Long, StoreSection>): List<ChecklistGroup> {
        val (unmatched, matched) = lines.indices.partition { lines[it].ingredientId == null }
        val bySection = matched.groupBy { index ->
            val id = lines[index].ingredientId!!
            sections[id] ?: newIngredients[id]?.proposal?.storeSection ?: StoreSection.OTHER
        }
        val groups = bySection.entries.sortedBy { it.key }.map { ChecklistGroup(it.key, it.value) }
        return if (unmatched.isEmpty()) groups else listOf(ChecklistGroup(null, unmatched)) + groups
    }

    /** Line indices in the order the checklist shows them, see [checklistGroups]. */
    fun checklistOrder(sections: Map<Long, StoreSection>): List<Int> = checklistGroups(sections).flatMap { it.lines }

    /**
     * The problem line to edit after the one at [index]: the next one in [order] (see
     * [checklistOrder]), wrapping around, or null when no other problem is left.
     */
    fun nextProblem(index: Int, order: List<Int>): Int? {
        val after = order.indexOf(index) + 1
        return (order.drop(after) + order.take(after)).firstOrNull { it != index && problem(lines[it]) != null }
    }

    /**
     * Moves the line at [from] to [to], the index of the line it is dropped next to: after it when
     * moving down, before it when moving up. Every other line keeps its order.
     */
    fun moveLine(from: Int, to: Int) = copy(lines = lines.toMutableList().apply { add(to, removeAt(from)) })

    /** New ingredients that have nutrition, with their temporary id, for previews and suggestions. */
    fun previewIngredients(): List<Ingredient> = newIngredients
        .filterValues { it.proposal.source != null }
        .map { (id, new) -> new.proposal.toIngredient(id) }

    /** The units of the new ingredients, by temporary id. */
    fun newIngredientUnits(): Map<Long, List<IngredientUnit>> = newIngredients.mapValues { (id, new) -> new.proposal.units(id) }

    /** The proposals to create on save: those a line uses. */
    fun newIngredientsToCreate(): Map<Long, IngredientProposal> {
        val used = lines.mapNotNull { it.ingredientId }.toSet()
        return newIngredients.filterKeys { it in used }.mapValues { it.value.proposal }
    }

    /**
     * The wordings the user confirmed, to remember as aliases: lines applied in the line editor and
     * lines using a new ingredient (whose nutrition source had to be confirmed), with the new ingredients'
     * temporary ids replaced by their [createdIds].
     */
    fun aliasesToLearn(createdIds: Map<Long, Long> = emptyMap()): List<Pair<String, Long>> = lines.mapNotNull { line ->
        val id = line.ingredientId ?: return@mapNotNull null
        if (!line.confirmed && id > 0) return@mapNotNull null
        val ingredientId = if (id > 0) id else createdIds[id] ?: return@mapNotNull null
        line.ingredientName.takeIf { it.isNotBlank() }?.let { it to ingredientId }
    }

    /** The lines that are complete enough for nutrition. */
    fun completeLines(): List<RecipeIngredientDraft> = lines.mapNotNull { it.toDraft() }

    fun removeLine(index: Int) = copy(lines = lines.filterIndexed { i, _ -> i != index })

    /** The servings nutrition is divided by, or null while not a positive number. */
    fun effectiveServings(): Int? = servings.toPositiveIntOrNull()

    fun errors() = RecipeFormErrors(
        title = title.isBlank(),
        page = page.isNotBlank() && page.toPositiveIntOrNull() == null,
        servings = servings.toPositiveIntOrNull() == null,
        activeTime = activeTime.isNotBlank() && activeTime.toPositiveIntOrNull() == null || activeExceedsTotal(),
        totalTime = totalTime.isNotBlank() && totalTime.toPositiveIntOrNull() == null,
        lines = lines.any { problem(it) != null },
        noLines = lines.isEmpty(),
    )

    /** The hands-on time can't be longer than the time until the food is ready. */
    private fun activeExceedsTotal(): Boolean {
        val active = activeTime.toPositiveIntOrNull() ?: return false
        val total = totalTime.toPositiveIntOrNull() ?: return false
        return active > total
    }

    /** Returns the validated draft, or null if any field is invalid. */
    fun toDraft(): RecipeDraft? {
        if (errors().hasAny) return null
        return RecipeDraft(
            title = title,
            source = source,
            page = page.toPositiveIntOrNull(),
            servings = servings.toPositiveIntOrNull()!!,
            totalTimeMinutes = totalTime.toPositiveIntOrNull(),
            lines = completeLines(),
            dish = dish,
            activeTimeMinutes = activeTime.toPositiveIntOrNull(),
            steps = CookingSteps.parse(steps),
        )
    }

    companion object {
        fun from(recipe: Recipe, lines: List<RecipeLine>) = RecipeForm(
            title = recipe.title,
            source = recipe.source,
            page = recipe.page?.toString().orEmpty(),
            servings = recipe.servings.toString(),
            activeTime = recipe.activeTimeMinutes?.toString().orEmpty(),
            totalTime = recipe.totalTimeMinutes?.toString().orEmpty(),
            dish = recipe.dish,
            lines = lines.map { RecipeFormLine.from(it.toDraft()) },
            steps = CookingSteps.text(recipe.steps),
        )
    }
}

data class RecipeFormErrors(
    val title: Boolean,
    val page: Boolean,
    val servings: Boolean,
    /** Not a number, or longer than the total time. */
    val activeTime: Boolean,
    val totalTime: Boolean,
    /** Some line is still a problem, see [RecipeForm.problem]. */
    val lines: Boolean = false,
    /** A recipe needs at least one ingredient line. */
    val noLines: Boolean = false,
) {
    /** Some recipe field (all but the lines) is invalid. */
    val fields: Boolean get() = title || page || servings || activeTime || totalTime

    val hasAny: Boolean get() = fields || lines || noLines
}

/** An ingredient line as edited; a scanned line may still lack an ingredient or an amount. */
data class RecipeFormLine(
    val amount: Double,
    val unit: LineUnit,
    val ingredientId: Long?,
    /** The line as the book words it; null shows the generated text. */
    val wording: String? = null,
    /** Claude's German name for the food, the search term while [ingredientId] is null. */
    val ingredientName: String = "",
    /** Claude was unsure about this line; cleared once the user edits it. */
    val uncertain: Boolean = false,
    /** English USDA search terms from the scan, while [ingredientId] is null. */
    val searchTerms: List<String> = emptyList(),
    /** The user applied the line in the editor, confirming [ingredientName] means [ingredientId]. */
    val confirmed: Boolean = false,
    /** Claude's grams for one [unit], pre-filled in the line editor while the ingredient lacks the unit. */
    val unitGrams: Double? = null,
    /** The ingredient lacks [unit], so its grams must be confirmed in the line editor. */
    val missingUnit: Boolean = false,
) {
    val complete: Boolean get() = ingredientId != null && amount > 0.0

    fun toDraft(): RecipeIngredientDraft? =
        if (complete) RecipeIngredientDraft(amount, unit, ingredientId!!, wording) else null

    companion object {
        fun from(line: RecipeIngredientDraft) = RecipeFormLine(line.amount, line.unit, line.ingredientId, line.wording)
    }
}

/** Line indices of the checklist under one store [section]; null for the lines without an ingredient. */
data class ChecklistGroup(val section: StoreSection?, val lines: List<Int>)

/** Why a line needs attention before the recipe can be saved, most pressing first. */
enum class LineProblem {
    NO_INGREDIENT,

    /** A new ingredient whose nutrition source is not confirmed yet. */
    NEW_INGREDIENT,
    NO_GRAMS,

    /** The ingredient lacks the line's unit; applying the line in the editor defines it. */
    MISSING_UNIT,

    /** Claude was unsure about the line; applying it in the editor confirms it. */
    UNCERTAIN,
}

/** A new ingredient Claude proposed; its nutrition source must be confirmed before saving. */
data class NewIngredient(val proposal: IngredientProposal, val confirmed: Boolean = false) {
    val ready: Boolean get() = confirmed && proposal.source != null
}

/** Replaces the temporary ids of new ingredients with the ids they were created with. */
fun RecipeDraft.withIngredientIds(ids: Map<Long, Long>) =
    copy(lines = lines.map { line -> ids[line.ingredientId]?.let { line.copy(ingredientId = it) } ?: line })

private fun String.normalizedName() = trim().lowercase()

/** A piece unit none of [units] is in; g and ml every ingredient has. */
private fun LineUnit.lackedBy(units: List<IngredientUnit>) = this is LineUnit.Of && units.none { it.unit == unit }

private fun String.toPositiveIntOrNull(): Int? = trim().toIntOrNull()?.takeIf { it > 0 }
