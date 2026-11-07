package com.example.plantry.data

import com.example.plantry.data.planner.WeekPlants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** The Zutaten as one loaded snapshot: the list, by id, and what each Mix contains. */
class IngredientBook(val ingredients: List<Ingredient>, val contents: MixContents = emptyMap()) {
    val byId: Map<Long, Ingredient> by lazy { ingredients.associateBy { it.id } }

    companion object {
        fun observe(dao: IngredientDao): Flow<IngredientBook> =
            combine(dao.observeAll(), dao.observeMixComponents()) { all, components -> IngredientBook(all, Mixes.contents(components)) }

        suspend fun load(dao: IngredientDao): IngredientBook = observe(dao).first()
    }
}

/**
 * Everything a recipe shows or is judged by, computed from one recipe, its lines and one
 * [IngredientBook]: stats, nutrition, price, snapshot and the plant points it would add to a week.
 * Lines of other recipes are ignored; lines whose ingredient is missing are skipped.
 */
class RecipeFacts private constructor(val recipe: Recipe, allLines: List<RecipeLine>, private val book: IngredientBook) {
    /** The recipe's lines in recipe order. */
    val lines: List<RecipeLine> = allLines.filter { it.recipeId == recipe.id }.sortedBy { it.position }

    val stats: RecipeStats by lazy { RecipeStats.of(recipe, this.lines, book.byId, book.contents) }

    val nutrition: RecipeNutrition by lazy { RecipeNutrition.calculate(nutritionLines(lines, book.byId), recipe.servings) }

    val snapshot: RecipeSnapshot by lazy { RecipeSnapshot.of(recipe, lines, book.byId, book.contents) }

    fun price(today: LocalDate): RecipePrice = RecipePrice.calculate(priceLines(lines, book.byId), recipe.servings, today)

    /** The plant points the recipe would add to [week], see [WeekPlants.newPoints]. */
    fun newPlantPoints(week: WeekPlants): Double = week.newPoints(lines.map { it.ingredientId })

    companion object {
        fun of(recipe: Recipe, lines: List<RecipeLine>, book: IngredientBook) = RecipeFacts(recipe, lines, book)

        /** The facts of each of [recipes], in order; [lines] may hold all recipes' lines. */
        fun all(recipes: List<Recipe>, lines: List<RecipeLine>, book: IngredientBook): List<RecipeFacts> {
            val byRecipe = lines.groupBy { it.recipeId }
            return recipes.map { of(it, byRecipe[it.id].orEmpty(), book) }
        }
    }
}
