package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MixesTest {

    // 1 Himbeeren, 2 Erdbeeren, 3 Heidelbeeren, 4 Zitrone; 10 Beerenmischung (1, 2, 3), 11 Rote Grütze (1, 2), 12 Zitronensaft (4).
    private val ingredients = listOf(
        ingredient(1, PlantPoints.ONE),
        ingredient(2, PlantPoints.ONE),
        ingredient(3, PlantPoints.ONE),
        ingredient(4, PlantPoints.ONE),
        // A mix's own points are ignored while it has components.
        ingredient(10, PlantPoints.ONE),
        ingredient(11, PlantPoints.ZERO),
        ingredient(12, PlantPoints.QUARTER),
    ).associateBy { it.id }
    private val contents = Mixes.contents(
        listOf(MixComponent(10, 1), MixComponent(10, 2), MixComponent(10, 3), MixComponent(11, 1), MixComponent(11, 2), MixComponent(12, 4)),
    )

    @Test
    fun plants_resolveMixesToTheirComponents() {
        assertEquals(setOf(1L, 2L, 3L, 4L), Mixes.plants(listOf(10L, 4L), contents))
    }

    @Test
    fun plantPoints_ofAMix_isItsDistinctComponents() {
        assertEquals(3.0, Mixes.plantPoints(listOf(10L), ingredients, contents), 0.0)
        assertEquals(2.0, Mixes.plantPoints(listOf(11L), ingredients, contents), 0.0)
    }

    @Test
    fun plantPoints_countAPlantOnceAcrossPlainIngredientsAndOverlappingMixes() {
        // Himbeeren on their own, in both mixes; Zitrone and its juice.
        assertEquals(4.0, Mixes.plantPoints(listOf(1L, 10L, 11L, 4L, 12L), ingredients, contents), 0.0)
    }

    @Test
    fun plantPoints_ofAProcessedPlant_isThatPlant() {
        assertEquals(1.0, Mixes.plantPoints(listOf(12L), ingredients, contents), 0.0)
    }

    @Test
    fun plantPoints_withoutContents_useTheIngredientsOwnPoints() {
        assertEquals(1.25, Mixes.plantPoints(listOf(10L, 11L, 12L), ingredients, emptyMap()), 0.0)
    }

    @Test
    fun canContain_neitherItselfNorAMix() {
        assertTrue(Mixes.canContain(10, 4, contents))
        assertFalse(Mixes.canContain(10, 10, contents))
        assertFalse(Mixes.canContain(10, 11, contents))
        assertFalse(Mixes.canContain(null, 11, contents))
        assertTrue(Mixes.canContain(null, 1, contents))
    }

    @Test
    fun canBeMix_notWhileItIsAComponent() {
        assertFalse(Mixes.canBeMix(1, contents))
        assertTrue(Mixes.canBeMix(10, contents))
        assertTrue(Mixes.canBeMix(99, contents))
        assertTrue(Mixes.canBeMix(null, contents))
    }

    private fun ingredient(id: Long, points: PlantPoints) = Ingredient(
        id = id,
        name = "Zutat $id",
        fdcId = null,
        usdaDescription = null,
        nutrition = Nutrition(),
        storeSection = StoreSection.OTHER,
        plantPoints = points,
    )
}
