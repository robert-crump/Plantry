"""Merges a seed into an export of the app (issue 107), so a newer seed doesn't wipe what was entered in the app.

Both are backup files as dicts. The app wins: its recipes, ingredients, Kochlog, Geplant, aliases,
settings and photos are kept as they are; only what the seed has and the app doesn't is added. The
seed's ids are not stable (seed.py numbers ingredients alphabetically and recipes by position), so
everything is matched by key:

- an ingredient by name (ignoring case and surrounding spaces);
- a book recipe by source + page, any other recipe by source + title.

A seed ingredient missing from the app is added, unless the app has its barcode or fdcId under a name
the seed doesn't know (probably renamed in the app). A seed recipe missing from the app is added,
unless its title already exists in the app or in the Kochlog of a deleted recipe. A unit a new recipe
line needs is added to the app's ingredient. All of it, and every difference between app and seed
for what both have, ends up in the report for 03-review.md.
"""
import copy
from dataclasses import dataclass, field

PLAIN_UNITS = {"G", "ML"}
NUTRIENTS = ["kcal", "protein", "carbs", "sugar", "fat", "fibre"]


class MergeError(Exception):
    def __init__(self, errors):
        super().__init__("\n".join(errors))
        self.errors = errors


@dataclass
class Report:
    added_recipes: list = field(default_factory=list)
    added_ingredients: list = field(default_factory=list)
    # "Gouda: Scheibe"
    added_units: list = field(default_factory=list)
    # (title or name, [difference, ...])
    recipe_diffs: list = field(default_factory=list)
    ingredient_diffs: list = field(default_factory=list)


def norm(name):
    return name.strip().casefold()


def recipe_key(recipe):
    page = recipe.get("page")
    return (recipe["source"], page) if page is not None else (recipe["source"], norm(recipe["title"]))


def merge(export, seed):
    """Returns (merged backup, Report); raises MergeError with every problem found."""
    merged = copy.deepcopy(export)
    app_ings = merged["ingredients"]
    errors, report = [], Report()

    app_by_name = {}
    for x in app_ings:
        app_by_name.setdefault(norm(x["name"]), []).append(x)
    app_by_id = {x["id"]: x for x in app_ings}
    seed_by_id = {x["id"]: x for x in seed["ingredients"]}
    seed_names = {norm(x["name"]) for x in seed["ingredients"]}

    # --- Ingredients: seed id -> app id, the new ones added with ids above the app's
    to_app, new_ings = {}, []
    next_id = max((x["id"] for x in app_ings), default=0) + 1
    for x in seed["ingredients"]:
        matches = app_by_name.get(norm(x["name"]), [])
        if len(matches) > 1:
            errors.append(f'{x["name"]}: {len(matches)} ingredients of that name in the app')
            continue
        if matches:
            to_app[x["id"]] = matches[0]["id"]
            continue
        renamed = [a["name"] for a in app_ings if norm(a["name"]) not in seed_names and (
            (x.get("labelBarcode") and a.get("labelBarcode") == x["labelBarcode"]) or
            (x.get("fdcId") is not None and a.get("fdcId") == x["fdcId"]))]
        if renamed:
            errors.append(f'{x["name"]}: probably renamed in the app to {", ".join(renamed)} (same barcode or USDA entry)')
            continue
        new = copy.deepcopy(x)
        new["id"] = next_id
        to_app[x["id"]] = next_id
        next_id += 1
        new_ings.append(new)
    for new in new_ings:
        components = []
        for c in new.get("contains", []):
            if c not in to_app:
                continue  # its own error above
            target = to_app[c]
            if target in app_by_id and app_by_id[target].get("contains"):
                errors.append(f'{new["name"]}: component {app_by_id[target]["name"]} is a mix in the app')
            components.append(target)
        new["contains"] = sorted(set(components))
        report.added_ingredients.append(new["name"])

    # --- Recipes
    app_recipes = merged["recipes"]
    app_by_key = {}
    for r in app_recipes:
        app_by_key.setdefault(recipe_key(r), r)
    app_titles = {norm(r["title"]) for r in app_recipes}
    orphan_titles = {norm(c["title"]) for c in merged.get("cookLog", []) if c.get("recipeId") is None}
    seed_keys = [recipe_key(r) for r in seed["recipes"]]
    for k in {k for k in seed_keys if seed_keys.count(k) > 1}:
        errors.append(f"seed has several recipes with the key {k}")

    next_recipe = max((r["id"] for r in app_recipes), default=0) + 1
    next_line = max((l["id"] for r in app_recipes for l in r["lines"]), default=0) + 1
    matched, new_recipes, added_units = [], [], set()
    for r in seed["recipes"]:
        app = app_by_key.get(recipe_key(r))
        if app:
            matched.append((app, r))
            continue
        if norm(r["title"]) in app_titles:
            errors.append(f'{r["title"]}: the app has a recipe of that title, but not from {source_text(r)}')
            continue
        if norm(r["title"]) in orphan_titles:
            errors.append(f'{r["title"]}: in the Kochlog of a recipe deleted in the app; remove it from RECIPES')
            continue
        lines = []
        for l in r["lines"]:
            if l["ingredientId"] not in to_app:
                continue  # its own error above
            target = to_app[l["ingredientId"]]
            if l["unit"] not in PLAIN_UNITS and target in app_by_id:
                ing = app_by_id[target]
                if l["unit"] not in {u["unit"] for u in ing["units"]}:
                    ing["units"].append(copy.deepcopy(next(u for u in seed_by_id[l["ingredientId"]]["units"] if u["unit"] == l["unit"])))
                    ing["units"].sort(key=lambda u: u["unit"])
                    added_units.add((ing["name"], l["unit"]))
            lines.append(dict(l, id=next_line, ingredientId=target))
            next_line += 1
        new = {k: v for k, v in r.items() if k != "photo"}
        new.update(id=next_recipe, modified=False, photos=[], lines=lines)
        next_recipe += 1
        new_recipes.append(new)
        report.added_recipes.append(r["title"])

    if errors:
        raise MergeError(errors)

    report.added_units = [f"{name}: {unit}" for name, unit in sorted(added_units)]
    app_ings += new_ings
    app_recipes += new_recipes
    names = {x["id"]: x["name"] for x in app_ings}
    seed_names_by_id = {x["id"]: x["name"] for x in seed["ingredients"]}

    for x in seed["ingredients"]:
        app = app_by_id.get(to_app[x["id"]])
        if app:
            diffs = ingredient_diffs(app, x, names, seed_names_by_id)
            if diffs:
                report.ingredient_diffs.append((app["name"], diffs))
    for app, r in matched:
        diffs = recipe_diffs(app, r, names, seed_names_by_id)
        if diffs:
            report.recipe_diffs.append((app["title"], diffs))
    return merged, report


def ingredient_diffs(app, seed, app_names, seed_names):
    diffs = []

    def differ(label, a, s):
        if a != s:
            diffs.append(f"{label}: App {show(a)} · Seed {show(s)}")

    for n in NUTRIENTS:
        a, s = app["nutrition"][n], seed["nutrition"][n]
        if abs(a - s) > 1e-6:
            diffs.append(f"{n}: App {a:g} · Seed {s:g}")
    differ("Pflanzenpunkte", app["plantPoints"], seed["plantPoints"])
    differ("Abteilung", app["storeSection"], seed["storeSection"])
    differ("USDA", app.get("usdaDescription"), seed.get("usdaDescription"))
    differ("Produkt", app.get("labelProduct"), seed.get("labelProduct"))
    differ("Barcode", app.get("labelBarcode"), seed.get("labelBarcode"))
    a_units = {u["unit"]: u for u in app["units"]}
    s_units = {u["unit"]: u for u in seed["units"]}
    for unit in sorted(a_units.keys() | s_units.keys()):
        differ(f"Einheit {unit}", unit_text(a_units.get(unit)), unit_text(s_units.get(unit)))
    differ("Enthält", sorted(app_names[c] for c in app.get("contains", [])),
           sorted(seed_names[c] for c in seed.get("contains", [])))
    return diffs


def recipe_diffs(app, seed, app_names, seed_names):
    diffs = []
    for key, label in [("title", "Titel"), ("servings", "Portionen"), ("totalTimeMinutes", "Gesamtzeit"), ("dish", "Art")]:
        if app.get(key) != seed.get(key):
            diffs.append(f"{label}: App {show(app.get(key))} · Seed {show(seed.get(key))}")
    a_lines = [line_text(l, app_names) for l in app["lines"]]
    s_lines = [line_text(l, seed_names) for l in seed["lines"]]
    a_folded, s_folded = [x.casefold() for x in a_lines], [x.casefold() for x in s_lines]
    if a_folded != s_folded:
        only_app = [f"nur App: {x}" for x, f in zip(a_lines, a_folded) if f not in s_folded]
        only_seed = [f"nur Seed: {x}" for x, f in zip(s_lines, s_folded) if f not in a_folded]
        diffs += only_app + only_seed or ["Zeilen: dieselben, andere Reihenfolge"]
    return diffs


def line_text(line, names):
    text = f'{line["amount"]:g} {line["unit"]} {names[line["ingredientId"]]}'
    return text + (f' „{line["wording"]}“' if line.get("wording") else "")


def unit_text(u):
    if u is None:
        return None
    if u.get("packageGrams") is not None:
        return f'{u["gramsPerUnit"]:g} g ({u["packageGrams"]:g} g / {u["packageCount"]})'
    return f'{u["gramsPerUnit"]:g} g'


def source_text(recipe):
    return recipe["source"] + (f' S. {recipe["page"]}' if recipe.get("page") is not None else "")


def show(value):
    if value is None:
        return "–"
    if isinstance(value, list):
        return ", ".join(value) or "–"
    return str(value)


def report_markdown(report, export_name):
    """The merge section of 03-review.md."""
    md = [f"## Merge into {export_name}", "",
          f"{len(report.added_recipes)} recipes and {len(report.added_ingredients)} ingredients added, "
          f"{len(report.added_units)} units added to app ingredients. The app's version wins wherever both have something.", "",
          "### Neu hinzugefügt", ""]
    md += [f"- Rezept: {t}" for t in report.added_recipes]
    md += [f"- Zutat: {n}" for n in report.added_ingredients]
    if not report.added_recipes and not report.added_ingredients:
        md.append("Nichts.")
    md += ["", "### Einheiten aus Seed ergänzt", ""]
    md += [f"- {u}" for u in report.added_units] or ["Keine."]
    md += ["", "### In App und Seed verschieden", "", "Kept as in the app; fix the app or the seed by hand.", ""]
    if not report.recipe_diffs and not report.ingredient_diffs:
        md.append("Nichts.")
    for kind, diffs in [("Rezept", report.recipe_diffs), ("Zutat", report.ingredient_diffs)]:
        for name, items in diffs:
            md.append(f"- {kind} **{name}**")
            md += [f"  - {d}" for d in items]
    return md + [""]
