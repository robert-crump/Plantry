package com.example.plantry.data

import com.example.plantry.data.usda.UsdaFood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
}

private class FakeIngredientDao : IngredientDao {
    private val ingredients = MutableStateFlow<Map<Long, Ingredient>>(emptyMap())
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

    override suspend fun update(ingredient: Ingredient) {
        if (ingredient.id in ingredients.value) ingredients.value += ingredient.id to ingredient
    }
}
