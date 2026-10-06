package com.example.plantry.data

import com.example.plantry.data.claude.ClaudeFailure
import com.example.plantry.data.claude.ClaudeResult
import com.example.plantry.data.claude.FoodCandidates
import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.claude.IngredientProposer
import com.example.plantry.data.claude.NewFood
import com.example.plantry.data.settings.ScanModel
import com.example.plantry.data.usda.UsdaCatalog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NewIngredientFinderTest {

    private val catalog = UsdaCatalog.parse(
        sequenceOf(
            "1\tTofu, raw, firm\t144\t17\t3\t1\t9\t2\t",
            "2\tTofu, smoked\t160\t16\t3\t1\t9\t1\t",
            "3\tOnions, raw\t40\t1\t9\t4\t0\t2\tmedium=110",
        ),
    )

    private class FakeProposer(
        private val terms: ClaudeResult<Map<Long, List<String>>> = ClaudeResult.Success(emptyMap()),
    ) : IngredientProposer {
        val termRequests = mutableListOf<List<NewFood>>()
        var proposeRequest: List<FoodCandidates>? = null

        override suspend fun searchTerms(apiKey: String, model: ScanModel, foods: List<NewFood>) =
            terms.also { termRequests += foods }

        override suspend fun propose(
            apiKey: String,
            model: ScanModel,
            foods: List<FoodCandidates>,
        ): ClaudeResult<Map<Long, IngredientProposal>> {
            proposeRequest = foods
            return ClaudeResult.Success(emptyMap())
        }
    }

    private val tofu = NewFood(-1, "Räuchertofu", "200 g Räuchertofu", listOf("tofu smoked", "tofu"))
    private val onion = NewFood(-2, "Zwiebel", "1 Zwiebel", emptyList())

    private suspend fun propose(proposer: FakeProposer, vararg foods: NewFood) =
        NewIngredientFinder(proposer) { catalog }.propose("key", ScanModel.OPUS, foods.toList())

    @Test
    fun foodsWithScanTerms_skipTheSearchTermsCall_andGetLocalCandidates() = runTest {
        val proposer = FakeProposer()

        propose(proposer, tofu)

        assertEquals(emptyList<List<NewFood>>(), proposer.termRequests)
        // Hits of "tofu smoked" first, then the rest of "tofu", without duplicates.
        assertEquals(listOf(2L, 1L), proposer.proposeRequest!!.single().candidates.map { it.fdcId })
    }

    @Test
    fun foodsWithoutTerms_askClaudeForTermsFirst() = runTest {
        val proposer = FakeProposer(ClaudeResult.Success(mapOf(-2L to listOf("onions raw"))))

        propose(proposer, tofu, onion)

        assertEquals(listOf(listOf(onion)), proposer.termRequests)
        val onionCandidates = proposer.proposeRequest!!.first { it.food.id == -2L }
        assertEquals(listOf("onions raw"), onionCandidates.food.searchTerms)
        assertEquals(listOf(3L), onionCandidates.candidates.map { it.fdcId })
    }

    @Test
    fun failedSearchTermsCall_isReturnedWithoutProposing() = runTest {
        val proposer = FakeProposer(ClaudeResult.Failure(ClaudeFailure.NETWORK))

        val result = propose(proposer, onion)

        assertEquals(ClaudeResult.Failure(ClaudeFailure.NETWORK), result)
        assertEquals(null, proposer.proposeRequest)
    }

    @Test
    fun noFoods_noCalls() = runTest {
        val proposer = FakeProposer()

        assertEquals(ClaudeResult.Success(emptyMap<Long, IngredientProposal>()), propose(proposer))
        assertEquals(null, proposer.proposeRequest)
    }
}
