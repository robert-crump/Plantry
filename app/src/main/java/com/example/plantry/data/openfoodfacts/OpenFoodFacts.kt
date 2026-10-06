package com.example.plantry.data.openfoodfacts

import com.example.plantry.data.LabelSource
import com.example.plantry.data.Nutrient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlin.math.round

/**
 * A product from Open Food Facts. [nutrition] is per 100 g; a nutrient OFF does not list is
 * missing from the map rather than 0, so the user fills it in from the package.
 */
data class OffProduct(
    val barcode: String,
    /** The generic name if OFF has one (e.g. "Kichererbsen, gegart"), else the product name. */
    val nameSuggestion: String,
    /** The product name as on the package, for "Gefunden: …" and the source line. */
    val productName: String,
    /** E.g. "400 g"; null if OFF has none. */
    val quantity: String?,
    val nutrition: Map<Nutrient, Double>,
) {
    val missing: Set<Nutrient> get() = Nutrient.entries.toSet() - nutrition.keys

    val labelSource: LabelSource get() = LabelSource(productName, barcode)
}

sealed interface OffLookup {
    data class Found(val product: OffProduct) : OffLookup
    data object NotFound : OffLookup
    /** No connection, or OFF did not answer properly. */
    data object Offline : OffLookup
}

fun interface ProductLookup {
    suspend fun lookup(barcode: String): OffLookup
}

object OpenFoodFacts {

    private val json = Json { ignoreUnknownKeys = true }

    private const val KJ_PER_KCAL = 4.184

    /** The fields [parse] reads, so OFF sends only those. */
    const val FIELDS = "code,product_name,product_name_de,generic_name,generic_name_de,brands,quantity,nutriments"

    private val nutrientKeys = mapOf(
        Nutrient.PROTEIN to "proteins_100g",
        Nutrient.CARBS to "carbohydrates_100g",
        Nutrient.SUGAR to "sugars_100g",
        Nutrient.FAT to "fat_100g",
        Nutrient.FIBRE to "fiber_100g",
    )

    /**
     * Reads an OFF API v2 product response for [barcode]. Null when OFF does not know the product,
     * or knows it without any name; a body that is no JSON object throws [SerializationException].
     */
    fun parse(barcode: String, body: String): OffProduct? {
        val root = json.parseToJsonElement(body) as? JsonObject ?: throw SerializationException("not an object")
        if ((root["status"] as? JsonPrimitive)?.intOrNull != 1) return null
        val product = root["product"] as? JsonObject ?: return null
        val productName = product.text("product_name_de") ?: product.text("product_name")
        val brand = product.text("brands")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val shownName = productName ?: brand ?: return null
        val generic = product.text("generic_name_de") ?: product.text("generic_name")
        val nutriments = product["nutriments"] as? JsonObject ?: JsonObject(emptyMap())
        val nutrition = buildMap {
            kcal(nutriments)?.let { put(Nutrient.KCAL, it) }
            nutrientKeys.forEach { (nutrient, key) -> nutriments.number(key)?.let { put(nutrient, roundTo(it, 10.0)) } }
        }
        return OffProduct(
            barcode = barcode,
            nameSuggestion = generic ?: shownName,
            productName = if (productName != null && brand != null && !productName.contains(brand, ignoreCase = true)) {
                "$productName ($brand)"
            } else {
                shownName
            },
            quantity = product.text("quantity"),
            nutrition = nutrition,
        )
    }

    /** kcal per 100 g, from kJ when OFF lists only that (as "energy-kj_100g" or the kJ "energy_100g"). */
    private fun kcal(nutriments: JsonObject): Double? {
        val kcal = nutriments.number("energy-kcal_100g")
            ?: (nutriments.number("energy-kj_100g") ?: nutriments.number("energy_100g"))?.let { it / KJ_PER_KCAL }
        return kcal?.let { round(it) }
    }

    private fun roundTo(value: Double, factor: Double) = round(value * factor) / factor

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** OFF sends numbers, sometimes as strings; negative or unreadable values count as missing. */
    private fun JsonObject.number(key: String): Double? {
        val value: JsonElement = this[key] ?: return null
        val primitive = value as? JsonPrimitive ?: return null
        val number = primitive.doubleOrNull ?: primitive.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
        return number?.takeIf { it.isFinite() && it >= 0 }
    }
}

/** Looks products up on world.openfoodfacts.org. */
class OpenFoodFactsClient : ProductLookup {

    override suspend fun lookup(barcode: String): OffLookup = withContext(Dispatchers.IO) {
        if (!barcode.all { it.isDigit() }) return@withContext OffLookup.NotFound
        try {
            val url = URI("https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=${OpenFoodFacts.FIELDS}").toURL()
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                // OFF asks every app to identify itself.
                connection.setRequestProperty("User-Agent", "Plantry/1.0 (Android)")
                val code = connection.responseCode
                // An unknown product is a 404 with the usual JSON body.
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }
                when {
                    body == null -> OffLookup.Offline
                    code != 200 && code != 404 -> OffLookup.Offline
                    else -> OpenFoodFacts.parse(barcode, body)?.let { OffLookup.Found(it) } ?: OffLookup.NotFound
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            OffLookup.Offline
        } catch (e: SerializationException) {
            OffLookup.Offline
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
    }
}
