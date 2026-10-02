package com.example.plantry.data

/** Suggests ingredients from the user's own table for what they type into an ingredient field. */
object IngredientSuggestions {

    const val DEFAULT_LIMIT = 5

    /**
     * Ingredients whose German name contains every word of [query], case-insensitively. Names that
     * start with the query rank first, then names with a word starting with it, then the rest;
     * ties are broken by shorter name, then alphabetically.
     */
    fun match(query: String, ingredients: List<Ingredient>, limit: Int = DEFAULT_LIMIT): List<Ingredient> {
        val normalizedQuery = query.trim().lowercase()
        val terms = words(normalizedQuery)
        if (terms.isEmpty()) return emptyList()
        return ingredients
            .map { it to it.name.lowercase() }
            .filter { (_, name) -> terms.all { it in name } }
            .sortedWith(
                compareBy<Pair<Ingredient, String>> { (_, name) ->
                    when {
                        name.startsWith(normalizedQuery) -> 0
                        words(name).any { it.startsWith(terms.first()) } -> 1
                        else -> 2
                    }
                }.thenBy { (_, name) -> name.length }.thenBy { (_, name) -> name },
            )
            .take(limit)
            .map { it.first }
    }

    private val WORD_SEPARATOR = Regex("[\\s,;()/-]+")

    private fun words(text: String) = text.split(WORD_SEPARATOR).filter { it.isNotEmpty() }
}
