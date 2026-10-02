package com.example.plantry.data.usda

import com.example.plantry.data.Nutrition
import com.example.plantry.data.UnitWeight
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsdaCatalogTest {

    private val catalog = UsdaCatalog.parse(
        sequenceOf(
            "1\tChickpeas (garbanzo beans), mature seeds, raw\t378\t20.47\t62.95\t10.7\t6.04\t12.2\tcup=200|tbsp=12.5",
            "2\tChickpeas (garbanzo beans), mature seeds, canned, drained solids\t139\t7.05\t22.53\t\t2.77\t6.4\t",
            "3\tHummus, commercial\t166\t7.9\t14.29\t0.27\t9.6\t6\t",
            "4\tSweet potato, raw, unprepared\t86\t1.57\t20.12\t4.18\t0.05\t3\tmedium (5\" long)=130",
            "5\tPotatoes, baked, flesh and skin\t93\t2.5\t21.15\t1.18\t0.13\t2.2\t",
            "",
        ),
    )

    private fun search(query: String) = catalog.search(query).map { it.fdcId }

    @Test
    fun parse_readsNutritionAndPortions() {
        val food = catalog.search("chickpeas raw").single()

        assertEquals(1L, food.fdcId)
        assertEquals(Nutrition(378.0, 20.47, 62.95, 10.7, 6.04, 12.2), food.nutrition)
        assertEquals(listOf(UnitWeight("cup", 200.0), UnitWeight("tbsp", 12.5)), food.portions)
    }

    @Test
    fun parse_missingValuesBecomeZeroAndNoPortions() {
        val food = catalog.search("canned").single()

        assertEquals(0.0, food.nutrition.sugar, 0.0)
        assertEquals(emptyList<UnitWeight>(), food.portions)
    }

    @Test
    fun search_matchesWordPrefixesInAnyOrderIgnoringCase() {
        assertEquals(listOf(1L, 2L), search("CHICK"))
        assertEquals(listOf(1L), search("raw chick"))
        assertEquals(listOf(4L), search("sweet pot"))
    }

    @Test
    fun search_rejectsInfixMatches() {
        assertEquals(emptyList<Long>(), search("peas"))
    }

    @Test
    fun search_ranksDescriptionsStartingWithFirstTermFirst() {
        assertEquals(listOf(5L, 4L), search("potato"))
    }

    @Test
    fun search_ranksShorterDescriptionsFirst() {
        assertEquals(listOf(1L, 2L), search("mature"))
    }

    @Test
    fun search_blankQueryReturnsNothing() {
        assertEquals(emptyList<Long>(), search("  ,"))
    }

    @Test
    fun search_respectsLimit() {
        assertEquals(1, catalog.search("chickpeas", limit = 1).size)
    }

    @Test
    fun candidates_mergeTermHitsInOrderWithoutDuplicatesUpToLimit() {
        assertEquals(listOf(2L, 1L, 3L), catalog.candidates(listOf("chickpeas canned", "chickpeas", "hummus")).map { it.fdcId })
        assertEquals(listOf(2L, 1L), catalog.candidates(listOf("chickpeas canned", "chickpeas", "hummus"), limit = 2).map { it.fdcId })
        assertEquals(listOf(1L), catalog.candidates(listOf("chickpeas"), perTerm = 1).map { it.fdcId })
        assertEquals(emptyList<Long>(), catalog.candidates(emptyList()).map { it.fdcId })
    }

    @Test
    fun bundledAsset_parsesAndIsSearchable() {
        val asset = File("src/main/assets/${UsdaCatalog.ASSET_NAME}")
        val bundled = asset.bufferedReader().useLines(UsdaCatalog::parse)

        val chickpeas = bundled.search("chickpeas raw")
        assertTrue(chickpeas.any { it.fdcId == 173756L })
        assertTrue(bundled.search("rice white long cooked").isNotEmpty())
    }
}
