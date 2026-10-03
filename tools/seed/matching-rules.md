# Ingredient matching rules

Rules for mapping a cookbook ingredient to a Plantry ingredient. Used by the seed job and meant to
be reused in the app's Claude prompt for new ingredients. Refined during the calibration round.

## USDA entry (SR Legacy)

1. **Raw over cooked**, unless the book says otherwise ("gekochter Reis", "gegarte Linsen").
2. **Unsalted / "without salt"** whenever USDA has both.
3. **Legumes**: *canned, drained* only when the book says canned ("aus der Dose") or the amount is
   clearly a drained can; otherwise *mature seeds, raw*.
4. **Plainest form**: no "enriched", "fortified", brand names or "prepared with …".
5. **One ingredient per form that appears in the books**: dry and canned chickpeas are two
   ingredients. Buy-as links only where one form is bought to make the other (cooked rice → dry rice).
6. **Name**: German everyday name, e.g. "Kichererbsen (Dose)".

## Plant points

| Points | What counts |
|---|---|
| 1 | Vegetables, fruit, legumes (incl. tofu, tempeh), whole grains, nuts, seeds, mushrooms — fresh, frozen, canned or dried |
| ¼ | Herbs (fresh or dried), spices, garlic, ginger, chili |
| 0 | Oils (incl. olive oil), refined grains (white rice, white pasta, white flour, couscous), sugar, animal products, salt, plain stock |

## Store section

Existing five sections, in this order: Obst & Gemüse · Kühlregal · Trockenware · Tiefkühl · Sonstiges.

## Calibration log

<!-- Picks from the calibration round and the rules derived from them. -->
