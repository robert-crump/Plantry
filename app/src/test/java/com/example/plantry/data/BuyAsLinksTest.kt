package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuyAsLinksTest {

    // 1 rice cooked -> 2 rice dry; 3 chickpeas canned -> 4 chickpeas dry; 5 salt
    private val links = mapOf(1L to 2L, 2L to null, 3L to 4L, 4L to null, 5L to null)

    @Test
    fun noLink_isValid() {
        assertNull(BuyAsLinks.validate(1, null, links))
    }

    @Test
    fun linkToUnlinkedIngredient_isValid() {
        assertNull(BuyAsLinks.validate(5, 2, links))
    }

    @Test
    fun linkIntoExistingChain_isValid() {
        assertNull(BuyAsLinks.validate(5, 1, links))
    }

    @Test
    fun selfLink_isRejected() {
        assertEquals(BuyAsError.SELF_LINK, BuyAsLinks.validate(2, 2, links))
    }

    @Test
    fun directCycle_isRejected() {
        assertEquals(BuyAsError.CYCLE, BuyAsLinks.validate(2, 1, links))
    }

    @Test
    fun indirectCycle_isRejected() {
        val chain = mapOf(1L to 2L, 2L to 3L, 3L to null)

        assertEquals(BuyAsError.CYCLE, BuyAsLinks.validate(3, 1, chain))
    }

    @Test
    fun changingAnExistingLink_ignoresItsOldTarget() {
        // 1 currently links to 2; relinking 1 to 4 must not be confused by the old link.
        assertNull(BuyAsLinks.validate(1, 4, links))
    }

    @Test
    fun preexistingCycleElsewhere_terminates() {
        val broken = mapOf(1L to 2L, 2L to 1L, 3L to null)

        assertNull(BuyAsLinks.validate(3, 1, broken))
    }

    @Test
    fun buyAsRoot_stopsOnCycle() {
        val a = ingredient(10, "A").copy(buyAsIngredientId = 11)
        val b = ingredient(11, "B").copy(buyAsIngredientId = 10)

        assertEquals(b, buyAsRoot(a, mapOf(a.id to a, b.id to b)))
    }
}
