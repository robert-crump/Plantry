package com.example.plantry.data

import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.claude.NutritionSource
import com.example.plantry.data.claude.ProposedBuyAs
import com.example.plantry.data.usda.UsdaFood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientRepositoryTest {

    private val dao = FakeIngredientDao()
    private val repository = IngredientRepository(dao)

    private val riceCooked = UsdaFood(
        fdcId = 168878,
        description = "Rice, white, long-grain, regular, enriched, cooked",
        nutrition = Nutrition(130.0, 2.69, 28.17, 0.05, 0.28, 0.4),
        portions = listOf(UnitWeight("cup", 158.0)),
    )
    private val riceDry = riceCooked.copy(fdcId = 168877, description = "Rice, white, long-grain, raw")

    private fun draft(ingredient: Ingredient, buyAs: Long? = null, factor: Double? = null) = IngredientDraft(
        name = " ${ingredient.name} ",
        nutrition = ingredient.nutrition.copy(protein = 3.0),
        unitWeights = ingredient.unitWeights,
        buyUnit = BuyUnit.PACK,
        packSizeGrams = 500.0,
        storeSection = StoreSection.DRY_GOODS,
        staple = true,
        plantPoints = PlantPoints.QUARTER,
        buyAsIngredientId = buyAs,
        buyAsYieldFactor = factor,
    )

    @Test
    fun createFromUsda_copiesUsdaDataAndIsUnreviewed() = runTest {
        val id = repository.createFromUsda(riceCooked, " Reis, gekocht ")

        val ingredient = repository.getIngredient(id)!!
        assertEquals("Reis, gekocht", ingredient.name)
        assertEquals(168878L, ingredient.fdcId)
        assertEquals(riceCooked.description, ingredient.usdaDescription)
        assertEquals(riceCooked.nutrition, ingredient.nutrition)
        assertEquals(riceCooked.portions, ingredient.unitWeights)
        assertFalse(ingredient.reviewed)
    }

    @Test
    fun createFromLabel_hasNoUsdaReferenceAndIsUnreviewed() = runTest {
        val label = Nutrition(52.0, 1.0, 9.0, 4.0, 1.5, 0.0)

        val ingredient = repository.getIngredient(repository.createFromLabel(" Haferdrink ", label))!!

        assertEquals("Haferdrink", ingredient.name)
        assertNull(ingredient.fdcId)
        assertNull(ingredient.usdaDescription)
        assertEquals(label, ingredient.nutrition)
        assertTrue(ingredient.unitWeights.isEmpty())
        assertEquals(BuyUnit.GRAMS, ingredient.buyUnit)
        assertFalse(ingredient.reviewed)
    }

    @Test
    fun createProposed_withLabelNutrition_hasNoUsdaReference() = runTest {
        val label = Nutrition(52.0, 1.0, 9.0, 4.0, 1.5, 0.0)
        val oatDrink = proposal(riceDry, "Haferdrink").copy(source = NutritionSource.Label(label))

        val ingredient = repository.getIngredient(repository.createProposed(mapOf(-1L to oatDrink)).getValue(-1))!!

        assertNull(ingredient.fdcId)
        assertNull(ingredient.usdaDescription)
        assertEquals(label, ingredient.nutrition)
        assertEquals(BuyUnit.PACK, ingredient.buyUnit)
        assertFalse(ingredient.reviewed)
    }

    @Test
    fun update_savesEditsAndMarksReviewed() = runTest {
        val id = repository.createFromUsda(riceCooked, "Reis, gekocht")

        assertNull(repository.update(id, draft(repository.getIngredient(id)!!)))

        val updated = repository.getIngredient(id)!!
        assertTrue(updated.reviewed)
        assertEquals("Reis, gekocht", updated.name)
        assertEquals(3.0, updated.nutrition.protein, 0.0)
        assertEquals(BuyUnit.PACK, updated.buyUnit)
        assertEquals(500.0, updated.packSizeGrams)
        assertEquals(StoreSection.DRY_GOODS, updated.storeSection)
        assertTrue(updated.staple)
        assertEquals(PlantPoints.QUARTER, updated.plantPoints)
        assertEquals(168878L, updated.fdcId)
    }

    @Test
    fun update_savesBuyAsLink() = runTest {
        val cooked = repository.createFromUsda(riceCooked, "Reis, gekocht")
        val dry = repository.createFromUsda(riceDry, "Reis, trocken")

        assertNull(repository.update(cooked, draft(repository.getIngredient(cooked)!!, buyAs = dry, factor = 0.4)))

        val updated = repository.getIngredient(cooked)!!
        assertEquals(dry, updated.buyAsIngredientId)
        assertEquals(0.4, updated.buyAsYieldFactor)
    }

    @Test
    fun update_rejectsSelfLinkWithoutSaving() = runTest {
        val id = repository.createFromUsda(riceCooked, "Reis, gekocht")

        val error = repository.update(id, draft(repository.getIngredient(id)!!, buyAs = id, factor = 1.0))

        assertEquals(BuyAsError.SELF_LINK, error)
        assertFalse(repository.getIngredient(id)!!.reviewed)
    }

    @Test
    fun update_rejectsCycle() = runTest {
        val cooked = repository.createFromUsda(riceCooked, "Reis, gekocht")
        val dry = repository.createFromUsda(riceDry, "Reis, trocken")
        repository.update(cooked, draft(repository.getIngredient(cooked)!!, buyAs = dry, factor = 0.4))

        val error = repository.update(dry, draft(repository.getIngredient(dry)!!, buyAs = cooked, factor = 2.5))

        assertEquals(BuyAsError.CYCLE, error)
        assertNull(repository.getIngredient(dry)!!.buyAsIngredientId)
    }

    private fun proposal(food: UsdaFood, name: String, buyAs: ProposedBuyAs? = null) = IngredientProposal(
        name = name,
        source = NutritionSource.Usda(food),
        searchTerms = emptyList(),
        unitWeights = emptyList(),
        buyUnit = BuyUnit.PACK,
        packSizeGrams = 500.0,
        storeSection = StoreSection.DRY_GOODS,
        staple = true,
        plantPoints = PlantPoints.ZERO,
        buyAs = buyAs,
    )

    @Test
    fun createProposed_createsUnreviewedIngredientsAndLinksWithinTheBatch() = runTest {
        val ids = repository.createProposed(
            mapOf(
                -1L to proposal(riceCooked, "Reis, gekocht", ProposedBuyAs(-2, 0.4)),
                -2L to proposal(riceDry, "Reis"),
            ),
        )

        val cooked = repository.getIngredient(ids.getValue(-1))!!
        val dry = repository.getIngredient(ids.getValue(-2))!!
        assertFalse(cooked.reviewed)
        assertEquals("Reis, gekocht", cooked.name)
        assertEquals(BuyUnit.PACK, cooked.buyUnit)
        assertEquals(500.0, cooked.packSizeGrams)
        assertTrue(cooked.staple)
        assertEquals(dry.id, cooked.buyAsIngredientId)
        assertEquals(0.4, cooked.buyAsYieldFactor)
        assertNull(dry.buyAsIngredientId)
    }

    @Test
    fun createProposed_linksToExistingIngredient() = runTest {
        val dry = repository.createFromUsda(riceDry, "Reis")

        val ids = repository.createProposed(mapOf(-1L to proposal(riceCooked, "Reis, gekocht", ProposedBuyAs(dry, 0.4))))

        assertEquals(dry, repository.getIngredient(ids.getValue(-1))!!.buyAsIngredientId)
    }

    @Test
    fun createProposed_dropsLinksToUnknownOrUncreatedIngredientsAndCycles() = runTest {
        val ids = repository.createProposed(
            mapOf(
                -1L to proposal(riceCooked, "Reis, gekocht", ProposedBuyAs(-2, 0.4)),
                -2L to proposal(riceDry, "Reis", ProposedBuyAs(-1, 2.5)),
                -3L to proposal(riceDry, "Naturreis", ProposedBuyAs(99, 1.0)),
                -4L to proposal(riceDry, "Basmati", ProposedBuyAs(-9, 1.0)),
            ),
        )

        // The first link wins; the one closing the cycle is dropped.
        assertEquals(ids.getValue(-2), repository.getIngredient(ids.getValue(-1))!!.buyAsIngredientId)
        assertNull(repository.getIngredient(ids.getValue(-2))!!.buyAsIngredientId)
        assertNull(repository.getIngredient(ids.getValue(-3))!!.buyAsIngredientId)
        assertNull(repository.getIngredient(ids.getValue(-4))!!.buyAsIngredientId)
    }

    @Test
    fun learnAliases_normalizesAndReplacesAnEarlierIngredient() = runTest {
        repository.learnAliases(listOf("Kichererbsen, abgetropft" to 3L, "Reis" to 5L))
        repository.learnAliases(listOf("kichererbsen abgetropft" to 4L, "" to 6L))

        assertEquals(mapOf("kichererbsen abgetropft" to 4L, "reis" to 5L), repository.observeAliases().first())
    }

    @Test
    fun moveTo_savesTheGroupValueForEachSelectedIngredient() = runTest {
        val cooked = repository.createFromUsda(riceCooked, "Reis, gekocht")
        val dry = repository.createFromUsda(riceDry, "Reis")
        val other = repository.createFromUsda(riceDry, "Naturreis")

        repository.moveTo(setOf(cooked, dry), SortGroup.Section(StoreSection.DRY_GOODS))
        repository.moveTo(setOf(cooked), SortGroup.Points(PlantPoints.ONE))

        assertEquals(StoreSection.DRY_GOODS, repository.getIngredient(cooked)!!.storeSection)
        assertEquals(PlantPoints.ONE, repository.getIngredient(cooked)!!.plantPoints)
        assertEquals(StoreSection.DRY_GOODS, repository.getIngredient(dry)!!.storeSection)
        assertEquals(PlantPoints.ZERO, repository.getIngredient(dry)!!.plantPoints)
        assertEquals(StoreSection.OTHER, repository.getIngredient(other)!!.storeSection)
        assertFalse(repository.getIngredient(cooked)!!.reviewed)
    }

    @Test
    fun markReviewed_marksOnlyTheGivenIngredients() = runTest {
        val cooked = repository.createFromUsda(riceCooked, "Reis, gekocht")
        val dry = repository.createFromUsda(riceDry, "Reis")

        repository.markReviewed(listOf(cooked))

        assertTrue(repository.getIngredient(cooked)!!.reviewed)
        assertFalse(repository.getIngredient(dry)!!.reviewed)
    }

    @Test
    fun delete_removesAnUnusedIngredient() = runTest {
        val id = repository.createFromUsda(riceCooked, "Reis, gekocht")

        assertEquals(emptyList<String>(), repository.delete(id))
        assertNull(repository.getIngredient(id))
    }

    @Test
    fun delete_keepsAnIngredientUsedInRecipes() = runTest {
        val id = repository.createFromUsda(riceCooked, "Reis, gekocht")
        dao.recipeTitles[id] = listOf("Curry", "Risotto")

        assertEquals(listOf("Curry", "Risotto"), repository.delete(id))
        assertEquals("Reis, gekocht", repository.getIngredient(id)?.name)
    }
}

private class FakeIngredientDao : IngredientDao {
    private val ingredients = MutableStateFlow<Map<Long, Ingredient>>(emptyMap())
    private val aliases = MutableStateFlow<Map<String, Long>>(emptyMap())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Ingredient>> =
        ingredients.map { all -> all.values.sortedBy { it.name.lowercase() } }

    override fun observeById(id: Long): Flow<Ingredient?> = ingredients.map { it[id] }

    override suspend fun getById(id: Long): Ingredient? = ingredients.value[id]

    override suspend fun getBuyAsLinks(): List<BuyAsLink> =
        ingredients.value.values.map { BuyAsLink(it.id, it.buyAsIngredientId) }

    override suspend fun insert(ingredient: Ingredient): Long {
        val id = nextId++
        ingredients.value += id to ingredient.copy(id = id)
        return id
    }

    override suspend fun getByIds(ids: List<Long>): List<Ingredient> = ids.mapNotNull { ingredients.value[it] }

    override suspend fun update(ingredient: Ingredient) {
        if (ingredient.id in ingredients.value) ingredients.value += ingredient.id to ingredient
    }

    override suspend fun updateAll(ingredients: List<Ingredient>) = ingredients.forEach { update(it) }

    override suspend fun markReviewed(ids: List<Long>) {
        ingredients.value = ingredients.value.mapValues { (id, it) -> if (id in ids) it.copy(reviewed = true) else it }
    }

    /** Recipe titles by ingredient id. */
    val recipeTitles = mutableMapOf<Long, List<String>>()

    override suspend fun getRecipeTitlesUsing(id: Long): List<String> = recipeTitles[id].orEmpty()

    override suspend fun deleteById(id: Long) {
        ingredients.value -= id
    }

    override fun observeAliases(): Flow<List<IngredientAlias>> =
        aliases.map { all -> all.map { (wording, id) -> IngredientAlias(wording, id) } }

    override suspend fun upsertAliases(aliases: List<IngredientAlias>) {
        this.aliases.value += aliases.associate { it.wording to it.ingredientId }
    }
}
