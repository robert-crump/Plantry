package com.example.plantry.data.backup

import com.example.plantry.data.CookLog
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientAlias
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.RecipeSnapshot
import com.example.plantry.data.RecipeStats
import com.example.plantry.data.StoreSection
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
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
    /** Since version 2; version 1 files have none. */
    val aliases: List<BackupAlias> = emptyList(),
    /** Geplant; since version 6. */
    val planned: List<BackupPlanned> = emptyList(),
) {
    companion object {
        /**
         * 2: learned ingredient aliases. 3: a recipe's cooking time may be null. 4: no week plan
         * and shopping ticks any more. 5: cooking log entries carry a recipe snapshot and may
         * belong to a deleted recipe. 6: Geplant. 7: cooking log entries may carry kcal and fibre.
         * 8: ingredients without unit weights, buy unit, pack size, staple flag and buy-as link.
         */
        const val FORMAT_VERSION = 8

        /**
         * Unknown keys are skipped, so older files with `weekPlan` and `shoppingTicks`, or with the
         * ingredient attributes dropped in version 8, still import.
         */
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        fun encode(file: BackupFile): String = json.encodeToString(file)

        /** Throws [InvalidBackupException] for anything that is not a backup this app can read. */
        fun decode(text: String): BackupFile {
            try {
                val version = json.parseToJsonElement(text).jsonObject["formatVersion"]?.jsonPrimitive?.int
                    ?: throw InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP)
                if (version > FORMAT_VERSION) throw InvalidBackupException(InvalidBackupException.Reason.NEWER_VERSION)
                return json.decodeFromString<BackupFile>(text)
            } catch (e: SerializationException) {
                throw InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP, e)
            } catch (e: IllegalArgumentException) {
                // Also thrown by the jsonObject / jsonPrimitive / int accessors.
                throw InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP, e)
            }
        }
    }
}

class InvalidBackupException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NOT_A_BACKUP, NEWER_VERSION }
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
)

@Serializable
data class BackupRecipe(
    val id: Long,
    val title: String,
    val source: String,
    val page: Int?,
    val bookServings: Int,
    val ourServings: Int,
    /** Null since version 3. */
    val cookingTimeMinutes: Int?,
    val modified: Boolean,
    /** The recipe photo, Base64-encoded; null when the recipe has none. */
    val photo: String?,
    /** In recipe order. */
    val lines: List<BackupLine>,
)

@Serializable
data class BackupLine(val id: Long, val originalText: String, val grams: Double, val ingredientId: Long)

/**
 * Dates are ISO-8601, e.g. "2026-10-02". The snapshot (title and stats) is there since version 5;
 * older entries get it computed from their recipe on import.
 */
@Serializable
data class BackupCookLog(
    val id: Long,
    /** Null since version 5, once the recipe was deleted. */
    val recipeId: Long?,
    val cookedOn: String,
    val title: String? = null,
    val plantPoints: Double? = null,
    val proteinPerPortion: Double? = null,
    val carbsPerPortion: Double? = null,
    /** Since version 7; null for entries logged before kcal and fibre were snapshotted. */
    val kcalPerPortion: Double? = null,
    val fibrePerPortion: Double? = null,
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
)

/** Recipe photos by recipe id. */
typealias Photos = Map<Long, ByteArray>

fun BackupSnapshot.toFile(photos: Photos, settings: BackupSettings): BackupFile {
    val linesByRecipe = lines.groupBy { it.recipeId }
    return BackupFile(
        formatVersion = BackupFile.FORMAT_VERSION,
        settings = settings,
        ingredients = ingredients.map { it.toBackup() },
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
 * Throws [InvalidBackupException] for malformed dates or photos, and for a cooking log entry
 * without a snapshot whose recipe is missing.
 */
fun BackupFile.toSnapshot(): BackupSnapshot = try {
    val ingredients = ingredients.map { it.toEntity() }
    val recipes = recipes.map { it.toEntity() }
    val lines = this.recipes.flatMap { recipe ->
        recipe.lines.mapIndexed { position, line ->
            RecipeIngredient(line.id, recipe.id, position, line.originalText, line.grams, line.ingredientId)
        }
    }
    val recipesById = recipes.associateBy { it.id }
    val ingredientsById = ingredients.associateBy { it.id }
    BackupSnapshot(
        ingredients = ingredients,
        recipes = recipes,
        lines = lines,
        cookLog = cookLog.map { log ->
            val snapshot = log.snapshot() ?: log.recipeId?.let(recipesById::get)?.let { recipe ->
                RecipeSnapshot.of(recipe, lines, ingredientsById)
            } ?: throw InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP)
            CookLog(log.id, log.recipeId, LocalDate.parse(log.cookedOn), snapshot.title, snapshot.stats)
        },
        aliases = aliases.map { IngredientAlias(it.wording, it.ingredientId) },
        planned = planned.map { PlannedRecipe(it.recipeId, LocalDate.parse(it.plannedOn)) },
    )
} catch (e: DateTimeParseException) {
    throw InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP, e)
}

/** The stored snapshot; null in files before version 5. */
private fun BackupCookLog.snapshot(): RecipeSnapshot? {
    return RecipeSnapshot(
        title = title ?: return null,
        stats = RecipeStats(
            plantPoints = plantPoints ?: return null,
            proteinPerPortion = proteinPerPortion ?: return null,
            carbsPerPortion = carbsPerPortion ?: return null,
            kcalPerPortion = kcalPerPortion,
            fibrePerPortion = fibrePerPortion,
        ),
    )
}

fun BackupFile.photos(): Photos = try {
    recipes.mapNotNull { recipe -> recipe.photo?.let { recipe.id to Base64.getDecoder().decode(it) } }.toMap()
} catch (e: IllegalArgumentException) {
    throw InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP, e)
}

private fun Ingredient.toBackup() = BackupIngredient(
    id = id,
    name = name,
    fdcId = fdcId,
    usdaDescription = usdaDescription,
    nutrition = with(nutrition) { BackupNutrition(kcal, protein, carbs, sugar, fat, fibre) },
    storeSection = storeSection,
    plantPoints = plantPoints,
    reviewed = reviewed,
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
)

private fun Recipe.toBackup(photo: String?, lines: List<RecipeIngredient>) = BackupRecipe(
    id = id,
    title = title,
    source = source,
    page = page,
    bookServings = bookServings,
    ourServings = ourServings,
    cookingTimeMinutes = cookingTimeMinutes,
    modified = modified,
    photo = photo,
    lines = lines.map { BackupLine(it.id, it.originalText, it.grams, it.ingredientId) },
)

private fun BackupRecipe.toEntity() = Recipe(
    id = id,
    title = title,
    source = source,
    page = page,
    bookServings = bookServings,
    ourServings = ourServings,
    cookingTimeMinutes = cookingTimeMinutes,
    modified = modified,
)
