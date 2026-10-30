package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CookingStepsTest {

    @Test
    fun parse_oneStepPerLine_inOrder() {
        assertEquals(listOf("Reis kochen.", "Gemüse anbraten."), CookingSteps.parse("Reis kochen.\nGemüse anbraten."))
    }

    @Test
    fun parse_dropsEmptyLines_andTrims() {
        assertEquals(listOf("Reis kochen.", "Servieren."), CookingSteps.parse("\n  Reis kochen.  \n\n   \nServieren.\n"))
        assertEquals(emptyList<String>(), CookingSteps.parse(""))
        assertEquals(emptyList<String>(), CookingSteps.parse(" \n\n"))
    }

    @Test
    fun parse_stripsLeadingNumbering() {
        val pasted = "1. Reis kochen.\n2) Gemüse anbraten.\n 3 . Würzen.\n10.Servieren."
        assertEquals(listOf("Reis kochen.", "Gemüse anbraten.", "Würzen.", "Servieren."), CookingSteps.parse(pasted))
    }

    @Test
    fun parse_keepsNumbersThatAreNotNumbering() {
        assertEquals(
            listOf("200 g Reis kochen.", "15 Minuten backen.", "Bei 180 °C (Umluft) backen."),
            CookingSteps.parse("200 g Reis kochen.\n15 Minuten backen.\nBei 180 °C (Umluft) backen."),
        )
    }

    @Test
    fun parse_dropsALineThatIsOnlyANumber() {
        assertEquals(listOf("Reis kochen."), CookingSteps.parse("1.\nReis kochen."))
    }

    @Test
    fun text_isReadBackAsTheSameSteps() {
        val steps = listOf("Reis kochen.", "Gemüse anbraten.")
        assertEquals(steps, CookingSteps.parse(CookingSteps.text(steps)))
    }

    @Test
    fun converter_storesEmptyAsEmpty_andRoundTrips() {
        val converter = CookingSteps.Converter()
        assertEquals("", converter.toText(emptyList()))
        assertEquals(emptyList<String>(), converter.fromText(""))
        val steps = listOf("Reis kochen.", "Servieren.")
        assertEquals(steps, converter.fromText(converter.toText(steps)))
    }
}
