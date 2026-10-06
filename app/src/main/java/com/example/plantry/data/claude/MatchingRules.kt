package com.example.plantry.data.claude

/**
 * How a food is classified when it becomes a Plantry ingredient: USDA entry, plant points and
 * store section. Derived from `tools/seed/matching-rules.md`, the rules calibrated for the
 * seeded catalog, so ingredients Claude proposes during a scan are classified the same way.
 * Change both together. Rules that only matter to the seed (label values for products USDA lacks)
 * become "no candidate fits" here, because an app ingredient always needs a USDA entry.
 */
object MatchingRules {

    /** Which form a search should look for, so the right candidates are found. */
    val SEARCH_FORMS = """
        Search for the form the food is bought in: beans and chickpeas canned unless the recipe
        says dried, lentils and split peas dried, everything else raw unless the recipe says
        cooked.
    """.trimIndent()

    val USDA_ENTRY = """
        Choosing the USDA entry (fdcId):
        - Raw over cooked, unless the recipe says cooked ("gekochter Reis", "gegarte Linsen").
        - Unsalted / "without salt" whenever both exist.
        - Beans and chickpeas: "canned, drained solids", the way they are bought; "mature seeds,
          raw" only when the recipe says dried ("getrocknet", "über Nacht einweichen"). Lentils
          and split peas: dried ("raw"). If the recipe blends the can liquid too, the canned entry
          with solids and liquids.
        - Plainest form: no "enriched", "fortified", brand names or "prepared with ...". Prefer
          unenriched rice, pasta and cornmeal. Basmati and jasmine rice -> long-grain white,
          risotto rice -> medium-grain white, Naturreis -> brown; Polenta -> cornmeal, degermed,
          unenriched; Spaghetti, Lasagneblätter -> pasta, dry, unenriched.
        - No exact variety in USDA: the nearest relative by nutrition, not by name (Hokkaido ->
          butternut squash, not "pumpkin"; Räucherfisch -> smoked chinook salmon).
        - What German shops sell: farmed Atlantic salmon, firm tofu, Greek yogurt plain whole
          milk, 80 % margarine without salt, parmesan hard.
        - Branded or composite products USDA does not have (Halloumi, Gnocchi, Kokosjoghurt,
          Rote-Linsen-Nudeln, Avocadocreme): 0. Do not stretch a base food to cover a product,
          even a single-ingredient one.
    """.trimIndent()

    val NAME = """
        German everyday name as a shopper would write it, one ingredient per form: dried and
        canned chickpeas are two ingredients, e.g. "Kichererbsen (Dose)", "Reis, gekocht".
    """.trimIndent()

    val STORE_SECTION = """
        PRODUCE (fresh fruit, vegetables, fresh herbs), DAIRY_CHILLED (dairy, tofu, other chilled
        goods), DRY_GOODS (pasta, rice, cans, spices, oils, stock), FROZEN, OTHER.
    """.trimIndent()

    val PLANT_POINTS = """
        ONE for vegetables, fruit, legumes (incl. tofu, tempeh), whole grains, nuts, seeds and
        mushrooms, fresh, frozen, canned or dried. QUARTER for herbs (fresh or dried), spices,
        garlic, ginger and chili. ZERO for oils (incl. olive and coconut oil), refined grains
        (white rice, white pasta, white flour, couscous, degermed polenta, gnocchi), sugar,
        animal products, salt, plain stock and condiments used by the spoon. Settled cases:
        Kokosmilch, Kokosraspeln, passierte and stückige Tomaten, Tahini, Erdnussbutter, Miso,
        Avocadocreme, Rote-Linsen- and Kichererbsen-Nudeln ONE; Harissa, Za'atar QUARTER;
        Tomatenmark, Sojasauce ZERO.
    """.trimIndent()
}
