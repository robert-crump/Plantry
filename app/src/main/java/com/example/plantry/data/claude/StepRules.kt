package com.example.plantry.data.claude

/**
 * How Claude writes a recipe's cooking steps, in the concise style of Pick Up Limes: used by
 * Kürzen, and later by the scan and "Aus Fotos lesen". The same rules are in
 * `tools/seed/step-rules.md`; change both together.
 */
object StepRules {

    val RULES = """
        Cooking steps:
        - Keep the language of the original steps; never translate.
        - Usually 3 to 5 steps, no hard limit. Merge steps that happen at the same time or in the
          same pan; start a new step only at a real wait (baking, simmering, resting).
        - At most two sentences per step, in the infinitive style of German recipes, e.g.
          "Ofen auf 200 °C vorheizen." or "Zwiebel glasig anschwitzen, dann Knoblauch 1 Min.
          mitbraten."
        - Keep temperatures, times, doneness cues ("bis goldbraun"), the pan or tool where it
          matters, and how an ingredient is split ("die Hälfte des Dressings").
        - Leave out ingredient amounts (they are in the ingredient list), background explanations,
          serving suggestions, storage tips and optional variations.
        - Steps that are already concise (about 5 short steps or fewer): only tidy them; never pad
          them or add anything.
        - No method in the source: no steps. Never invent steps.
        - Each step is one entry, without numbering.
    """.trimIndent()
}
