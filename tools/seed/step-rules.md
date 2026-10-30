# Cooking step rules

How a recipe's cooking steps (Zubereitung) are written, in the concise style of Pick Up Limes.
Used by the app's Claude prompts for Kürzen, the scan and "Aus Fotos lesen"
(`app/.../data/claude/StepRules.kt`); change both together. The seed job may use them when it
reads steps, but needn't.

1. **Original language**: keep the language of the source steps; never translate.
2. **Usually 3–5 steps**, no hard cap. Merge steps that happen in parallel or in the same pan;
   split only at a real wait (baking, simmering, resting).
3. **At most two sentences per step**, infinitive style: "Ofen auf 200 °C vorheizen.",
   "Zwiebel glasig anschwitzen, dann Knoblauch 1 Min. mitbraten."
4. **Keep**: temperatures, times, doneness cues ("bis goldbraun"), the pan or tool where it
   matters, how ingredients are split ("die Hälfte des Dressings").
5. **Drop**: ingredient amounts (they're in the ingredient list), background explanations,
   serving suggestions, storage tips, optional variations.
6. **Already concise** (about 5 short steps or fewer): tidy only, never pad.
7. **No method present**: no steps, never invented.
8. One step per entry, without numbering.
