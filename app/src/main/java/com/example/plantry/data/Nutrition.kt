package com.example.plantry.data

/** Nutrition values per 100 g of an ingredient. */
data class Nutrition(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val sugar: Double = 0.0,
    val fat: Double = 0.0,
    val fibre: Double = 0.0,
) {
    operator fun get(nutrient: Nutrient): Double = when (nutrient) {
        Nutrient.KCAL -> kcal
        Nutrient.PROTEIN -> protein
        Nutrient.CARBS -> carbs
        Nutrient.SUGAR -> sugar
        Nutrient.FAT -> fat
        Nutrient.FIBRE -> fibre
    }

    companion object {
        fun of(values: Map<Nutrient, Double>) = Nutrition(
            kcal = values[Nutrient.KCAL] ?: 0.0,
            protein = values[Nutrient.PROTEIN] ?: 0.0,
            carbs = values[Nutrient.CARBS] ?: 0.0,
            sugar = values[Nutrient.SUGAR] ?: 0.0,
            fat = values[Nutrient.FAT] ?: 0.0,
            fibre = values[Nutrient.FIBRE] ?: 0.0,
        )
    }
}

enum class Nutrient { KCAL, PROTEIN, CARBS, SUGAR, FAT, FIBRE }
