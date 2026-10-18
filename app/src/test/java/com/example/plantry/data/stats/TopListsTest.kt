package com.example.plantry.data.stats

import com.example.plantry.data.CookLog
import com.example.plantry.data.CookLogPlant
import com.example.plantry.data.RecipeStats
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TopListsTest {

    private fun date(month: Int, day: Int) = LocalDate.of(2026, month, day)

    private val week = StatsPeriod(StatsRange.WEEK, date(10, 12))

    private var nextId = 1L

    private fun entry(on: LocalDate, recipeId: Long?, title: String) =
        CookLog(id = nextId++, recipeId = recipeId, cookedOn = on, title = title, stats = RecipeStats(0.0, 0.0, 0.0))

    private fun plant(entry: CookLog, ingredientId: Long?, name: String, points: Double = 1.0) =
        CookLogPlant(cookLogId = entry.id, ingredientId = ingredientId, name = name, plantPoints = points)

    @Test
    fun recipes_areCountedByIdInThePeriod_titledAsTheirNewestEntry() {
        val entries = listOf(
            entry(date(10, 12), 1, "Dal"),
            entry(date(10, 14), 1, "Linsen-Dal"),
            entry(date(10, 13), 2, "Pasta"),
            entry(date(10, 19), 2, "Pasta"),
            entry(date(10, 11), 2, "Pasta"),
        )

        assertEquals(
            listOf(TopRecipe(1, "Linsen-Dal", 2), TopRecipe(2, "Pasta", 1)),
            topRecipes(week, entries),
        )
    }

    @Test
    fun deletedRecipes_areGroupedByTitle_withoutId() {
        val entries = listOf(
            entry(date(10, 12), null, "Chili"),
            entry(date(10, 13), null, "Chili"),
            entry(date(10, 14), null, "Curry"),
            entry(date(10, 15), 3, "Chili"),
        )

        assertEquals(
            listOf(TopRecipe(null, "Chili", 2), TopRecipe(3, "Chili", 1), TopRecipe(null, "Curry", 1)),
            topRecipes(week, entries),
        )
    }

    @Test
    fun ties_goToTheMostRecent_thenByName() {
        val entries = listOf(
            entry(date(10, 12), 1, "Zucchini-Pfanne"),
            // The same day: the later logged one counts as more recent.
            entry(date(10, 14), 2, "Bohnen"),
            entry(date(10, 14), 3, "Auflauf"),
            entry(date(10, 14), 4, "Wok"),
            entry(date(10, 13), 5, "Ofengemüse"),
        )
        assertEquals(
            listOf("Wok", "Auflauf", "Bohnen", "Ofengemüse", "Zucchini-Pfanne"),
            topRecipes(week, entries).map { it.title },
        )
    }

    @Test
    fun ties_ofEqualRecency_goByName() {
        val dal = entry(date(10, 14), 1, "Dal")
        val plants = listOf(plant(dal, 10, "Zwiebel"), plant(dal, 11, "apfel"), plant(dal, 12, "Bohne"))

        assertEquals(listOf("apfel", "Bohne", "Zwiebel"), topPlants(week, listOf(dal), plants).map { it.name })
    }

    @Test
    fun onlyTheTopFive_areShown() {
        val entries = (1L..7L).map { entry(date(10, 12), it, "R$it") }

        assertEquals(5, topRecipes(week, entries).size)
    }

    @Test
    fun plants_areCountedByEntriesTheyAppearIn() {
        val monday = entry(date(10, 12), 1, "Dal")
        val tuesday = entry(date(10, 13), 2, "Curry")
        val nextWeek = entry(date(10, 19), 1, "Dal")
        val plants = listOf(
            plant(monday, 10, "Linsen"),
            plant(monday, 11, "Zwiebel"),
            plant(monday, 12, "Kreuzkümmel", 0.25),
            plant(tuesday, 11, "Zwiebel"),
            plant(tuesday, 12, "Kreuzkümmel", 0.25),
            // A mix and its component in one entry still count that entry once.
            plant(tuesday, 11, "Zwiebel"),
            plant(nextWeek, 10, "Linsen"),
        )

        assertEquals(
            listOf(TopPlant(12, "Kreuzkümmel", 0.25, 2), TopPlant(11, "Zwiebel", 1.0, 2), TopPlant(10, "Linsen", 1.0, 1)),
            topPlants(week, listOf(monday, tuesday, nextWeek), plants),
        )
    }

    @Test
    fun deletedIngredients_matchByName() {
        val monday = entry(date(10, 12), 1, "Dal")
        val tuesday = entry(date(10, 13), 2, "Curry")
        val plants = listOf(plant(monday, null, "Ingwer"), plant(tuesday, null, "Ingwer"), plant(tuesday, 20, "Ingwer"))

        assertEquals(
            listOf(TopPlant(null, "Ingwer", 1.0, 2), TopPlant(20, "Ingwer", 1.0, 1)),
            topPlants(week, listOf(monday, tuesday), plants),
        )
    }

    @Test
    fun aPlant_isNamedAsInItsNewestEntry_withItsHighestPoints() {
        val monday = entry(date(10, 12), 1, "Dal")
        val tuesday = entry(date(10, 13), 2, "Curry")
        val plants = listOf(plant(monday, 10, "Koriander", 1.0), plant(tuesday, 10, "Korianderblätter", 0.25))

        assertEquals(listOf(TopPlant(10, "Korianderblätter", 1.0, 2)), topPlants(week, listOf(monday, tuesday), plants))
    }

    @Test
    fun threeMonths_countTheDaysOfTheirWeeks() {
        // Aug–Okt 2026: 1 Aug belongs to the week of 27 Jul; the week of 26 Oct runs into November.
        val quarter = StatsPeriod(StatsRange.QUARTER, date(8, 1))
        val entries = listOf(entry(date(8, 1), 1, "Juli"), entry(date(8, 3), 2, "August"), entry(date(11, 1), 3, "November"))

        assertEquals(listOf("November", "August"), topRecipes(quarter, entries).map { it.title })
    }

    @Test
    fun anEmptyPeriod_hasNoRows() {
        val entries = listOf(entry(date(10, 5), 1, "Dal"))

        assertEquals(emptyList<TopRecipe>(), topRecipes(week, entries))
        assertEquals(emptyList<TopPlant>(), topPlants(week, entries, emptyList()))
    }
}
