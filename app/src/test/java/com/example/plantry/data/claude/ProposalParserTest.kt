package com.example.plantry.data.claude

import com.example.plantry.data.DrainedWeight
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import com.example.plantry.data.usda.UsdaFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ProposalParserTest {

    private val smokedTofu = UsdaFood(
        fdcId = 172476,
        description = "Tofu, smoked",
        nutrition = Nutrition(160.0, 16.0, 3.0, 1.0, 9.0, 1.0),
    )
    private val firmTofu = smokedTofu.copy(fdcId = 172475, description = "Tofu, raw, firm")
    private val riceCooked = smokedTofu.copy(fdcId = 168878, description = "Rice, white, cooked")

    private val tofu = FoodCandidates(NewFood(-1, "Räuchertofu", "200 g Räuchertofu", listOf("tofu smoked")), listOf(smokedTofu, firmTofu))
    private val cookedRice = FoodCandidates(NewFood(-2, "Reis, gekocht", "300 g gekochter Reis", listOf("rice cooked")), listOf(riceCooked))

    /** The fields Claude must always send, as valid defaults; [fields] override them. */
    private fun proposal(key: String, vararg fields: Pair<String, String>): String {
        val all = linkedMapOf(
            "key" to "\"$key\"", "fdcId" to "0", "name" to "\"\"", "storeSection" to "\"OTHER\"",
            "plantPoints" to "\"ZERO\"", "netWeightGrams" to "0", "drainedWeightGrams" to "0",
        )
        all.putAll(fields)
        return all.entries.joinToString(",", "{", "}") { (name, value) -> "\"$name\":$value" }
    }

    private fun answer(vararg proposals: String) = """{"ingredients":[${proposals.joinToString(",")}]}"""

    private fun parse(text: String?, vararg foods: FoodCandidates) =
        (ProposalParser.proposals(text, foods.toList()) as ClaudeResult.Success).value

    @Test
    fun mapsAllProposedAttributes() {
        val result = parse(
            answer(
                proposal(
                    "N1",
                    "fdcId" to "172476",
                    "name" to "\" Räuchertofu \"",
                    "storeSection" to "\"DAIRY_CHILLED\"",
                    "plantPoints" to "\"ONE\"",
                ),
            ),
            tofu,
        )

        assertEquals(
            IngredientProposal(
                name = "Räuchertofu",
                source = NutritionSource.Usda(smokedTofu),
                searchTerms = listOf("tofu smoked"),
                storeSection = StoreSection.DAIRY_CHILLED,
                plantPoints = PlantPoints.ONE,
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
            answer(proposal("N1", "storeSection" to "\"bakery\"", "plantPoints" to "\"HALF\"", "name" to "\" \"")),
            tofu,
        ).getValue(-1)

        assertEquals("Räuchertofu", result.name)
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
    fun foodClaudeLeftOut_getsDefaultsWithoutUsdaEntry() {
        val result = parse(answer(proposal("N1", "fdcId" to "172476")), tofu, cookedRice)

        val missing = result.getValue(-2)
        assertEquals("Reis, gekocht", missing.name)
        assertNull(missing.food)
        assertEquals(PlantPoints.ZERO, missing.plantPoints)
    }

    @Test
    fun missingFields_useDefaults() {
        val result = parse("""{"ingredients":[{"key":"N1","fdcId":172476}]}""", tofu).getValue(-1)

        assertEquals(smokedTofu, result.food)
        assertEquals("Räuchertofu", result.name)
        assertEquals(StoreSection.OTHER, result.storeSection)
    }

    @Test
    fun drainedWeights_areKeptAndPassedToTheIngredient() {
        val chickpeas = FoodCandidates(NewFood(-1, "Kichererbsen (Dose)", "1 Dose Kichererbsen", emptyList()), listOf(firmTofu))

        val result = parse(answer(proposal("N1", "netWeightGrams" to "400", "drainedWeightGrams" to "240")), chickpeas)

        assertEquals(DrainedWeight(400.0, 240.0), result.getValue(-1).drainedWeight)
        assertEquals(DrainedWeight(400.0, 240.0), result.getValue(-1).copy(source = NutritionSource.Usda(firmTofu)).toIngredient().drainedWeight)
    }

    @Test
    fun zeroOrInconsistentWeights_meanNotDrained() {
        listOf("0" to "0", "240" to "400", "400" to "0", "0" to "240").forEach { (net, drained) ->
            val result = parse(answer(proposal("N1", "netWeightGrams" to net, "drainedWeightGrams" to drained)), tofu)
            assertNull("$net/$drained", result.getValue(-1).drainedWeight)
        }
        assertNull(parse("""{"ingredients":[{"key":"N1"}]}""", tofu).getValue(-1).drainedWeight)
    }

    @Test
    fun malformedOrMissingAnswer_isBadResponse() {
        val bad = ClaudeResult.Failure(ClaudeFailure.BAD_RESPONSE)
        assertEquals(bad, ProposalParser.proposals("{\"ingredients\":", listOf(tofu)))
        assertEquals(bad, ProposalParser.proposals(null, listOf(tofu)))
    }

    @Test
    fun toIngredient_isUnreviewedWithUsdaNutrition() {
        val proposal = parse(
            answer(proposal("N1", "fdcId" to "172476", "name" to "\"Räuchertofu\"")),
            tofu,
        ).getValue(-1)

        val ingredient = proposal.toIngredient()

        assertFalse(ingredient.reviewed)
        assertEquals("Räuchertofu", ingredient.name)
        assertEquals(172476L, ingredient.fdcId)
        assertEquals("Tofu, smoked", ingredient.usdaDescription)
        assertEquals(smokedTofu.nutrition, ingredient.nutrition)
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
