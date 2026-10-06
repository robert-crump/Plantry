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
}
