package com.example.plantry.data.claude

import com.example.plantry.data.Nutrition
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
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
    )
    private val firmTofu = smokedTofu.copy(fdcId = 172475, description = "Tofu, raw, firm")
    private val riceCooked = smokedTofu.copy(fdcId = 168878, description = "Rice, white, cooked")

    private val tofu = FoodCandidates(NewFood(-1, "Räuchertofu", "200 g Räuchertofu", listOf("tofu smoked")), listOf(smokedTofu, firmTofu))
    private val cookedRice = FoodCandidates(NewFood(-2, "Reis, gekocht", "300 g gekochter Reis", listOf("rice cooked")), listOf(riceCooked))

    /** The fields Claude must always send, as valid defaults; [fields] override them. */
    private fun proposal(key: String, vararg fields: Pair<String, String>): String {
        val all = linkedMapOf(
            "key" to "\"$key\"", "fdcId" to "0", "name" to "\"\"", "storeSection" to "\"OTHER\"",
            "plantPoints" to "\"ZERO\"",
            "pieceUnit" to "\"NONE\"", "pieceGrams" to "0",
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
        assertTrue(result.storeSectionUndecided)
        assertTrue(result.plantPointsUndecided)
    }

    @Test
    fun validEnums_areDecided() {
        val result = parse(answer(proposal("N1", "storeSection" to "\"PRODUCE\"", "plantPoints" to "\"ONE\"")), tofu).getValue(-1)

        assertFalse(result.storeSectionUndecided)
        assertFalse(result.plantPointsUndecided)
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
    fun pieceWeight_isKeptForOnePieceAndBecomesAUnit() {
        val egg = FoodCandidates(NewFood(-1, "Ei", "2 Eier", emptyList()), listOf(firmTofu))

        val result = parse(answer(proposal("N1", "pieceUnit" to "\" piece \"", "pieceGrams" to "55")), egg).getValue(-1)

        assertEquals(IngredientUnit(0, PieceUnit.PIECE, 55.0), result.pieceWeight)
        assertEquals(listOf(IngredientUnit(-1, PieceUnit.PIECE, 55.0)), result.units(-1))
    }

    @Test
    fun clove_isKeptWithFractionalGrams() {
        val result = parse(answer(proposal("N1", "pieceUnit" to "\"CLOVE\"", "pieceGrams" to "3.5")), tofu).getValue(-1)

        assertEquals(IngredientUnit(0, PieceUnit.CLOVE, 3.5), result.pieceWeight)
    }

    @Test
    fun can_isKeptWithItsDrainedGrams() {
        val chickpeas = FoodCandidates(NewFood(-1, "Kichererbsen (Dose)", "1 Dose Kichererbsen", emptyList()), listOf(firmTofu))

        val result = parse(answer(proposal("N1", "pieceUnit" to "\"CAN\"", "pieceGrams" to "240")), chickpeas).getValue(-1)

        assertEquals(listOf(IngredientUnit(-1, PieceUnit.CAN, 240.0)), result.units(-1))
    }

    @Test
    fun noneUnknownOrPackageUnitsAndNonPositiveGrams_meanNoPieceWeight() {
        listOf(
            "NONE" to "60", "PIECE" to "0", "PIECE" to "-5", "EGG" to "60", "SLICE" to "37.5", "CAN" to "0",
            "PACKET" to "500", "TBSP" to "15", "HEAD" to "500", "" to "60",
        ).forEach { (unit, grams) ->
            val result = parse(answer(proposal("N1", "pieceUnit" to "\"$unit\"", "pieceGrams" to grams)), tofu)
            assertNull("$unit/$grams", result.getValue(-1).pieceWeight)
        }
        assertNull(parse("""{"ingredients":[{"key":"N1"}]}""", tofu).getValue(-1).pieceWeight)
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
