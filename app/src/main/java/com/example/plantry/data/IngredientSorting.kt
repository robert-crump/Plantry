package com.example.plantry.data

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

    /** Plant points 1 / ¼ / 0, or the store sections in shopping order. */
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
