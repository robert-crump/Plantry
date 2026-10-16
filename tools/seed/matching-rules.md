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
   ingredients.
6. **Name**: German everyday name, e.g. "Kichererbsen (Dose)".
7. **No exact variety in USDA**: take the nearest relative by nutrition, not by name
   (Hokkaido → butternut squash, not "pumpkin").
8. **Not in USDA at all** (branded or composite products such as Halloumi, Gnocchi, Kokosjoghurt,
   protein pastas, Avocadocreme): nutrition from a typical German package label, no USDA link.
   Don't stretch a base food to cover a product, even a single-ingredient one.
   **Products the user photographed** (`input/Ingredients/`) always take their package's label
   values, even where USDA has the base food (Chiasamen, Tofu), and record product name and barcode
   as the nutrition source. A photo showing only the barcode: the values from Open Food Facts for
   that barcode, flagged. No readable barcode: label values without a source, flagged.
9. **What German shops sell**: farmed Atlantic salmon, firm tofu, Greek yogurt whole milk,
   80 % margarine, parmesan as a block.
10. **Juice** is its own ingredient: Zitronensaft, not Zitrone.
11. **Stock** is the ready-to-serve broth, in ml of prepared broth; the powder is not tracked.
12. **Drained canned and jarred goods** ("(Dose)", "(Glas)": beans, chickpeas, corn, artichokes …)
    count in drained grams: nutrition per 100 g drained (*canned, drained solids*), and their
    *Dose* unit is the drained weight of a typical German can (Kichererbsen 240 g, Mais 140 g),
    so "1 Dose Kidneybohnen" = 240 g and "200 g" means 200 g drained. Goods used with their liquid
    (Kokosmilch, stückige Tomaten) have Dose = the full content, e.g. 400 g.
13. **Units**: besides g and ml, an ingredient gets every unit recipes commonly use for it, each
    with the grams of one unit. Stück (egg, onion, apple, lemon …), Zehe (garlic) and Bund
    (parsley, chives, radishes) are the edible weight of one typical medium piece without shell,
    peel or core (Ei 55 g, Zwiebel 100 g, Knoblauch: Zehe = 3 g). TL and EL for spices, dried
    herbs, seeds, salt, sugar, oils, pastes and sauces used by the spoon, Tasse for grains, flakes,
    nuts and seeds measured in cups: the grams of one level spoon or cup of that food
    (Kreuzkümmel: TL = 2 g, EL = 6 g; Olivenöl: TL = 4 g, EL = 12 g; Haferflocken: Tasse = 90 g).
    Dose as in rule 12. Scheibe, Packung and the Stück of a packaged product come from its package
    (750 g ÷ 20 Scheiben), the count from the photo's file name when the package doesn't print it;
    a missing count is flagged, never guessed. A spoon the user measured wins over the level spoon
    (Erdnussmus: EL = 33 g). Foods recipes only weigh or measure (Mehl, Reis, Öl, Milch, cereals)
    need none.

## Plant points

| Points | What counts |
|---|---|
| 1 | Vegetables, fruit, legumes (incl. tofu, tempeh), whole grains, nuts, seeds, mushrooms — fresh, frozen, canned or dried |
| 0.25 | Herbs (fresh or dried), spices, garlic, ginger, chili |
| 0 | Oils (incl. olive oil and coconut oil), refined grains (white rice, white pasta, white flour, couscous, degermed polenta, gnocchi), sugar, animal products, salt, plain stock, condiments used by the spoon (Tomatenmark, Sojasauce) |

Edge cases from calibration: Kokosmilch and Kokosraspeln 1 · passierte and stückige Tomaten 1 ·
Tahini, Erdnussbutter, Miso 1 · Avocadocreme 1 · Rote-Linsen- and Kichererbsen-Nudeln 1 ·
Harissa and Za'atar 0.25 · Tomatenmark 0 · Sojasauce 0.

## Store section

Existing five sections, in this order: Obst & Gemüse · Kühlregal · Trockenware · Tiefkühl · Sonstiges.

## Calibration log

Round 1, 2026-10-03 (20 items; ★ = proposed pick, all accepted unless noted). Buy-as links and
the staple flag were dropped from the app since (#55); the log keeps them as decided then.

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
