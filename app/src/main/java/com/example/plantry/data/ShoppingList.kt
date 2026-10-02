package com.example.plantry.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.LocalDate
import kotlin.math.ceil

/** An item ticked off the shopping list of a week; the list itself is computed, not stored. */
@Entity(
    tableName = "shopping_ticks",
    primaryKeys = ["weekStart", "ingredientId"],
    foreignKeys = [
        ForeignKey(
            entity = Ingredient::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("ingredientId")],
)
data class ShoppingTick(
    /** Always a Saturday, see [WeekPlan.startOf]. */
    val weekStart: LocalDate,
    /** The ingredient as bought, i.e. the end of its buy-as chain. */
    val ingredientId: Long,
)

/** How much to buy of an ingredient, in its buy unit, always rounded up. */
sealed interface BuyQuantity {
    data class Pieces(val count: Int) : BuyQuantity

    data class Packs(val count: Int, val packSizeGrams: Double) : BuyQuantity

    /** Also used for pieces or packs whose weight is unknown. */
    data class Grams(val grams: Int) : BuyQuantity

    companion object {
        fun of(ingredient: Ingredient, neededGrams: Double): BuyQuantity {
            val pieceWeight = ingredient.pieceWeightGrams?.takeIf { it > 0 }
            val packSize = ingredient.packSizeGrams?.takeIf { it > 0 }
            return when {
                ingredient.buyUnit == BuyUnit.PIECES && pieceWeight != null -> Pieces(roundUp(neededGrams / pieceWeight))
                ingredient.buyUnit == BuyUnit.PACK && packSize != null -> Packs(roundUp(neededGrams / packSize), packSize)
                else -> Grams(roundUp(neededGrams))
            }
        }

        /** Rounds up, ignoring floating point noise such as 2.0000000001. */
        private fun roundUp(value: Double): Int = ceil(value - 1e-9).toInt()
    }
}

data class ShoppingItem(
    /** The ingredient as bought, i.e. the end of its buy-as chain. */
    val ingredientId: Long,
    val name: String,
    val neededGrams: Double,
    val quantity: BuyQuantity,
    val ticked: Boolean,
)

data class ShoppingSection(val storeSection: StoreSection, val items: List<ShoppingItem>)

/** The shopping list of a week's menu, cooked or not. */
data class ShoppingList(
    /** Non-empty sections in [StoreSection] order; items by name. */
    val sections: List<ShoppingSection>,
    /** Names of the staples the menu needs, by name; their quantities are not shown. */
    val staples: List<String>,
) {
    companion object {
        /**
         * The shopping list of the [recipes] on the menu. Lines are converted to their buy-as
         * target via the yield factors before adding up. [lines] may contain lines of other
         * recipes; lines whose ingredient is missing from [ingredients] are skipped. [ticked]
         * holds the ids of the ingredients already ticked off.
         */
        fun of(
            recipes: List<Recipe>,
            lines: List<RecipeIngredient>,
            ingredients: Map<Long, Ingredient>,
            ticked: Set<Long>,
        ): ShoppingList {
            val linesByRecipe = lines.groupBy { it.recipeId }
            val needed = recipes
                .flatMap { linesByRecipe[it.id].orEmpty() }
                .mapNotNull { line ->
                    val ingredient = ingredients[line.ingredientId] ?: return@mapNotNull null
                    val target = resolveBuyAs(ingredient, ingredients)
                    target.ingredient to line.grams * target.factor
                }
                .groupBy({ it.first.id }, { it })
                .values
                .map { group -> group.first().first to group.sumOf { it.second } }
            val (staples, toBuy) = needed.partition { (ingredient, _) -> ingredient.staple }
            val items = toBuy.map { (ingredient, grams) ->
                ingredient.storeSection to ShoppingItem(
                    ingredientId = ingredient.id,
                    name = ingredient.name,
                    neededGrams = grams,
                    quantity = BuyQuantity.of(ingredient, grams),
                    ticked = ingredient.id in ticked,
                )
            }.groupBy({ it.first }, { it.second })
            return ShoppingList(
                sections = StoreSection.entries.mapNotNull { section ->
                    items[section]?.let { ShoppingSection(section, it.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { item -> item.name })) }
                },
                staples = staples.map { it.first.name }.sortedWith(String.CASE_INSENSITIVE_ORDER),
            )
        }
    }
}
