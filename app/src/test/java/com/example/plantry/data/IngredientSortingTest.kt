package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class IngredientSortingTest {

    private val garlic = ingredient(1, "Knoblauch").copy(plantPoints = PlantPoints.QUARTER, storeSection = StoreSection.PRODUCE)
    private val lentils = ingredient(2, "Linsen").copy(plantPoints = PlantPoints.ONE, storeSection = StoreSection.DRY_GOODS, reviewed = false)
    private val oil = ingredient(3, "Olivenöl").copy(plantPoints = PlantPoints.ZERO, storeSection = StoreSection.DRY_GOODS)
    private val spinach = ingredient(4, "Spinat").copy(plantPoints = PlantPoints.ONE, storeSection = StoreSection.FROZEN, reviewed = false)
    private val all = listOf(garlic, lentils, oil, spinach)

    private fun List<SortGroupItems>.names() = associate { it.group to it.ingredients.map(Ingredient::name) }

    @Test
    fun plantPoints_groupsOneQuarterZeroInOrder() {
        val groups = IngredientSorting.group(all, SortView.PLANT_POINTS, onlyUnreviewed = false)

        assertEquals(
            listOf(PlantPoints.ONE, PlantPoints.QUARTER, PlantPoints.ZERO).map { SortGroup.Points(it) },
            groups.map { it.group },
        )
        assertEquals(
            mapOf(
                SortGroup.Points(PlantPoints.ONE) to listOf("Linsen", "Spinat"),
                SortGroup.Points(PlantPoints.QUARTER) to listOf("Knoblauch"),
                SortGroup.Points(PlantPoints.ZERO) to listOf("Olivenöl"),
            ),
            groups.names(),
        )
    }

    @Test
    fun storeSections_allFiveInShoppingOrderEmptyOnesIncluded() {
        val groups = IngredientSorting.group(all, SortView.STORE_SECTION, onlyUnreviewed = false)

        assertEquals(StoreSection.entries.map { SortGroup.Section(it) }, groups.map { it.group })
        assertEquals(listOf("Linsen", "Olivenöl"), groups.names()[SortGroup.Section(StoreSection.DRY_GOODS)])
        assertTrue(groups.names().getValue(SortGroup.Section(StoreSection.DAIRY_CHILLED)).isEmpty())
    }

    @Test
    fun onlyUnreviewed_leavesReviewedOut() {
        val groups = IngredientSorting.group(all, SortView.STORE_SECTION, onlyUnreviewed = true)

        assertEquals(listOf("Linsen", "Spinat"), groups.flatMap { group -> group.ingredients.map { it.name } })
    }

    @Test
    fun move_setsTheGroupValueOnSelectedOnlyAndKeepsReviewed() {
        val moved = IngredientSorting.move(all, setOf(1, 2), SortGroup.Section(StoreSection.OTHER))

        assertEquals(
            listOf(
                garlic.copy(storeSection = StoreSection.OTHER),
                lentils.copy(storeSection = StoreSection.OTHER),
            ),
            moved,
        )
    }

    @Test
    fun move_skipsIngredientsAlreadyInTheGroup() {
        val moved = IngredientSorting.move(all, setOf(2, 3), SortGroup.Points(PlantPoints.ONE))

        assertEquals(listOf(oil.copy(plantPoints = PlantPoints.ONE)), moved)
    }

    @Test
    fun sortedByName_groupsUmlautsWithTheirBaseLetterAndIgnoresCase() {
        val names = listOf("Zwiebel", "Äpfel", "apfelessig", "Birne", "Öl", "Oliven")
            .mapIndexed { i, name -> ingredient(i.toLong(), name) }

        val sorted = IngredientSorting.sortedByName(names, Locale.GERMANY).map(Ingredient::name)

        assertEquals(listOf("Äpfel", "apfelessig", "Birne", "Öl", "Oliven", "Zwiebel"), sorted)
    }
}
