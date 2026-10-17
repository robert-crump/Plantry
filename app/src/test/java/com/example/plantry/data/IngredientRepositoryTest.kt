package com.example.plantry.data

import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.claude.NutritionSource
import com.example.plantry.data.usda.UsdaFood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    )
    private val riceDry = riceCooked.copy(fdcId = 168877, description = "Rice, white, long-grain, raw")

    private fun draft(ingredient: Ingredient) = IngredientDraft(
        name = " ${ingredient.name} ",
        nutrition = ingredient.nutrition.copy(protein = 3.0),
        storeSection = StoreSection.DRY_GOODS,
        plantPoints = PlantPoints.QUARTER,
    )

    private fun slice(count: Int) = IngredientUnit.ofPackage(PieceUnit.SLICE, 750.0, count)!!

    private val decided = IngredientDraft(
        name = "",
        nutrition = Nutrition(),
        storeSection = StoreSection.OTHER,
        plantPoints = PlantPoints.ZERO,
    )

    private suspend fun IngredientRepository.createFromUsda(food: UsdaFood, name: String): Long =
        create(NewIngredientSeed(name, food.nutrition, food.fdcId, food.description), decided.copy(name = name, nutrition = food.nutrition))

    private suspend fun IngredientRepository.createFromLabel(name: String, nutrition: Nutrition, labelSource: LabelSource? = null): Long =
        create(NewIngredientSeed(name, nutrition, labelSource = labelSource), decided.copy(name = name, nutrition = nutrition))

    @Test
    fun create_storesTheUsersDecisionsAndEditedNutrition() = runTest {
        val seed = NewIngredientSeed("Kichererbsen", riceCooked.nutrition, riceCooked.fdcId, riceCooked.description)
        val edited = riceCooked.nutrition.copy(protein = 9.0)

        val id = repository.create(
            seed,
            IngredientDraft(" Kichererbsen (Dose) ", edited, StoreSection.DRY_GOODS, PlantPoints.ONE),
        )

        val ingredient = repository.getIngredient(id)!!
        assertEquals("Kichererbsen (Dose)", ingredient.name)
        assertEquals(edited, ingredient.nutrition)
        assertEquals(StoreSection.DRY_GOODS, ingredient.storeSection)
        assertEquals(PlantPoints.ONE, ingredient.plantPoints)
        assertEquals(riceCooked.fdcId, ingredient.fdcId)
    }

    @Test
    fun create_aScannedLabelReplacesTheUsdaSource() = runTest {
        val seed = NewIngredientSeed("Reis", riceCooked.nutrition, riceCooked.fdcId, riceCooked.description)
        val label = LabelSource("Bio Reis", "4012345678901")

        val id = repository.create(seed, decided.copy(name = "Reis", nutrition = riceCooked.nutrition, scannedLabel = label))

        val ingredient = repository.getIngredient(id)!!
        assertEquals(label, ingredient.labelSource)
        assertNull(ingredient.fdcId)
        assertNull(ingredient.usdaDescription)
    }

    @Test
    fun createFromUsda_copiesUsdaData() = runTest {
        val id = repository.createFromUsda(riceCooked, " Reis, gekocht ")

        val ingredient = repository.getIngredient(id)!!
        assertEquals("Reis, gekocht", ingredient.name)
        assertEquals(168878L, ingredient.fdcId)
        assertEquals(riceCooked.description, ingredient.usdaDescription)
        assertEquals(riceCooked.nutrition, ingredient.nutrition)
    }

    @Test
    fun createFromLabel_hasNoUsdaReference() = runTest {
        val label = Nutrition(52.0, 1.0, 9.0, 4.0, 1.5, 0.0)

        val ingredient = repository.getIngredient(repository.createFromLabel(" Haferdrink ", label))!!

        assertEquals("Haferdrink", ingredient.name)
        assertNull(ingredient.fdcId)
        assertNull(ingredient.usdaDescription)
        assertEquals(label, ingredient.nutrition)
    }

    @Test
    fun createFromLabel_keepsTheScannedPackageAsSource() = runTest {
        val label = Nutrition(128.0, 7.0, 15.4, 0.5, 2.1, 5.0)
        val source = LabelSource("Bio Kichererbsen (Alnatura)", "4104420208117")

        val ingredient = repository.getIngredient(repository.createFromLabel("Kichererbsen (Dose)", label, source))!!

        assertEquals(source, ingredient.labelSource)
        assertNull(ingredient.fdcId)
    }

    @Test
    fun update_withScannedLabel_replacesTheUsdaReference() = runTest {
        val id = repository.createFromUsda(riceCooked, "Reis, gekocht")
        val source = LabelSource("Basmati Reis", "4000000000001")

        repository.update(id, draft(repository.getIngredient(id)!!).copy(scannedLabel = source))

        val updated = repository.getIngredient(id)!!
        assertNull(updated.fdcId)
        assertNull(updated.usdaDescription)
        assertEquals(source, updated.labelSource)
        assertEquals("Reis, gekocht", updated.name)
    }

    @Test
    fun update_withoutScannedLabel_keepsTheSource() = runTest {
        val id = repository.createFromUsda(riceCooked, "Reis, gekocht")

        repository.update(id, draft(repository.getIngredient(id)!!))

        val updated = repository.getIngredient(id)!!
        assertEquals(168878L, updated.fdcId)
        assertNull(updated.labelSource)
    }

    @Test
    fun update_storesComponents_andCreatesThoseGivenByName() = runTest {
        val berries = repository.createFromLabel("Himbeeren", Nutrition())
        val mix = repository.createFromLabel("Beerenmischung", Nutrition())

        repository.update(
            mix,
            draft(repository.getIngredient(mix)!!).copy(components = listOf(MixPart(berries, "Himbeeren"), MixPart(null, " Johannisbeeren "))),
        )

        val created = repository.observeIngredients().first().single { it.name == "Johannisbeeren" }
        assertEquals(mapOf(mix to listOf(berries, created.id)), repository.observeContents().first())
        // In the mix's store section, as one plant.
        assertEquals(StoreSection.DRY_GOODS, created.storeSection)
        assertEquals(PlantPoints.ONE, created.plantPoints)
        assertNull(created.fdcId)
    }

    @Test
    fun update_replacesComponents() = runTest {
        val berries = repository.createFromLabel("Himbeeren", Nutrition())
        val currants = repository.createFromLabel("Johannisbeeren", Nutrition())
        val mix = repository.createFromLabel("Beerenmischung", Nutrition())
        repository.update(mix, draft(repository.getIngredient(mix)!!).copy(components = listOf(MixPart(berries, ""), MixPart(currants, ""))))

        repository.update(mix, draft(repository.getIngredient(mix)!!).copy(components = listOf(MixPart(currants, ""))))

        assertEquals(mapOf(mix to listOf(currants)), repository.observeContents().first())
    }

    @Test
    fun update_dropsAMixAndTheIngredientItselfAsComponents() = runTest {
        val berries = repository.createFromLabel("Himbeeren", Nutrition())
        val mix = repository.createFromLabel("Beerenmischung", Nutrition())
        val muesli = repository.createFromLabel("Müsli", Nutrition())
        repository.update(mix, draft(repository.getIngredient(mix)!!).copy(components = listOf(MixPart(berries, ""))))

        repository.update(
            muesli,
            draft(repository.getIngredient(muesli)!!).copy(components = listOf(MixPart(mix, ""), MixPart(muesli, ""), MixPart(berries, ""))),
        )

        assertEquals(mapOf(mix to listOf(berries), muesli to listOf(berries)), repository.observeContents().first())
    }

    @Test
    fun update_aComponentGetsNoComponentsItself() = runTest {
        val berries = repository.createFromLabel("Himbeeren", Nutrition())
        val other = repository.createFromLabel("Erdbeeren", Nutrition())
        val mix = repository.createFromLabel("Beerenmischung", Nutrition())
        repository.update(mix, draft(repository.getIngredient(mix)!!).copy(components = listOf(MixPart(berries, ""))))

        repository.update(berries, draft(repository.getIngredient(berries)!!).copy(components = listOf(MixPart(other, ""), MixPart(null, "Neu"))))

        assertEquals(mapOf(mix to listOf(berries)), repository.observeContents().first())
        assertTrue(repository.observeIngredients().first().none { it.name == "Neu" })
    }

    @Test
    fun update_refusedForAUnitInUse_keepsTheComponents() = runTest {
        val berries = repository.createFromLabel("Himbeeren", Nutrition())
        val mix = repository.create(
            NewIngredientSeed("Beerenmischung", Nutrition(), units = listOf(slice(4))),
            decided.copy(name = "Beerenmischung", units = listOf(slice(4))),
        )
        dao.unitTitles[mix to PieceUnit.SLICE] = listOf("Porridge")

        repository.update(mix, draft(repository.getIngredient(mix)!!).copy(components = listOf(MixPart(berries, ""), MixPart(null, "Neu"))))

        assertEquals(emptyMap<Long, List<Long>>(), repository.observeContents().first())
        assertTrue(repository.observeIngredients().first().none { it.name == "Neu" })
    }

    @Test
    fun create_storesComponents() = runTest {
        val berries = repository.createFromLabel("Himbeeren", Nutrition())

        val mix = repository.create(
            NewIngredientSeed("Beerenmischung", Nutrition()),
            decided.copy(name = "Beerenmischung", components = listOf(MixPart(berries, "Himbeeren"))),
        )

        assertEquals(mapOf(mix to listOf(berries)), repository.observeContents().first())
    }

    @Test
    fun createProposed_withLabelNutrition_hasNoUsdaReference() = runTest {
        val label = Nutrition(52.0, 1.0, 9.0, 4.0, 1.5, 0.0)
        val oatDrink = proposal(riceDry, "Haferdrink").copy(source = NutritionSource.Label(label))

        val ingredient = repository.getIngredient(repository.createProposed(mapOf(-1L to oatDrink)).getValue(-1))!!

        assertNull(ingredient.fdcId)
        assertNull(ingredient.usdaDescription)
        assertEquals(label, ingredient.nutrition)
    }

    @Test
    fun create_storesTheUnitsAsEntered() = runTest {
        val id = repository.create(
            NewIngredientSeed("Toast", Nutrition()),
            decided.copy(name = "Toast", units = listOfNotNull(IngredientUnit.ofPackage(PieceUnit.SLICE, 750.0, 20))),
        )

        assertEquals(listOf(IngredientUnit(id, PieceUnit.SLICE, 37.5, 750.0, 20)), repository.getUnits(id))
    }

    @Test
    fun createProposed_storesTheProposedPieceWeightAsAUnit() = runTest {
        val egg = proposal(riceDry, "Ei").copy(proposedUnits = listOf(IngredientUnit(0, PieceUnit.PIECE, 55.0)))

        val id = repository.createProposed(mapOf(-1L to egg)).getValue(-1)

        assertEquals(listOf(IngredientUnit(id, PieceUnit.PIECE, 55.0)), repository.getUnits(id))
    }

    @Test
    fun addUnit_addsARowAndKeepsTheOthers_replacingOneWithTheSameUnit() = runTest {
        val id = repository.createFromLabel("Toast", Nutrition())
        val clove = IngredientUnit(id, PieceUnit.CLOVE, 4.0)
        repository.addUnit(clove)

        repository.addUnit(slice(20).copy(ingredientId = id))
        repository.addUnit(slice(25).copy(ingredientId = id))

        assertEquals(setOf(clove, slice(25).copy(ingredientId = id)), repository.getUnits(id).toSet())
    }

    @Test
    fun update_changedUnitWeight_isSavedWithoutAsking() = runTest {
        val id = repository.createFromLabel("Toast", Nutrition())
        val toast = repository.getIngredient(id)!!
        repository.update(id, draft(toast).copy(units = listOf(slice(20))))
        dao.unitTitles[id to PieceUnit.SLICE] = listOf("Croque")

        val usedIn = repository.update(id, draft(toast).copy(units = listOf(slice(22))))

        assertEquals(emptyList<String>(), usedIn)
        assertEquals(750.0 / 22, repository.getUnits(id).single().gramsPerUnit, 1e-9)
    }

    @Test
    fun update_removingOrReplacingAUnitInUse_isRefusedWithTheRecipes_nothingSaved() = runTest {
        val id = repository.createFromLabel("Toast", Nutrition())
        val toast = repository.getIngredient(id)!!
        val slice = slice(20)
        repository.update(id, draft(toast).copy(units = listOf(slice)))
        dao.unitTitles[id to PieceUnit.SLICE] = listOf("Croque", "Toast Hawaii")

        assertEquals(listOf("Croque", "Toast Hawaii"), repository.update(id, draft(toast).copy(name = "Brot")))
        assertEquals(listOf("Croque", "Toast Hawaii"), repository.update(id, draft(toast).copy(units = listOf(IngredientUnit(0, PieceUnit.PACKET, 750.0)))))

        assertEquals("Toast", repository.getIngredient(id)!!.name)
        assertEquals(listOf(slice.copy(ingredientId = id)), repository.getUnits(id))
    }

    @Test
    fun update_removingAnUnusedUnit_isSaved() = runTest {
        val id = repository.createFromLabel("Toast", Nutrition())
        val toast = repository.getIngredient(id)!!
        repository.update(id, draft(toast).copy(units = listOf(slice(20))))

        assertEquals(emptyList<String>(), repository.update(id, draft(toast)))
        assertEquals(emptyList<IngredientUnit>(), repository.getUnits(id))
    }

    @Test
    fun update_savesSeveralUnits_andRemovesAnUnusedOneWhileAnotherIsInUse() = runTest {
        val id = repository.createFromLabel("Kichererbsen", Nutrition())
        val chickpeas = repository.getIngredient(id)!!
        val can = IngredientUnit(id, PieceUnit.CAN, 240.0)
        val tbsp = IngredientUnit(id, PieceUnit.TBSP, 15.0)

        repository.update(id, draft(chickpeas).copy(units = listOf(can, tbsp)))
        assertEquals(setOf(can, tbsp), repository.getUnits(id).toSet())

        dao.unitTitles[id to PieceUnit.CAN] = listOf("Curry")
        assertEquals(emptyList<String>(), repository.update(id, draft(chickpeas).copy(units = listOf(can))))
        assertEquals(listOf(can), repository.getUnits(id))
    }

    @Test
    fun update_savesEdits() = runTest {
        val id = repository.createFromUsda(riceCooked, "Reis, gekocht")

        repository.update(id, draft(repository.getIngredient(id)!!))

        val updated = repository.getIngredient(id)!!
        assertEquals("Reis, gekocht", updated.name)
        assertEquals(3.0, updated.nutrition.protein, 0.0)
        assertEquals(StoreSection.DRY_GOODS, updated.storeSection)
        assertEquals(PlantPoints.QUARTER, updated.plantPoints)
        assertEquals(168878L, updated.fdcId)
    }

    private fun proposal(food: UsdaFood, name: String) = IngredientProposal(
        name = name,
        source = NutritionSource.Usda(food),
        searchTerms = emptyList(),
        storeSection = StoreSection.DRY_GOODS,
        plantPoints = PlantPoints.ZERO,
    )

    @Test
    fun createProposed_createsTheIngredients() = runTest {
        val ids = repository.createProposed(
            mapOf(
                -1L to proposal(riceCooked, "Reis, gekocht"),
                -2L to proposal(riceDry, "Reis"),
            ),
        )

        val cooked = repository.getIngredient(ids.getValue(-1))!!
        assertEquals("Reis, gekocht", cooked.name)
        assertEquals(168878L, cooked.fdcId)
        assertEquals(StoreSection.DRY_GOODS, cooked.storeSection)
        assertEquals("Reis", repository.getIngredient(ids.getValue(-2))!!.name)
    }

    @Test
    fun learnAliases_normalizesAndReplacesAnEarlierIngredient() = runTest {
        repository.learnAliases(listOf("Kichererbsen, abgetropft" to 3L, "Reis" to 5L))
        repository.learnAliases(listOf("kichererbsen abgetropft" to 4L, "" to 6L))

        assertEquals(mapOf("kichererbsen abgetropft" to 4L, "reis" to 5L), repository.observeAliases().first())
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

    override suspend fun insert(ingredient: Ingredient): Long {
        val id = nextId++
        ingredients.value += id to ingredient.copy(id = id)
        return id
    }

    override suspend fun update(ingredient: Ingredient) {
        if (ingredient.id in ingredients.value) ingredients.value += ingredient.id to ingredient
    }

    /** Recipe titles by ingredient id. */
    val recipeTitles = mutableMapOf<Long, List<String>>()

    override suspend fun getRecipeTitlesUsing(id: Long): List<String> = recipeTitles[id].orEmpty()

    /** Recipe titles by ingredient id and unit. */
    val unitTitles = mutableMapOf<Pair<Long, PieceUnit>, List<String>>()

    override suspend fun getRecipeTitlesUsingUnit(id: Long, unit: PieceUnit): List<String> = unitTitles[id to unit].orEmpty()

    private val units = MutableStateFlow<List<IngredientUnit>>(emptyList())

    override fun observeUnits(): Flow<List<IngredientUnit>> = units

    override suspend fun getUnits(id: Long): List<IngredientUnit> = units.value.filter { it.ingredientId == id }

    override suspend fun insertUnits(units: List<IngredientUnit>) {
        this.units.value += units
    }

    override suspend fun upsertUnit(unit: IngredientUnit) {
        units.value = units.value.filter { it.ingredientId != unit.ingredientId || it.unit != unit.unit } + unit
    }

    override suspend fun deleteUnits(id: Long) {
        units.value = units.value.filter { it.ingredientId != id }
    }

    override suspend fun deleteById(id: Long) {
        ingredients.value -= id
        components.value = components.value.filter { it.mixId != id && it.componentId != id }
    }

    private val components = MutableStateFlow<List<MixComponent>>(emptyList())

    override fun observeMixComponents(): Flow<List<MixComponent>> = components

    override suspend fun getMixComponents(): List<MixComponent> = components.value

    override suspend fun deleteComponents(mixId: Long) {
        components.value = components.value.filter { it.mixId != mixId }
    }

    override suspend fun insertComponents(components: List<MixComponent>) {
        this.components.value += components
    }

    override fun observeAliases(): Flow<List<IngredientAlias>> =
        aliases.map { all -> all.map { (wording, id) -> IngredientAlias(wording, id) } }

    override suspend fun upsertAliases(aliases: List<IngredientAlias>) {
        this.aliases.value += aliases.associate { it.wording to it.ingredientId }
    }
}
