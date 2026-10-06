package com.example.plantry.data

import com.example.plantry.data.claude.ClaudeResult
import com.example.plantry.data.claude.FoodCandidates
import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.claude.IngredientProposer
import com.example.plantry.data.claude.NewFood
import com.example.plantry.data.settings.ScanModel
import com.example.plantry.data.usda.UsdaCatalog

/**
 * Proposes new ingredients: Claude gives English search terms (unless the scan already did), the
 * bundled USDA data is searched locally, and Claude picks from those candidates.
 */
class NewIngredientFinder(
    private val proposer: IngredientProposer,
    private val catalog: suspend () -> UsdaCatalog,
) {

    suspend fun propose(
        apiKey: String,
        model: ScanModel,
        foods: List<NewFood>,
    ): ClaudeResult<Map<Long, IngredientProposal>> {
        if (foods.isEmpty()) return ClaudeResult.Success(emptyMap())
        val withoutTerms = foods.filter { it.searchTerms.isEmpty() }
        val terms = if (withoutTerms.isEmpty()) {
            emptyMap()
        } else {
            when (val result = proposer.searchTerms(apiKey, model, withoutTerms)) {
                is ClaudeResult.Success -> result.value
                is ClaudeResult.Failure -> return result
            }
        }
        val catalog = catalog()
        val candidates = foods.map { food ->
            val withTerms = food.copy(searchTerms = food.searchTerms.ifEmpty { terms[food.id].orEmpty() })
            FoodCandidates(withTerms, catalog.candidates(withTerms.searchTerms))
        }
        return proposer.propose(apiKey, model, candidates)
    }
}
