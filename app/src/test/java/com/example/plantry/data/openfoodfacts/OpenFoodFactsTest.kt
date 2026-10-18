package com.example.plantry.data.openfoodfacts

import com.example.plantry.data.LabelSource
import com.example.plantry.data.Nutrient
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenFoodFactsTest {

    @Test
    fun completeProduct_readsNamesQuantityAndAllNutrients() {
        val body = """
            {"code":"4104420208117","status":1,"status_verbose":"product found","product":{
              "product_name":"Kichererbsen","product_name_de":"Bio Kichererbsen","generic_name_de":"Kichererbsen, gegart",
              "brands":"Alnatura, Alnatura GmbH","quantity":"400 g",
              "nutriments":{"energy-kcal_100g":128,"energy-kj_100g":536,"proteins_100g":6.96,"carbohydrates_100g":15.4,
                "sugars_100g":0.5,"fat_100g":2.1,"fiber_100g":"5,04","salt_100g":0.01}}}
        """.trimIndent()

        val product = OpenFoodFacts.parse("4104420208117", body)!!

        assertEquals("Kichererbsen, gegart", product.nameSuggestion)
        assertEquals("Bio Kichererbsen (Alnatura)", product.productName)
        assertEquals("400 g", product.quantity)
        assertEquals(400.0, product.packageGrams!!, 0.0)
        assertEquals(
            mapOf(
                Nutrient.KCAL to 128.0,
                Nutrient.PROTEIN to 7.0,
                Nutrient.CARBS to 15.4,
                Nutrient.SUGAR to 0.5,
                Nutrient.FAT to 2.1,
                Nutrient.FIBRE to 5.0,
            ),
            product.nutrition,
        )
        assertEquals(emptySet<Nutrient>(), product.missing)
        assertEquals(LabelSource("Bio Kichererbsen (Alnatura)", "4104420208117"), product.labelSource)
    }

    @Test
    fun missingValues_stayMissingInsteadOfZero() {
        val body = """
            {"status":1,"product":{"product_name":"Haferflocken zart","brands":"ja!",
              "nutriments":{"energy-kcal_100g":372,"proteins_100g":13.5,"fat_100g":7,"fiber_100g":null}}}
        """.trimIndent()

        val product = OpenFoodFacts.parse("4388860123456", body)!!

        assertEquals("Haferflocken zart", product.nameSuggestion)
        assertEquals("Haferflocken zart (ja!)", product.productName)
        assertNull(product.quantity)
        assertNull(product.packageGrams)
        assertEquals(setOf(Nutrient.CARBS, Nutrient.SUGAR, Nutrient.FIBRE), product.missing)
        assertEquals(372.0, product.nutrition.getValue(Nutrient.KCAL), 0.0)
    }

    @Test
    fun kilojouleOnlyEnergy_isConvertedToKcal() {
        val kjField = """{"status":1,"product":{"product_name":"Linsen","nutriments":{"energy-kj_100g":1400}}}"""
        val energyField = """{"status":1,"product":{"product_name":"Linsen","nutriments":{"energy_100g":"1400"}}}"""

        assertEquals(335.0, OpenFoodFacts.parse("1", kjField)!!.nutrition.getValue(Nutrient.KCAL), 0.0)
        assertEquals(335.0, OpenFoodFacts.parse("1", energyField)!!.nutrition.getValue(Nutrient.KCAL), 0.0)
    }

    @Test
    fun productWithoutNutriments_hasEverythingMissing() {
        val product = OpenFoodFacts.parse("1", """{"status":1,"product":{"product_name":"Tofu natur"}}""")!!

        assertEquals(Nutrient.entries.toSet(), product.missing)
    }

    @Test
    fun packageQuantity_prefersOffsNormalizedAmount() {
        val body = """{"status":1,"product":{"product_name":"Hafermilch","quantity":"1 l",
            "product_quantity":"1000","product_quantity_unit":"ml"}}"""

        assertEquals(1000.0, OpenFoodFacts.parse("1", body)!!.packageGrams!!, 0.0)
    }

    @Test
    fun packageQuantity_fromQuantityText() {
        fun grams(quantity: String) =
            OpenFoodFacts.parse("1", """{"status":1,"product":{"product_name":"X","quantity":"$quantity"}}""")!!.packageGrams

        assertEquals(500.0, grams("500g")!!, 0.0)
        assertEquals(750.0, grams("0,75 kg")!!, 0.0)
        assertEquals(1500.0, grams("1.5 L")!!, 0.0)
        assertEquals(330.0, grams("33 cl")!!, 0.0)
        assertNull(grams("6 x 125 g"))
        assertNull(grams("8 Stück"))
    }

    @Test
    fun packageQuantity_inAnotherUnit_fallsBackToText() {
        val body = """{"status":1,"product":{"product_name":"X","quantity":"250 g","product_quantity":"8","product_quantity_unit":"pcs"}}"""

        assertEquals(250.0, OpenFoodFacts.parse("1", body)!!.packageGrams!!, 0.0)
    }

    @Test
    fun notFound_isNull() {
        val body = """{"code":"0000000000000","status":0,"status_verbose":"product not found"}"""

        assertNull(OpenFoodFacts.parse("0000000000000", body))
    }

    @Test
    fun productWithoutAnyName_isNull() {
        assertNull(OpenFoodFacts.parse("1", """{"status":1,"product":{"nutriments":{"fat_100g":1}}}"""))
    }

    @Test(expected = SerializationException::class)
    fun notJson_throws() {
        OpenFoodFacts.parse("1", "<html>Bad gateway</html>")
    }

    @Test
    fun searchResponse_readsHitsWithBrandListAndNutrients() {
        val body = """
            {"hits":[
              {"code":"4012359113108","brands":["taifun","Life Food"],"quantity":"200g",
               "product_name":"Smoked tofu","product_name_de":"Räuchertofu Mandel-Sesam",
               "nutriments":{"energy-kcal_100g":198,"proteins_100g":19,"carbohydrates_100g":1.5,"sugars_100g":0.5,"fat_100g":12}},
              {"code":"26032832","brands":[],"product_name_de":"Räuchertofu","nutriments":{}}
            ],"page":1,"page_size":20,"count":2}
        """.trimIndent()

        val products = OpenFoodFacts.parseSearch(body)

        assertEquals(listOf("4012359113108", "26032832"), products.map { it.barcode })
        val first = products[0]
        assertEquals("Räuchertofu Mandel-Sesam (taifun)", first.productName)
        assertEquals(200.0, first.packageGrams!!, 0.0)
        assertEquals(198.0, first.nutrition.getValue(Nutrient.KCAL), 0.0)
        assertEquals(setOf(Nutrient.FIBRE), first.missing)
        assertEquals("Räuchertofu", products[1].productName)
        assertEquals(Nutrient.entries.toSet(), products[1].missing)
    }

    @Test
    fun germanProductName_winsOverUntranslatedGenericName() {
        val body = """
            {"status":1,"product":{"product_name":"Smoked tofu","product_name_de":"Räuchertofu Mandel-Sesam",
              "generic_name":"Tofu Fumé Amandes-Sésame","brands":"Taifun"}}
        """.trimIndent()

        assertEquals("Räuchertofu Mandel-Sesam", OpenFoodFacts.parse("4012359113108", body)!!.nameSuggestion)
    }

    @Test
    fun searchResponse_skipsHitsWithoutBarcodeOrNameAndRepeats() {
        val body = """
            {"hits":[
              {"code":"4056489488774","product_name":"Räuchertofu"},
              {"product_name":"Ohne Barcode"},
              {"code":"abc","product_name":"Kein Barcode"},
              {"code":"4311501012215","nutriments":{"energy-kcal_100g":150}},
              {"code":"4056489488774","product_name":"Räuchertofu"}
            ]}
        """.trimIndent()

        assertEquals(listOf("4056489488774"), OpenFoodFacts.parseSearch(body).map { it.barcode })
    }

    @Test
    fun searchResponse_withoutHits_isEmpty() {
        assertEquals(emptyList<OffProduct>(), OpenFoodFacts.parseSearch("""{"detail":"nothing"}"""))
    }

    @Test(expected = SerializationException::class)
    fun searchResponse_noJsonObject_throws() {
        OpenFoodFacts.parseSearch("[]")
    }

    @Test
    fun searchQueries_oneWord_isThePlainQuery() {
        assertEquals(listOf("Haferflocken"), OpenFoodFacts.searchQueries(" Haferflocken "))
    }

    @Test
    fun searchQueries_severalWords_tryEachAsBrandFirst() {
        assertEquals(
            listOf("brands:\"taifun\" räuchertofu", "brands:\"räuchertofu\" taifun", "taifun räuchertofu"),
            OpenFoodFacts.searchQueries("taifun räuchertofu"),
        )
    }

    @Test
    fun searchQueries_onlyTheFirstFourWordsAsBrand() {
        assertEquals(5, OpenFoodFacts.searchQueries("a b c d e").size)
    }

    @Test
    fun searchQueries_operatorsCountAsSpaces() {
        assertEquals("Müller Milch light", OpenFoodFacts.searchQueries("  Müller: Milch (light) ").last())
        assertEquals("brands:\"Mandel\" Räuchertofu Sesam", OpenFoodFacts.searchQueries("Räuchertofu \"Mandel-Sesam\"")[1])
        assertEquals(emptyList<String>(), OpenFoodFacts.searchQueries("\"*?\""))
    }
}
