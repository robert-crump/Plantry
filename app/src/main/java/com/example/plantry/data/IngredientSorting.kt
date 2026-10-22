package com.example.plantry.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** The ingredient list's orders; the nutrients per 100 g and the recipe count go highest first. */
enum class IngredientSort(
    /** The chip for the nutrient sorted by; null for the name and the recipe count. */
    val nutrient: IngredientChip? = null,
) {
    NAME,
    PROTEIN(IngredientChip.HIGH_PROTEIN),
    CARBS(IngredientChip.HIGH_CARBS),
    FIBRE(IngredientChip.HIGH_FIBRE),
    RECIPES,
}

object IngredientSorting {

    /**
     * The [ingredients] in [sort]'s order, ties alphabetical: by name with [locale]'s collator,
     * ignoring case, so umlauts sort with their base letter ("Äpfel" next to "Apfel", not after
     * "Z"). [recipeCounts] holds the recipes using each ingredient; missing ids mean 0.
     */
    fun sorted(
        ingredients: List<Ingredient>,
        sort: IngredientSort,
        recipeCounts: Map<Long, Int>,
        locale: Locale,
    ): List<Ingredient> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        val first: Comparator<Ingredient> = when {
            sort.nutrient != null -> compareByDescending { IngredientFilters.amount(sort.nutrient, it.nutrition) }
            sort == IngredientSort.RECIPES -> compareByDescending { recipeCounts[it.id] ?: 0 }
            else -> Comparator { _, _ -> 0 }
        }
        return ingredients.sortedWith(first.then(compareBy(collator) { it.name }))
    }

    /**
     * The fast-scroll letter for [name], matching [IngredientSort.NAME]: its first letter in upper
     * case without accents ("Äpfel" -> "A"), "#" when it starts with a digit, null without either.
     */
    fun indexLetter(name: String, locale: Locale): String? {
        val first = name.firstOrNull(Char::isLetterOrDigit) ?: return null
        if (first.isDigit()) return "#"
        val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first()
        return base.toString().uppercase(locale)
    }
}
