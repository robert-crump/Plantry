package com.example.plantry.data

import kotlin.math.roundToLong

/**
 * Grams read from the amount at the start of a recipe line: [count] pieces of [gramsPerPiece]
 * each, or grams as written when [gramsPerPiece] is null ([count] is then the grams).
 */
data class ParsedAmount(val count: Double, val gramsPerPiece: Double?) {
    /** Rounded to 0.1 g, like the grams of a line are entered. */
    val grams: Double get() = ((gramsPerPiece?.let { count * it } ?: count) * 10).roundToLong() / 10.0
}

/**
 * Reads the amount a recipe line starts with, e.g. "4 Scheiben Toast" or "150 g Mehl". Grams and
 * kilograms work for every ingredient; a count needs the ingredient's [PieceWeight], and its unit
 * word must be the piece unit (no unit word at all counts as [PieceUnit.PIECE]).
 */
object AmountParser {

    fun parse(text: String, pieceWeight: PieceWeight?): ParsedAmount? {
        val (count, rest) = readNumber(text.trim()) ?: return null
        if (!(count > 0) || count.isInfinite()) return null
        val word = WORD.find(rest)?.takeIf { it.range.first == 0 }?.value?.removeSuffix(".")
        val key = word?.let(::normalize)
        when (key) {
            in GRAMS -> return ParsedAmount(count, gramsPerPiece = null)
            in KILOGRAMS -> return ParsedAmount(count * 1000, gramsPerPiece = null)
            in STOP_WORDS -> return null
        }
        pieceWeight ?: return null
        val unit = key?.let(UNITS::get) ?: PieceUnit.PIECE
        return if (unit == pieceWeight.pieceUnit) ParsedAmount(count, pieceWeight.gramsPerPiece) else null
    }

    /** The number at the start of [text] and what follows it, without the spaces between. */
    private fun readNumber(text: String): Pair<Double, String>? {
        val (first, afterFirst) = readSingle(text) ?: return null
        val range = RANGE.find(afterFirst)?.takeIf { it.range.first == 0 }
        val second = range?.let { readSingle(afterFirst.substring(it.range.last + 1)) }
        val (count, rest) = if (second != null) (first + second.first) / 2 to second.second else first to afterFirst
        return count to rest.trimStart()
    }

    private fun readSingle(text: String): Pair<Double, String>? {
        for ((pattern, value) in NUMBERS) {
            val match = pattern.find(text)?.takeIf { it.range.first == 0 } ?: continue
            return value(match.groupValues) to text.substring(match.range.last + 1)
        }
        return null
    }

    private fun fraction(numerator: String, denominator: String): Double {
        val d = denominator.toDouble()
        return if (d == 0.0) Double.NaN else numerator.toDouble() / d
    }

    private val VULGAR = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)
    private const val V = "[½¼¾⅓⅔]"

    /** Tried in order, so mixed numbers and fractions come before the plain number they start with. */
    private val NUMBERS: List<Pair<Regex, (List<String>) -> Double>> = listOf(
        Regex("""(\d+)\s+(\d+)/(\d+)""") to { g -> g[1].toDouble() + fraction(g[2], g[3]) },
        Regex("""(\d+)\s*($V)""") to { g -> g[1].toDouble() + VULGAR.getValue(g[2][0]) },
        Regex("""(\d+)/(\d+)""") to { g -> fraction(g[1], g[2]) },
        Regex("""($V)""") to { g -> VULGAR.getValue(g[1][0]) },
        Regex("""(\d+(?:[.,]\d+)?)""") to { g -> g[1].replace(',', '.').toDouble() },
        Regex("""(?i)(eine[nr]?|ein|one|an|a)(?=\s)""") to { _ -> 1.0 },
    )

    private val RANGE = Regex("""\s*(?:-|–|\s+bis\s+)\s*""")

    private val WORD = Regex("""\p{L}+\.?""")

    /** Lower case without umlauts, so "Blätter", "Blaetter" and "Blatter" are one word. */
    private fun normalize(word: String) = word.lowercase()
        .replace("ae", "a").replace("oe", "o").replace("ue", "u")
        .replace('ä', 'a').replace('ö', 'o').replace('ü', 'u').replace("ß", "ss")

    private val GRAMS = setOf("g", "gr", "gramm", "gram", "grams")
    private val KILOGRAMS = setOf("kg")

    /** Units that are no piece of anything, so the line is left to the user. */
    private val STOP_WORDS = listOf(
        "mg", "ml", "cl", "dl", "l", "liter", "el", "tl", "essloffel", "teeloffel", "prise", "prisen", "msp",
        "messerspitze", "tasse", "tassen", "handvoll", "schuss", "spritzer", "tropfen",
        "tbsp", "tsp", "tablespoon", "tablespoons", "teaspoon", "teaspoons", "cup", "cups", "oz", "lb", "lbs",
        "pinch", "dash", "handful",
    ).map(::normalize).toSet()

    /** The German singular of [unit] as a recipe would write it, e.g. "Scheibe". */
    fun word(unit: PieceUnit): String = WORDS.getValue(unit).substringBefore(' ')

    /** Every word a recipe may use for a unit; the German singular first. */
    private val WORDS: Map<PieceUnit, String> = mapOf(
        PieceUnit.PIECE to "Stück Stk Stck piece pieces pc pcs",
        PieceUnit.SLICE to "Scheibe Scheiben slice slices",
        PieceUnit.CLOVE to "Zehe Zehen clove cloves",
        PieceUnit.LEAF to "Blatt Blätter leaf leaves",
        PieceUnit.SPRIG to "Zweig Zweige sprig sprigs",
        PieceUnit.STALK to "Stange Stangen Stängel stalk stalks stick sticks",
        PieceUnit.BULB to "Knolle Knollen bulb bulbs",
        PieceUnit.HEAD to "Kopf Köpfe head heads",
        PieceUnit.BUNCH to "Bund Bünde bunch bunches",
        PieceUnit.CAN to "Dose Dosen can cans tin tins",
        PieceUnit.JAR to "Glas Gläser jar jars",
        PieceUnit.TUB to "Becher tub tubs pot pots",
        PieceUnit.PACKET to "Päckchen Packung Packungen packet packets pack packs",
    )

    private val UNITS: Map<String, PieceUnit> =
        WORDS.flatMap { (unit, words) -> words.split(' ').map { normalize(it) to unit } }.toMap()
}
