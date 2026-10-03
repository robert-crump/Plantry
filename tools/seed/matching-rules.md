# Ingredient matching rules

Rules for mapping a cookbook ingredient to a Plantry ingredient. Used by the seed job and, condensed,
by the app's Claude prompt for new ingredients (`app/.../data/claude/MatchingRules.kt`); change both
together. Refined during the calibration round.

## USDA entry (SR Legacy)

1. **Raw over cooked**, unless the book says otherwise ("gekochter Reis", "gegarte Linsen").
2. **Unsalted / "without salt"** whenever USDA has both.
3. **Legumes**: beans and chickpeas default to *canned, drained solids*, the way they're bought;
   *mature seeds, raw* only when the book says dried ("getrocknet", "über Nacht einweichen").
   Lentils and split peas default to dried (*raw*). When a recipe uses the can liquid too
   (blended), use the *canned* entry with solids and liquids and the full can weight.
4. **Plainest form**: no "enriched", "fortified", brand names or "prepared with …".
   Prefer *unenriched* for rice, pasta and cornmeal.
5. **One ingredient per form that appears in the books**: dry and canned chickpeas are two
   ingredients. Buy-as links only where one form is bought to make the other (cooked rice → dry rice).
6. **Name**: German everyday name, e.g. "Kichererbsen (Dose)".
7. **No exact variety in USDA**: take the nearest relative by nutrition, not by name
   (Hokkaido → butternut squash, not "pumpkin").
8. **Not in USDA at all** (branded or composite products such as Halloumi, Gnocchi, Kokosjoghurt,
   protein pastas, Avocadocreme): nutrition from a typical German package label, no USDA link.
   Don't stretch a base food to cover a product, even a single-ingredient one.
9. **What German shops sell**: farmed Atlantic salmon, firm tofu, Greek yogurt whole milk,
   80 % margarine, parmesan as a block.
10. **Juice** is its own ingredient, bought as the fruit: Zitronensaft → Zitrone, yield factor 2.2.
11. **Stock** is the ready-to-serve broth, in ml of prepared broth, and a staple; the powder is
    not tracked.

## Plant points

| Points | What counts |
|---|---|
| 1 | Vegetables, fruit, legumes (incl. tofu, tempeh), whole grains, nuts, seeds, mushrooms — fresh, frozen, canned or dried |
| ¼ | Herbs (fresh or dried), spices, garlic, ginger, chili |
| 0 | Oils (incl. olive oil and coconut oil), refined grains (white rice, white pasta, white flour, couscous, degermed polenta, gnocchi), sugar, animal products, salt, plain stock, condiments used by the spoon (Tomatenmark, Sojasauce) |

Edge cases from calibration: Kokosmilch and Kokosraspeln 1 · passierte and stückige Tomaten 1 ·
Tahini, Erdnussbutter, Miso 1 · Avocadocreme 1 · Rote-Linsen- and Kichererbsen-Nudeln 1 ·
Harissa and Za'atar ¼ · Tomatenmark 0 · Sojasauce 0.

## Store section

Existing five sections, in this order: Obst & Gemüse · Kühlregal · Trockenware · Tiefkühl · Sonstiges.

## Calibration log

Round 1, 2026-10-03 (20 items; ★ = proposed pick, all accepted unless noted).

| # | Ingredient | Pick | Rule |
|---|---|---|---|
| 1 | Hokkaido-Kürbis | Squash, winter, butternut, raw | 7 |
| 2 | Beans in an index, no form | canned, drained solids | 3 |
| 3 | Limabohnen blended with liquid | Lima beans, large, mature seeds, canned; full 400 g | 3 |
| 4 | Zitronensaft | Lemon juice, raw; buy-as Zitrone × 2.2 | 10 |
| 5 | Gemüsebrühe | Soup, vegetable broth, ready to serve; staple | 11 |
| 6 | Sojaschnetzel | Soy flour, defatted | 7 |
| 7 | Griechischer Joghurt | Yogurt, Greek, plain, whole milk | 9 |
| 8 | Tofu | Tofu, raw, firm, prepared with calcium sulfate | 9 |
| 9 | Margarine | Margarine, regular, 80% fat, without salt | 2, 9 |
| 10 | Parmesan | Cheese, parmesan, hard | 9 |
| 11 | Lachs | Fish, salmon, Atlantic, farmed, raw | 9 |
| 12 | Räucherfisch | Fish, salmon, chinook, smoked | 7 |
| 13 | Halloumi, Gnocchi, Kokosjoghurt | label values, no USDA link | 8 |
| 14 | Rote-Linsen-, Kichererbsen-Nudeln | **label values** (not the base food) | 8 |
| 15 | Avocadocreme | **label values** (not avocado) | 8 |
| 16 | Polenta | Cornmeal, degermed, unenriched, yellow; 0 points | 4 |
| 17 | Reis | Basmati, Jasmin → long-grain white; Risotto → medium-grain white; Naturreis → brown | 4, 5 |
| 18 | Spaghetti, Lasagneblätter | Pasta, dry, unenriched; 0 points | 4 |
| 19 | Plant-point edge cases | as proposed, **except Tomatenmark 0** | points |
| 20 | Garnelen | keep; Crustaceans, shrimp, raw | — |
