"""Run from the repo root: python -m unittest discover -s tools/seed"""
import copy
import unittest

from merge import MergeError, merge, report_markdown


def nutrition(kcal=100.0):
    return dict(kcal=kcal, protein=1.0, carbs=2.0, sugar=0.5, fat=0.3, fibre=1.5)


def ingredient(id, name, units=(), fdc=None, barcode=None, contains=(), kcal=100.0):
    return dict(id=id, name=name, fdcId=fdc, usdaDescription=f"USDA {fdc}" if fdc else None, nutrition=nutrition(kcal),
                storeSection="PRODUCE", plantPoints="ONE", labelProduct="Produkt" if barcode else None,
                labelBarcode=barcode, units=[dict(unit=u, gramsPerUnit=g, packageGrams=None, packageCount=None) for u, g in units],
                contains=list(contains))


def line(id, ingredient_id, amount=100.0, unit="G", wording=None):
    return dict(id=id, amount=amount, unit=unit, ingredientId=ingredient_id, wording=wording)


def recipe(id, title, lines, source="Buch", page=None, photo_field=None):
    r = dict(id=id, title=title, source=source, page=page, servings=2, totalTimeMinutes=None, activeTimeMinutes=None,
             modified=False, dish="MAIN", lines=lines)
    if photo_field == "seed":
        r["photo"] = None
    else:
        r["photos"] = []
    return r


def backup(ingredients, recipes, cook_log=(), planned=(), aliases=(), version=3):
    return dict(formatVersion=version, settings=dict(scanModel="opus", cooldownDays=21), ingredients=ingredients,
                recipes=recipes, cookLog=list(cook_log), aliases=list(aliases), planned=list(planned))


def cooked(id, recipe_id, title):
    return dict(id=id, recipeId=recipe_id, cookedOn="2026-10-01", title=title, plantPoints=3.0, proteinPerPortion=10.0,
                carbsPerPortion=20.0, kcalPerPortion=500.0, fibrePerPortion=8.0)


class MergeTest(unittest.TestCase):

    def setUp(self):
        # The app numbers differently from the seed: Zwiebel is 7 in the app, 2 in the seed.
        self.export = backup(
            ingredients=[ingredient(7, "Zwiebel", [("PIECE", 110)], fdc=11282), ingredient(9, "Linsen", fdc=16069)],
            recipes=[recipe(4, "Dal", [line(30, 9), line(31, 7, 1, "PIECE")], page=12)],
            cook_log=[cooked(1, 4, "Dal")],
            planned=[dict(recipeId=4, plannedOn="2026-10-20")],
            aliases=[dict(wording="rote Linsen", ingredientId=9)],
        )
        self.seed = backup(
            ingredients=[ingredient(1, "Linsen", fdc=16069), ingredient(2, "Zwiebel", [("PIECE", 110)], fdc=11282)],
            recipes=[recipe(1, "Dal", [line(1, 1), line(2, 2, 1, "PIECE")], page=12, photo_field="seed")],
            version=2,
        )

    def test_a_seed_the_app_already_has_changes_nothing(self):
        merged, report = merge(self.export, self.seed)
        self.assertEqual(self.export, merged)
        self.assertEqual([], report.added_recipes + report.added_ingredients + report.added_units)
        self.assertEqual([], report.recipe_diffs + report.ingredient_diffs)

    def test_a_new_recipe_gets_new_ids_and_points_to_the_app_ingredients(self):
        self.seed["recipes"].append(recipe(2, "Zwiebelsuppe", [line(3, 2, 3, "PIECE")], source="Eigenes Rezept", photo_field="seed"))
        merged, report = merge(self.export, self.seed)
        new = merged["recipes"][-1]
        self.assertEqual(5, new["id"])
        self.assertEqual([line(32, 7, 3, "PIECE")], new["lines"])
        self.assertEqual([], new["photos"])
        self.assertNotIn("photo", new)
        self.assertEqual(["Zwiebelsuppe"], report.added_recipes)
        self.assertEqual(3, merged["formatVersion"])

    def test_the_rest_of_the_export_is_kept(self):
        self.seed["recipes"].append(recipe(2, "Suppe", [line(3, 1)], source="Eigenes Rezept"))
        merged, _ = merge(self.export, self.seed)
        for key in ["cookLog", "planned", "aliases", "settings"]:
            self.assertEqual(self.export[key], merged[key])

    def test_a_book_recipe_renamed_in_the_app_still_matches_by_page(self):
        self.export["recipes"][0]["title"] = "Mein Dal"
        merged, report = merge(self.export, self.seed)
        self.assertEqual(1, len(merged["recipes"]))
        self.assertEqual([("Mein Dal", ["Titel: App Mein Dal · Seed Dal"])], report.recipe_diffs)

    def test_a_recipe_in_both_keeps_the_app_version_and_reports_the_lines(self):
        self.export["recipes"][0]["lines"][0]["amount"] = 150.0
        merged, report = merge(self.export, self.seed)
        self.assertEqual(150.0, merged["recipes"][0]["lines"][0]["amount"])
        self.assertEqual([("Dal", ["nur App: 150 G Linsen", "nur Seed: 100 G Linsen"])], report.recipe_diffs)

    def test_lines_in_another_order_are_reported(self):
        self.export["recipes"][0]["lines"].reverse()
        _, report = merge(self.export, self.seed)
        self.assertEqual([("Dal", ["Zeilen: dieselben, andere Reihenfolge"])], report.recipe_diffs)

    def test_a_new_recipe_with_a_title_the_app_has_stops(self):
        self.seed["recipes"].append(recipe(2, "dal ", [line(3, 1)], source="Handschriftlich"))
        with self.assertRaises(MergeError) as e:
            merge(self.export, self.seed)
        self.assertIn("dal : the app has a recipe of that title, but not from Handschriftlich", e.exception.errors)

    def test_a_recipe_deleted_in_the_app_and_cooked_stops(self):
        self.export["cookLog"].append(cooked(2, None, "Linsensuppe"))
        self.seed["recipes"].append(recipe(2, "Linsensuppe", [line(3, 1)], source="Eigenes Rezept"))
        with self.assertRaises(MergeError) as e:
            merge(self.export, self.seed)
        self.assertEqual(["Linsensuppe: in the Kochlog of a recipe deleted in the app; remove it from RECIPES"], e.exception.errors)

    def test_a_unit_a_new_line_needs_is_added_and_the_app_grams_are_kept(self):
        self.seed["ingredients"][1]["units"] = [dict(unit="PIECE", gramsPerUnit=150.0, packageGrams=None, packageCount=None),
                                                dict(unit="SLICE", gramsPerUnit=10.0, packageGrams=200.0, packageCount=20)]
        self.seed["recipes"].append(recipe(2, "Salat", [line(3, 2, 4, "SLICE")], source="Eigenes Rezept"))
        merged, report = merge(self.export, self.seed)
        zwiebel = merged["ingredients"][0]
        self.assertEqual([("PIECE", 110), ("SLICE", 10.0)], [(u["unit"], u["gramsPerUnit"]) for u in zwiebel["units"]])
        self.assertEqual(["Zwiebel: SLICE"], report.added_units)
        self.assertEqual([("Zwiebel", ["Einheit PIECE: App 110 g · Seed 150 g"])], report.ingredient_diffs)

    def test_a_unit_no_new_line_needs_is_only_reported(self):
        self.seed["ingredients"][0]["units"] = [dict(unit="CUP", gramsPerUnit=190.0, packageGrams=None, packageCount=None)]
        merged, report = merge(self.export, self.seed)
        self.assertEqual([], merged["ingredients"][1]["units"])
        self.assertEqual([("Linsen", ["Einheit CUP: App – · Seed 190 g"])], report.ingredient_diffs)

    def test_ingredients_match_ignoring_case_and_keep_the_app_values(self):
        self.seed["ingredients"][0].update(name=" linsen", nutrition=nutrition(kcal=116.0))
        merged, report = merge(self.export, self.seed)
        self.assertEqual(2, len(merged["ingredients"]))
        self.assertEqual(100.0, merged["ingredients"][1]["nutrition"]["kcal"])
        self.assertEqual([("Linsen", ["kcal: App 100 · Seed 116"])], report.ingredient_diffs)

    def test_a_new_ingredient_and_mix_get_new_ids(self):
        self.seed["ingredients"] += [ingredient(3, "Zitrone", fdc=9150), ingredient(4, "Zitronensaft", contains=[3, 2])]
        merged, report = merge(self.export, self.seed)
        zitrone, saft = merged["ingredients"][2:]
        self.assertEqual((10, 11), (zitrone["id"], saft["id"]))
        self.assertEqual([7, 10], saft["contains"])
        self.assertEqual(["Zitrone", "Zitronensaft"], report.added_ingredients)

    def test_a_new_mix_of_a_mix_in_the_app_stops(self):
        self.export["ingredients"][0]["contains"] = [9]
        self.seed["ingredients"].append(ingredient(3, "Suppengrün", contains=[2]))
        with self.assertRaises(MergeError) as e:
            merge(self.export, self.seed)
        self.assertEqual(["Suppengrün: component Zwiebel is a mix in the app"], e.exception.errors)

    def test_an_ingredient_renamed_in_the_app_stops_by_barcode(self):
        self.export["ingredients"].append(ingredient(12, "Mein Toast", barcode="4001"))
        self.seed["ingredients"].append(ingredient(3, "Toast", barcode="4001"))
        with self.assertRaises(MergeError) as e:
            merge(self.export, self.seed)
        self.assertEqual(["Toast: probably renamed in the app to Mein Toast (same barcode or USDA entry)"], e.exception.errors)

    def test_an_ingredient_renamed_in_the_app_stops_by_usda_entry(self):
        self.export["ingredients"][0]["name"] = "Gemüsezwiebel"
        with self.assertRaises(MergeError) as e:
            merge(self.export, self.seed)
        self.assertIn("Zwiebel: probably renamed in the app to Gemüsezwiebel (same barcode or USDA entry)", e.exception.errors)

    def test_two_seed_names_sharing_a_usda_entry_are_no_rename(self):
        self.seed["ingredients"].append(ingredient(3, "Rote Zwiebel", fdc=11282))
        merged, report = merge(self.export, self.seed)
        self.assertEqual(["Rote Zwiebel"], report.added_ingredients)

    def test_an_ambiguous_app_name_stops(self):
        self.export["ingredients"].append(ingredient(12, "zwiebel"))
        with self.assertRaises(MergeError) as e:
            merge(self.export, self.seed)
        self.assertEqual(["Zwiebel: 2 ingredients of that name in the app"], e.exception.errors)

    def test_the_input_is_not_changed(self):
        self.seed["recipes"].append(recipe(2, "Salat", [line(3, 2, 4, "PIECE")], source="Eigenes Rezept"))
        export, seed = copy.deepcopy(self.export), copy.deepcopy(self.seed)
        merge(self.export, self.seed)
        self.assertEqual((export, seed), (self.export, self.seed))

    def test_the_report_lists_everything(self):
        self.export["recipes"][0]["servings"] = 4
        self.seed["recipes"].append(recipe(2, "Suppe", [line(3, 1)], source="Eigenes Rezept"))
        _, report = merge(self.export, self.seed)
        md = "\n".join(report_markdown(report, "261020-plantry.json"))
        self.assertIn("## Merge into 261020-plantry.json", md)
        self.assertIn("- Rezept: Suppe", md)
        self.assertIn("- Rezept **Dal**\n  - Portionen: App 4 · Seed 2", md)


if __name__ == "__main__":
    unittest.main()
