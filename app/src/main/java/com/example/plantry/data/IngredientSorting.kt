package com.example.plantry.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

object IngredientSorting {

    /**
     * Alphabetical by name with [locale]'s collator, ignoring case, so umlauts sort with their base
     * letter ("Äpfel" next to "Apfel", not after "Z").
     */
    fun sortedByName(ingredients: List<Ingredient>, locale: Locale): List<Ingredient> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        return ingredients.sortedWith(compareBy(collator) { it.name })
    }

    /**
     * The fast-scroll letter for [name], matching [sortedByName]: its first letter in upper case
     * without accents ("Äpfel" -> "A"), "#" when it starts with a digit, null without either.
     */
    fun indexLetter(name: String, locale: Locale): String? {
        val first = name.firstOrNull(Char::isLetterOrDigit) ?: return null
        if (first.isDigit()) return "#"
        val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first()
        return base.toString().uppercase(locale)
    }
}
