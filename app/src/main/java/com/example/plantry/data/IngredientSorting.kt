package com.example.plantry.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** The attribute the Sortieren screen groups ingredients by. */
enum class SortView { PLANT_POINTS, STORE_SECTION }

/** One group of the Sortieren screen; moving an ingredient there sets its value. */
sealed interface SortGroup {
    fun contains(ingredient: Ingredient): Boolean
    fun applyTo(ingredient: Ingredient): Ingredient

    data class Points(val points: PlantPoints) : SortGroup {
        override fun contains(ingredient: Ingredient) = ingredient.plantPoints == points
        override fun applyTo(ingredient: Ingredient) = ingredient.copy(plantPoints = points)
    }

    data class Section(val section: StoreSection) : SortGroup {
        override fun contains(ingredient: Ingredient) = ingredient.storeSection == section
        override fun applyTo(ingredient: Ingredient) = ingredient.copy(storeSection = section)
    }
}

data class SortGroupItems(val group: SortGroup, val ingredients: List<Ingredient>)

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

    /** Plant points 1 / 0.25 / 0, or the store sections in shopping order. */
    fun groupsOf(view: SortView): List<SortGroup> = when (view) {
        SortView.PLANT_POINTS -> PlantPoints.entries.map { SortGroup.Points(it) }
        SortView.STORE_SECTION -> StoreSection.entries.map { SortGroup.Section(it) }
    }

    /**
     * Every group of [view], empty ones included so they can still be picked as a target, each with
     * its ingredients in the given order. With [onlyUnreviewed], reviewed ingredients are left out.
     */
    fun group(ingredients: List<Ingredient>, view: SortView, onlyUnreviewed: Boolean): List<SortGroupItems> {
        val shown = if (onlyUnreviewed) ingredients.filterNot { it.reviewed } else ingredients
        return groupsOf(view).map { group -> SortGroupItems(group, shown.filter(group::contains)) }
    }

    /** The ingredients of [ids] that change when moved to [group]; the reviewed flag is kept. */
    fun move(ingredients: List<Ingredient>, ids: Set<Long>, group: SortGroup): List<Ingredient> =
        ingredients.filter { it.id in ids && !group.contains(it) }.map(group::applyTo)
}
