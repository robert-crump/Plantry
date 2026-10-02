package com.example.plantry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class ShoppingListRepository(
    private val tickDao: ShoppingTickDao,
    private val weekPlanRepository: WeekPlanRepository,
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
) {

    /** The shopping list of the menu of [weekStart]; updates on menu, recipe, ingredient or tick changes. */
    fun observe(weekStart: LocalDate): Flow<ShoppingList> = combine(
        weekPlanRepository.observeWeek(weekStart),
        recipeRepository.observeRecipes(),
        recipeRepository.observeAllLines(),
        ingredientRepository.observeIngredients(),
        tickDao.observeTicked(weekStart).map { it.toSet() },
    ) { plan, recipes, lines, ingredients, ticked ->
        val menu = recipes.filter { it.id in plan.recipeIds }
        ShoppingList.of(menu, lines, ingredients.associateBy { it.id }, ticked)
    }

    suspend fun setTicked(weekStart: LocalDate, ingredientId: Long, ticked: Boolean) {
        if (ticked) tickDao.insert(ShoppingTick(weekStart, ingredientId)) else tickDao.delete(weekStart, ingredientId)
    }
}
