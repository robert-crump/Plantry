package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChipSelectionTest {

    private data class Chip(val name: String, override val group: Any? = null) : FilterChoice

    private val main = Chip("Hauptgericht", "dish")
    private val snack = Chip("Snack", "dish")
    private val lentils = Chip("Linsen")
    private val spinach = Chip("Spinat")

    @Test
    fun chipsAreActiveInActivationOrder() =
        assertEquals(listOf(spinach, lentils), ChipSelection<Chip>().toggle(spinach).toggle(lentils).active)

    @Test
    fun togglingAgain_switchesOff() =
        assertEquals(listOf(lentils), ChipSelection<Chip>().toggle(spinach).toggle(lentils).toggle(spinach).active)

    @Test
    fun sameGroup_newChipTakesTheReplacedChipsSlot() {
        val selection = ChipSelection<Chip>().toggle(main).toggle(lentils).toggle(snack)

        assertEquals(listOf(snack, lentils), selection.active)
    }
}
