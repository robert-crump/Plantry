package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChipSelectionTest {

    private data class Chip(val name: String, override val group: Any? = null) : FilterChoice

    private val reviewed = Chip("Geprüft", "review")
    private val unreviewed = Chip("Ungeprüft", "review")
    private val used = Chip("Verwendet", "usage")
    private val seed = Chip("Mitgeliefert", "origin")
    private val all = listOf(reviewed, unreviewed, used, seed)

    private fun ChipSelection<Chip>.names() = arranged(all).map { it.name }

    @Test
    fun nothingActive_keepsHomeOrder() =
        assertEquals(listOf("Geprüft", "Ungeprüft", "Verwendet", "Mitgeliefert"), ChipSelection<Chip>().names())

    @Test
    fun activatedChips_comeFirstInActivationOrder() {
        val selection = ChipSelection<Chip>().toggle(seed).toggle(unreviewed)

        assertEquals(listOf(seed, unreviewed), selection.active)
        assertEquals(listOf("Mitgeliefert", "Ungeprüft", "Geprüft", "Verwendet"), selection.names())
    }

    @Test
    fun deactivatedChip_goesBackHome_laterOnesMoveUp() {
        val selection = ChipSelection<Chip>().toggle(seed).toggle(unreviewed).toggle(seed)

        assertEquals(listOf("Ungeprüft", "Geprüft", "Verwendet", "Mitgeliefert"), selection.names())
    }

    @Test
    fun sameGroup_newChipTakesTheReplacedChipsSlot() {
        val selection = ChipSelection<Chip>().toggle(unreviewed).toggle(used).toggle(reviewed)

        assertEquals(listOf(reviewed, used), selection.active)
        assertEquals(listOf("Geprüft", "Verwendet", "Ungeprüft", "Mitgeliefert"), selection.names())
    }

    @Test
    fun ungroupedChips_combineFreely() {
        val a = Chip("Linsen")
        val b = Chip("Spinat")

        assertEquals(listOf(a, b), ChipSelection<Chip>().toggle(a).toggle(b).active)
    }
}
