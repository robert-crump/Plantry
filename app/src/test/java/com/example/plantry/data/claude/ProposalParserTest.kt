package com.example.plantry.data.claude

import com.example.plantry.data.Nutrition
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import com.example.plantry.data.usda.UsdaFood
import org.junit.Assert.assertEquals
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
            "plantPoints" to "\"ZERO\"",
            "units" to "[]",
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

    /** A units array, e.g. units("PIECE" to "55"). */
    private fun units(vararg units: Pair<String, String>) =
        units.joinToString(",", "[", "]") { (unit, grams) -> """{"unit":"$unit","grams":$grams}""" }

    @Test
    fun units_areKeptWithTheirGrams() {
        val garlic = FoodCandidates(NewFood(-1, "Knoblauch", "2 Zehen Knoblauch", emptyList()), listOf(firmTofu))

        val result = parse(answer(proposal("N1", "units" to units(" clove " to "3"))), garlic).getValue(-1)

        assertEquals(listOf(IngredientUnit(0, PieceUnit.CLOVE, 3.0)), result.proposedUnits)
        assertEquals(listOf(IngredientUnit(-1, PieceUnit.CLOVE, 3.0)), result.units(-1))
    }

    @Test
    fun spoonUnits_areKeptTogetherInUnitOrder() {
        val cumin = FoodCandidates(NewFood(-1, "Kreuzkümmel", "1 TL Kreuzkümmel", emptyList()), listOf(firmTofu))

        val result = parse(answer(proposal("N1", "units" to units("TBSP" to "6", "TSP" to "2"))), cumin).getValue(-1)

        assertEquals(
            listOf(IngredientUnit(-1, PieceUnit.TSP, 2.0), IngredientUnit(-1, PieceUnit.TBSP, 6.0)),
            result.units(-1),
        )
    }

    @Test
    fun can_isKeptWithItsDrainedGrams() {
        val chickpeas = FoodCandidates(NewFood(-1, "Kichererbsen (Dose)", "1 Dose Kichererbsen", emptyList()), listOf(firmTofu))

        val result = parse(answer(proposal("N1", "units" to units("CAN" to "240"))), chickpeas).getValue(-1)

        assertEquals(listOf(IngredientUnit(-1, PieceUnit.CAN, 240.0)), result.units(-1))
    }

    @Test
    fun unknownOrPackageUnitsNonPositiveGramsAndRepeats_areDropped() {
        val result = parse(
            answer(
                proposal(
                    "N1",
                    "units" to units(
                        "PIECE" to "0", "CLOVE" to "-5", "EGG" to "60", "SLICE" to "37.5", "PACKET" to "500",
                        "HEAD" to "500", "" to "60", "TSP" to "2", "TSP" to "3",
                    ),
                ),
            ),
            tofu,
        ).getValue(-1)

        assertEquals(listOf(IngredientUnit(0, PieceUnit.TSP, 2.0)), result.proposedUnits)
        assertEquals(emptyList<IngredientUnit>(), parse("""{"ingredients":[{"key":"N1"}]}""", tofu).getValue(-1).proposedUnits)
    }

    @Test
    fun aUnitTheUserDefined_replacesClaudesUnit() {
        val proposal = parse(answer(proposal("N1", "units" to units("PIECE" to "55", "CUP" to "90"))), tofu).getValue(-1)

        val edited = proposal.withUnit(IngredientUnit(0, PieceUnit.PIECE, 60.0))

        assertEquals(
            listOf(IngredientUnit(-1, PieceUnit.PIECE, 60.0), IngredientUnit(-1, PieceUnit.CUP, 90.0)),
            edited.units(-1),
        )
    }

    @Test
    fun malformedOrMissingAnswer_isBadResponse() {
        val bad = ClaudeResult.Failure(ClaudeFailure.BAD_RESPONSE)
        assertEquals(bad, ProposalParser.proposals("{\"ingredients\":", listOf(tofu)))
        assertEquals(bad, ProposalParser.proposals(null, listOf(tofu)))
    }

    @Test
    fun toIngredient_takesUsdaNutrition() {
        val proposal = parse(
            answer(proposal("N1", "fdcId" to "172476", "name" to "\"Räuchertofu\"")),
            tofu,
        ).getValue(-1)

        val ingredient = proposal.toIngredient()

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
