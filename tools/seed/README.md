# Seed and bulk import

One-time offline job: turn photos of the cookbooks and of the packages at home into a complete
Plantry backup file (ingredients with their units + recipes) that is imported via Settings →
Datensicherung → Importieren. The generator (`output/seed.py`) is run by Claude Code in this repo.

`input/` and `output/` are gitignored — they contain cookbook content.

## 1. Photos (`input/`)

One folder per book, named after the book as it should appear as the recipe source:

```
input/
  <Book title>/
    index-01.jpg, index-02.jpg, ...   pages listing the ingredients used in the book
    p123.jpg, p124.jpg, ...           recipe pages, named by page number
  handwritten/
    <recipe name>.jpg                 handwritten recipes (source "Handschriftlich")
  Ingredients/
    <name>.jpg                        branded products: nutrition table, package size, barcode
    <name>_<count> Scheiben.jpg       ... with the piece count when the package doesn't print it
```

- Recipe photos must show title, servings, cooking time and the complete ingredient list.
  A recipe spread over two pages: one photo per page (`p123.jpg`, `p124.jpg`).
- Product photos show the Nährwerte table, the package size and the barcode; one photo or several
  of the same package. Products eaten by the slice or piece (toast, sliced cheese, wraps, falafel)
  need the count, e.g. `Toast_20-Scheiben.jpg` or `Gouda_9 Scheiben.jpg`. A missing count is
  flagged in the review table and the unit left out, never guessed.
- Sharp, flat, well lit; no need to crop.

## 2. Pipeline

1. **Extract** all ingredients (index pages + recipe lines), recipes and product labels.
2. **Calibrate**: ~20 ambiguous ingredients, top USDA candidates each; the user picks, the picks
   refine [`matching-rules.md`](matching-rules.md).
3. **Apply** the rules to all ingredients; only low-confidence matches are flagged for review.
4. **Generate**: `python output/seed.py` writes the review table and the backup file. It fails on
   an unknown ingredient name, a unit an ingredient lacks or a missing USDA entry.
5. **Review** `output/03-review.md`: flags first, then the products (name, source, plant points,
   store section, units), the recipes with each line's computed grams, and all ingredients.
6. **Import** `output/YYMMDD-plantry.json` in the app. This replaces all app data; the API key is
   kept.

## 3. Generator format

**Ingredient**: German name, then the nutrition source — an exact USDA SR Legacy description, or
label values (kcal, protein, carbs, sugar, fat, fibre per 100 g) — plant points, store section and
units. Units come from the app's fixed list (Stück, Scheibe, Zehe, Bund, Dose, Packung, TL, EL,
Tasse), each either per unit (`("Zehe", 3)`) or from a package (`("Scheibe", 750, 20)`, a count
read from the photo's file name). g and ml exist for every ingredient and are never listed. A
photographed product records its product name and barcode, which the app shows as the nutrition
source; values read from Open Food Facts because the photo shows only the barcode are flagged.

**Recipe**: title, Art (Hauptgericht or Snack), Portionen, source (empty for snacks) and lines as
`(amount, unit, ingredient name[, wording])`. Grams are never written: the app computes them from
the ingredient's units, so changing a unit's grams changes every recipe using it. The wording keeps
a book phrasing ("1 daumengroßes Stück Ingwer"); a line with no fitting unit ("eine Handvoll") is
grams with the wording.

The backup is format 1 (`BackupFile`): ingredients with their unit rows, recipes with structured
lines. Once the seed is imported, schema v1 and backup format 1 are frozen: every later schema
change needs a migration.
