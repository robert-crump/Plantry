package com.example.plantry.data.backup

import com.example.plantry.data.CookLog
import com.example.plantry.data.Dish
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientAlias
import com.example.plantry.data.IngredientOrigin
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.LabelSource
import com.example.plantry.data.LineUnit
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.RecipeStats
import com.example.plantry.data.StoreSection
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Base64

/**
 * The export file. Deliberately separate from the Room entities, so a schema change never
 * silently changes the file format; bump [FORMAT_VERSION] and migrate in [decode] instead.
 * Never contains the API key.
 */
@Serializable
data class BackupFile(
    val formatVersion: Int,
    val settings: BackupSettings,
    val ingredients: List<BackupIngredient>,
    val recipes: List<BackupRecipe>,
    val cookLog: List<BackupCookLog>,
    val aliases: List<BackupAlias>,
    /** Geplant. */
    val planned: List<BackupPlanned>,
) {
    companion object {
        /**
         * Restarted at 1 with structured recipe lines and ingredient units; files of the format
         * before (versions 2 to 15, without units) are no longer read.
         */
        const val FORMAT_VERSION = 1

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        fun encode(file: BackupFile): String = json.encodeToString(file)

        /** Throws [InvalidBackupException] for anything that is not a backup this app can read. */
        fun decode(text: String): BackupFile {
            try {
                val root = json.parseToJsonElement(text).jsonObject
                val version = root["formatVersion"]?.jsonPrimitive?.int
                    ?: throw InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP)
                if (version > FORMAT_VERSION) {
                    val reason = if (isOldFormat(root)) InvalidBackupException.Reason.OLD_FORMAT else InvalidBackupException.Reason.NEWER_VERSION
                    throw InvalidBackupException(reason)
                }
                if (version < FORMAT_VERSION) throw InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP)
                return json.decodeFromString<BackupFile>(text)
            } catch (e: SerializationException) {
                throw InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP, e)
            } catch (e: IllegalArgumentException) {
                // Also thrown by the jsonObject / jsonPrimitive / int accessors.
                throw InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP, e)
            }
        }

        /** The format before the restart counted up to 15 and had no units on its ingredients. */
        private fun isOldFormat(root: JsonObject): Boolean {
            val ingredients = root["ingredients"]?.jsonArray ?: return false
            return ingredients.isNotEmpty() && ingredients.none { "units" in it.jsonObject }
        }
    }
}

class InvalidBackupException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        NOT_A_BACKUP,
        NEWER_VERSION,

        /** Written before the format restarted at 1. */
        OLD_FORMAT,

        /** A line points to an ingredient or unit the file doesn't have. */
        BROKEN_REFERENCE,
    }
}

@Serializable
data class BackupSettings(val scanModel: String, val cooldownDays: Int)

@Serializable
data class BackupNutrition(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val sugar: Double,
    val fat: Double,
    val fibre: Double,
)

@Serializable
data class BackupIngredient(
    val id: Long,
    val name: String,
    val fdcId: Long?,
    val usdaDescription: String?,
    val nutrition: BackupNutrition,
    val storeSection: StoreSection,
    val plantPoints: PlantPoints,
    val reviewed: Boolean,
    /** Both null unless the nutrition came from a scanned package. */
    val labelProduct: String?,
    val labelBarcode: String?,
    val origin: IngredientOrigin,
    /** True while [storeSection] is only a fallback the user has yet to replace. */
    val storeSectionUndecided: Boolean,
    /** Like [storeSectionUndecided], for [plantPoints]. */
    val plantPointsUndecided: Boolean,
    /** Besides g and ml, which every ingredient has. */
    val units: List<BackupUnit>,
)

/** [packageGrams] and [packageCount] are both null unless the unit was entered as a package. */
@Serializable
data class BackupUnit(val unit: PieceUnit, val gramsPerUnit: Double, val packageGrams: Double?, val packageCount: Int?)

@Serializable
data class BackupRecipe(
    val id: Long,
    val title: String,
    val source: String,
    val page: Int?,
    val bookServings: Int,
    val ourServings: Int,
    /** The Gesamtzeit; null when unknown. */
    val totalTimeMinutes: Int?,
    /** The Arbeitszeit; null when unknown. */
    val activeTimeMinutes: Int?,
    val modified: Boolean,
    val dish: Dish,
    /** The recipe photo, Base64-encoded; null when the recipe has none. */
    val photo: String?,
    /** In recipe order. */
    val lines: List<BackupLine>,
)

/** [unit] is "G", "ML" or one of the ingredient's units, see [LineUnit.code]; no grams, they follow from the units. */
@Serializable
data class BackupLine(val id: Long, val amount: Double, val unit: String, val ingredientId: Long, val wording: String?)

/** Dates are ISO-8601, e.g. "2026-10-02". */
@Serializable
data class BackupCookLog(
    val id: Long,
    /** Null once the recipe was deleted. */
    val recipeId: Long?,
    val cookedOn: String,
    val title: String,
    val plantPoints: Double,
    val proteinPerPortion: Double,
    val carbsPerPortion: Double,
    /** Null for entries logged before kcal and fibre were snapshotted. */
    val kcalPerPortion: Double?,
    val fibrePerPortion: Double?,
)

@Serializable
data class BackupAlias(val wording: String, val ingredientId: Long)

/** [plannedOn] is ISO-8601, e.g. "2026-10-02". */
@Serializable
data class BackupPlanned(val recipeId: Long, val plannedOn: String)

/** All database content, as exported and restored. */
data class BackupSnapshot(
    val ingredients: List<Ingredient>,
    val recipes: List<Recipe>,
    val lines: List<RecipeIngredient>,
    val cookLog: List<CookLog>,
    val aliases: List<IngredientAlias> = emptyList(),
    val planned: List<PlannedRecipe> = emptyList(),
    val units: List<IngredientUnit> = emptyList(),
)

/** Recipe photos by recipe id. */
typealias Photos = Map<Long, ByteArray>

fun BackupSnapshot.toFile(photos: Photos, settings: BackupSettings): BackupFile {
    val linesByRecipe = lines.groupBy { it.recipeId }
    val unitsByIngredient = units.groupBy { it.ingredientId }
    return BackupFile(
        formatVersion = BackupFile.FORMAT_VERSION,
        settings = settings,
        ingredients = ingredients.map { it.toBackup(unitsByIngredient[it.id].orEmpty().sortedBy { unit -> unit.unit }) },
        recipes = recipes.map { recipe ->
            recipe.toBackup(
                photo = photos[recipe.id]?.let(Base64.getEncoder()::encodeToString),
                lines = linesByRecipe[recipe.id].orEmpty().sortedBy { it.position },
            )
        },
        cookLog = cookLog.map { log ->
            BackupCookLog(
                id = log.id,
                recipeId = log.recipeId,
                cookedOn = log.cookedOn.toString(),
                title = log.title,
                plantPoints = log.stats.plantPoints,
                proteinPerPortion = log.stats.proteinPerPortion,
                carbsPerPortion = log.stats.carbsPerPortion,
                kcalPerPortion = log.stats.kcalPerPortion,
                fibrePerPortion = log.stats.fibrePerPortion,
            )
        },
        aliases = aliases.map { BackupAlias(it.wording, it.ingredientId) },
        planned = planned.map { BackupPlanned(it.recipeId, it.plannedOn.toString()) },
    )
}

/**
 * Throws [InvalidBackupException] for malformed dates or units, and for a line whose ingredient,
 * or whose unit of that ingredient, the file doesn't have.
 */
fun BackupFile.toSnapshot(): BackupSnapshot = try {
    val unitsByIngredient = ingredients.associate { ingredient -> ingredient.id to ingredient.units.mapTo(mutableSetOf()) { it.unit } }
    val lines = recipes.flatMap { recipe ->
        recipe.lines.mapIndexed { position, line ->
            val unit = LineUnit.fromCode(line.unit) ?: throw InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP)
            val units = unitsByIngredient[line.ingredientId] ?: throw InvalidBackupException(InvalidBackupException.Reason.BROKEN_REFERENCE)
            if (unit is LineUnit.Of && unit.unit !in units) throw InvalidBackupException(InvalidBackupException.Reason.BROKEN_REFERENCE)
            RecipeIngredient(line.id, recipe.id, position, line.amount, unit, line.ingredientId, line.wording)
        }
    }
    BackupSnapshot(
        ingredients = ingredients.map { it.toEntity() },
        recipes = recipes.map { it.toEntity() },
        lines = lines,
        cookLog = cookLog.map { log ->
            val stats = RecipeStats(log.plantPoints, log.proteinPerPortion, log.carbsPerPortion, log.kcalPerPortion, log.fibrePerPortion)
            CookLog(log.id, log.recipeId, LocalDate.parse(log.cookedOn), log.title, stats)
        },
        aliases = aliases.map { IngredientAlias(it.wording, it.ingredientId) },
        planned = planned.map { PlannedRecipe(it.recipeId, LocalDate.parse(it.plannedOn)) },
        units = ingredients.flatMap { ingredient ->
            ingredient.units.map { IngredientUnit(ingredient.id, it.unit, it.gramsPerUnit, it.packageGrams, it.packageCount) }
        },
    )
} catch (e: DateTimeParseException) {
    throw InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP, e)
}

fun BackupFile.photos(): Photos = try {
    recipes.mapNotNull { recipe -> recipe.photo?.let { recipe.id to Base64.getDecoder().decode(it) } }.toMap()
} catch (e: IllegalArgumentException) {
    throw InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP, e)
}

private fun Ingredient.toBackup(units: List<IngredientUnit>) = BackupIngredient(
    id = id,
    name = name,
    fdcId = fdcId,
    usdaDescription = usdaDescription,
    nutrition = with(nutrition) { BackupNutrition(kcal, protein, carbs, sugar, fat, fibre) },
    storeSection = storeSection,
    plantPoints = plantPoints,
    reviewed = reviewed,
    labelProduct = labelSource?.labelProduct,
    labelBarcode = labelSource?.labelBarcode,
    origin = origin,
    storeSectionUndecided = storeSectionUndecided,
    plantPointsUndecided = plantPointsUndecided,
    units = units.map { BackupUnit(it.unit, it.gramsPerUnit, it.packageGrams, it.packageCount) },
)

private fun BackupIngredient.toEntity() = Ingredient(
    id = id,
    name = name,
    fdcId = fdcId,
    usdaDescription = usdaDescription,
    nutrition = with(nutrition) { Nutrition(kcal, protein, carbs, sugar, fat, fibre) },
    storeSection = storeSection,
    plantPoints = plantPoints,
    reviewed = reviewed,
    labelSource = if (labelProduct != null && labelBarcode != null) LabelSource(labelProduct, labelBarcode) else null,
    origin = origin,
    storeSectionUndecided = storeSectionUndecided,
    plantPointsUndecided = plantPointsUndecided,
)

private fun Recipe.toBackup(photo: String?, lines: List<RecipeIngredient>) = BackupRecipe(
    id = id,
    title = title,
    source = source,
    page = page,
    bookServings = bookServings,
    ourServings = ourServings,
    totalTimeMinutes = totalTimeMinutes,
    activeTimeMinutes = activeTimeMinutes,
    modified = modified,
    dish = dish,
    photo = photo,
    lines = lines.map { BackupLine(it.id, it.amount, it.unit.code, it.ingredientId, it.wording) },
)

private fun BackupRecipe.toEntity() = Recipe(
    id = id,
    title = title,
    source = source,
    page = page,
    bookServings = bookServings,
    ourServings = ourServings,
    totalTimeMinutes = totalTimeMinutes,
    modified = modified,
    dish = dish,
    activeTimeMinutes = activeTimeMinutes,
)
