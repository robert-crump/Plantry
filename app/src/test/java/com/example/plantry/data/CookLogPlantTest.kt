package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CookLogPlantTest {

    private val ingredients = listOf(
        ingredient(1, "Zwiebel", PlantPoints.ONE),
        ingredient(2, "Paprika", PlantPoints.ONE),
        ingredient(3, "Kreuzkümmel", PlantPoints.QUARTER),
        ingredient(4, "Salz", PlantPoints.ZERO),
        // A mix of 2 and 3; its own point is ignored.
        ingredient(5, "Gewürzmischung", PlantPoints.ONE),
    ).associateBy { it.id }
    private val contents = Mixes.contents(listOf(MixComponent(5, 2), MixComponent(5, 3)))

    @Test
    fun of_resolvesMixesOnceEach_andSkipsPlantsWithoutPointsAndMissingIngredients() {
        val plants = CookLogPlant.of(listOf(5, 1, 2, 4, 99), ingredients, contents)

        assertEquals(
            listOf(
                CookLogPlant(ingredientId = 1, name = "Zwiebel", plantPoints = 1.0),
                CookLogPlant(ingredientId = 2, name = "Paprika", plantPoints = 1.0),
                CookLogPlant(ingredientId = 3, name = "Kreuzkümmel", plantPoints = 0.25),
            ),
            plants,
        )
    }

    @Test
    fun of_addsUpToTheRecipesPlantPoints() {
        val ids = listOf(5L, 1L, 4L)

        assertEquals(Mixes.plantPoints(ids, ingredients, contents), distinctPlantPoints(CookLogPlant.of(ids, ingredients, contents)), 1e-9)
    }

    @Test
    fun distinct_matchesByIngredient_thenByNameOnceTheIngredientIsGone() {
        val plants = listOf(
            CookLogPlant(ingredientId = 1, name = "Zwiebel", plantPoints = 1.0),
            // Renamed since; still the same ingredient.
            CookLogPlant(ingredientId = 1, name = "Zwiebel, rot", plantPoints = 1.0),
            CookLogPlant(ingredientId = null, name = "Lauch", plantPoints = 1.0),
            CookLogPlant(ingredientId = null, name = "Lauch", plantPoints = 1.0),
        )

        assertEquals(2.0, distinctPlantPoints(plants), 1e-9)
    }

    private fun ingredient(id: Long, name: String, points: PlantPoints) = Ingredient(
        id = id,
        name = name,
        fdcId = null,
        usdaDescription = null,
        nutrition = Nutrition(),
        storeSection = StoreSection.OTHER,
        plantPoints = points,
    )
}
