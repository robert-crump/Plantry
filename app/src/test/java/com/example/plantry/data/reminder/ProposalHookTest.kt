package com.example.plantry.data.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ProposalHookTest {

    private val today = LocalDate.of(2026, 10, 28)

    /** By default none applies: far from the goal, cooked within the cooldown, little protein. */
    private fun hook(
        weekPoints: Double = 10.0,
        newPlantPoints: Double = 3.0,
        lastCooked: LocalDate? = today.minusDays(5),
        cooldownDays: Int = 14,
        protein: Double = 20.0,
    ) = ProposalHook.of(weekPoints, newPlantPoints, lastCooked, today, cooldownDays, protein)

    @Test
    fun noneApplies() = assertNull(hook())

    @Test
    fun weeklyGoal_whenCookingItReachesThirty() = assertEquals(ProposalHook.WeeklyGoal, hook(weekPoints = 26.0, newPlantPoints = 4.0))

    @Test
    fun weeklyGoal_notWhenAlreadyReached() = assertNull(hook(weekPoints = 31.0, newPlantPoints = 4.0))

    @Test
    fun weeklyGoal_notWhenStillShort() = assertNull(hook(weekPoints = 25.0, newPlantPoints = 4.0))

    @Test
    fun new_whenNeverCooked() = assertEquals(ProposalHook.New, hook(lastCooked = null))

    @Test
    fun longAgo_fromTwiceTheCooldown() {
        assertEquals(ProposalHook.LongAgo(28), hook(lastCooked = today.minusDays(28), cooldownDays = 14))
        assertNull(hook(lastCooked = today.minusDays(27), cooldownDays = 14))
    }

    @Test
    fun longAgo_followsTheCooldownSetting() {
        assertEquals(ProposalHook.LongAgo(20), hook(lastCooked = today.minusDays(20), cooldownDays = 10))
        assertNull(hook(lastCooked = today.minusDays(20), cooldownDays = 11))
    }

    @Test
    fun proteinPower_fromThirtyGramsRounded() {
        assertEquals(ProposalHook.ProteinPower(30), hook(protein = 29.5))
        assertNull(hook(protein = 29.4))
    }

    @Test
    fun priority_weeklyGoalBeatsNew() =
        assertEquals(ProposalHook.WeeklyGoal, hook(weekPoints = 26.0, newPlantPoints = 4.0, lastCooked = null, protein = 40.0))

    @Test
    fun priority_newBeatsProteinPower() = assertEquals(ProposalHook.New, hook(lastCooked = null, protein = 40.0))

    @Test
    fun priority_longAgoBeatsProteinPower() =
        assertEquals(ProposalHook.LongAgo(60), hook(lastCooked = today.minusDays(60), protein = 40.0))

    @Test
    fun ids_areStable() = assertEquals(
        listOf("weekly_goal", "new", "long_ago", "protein_power"),
        listOf(ProposalHook.WeeklyGoal, ProposalHook.New, ProposalHook.LongAgo(30), ProposalHook.ProteinPower(32)).map { it.id },
    )

    @Test
    fun text_hookInFrontOfTheName() = assertEquals(
        ProposalText("Protein-Power: Linsen-Dal", "32 g Eiweiß pro Portion.\n🌱 +5 neu · 32 g Eiweiß"),
        ProposalText.of("Linsen-Dal", "Protein-Power", "32 g Eiweiß pro Portion.", "🌱 +5 neu · 32 g Eiweiß"),
    )

    @Test
    fun text_withoutHook_isTheNameOverTheFacts() =
        assertEquals(ProposalText("Linsen-Dal", "🌱 +5 neu"), ProposalText.of("Linsen-Dal", null, null, "🌱 +5 neu"))

    @Test
    fun text_longName_movesTheHookIntoTheExpandedText() {
        val name = "Ofengemüse mit Kichererbsen und Tahini"
        assertEquals(
            ProposalText(name, "Lange nicht gegessen: Zuletzt gekocht vor sechs Wochen.\n🌱 +5 neu"),
            ProposalText.of(name, "Lange nicht gegessen", "Zuletzt gekocht vor sechs Wochen.", "🌱 +5 neu"),
        )
    }

    @Test
    fun text_titleUpToTheLimitStaysCombined() {
        val hook = "Mal was Neues"
        val name = "x".repeat(ProposalText.MAX_TITLE_LENGTH - hook.length - 2)
        assertEquals("$hook: $name", ProposalText.of(name, hook, "Noch nie gekocht.", "").title)
        assertEquals(name + "x", ProposalText.of(name + "x", hook, "Noch nie gekocht.", "").title)
    }

    @Test
    fun duration_inFriendlyWords() {
        assertEquals("vor zwei Tagen", FriendlyDuration.ago(2))
        assertEquals("vor dreizehn Tagen", FriendlyDuration.ago(13))
        assertEquals("vor zwei Wochen", FriendlyDuration.ago(14))
        assertEquals("vor sechs Wochen", FriendlyDuration.ago(43))
        assertEquals("vor acht Wochen", FriendlyDuration.ago(59))
        assertEquals("vor zwei Monaten", FriendlyDuration.ago(60))
        assertEquals("vor sechs Monaten", FriendlyDuration.ago(182))
        assertEquals("vor über einem halben Jahr", FriendlyDuration.ago(183))
        assertEquals("vor über einem Jahr", FriendlyDuration.ago(400))
        assertEquals("vor über zwei Jahren", FriendlyDuration.ago(800))
    }
}
