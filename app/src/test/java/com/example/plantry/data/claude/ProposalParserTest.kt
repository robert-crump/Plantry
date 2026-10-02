package com.example.plantry.data.claude

import com.example.plantry.data.BuyUnit
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import com.example.plantry.data.UnitWeight
import com.example.plantry.data.usda.UsdaFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalParserTest {

    private val smokedTofu = UsdaFood(
        fdcId = 172476,
        description = "Tofu, smoked",
        nutrition = Nutrition(160.0, 16.0, 3.0, 1.0, 9.0, 1.0),
        portions = listOf(UnitWeight("oz", 28.35)),
    )
    private val firmTofu = smokedTofu.copy(fdcId = 172475, description = "Tofu, raw, firm")
    private val riceCooked = smokedTofu.copy(fdcId = 168878, description = "Rice, white, cooked", portions = emptyList())
    private val riceDry = smokedTofu.copy(fdcId = 168877, description = "Rice, white, raw")

    private val tofu = FoodCandidates(NewFood(-1, "Räuchertofu", "200 g Räuchertofu", listOf("tofu smoked")), listOf(smokedTofu, firmTofu))
    private val cookedRice = FoodCandidates(NewFood(-2, "Reis, gekocht", "300 g gekochter Reis", listOf("rice cooked")), listOf(riceCooked))
    private val dryRice = FoodCandidates(NewFood(-3, "Reis", "", listOf("rice raw")), listOf(riceDry))

    /** The fields Claude must always send, as valid defaults; [fields] override them. */
    private fun proposal(key: String, vararg fields: Pair<String, String>): String {
        val all = linkedMapOf(
            "key" to "\"$key\"", "fdcId" to "0", "name" to "\"\"", "unitWeights" to "[]", "buyUnit" to "\"GRAMS\"",
            "packSizeGrams" to "0", "storeSection" to "\"OTHER\"", "staple" to "false", "plantPoints" to "\"ZERO\"",
            "buyAsIngredientId" to "0", "buyAsNewKey" to "\"\"", "buyAsYieldFactor" to "0",
        )
        all.putAll(fields)
        return all.entries.joinToString(",", "{", "}") { (name, value) -> "\"$name\":$value" }
    }

    private fun answer(vararg proposals: String) = """{"ingredients":[${proposals.joinToString(",")}]}"""

    private fun parse(text: String?, vararg foods: FoodCandidates, known: Set<Long> = setOf(5L)) =
        (ProposalParser.proposals(text, foods.toList(), known) as ClaudeResult.Success).value

    @Test
    fun mapsAllProposedAttributes() {
        val result = parse(
            answer(
                proposal(
                    "N1",
                    "fdcId" to "172476",
                    "name" to "\" Räuchertofu \"",
                    "unitWeights" to """[{"label":"Packung","grams":200}]""",
                    "buyUnit" to "\"PACK\"",
                    "packSizeGrams" to "200",
                    "storeSection" to "\"DAIRY_CHILLED\"",
                    "staple" to "false",
                    "plantPoints" to "\"ONE\"",
                ),
            ),
            tofu,
        )

        assertEquals(
            IngredientProposal(
                name = "Räuchertofu",
                food = smokedTofu,
                searchTerms = listOf("tofu smoked"),
                unitWeights = listOf(UnitWeight("Packung", 200.0)),
                buyUnit = BuyUnit.PACK,
                packSizeGrams = 200.0,
                storeSection = StoreSection.DAIRY_CHILLED,
                staple = false,
                plantPoints = PlantPoints.ONE,
                buyAs = null,
            ),
            result.getValue(-1),
        )
    }

    @Test
    fun fdcIdThatWasNoCandidate_meansNoUsdaEntry() {
        val result = parse(answer(proposal("N1", "fdcId" to "999")), tofu)

        assertNull(result.getValue(-1).food)
    }

    @Test
    fun invalidEnumsAndBlankName_fallBackToNeutralValues() {
        val result = parse(
            answer(proposal("N1", "buyUnit" to "\"BOX\"", "storeSection" to "\"bakery\"", "plantPoints" to "\"HALF\"", "name" to "\" \"")),
            tofu,
        ).getValue(-1)

        assertEquals("Räuchertofu", result.name)
        assertEquals(BuyUnit.GRAMS, result.buyUnit)
        assertEquals(StoreSection.OTHER, result.storeSection)
        assertEquals(PlantPoints.ZERO, result.plantPoints)
    }

    @Test
    fun enumsAreReadCaseInsensitively() {
        val result = parse(answer(proposal("N1", "storeSection" to "\"produce\"", "plantPoints" to "\"quarter\"")), tofu)

        assertEquals(StoreSection.PRODUCE, result.getValue(-1).storeSection)
        assertEquals(PlantPoints.QUARTER, result.getValue(-1).plantPoints)
    }

    @Test
    fun packWithoutSize_isBoughtByGrams_andSizeOnlyKeptForPacks() {
        val noSize = parse(answer(proposal("N1", "buyUnit" to "\"PACK\"", "packSizeGrams" to "0")), tofu).getValue(-1)
        val pieces = parse(answer(proposal("N1", "buyUnit" to "\"PIECES\"", "packSizeGrams" to "200")), tofu).getValue(-1)

        assertEquals(BuyUnit.GRAMS, noSize.buyUnit)
        assertNull(noSize.packSizeGrams)
        assertEquals(BuyUnit.PIECES, pieces.buyUnit)
        assertNull(pieces.packSizeGrams)
    }

    @Test
    fun invalidUnitWeights_areDropped() {
        val result = parse(
            answer(
                proposal(
                    "N1",
                    "unitWeights" to """[{"label":"mittel","grams":110},{"label":" ","grams":5},{"label":"EL","grams":0},{"label":"Mittel","grams":120}]""",
                ),
            ),
            tofu,
        )

        assertEquals(listOf(UnitWeight("mittel", 110.0)), result.getValue(-1).unitWeights)
    }

    @Test
    fun buyAs_linksToKnownIngredientOrAnotherNewFood() {
        val result = parse(
            answer(
                proposal("N2", "buyAsNewKey" to "\"N3\"", "buyAsYieldFactor" to "0.4"),
                proposal("N3", "buyAsIngredientId" to "5", "buyAsYieldFactor" to "1"),
            ),
            cookedRice, dryRice,
        )

        assertEquals(ProposedBuyAs(-3, 0.4), result.getValue(-2).buyAs)
        assertEquals(ProposedBuyAs(5, 1.0), result.getValue(-3).buyAs)
    }

    @Test
    fun buyAs_toUnknownIngredientOrItself_isDropped_andInvalidFactorIsMissing() {
        val result = parse(
            answer(
                proposal("N2", "buyAsIngredientId" to "42", "buyAsNewKey" to "\"N9\""),
                proposal("N3", "buyAsNewKey" to "\"N3\""),
                proposal("N1", "buyAsIngredientId" to "5", "buyAsYieldFactor" to "-1"),
            ),
            cookedRice, dryRice, tofu,
        )

        assertNull(result.getValue(-2).buyAs)
        assertNull(result.getValue(-3).buyAs)
        assertEquals(ProposedBuyAs(5, null), result.getValue(-1).buyAs)
    }

    @Test
    fun foodClaudeLeftOut_getsDefaultsWithoutUsdaEntry() {
        val result = parse(answer(proposal("N1", "fdcId" to "172476")), tofu, cookedRice)

        val missing = result.getValue(-2)
        assertEquals("Reis, gekocht", missing.name)
        assertNull(missing.food)
        assertEquals(BuyUnit.GRAMS, missing.buyUnit)
        assertFalse(missing.staple)
    }

    @Test
    fun missingFields_useDefaults() {
        val result = parse("""{"ingredients":[{"key":"N1","fdcId":172476}]}""", tofu).getValue(-1)

        assertEquals(smokedTofu, result.food)
        assertEquals("Räuchertofu", result.name)
        assertEquals(StoreSection.OTHER, result.storeSection)
        assertNull(result.buyAs)
    }

    @Test
    fun malformedOrMissingAnswer_isBadResponse() {
        val bad = ClaudeResult.Failure(ClaudeFailure.BAD_RESPONSE)
        assertEquals(bad, ProposalParser.proposals("{\"ingredients\":", listOf(tofu), emptySet()))
        assertEquals(bad, ProposalParser.proposals(null, listOf(tofu), emptySet()))
    }

    @Test
    fun toIngredient_isUnreviewedWithUsdaNutrition() {
        val proposal = parse(
            answer(proposal("N1", "fdcId" to "172476", "name" to "\"Räuchertofu\"", "staple" to "true")),
            tofu,
        ).getValue(-1)

        val ingredient = proposal.toIngredient()

        assertFalse(ingredient.reviewed)
        assertEquals("Räuchertofu", ingredient.name)
        assertEquals(172476L, ingredient.fdcId)
        assertEquals("Tofu, smoked", ingredient.usdaDescription)
        assertEquals(smokedTofu.nutrition, ingredient.nutrition)
        assertTrue(ingredient.staple)
        assertNull(ingredient.buyAsIngredientId)
        // Without proposed unit weights, the USDA portions are used.
        assertEquals(smokedTofu.portions, ingredient.unitWeights)
    }

    @Test
    fun toIngredient_prefersProposedUnitWeights() {
        val proposal = parse(
            answer(proposal("N1", "fdcId" to "172476", "unitWeights" to """[{"label":"Scheibe","grams":20}]""")),
            tofu,
        ).getValue(-1)

        assertEquals(listOf(UnitWeight("Scheibe", 20.0)), proposal.toIngredient().unitWeights)
    }

    @Test
    fun searchTerms_perFood_cleanedAndEmptyWhenLeftOut() {
        val foods = listOf(tofu.food, cookedRice.food)

        val result = ProposalParser.searchTerms(
            """{"foods":[{"key":"N1","searchTerms":[" tofu smoked ","tofu","tofu","", "bean curd", "soy"]}]}""",
            foods,
        )

        assertEquals(
            ClaudeResult.Success(mapOf(-1L to listOf("tofu smoked", "tofu", "bean curd"), -2L to emptyList())),
            result,
        )
    }
}
