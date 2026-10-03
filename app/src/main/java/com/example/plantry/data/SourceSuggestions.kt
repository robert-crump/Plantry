package com.example.plantry.data

/** Suggests sources other recipes already use for what the user types into the source field. */
object SourceSuggestions {

    const val DEFAULT_LIMIT = 5

    /**
     * Distinct [sources] (case-insensitively, first spelling wins) containing [query], ignoring
     * case. Sources that start with the query rank first, then alphabetically. Blank queries and a
     * source equal to the query suggest nothing.
     */
    fun match(query: String, sources: List<String>, limit: Int = DEFAULT_LIMIT): List<String> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return emptyList()
        return sources
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .filter { it.lowercase() != normalizedQuery && normalizedQuery in it.lowercase() }
            .sortedWith(compareBy<String> { !it.lowercase().startsWith(normalizedQuery) }.thenBy { it.lowercase() })
            .take(limit)
    }
}
