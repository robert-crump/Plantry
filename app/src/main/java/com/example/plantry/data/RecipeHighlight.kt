package com.example.plantry.data

/**
 * What makes a recipe stand out, judged on the rounded numbers the stats row shows, so a
 * highlighted number never contradicts its threshold.
 */
enum class RecipeHighlight {
    /** "Proteinreich": at least [PROTEIN_MIN] g protein per portion. */
    HIGH_PROTEIN,

    /** "Pflanzenpunkte-Power": at least [PLANT_POINTS_MIN] plant points. */
    PLANT_POINT_POWER,

    /** "Kalorienarm": at most [KCAL_MAX] kcal per portion. */
    LOW_CALORIE,

    /** "Ballaststoffreich": at least [FIBRE_MIN] g fibre per portion. */
    HIGH_FIBRE,
    ;

    companion object {
        val PROTEIN_MIN = ProteinRating.GREEN_MIN.toInt()
        const val PLANT_POINTS_MIN = 5
        const val KCAL_MAX = 600
        const val FIBRE_MIN = 10

        /** None for a recipe without ingredient lines, whose zeros would otherwise count as light. */
        fun of(stats: RecipeStats, hasLines: Boolean): Set<RecipeHighlight> {
            if (!hasLines) return emptySet()
            return buildSet {
                if (stats.roundedProtein >= PROTEIN_MIN) add(HIGH_PROTEIN)
                if (stats.roundedPlantPoints >= PLANT_POINTS_MIN) add(PLANT_POINT_POWER)
                stats.roundedKcal?.let { if (it in 1..KCAL_MAX) add(LOW_CALORIE) }
                stats.roundedFibre?.let { if (it >= FIBRE_MIN) add(HIGH_FIBRE) }
            }
        }
    }
}
