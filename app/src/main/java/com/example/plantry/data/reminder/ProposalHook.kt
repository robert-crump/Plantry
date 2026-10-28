package com.example.plantry.data.reminder

import com.example.plantry.data.RecipeHighlight
import com.example.plantry.data.planner.plantPointsGoalReachedWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.random.Random

/** What leads the proposal notification's title: a [ProposalHook] or a [MoodHook]. [id] is what the ProposalEvent log stores. */
sealed interface TitleHook {
    val id: String

    companion object {
        /**
         * Today's title hook: a coin toss between the [factual] hook and a mood hook when both are
         * eligible, else whichever is, else none. Never the [lastId] again; a mood hook only on its
         * weekday and only when its title for [name] fits uncut. Without one, a [factual] hook still
         * goes in the expanded text.
         */
        fun choose(
            factual: ProposalHook?,
            moods: List<MoodHook>,
            name: String,
            today: LocalDate,
            lastId: String?,
            random: Random,
        ): TitleHook? {
            val fact = factual?.takeIf { it.id != lastId }
            val fitting = moods.filter {
                it.id != lastId &&
                    (it.weekday == null || it.weekday == today.dayOfWeek) &&
                    it.title(name).length <= ProposalText.MAX_TITLE_LENGTH
            }
            return when {
                fitting.isEmpty() -> fact
                fact != null && random.nextBoolean() -> fact
                else -> fitting.random(random)
            }
        }
    }
}

/**
 * Why this recipe, in front of its name in the proposal notification: a fact that is true for the
 * suggester's pick. It never changes which recipe is picked. [id] is what the ProposalEvent log stores.
 */
sealed class ProposalHook(override val id: String) : TitleHook {
    /** "Wochenziel in Reichweite": cooking it takes this week to the plant-points goal. */
    data object WeeklyGoal : ProposalHook("weekly_goal")

    /** "Mal was Neues": never cooked. */
    data object New : ProposalHook("new")

    /** "Lange nicht gegessen": last cooked [days] ago, at least twice the cooldown. */
    data class LongAgo(val days: Long) : ProposalHook("long_ago")

    /** "Protein-Power": [grams] protein per portion, as the recipe's Protein-Power tag judges it. */
    data class ProteinPower(val grams: Int) : ProposalHook("protein_power")

    companion object {
        /**
         * The strongest hook that applies, in the order of the classes above; null when none does.
         * [lastCooked] is null for a recipe never cooked.
         */
        fun of(
            weekPoints: Double,
            newPlantPoints: Double,
            lastCooked: LocalDate?,
            today: LocalDate,
            cooldownDays: Int,
            proteinPerPortion: Double,
        ): ProposalHook? {
            if (plantPointsGoalReachedWith(weekPoints, newPlantPoints)) return WeeklyGoal
            if (lastCooked == null) return New
            val days = ChronoUnit.DAYS.between(lastCooked, today)
            if (days >= 2L * cooldownDays) return LongAgo(days)
            val protein = proteinPerPortion.roundToInt()
            if (protein >= RecipeHighlight.PROTEIN_MIN) return ProteinPower(protein)
            return null
        }
    }
}

/**
 * A title with no fact behind it, so the proposal doesn't read the same every day: "Lust auf
 * {name}?". Only on [weekday] when it has one.
 */
data class MoodHook(val key: String, val template: String, val weekday: DayOfWeek? = null) : TitleHook {
    override val id: String get() = "mood_$key"

    fun title(name: String): String = template.replace(NAME, name)

    companion object {
        const val NAME = "{name}"

        private val WEEKDAYS = listOf("MO", "DI", "MI", "DO", "FR", "SA", "SO")

        /** "key|template", or "key|MO|template" for one only on Mondays (MO DI MI DO FR SA SO), as strings.xml lists them. */
        fun parse(item: String): MoodHook {
            val parts = item.split("|")
            return when (parts.size) {
                2 -> MoodHook(parts[0], parts[1])
                3 -> MoodHook(parts[0], parts[2], DayOfWeek.of(WEEKDAYS.indexOf(parts[1]).also { require(it >= 0) { item } } + 1))
                else -> throw IllegalArgumentException(item)
            }
        }
    }
}

/** The proposal notification's collapsed title and its expanded (BigText) text. */
data class ProposalText(val title: String, val expanded: String) {
    companion object {
        /**
         * Roughly what a collapsed notification title shows on a phone before it is cut off. Counted
         * in characters, so it is a guess that errs on the short side.
         */
        const val MAX_TITLE_LENGTH = 40

        /**
         * "{hook}: {name}" over the hook's [sentence] and the [facts] line. When that title would be
         * cut off, the title is the plain [name] and the hook leads the expanded text instead. Without
         * a [hook], just the name over the facts.
         */
        fun of(name: String, hook: String?, sentence: String?, facts: String): ProposalText {
            if (hook == null || sentence == null) return ProposalText(name, facts)
            val title = "$hook: $name"
            return if (title.length <= MAX_TITLE_LENGTH) ProposalText(title, "$sentence\n$facts") else behind(name, hook, sentence, facts)
        }

        /** The plain [name] over "{hook}: {sentence}" and the [facts]: the hook only in the expanded text. */
        fun behind(name: String, hook: String, sentence: String, facts: String) = ProposalText(name, "$hook: $sentence\n$facts")

        /** The [mood] hook's title for [name] over the factual hook's [sentence], if any, and the [facts]. */
        fun mood(name: String, mood: MoodHook, sentence: String?, facts: String) =
            ProposalText(mood.title(name), if (sentence == null) facts else "$sentence\n$facts")
    }
}

/** Spans of time in friendly words, as the app's German speaks them. */
object FriendlyDuration {

    /**
     * "vor zehn Tagen", "vor sechs Wochen", "vor zwei Monaten", "vor über einem halben Jahr", "vor
     * über einem Jahr": rounded down, never a count like "vor 43 Tagen".
     */
    fun ago(days: Long): String = when {
        days < 1 -> "heute"
        days == 1L -> "vor einem Tag"
        days < 14 -> "vor ${number(days)} Tagen"
        days < 60 -> "vor ${number(days / 7)} Wochen"
        days < 183 -> "vor ${number(days / 30)} Monaten"
        days < 365 -> "vor über einem halben Jahr"
        days < 730 -> "vor über einem Jahr"
        else -> "vor über ${number(days / 365)} Jahren"
    }

    private val NUMBERS = listOf("zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwölf", "dreizehn")

    /** Two to thirteen in words, so every count of days reads as a word; more in digits. Never called with less than two. */
    private fun number(n: Long): String = if (n in 2..13) NUMBERS[n.toInt() - 2] else n.toString()
}
