# Seed and bulk import

Offline job: turn photos of the cookbooks and of the packages at home into a complete Plantry backup
file (ingredients with their units + recipes) that is imported via Settings → Datensicherung →
Importieren. The generator (`output/seed.py`) is run by Claude Code in this repo. The seed is
cumulative: each run holds every book so far, and once the app is in use it is merged into an
export of the app (section 4), so nothing entered in the app is lost.

`input/` and `output/` are gitignored — they contain cookbook content.

## 1. Photos (`input/`)

One folder per book, named after the book as it should appear as the recipe source:

```
input/
  <Book title>/
    index-01.jpg, index-02.jpg, ...   pages listing the ingredients used in the book
    p123.jpg, p124.jpg, ...           recipe pages, named by page number
  handwritten/
    Hauptgericht/<recipe name>.jpg    handwritten recipes (source "Handschriftlich"); the folder
    Frühstück/<recipe name>.jpg       sets the recipe's Art
  Ingredients/
    <name>.jpg                        branded products: nutrition table, package size, barcode
    <name>_<count> Scheiben.jpg       ... with the piece count when the package doesn't print it
```

- Recipe photos must show title, servings, cooking time and the complete ingredient list.
  A recipe spread over two pages: one photo per page (`p123.jpg`, `p124.jpg`).
- Product photos show the Nährwerte table, the package size and the barcode; one photo or several
  of the same package. Products eaten by the slice or piece (toast, sliced cheese, wraps, falafel)
  need the count, e.g. `Gouda_9 Scheiben.jpg`. A missing count is
  flagged in the review table and the unit left out, never guessed.
- Sharp, flat, well lit; no need to crop. A handwritten recipe may also be a PDF scan.

## 2. Pipeline

1. **Extract** all ingredients (index pages + recipe lines), recipes and product labels.
2. **Calibrate**: ~20 ambiguous ingredients, top USDA candidates each; the user picks, the picks
   refine [`matching-rules.md`](matching-rules.md).
3. **Apply** the rules to all ingredients; only low-confidence matches are flagged for review.
4. **Generate**: `python output/seed.py` writes the review table and the backup file. It fails on
   an unknown ingredient name, a unit an ingredient lacks or a missing USDA entry. Once the app is
   in use, export in the app first, copy the file to `input/` and run
   `python output/seed.py input/YYMMDD-plantry.json`: this writes `output/YYMMDD-plantry-merged.json`
   instead, the seed merged into the export (section 4).
5. **Review** `output/03-review.md`: the merge report first when merging, then the flags, the
   products (name, source, plant points, store section, units), the recipes with each line's
   computed grams, and all ingredients.
6. **Import** the backup file in the app. This replaces all app data; the API key is kept. Don't
   change anything in the app between the export and the import of a merged file: that change
   isn't in the export, so the import drops it.

## 3. Generator format

**Ingredient**: German name, then the nutrition source — an exact USDA SR Legacy description, or
label values (kcal, protein, carbs, sugar, fat, fibre per 100 g) — plant points, store section and
units. Units come from the app's fixed list (Stück, Scheibe, Zehe, Bund, Stange, Dose, Packung, TL, EL,
Tasse), each either per unit (`("Zehe", 3)`) or from a package (`("Scheibe", 750, 20)`, a count
read from the photo's file name). g and ml exist for every ingredient and are never listed. A
photographed product records its product name and barcode, which the app shows as the nutrition
source; values read from Open Food Facts because the photo shows only the barcode are flagged.
A mix (Beerenmischung, Zitronensaft) also lists its components by ingredient name
(`contains=["Zitrone"]`), which the app shows as "Enthält". Its plant points are then the sum over
its distinct components; an unknown name or a component that is itself a mix fails the run.

**Recipe**: title, Art (Hauptgericht, Frühstück or Snack), Portionen, source and lines as
`(amount, unit, ingredient name[, wording])`. Grams are never written: the app computes them from
the ingredient's units, so changing a unit's grams changes every recipe using it. The wording keeps
a book phrasing ("1 daumengroßes Stück Ingwer"); a line with no fitting unit ("eine Handvoll") is
grams with the wording. The source is the book title with its page and Gesamtzeit, "Handschriftlich"
for a handwritten recipe, or "Eigenes Rezept". Changes the user noted on a book page (added,
dropped or swapped ingredients) are applied, the changed lines marked "(notiert)" in their wording.

A standalone seed is format 2 (`BackupFile`): ingredients with their unit rows and the ids of their
components (`contains`), recipes with structured lines. Every schema change after the import needs
a migration.

## 4. Merging into an app export

[`merge.py`](merge.py) (tests: `python -m unittest discover -s tools/seed`) adds to the export what
only the seed has; the app's version wins wherever both have something. Kochlog, Geplant, aliases,
settings and photos are taken from the export unchanged, and the merged file keeps its format. The
seed's ids are not stable, so the merge matches by key:

- **Ingredient**: by name, ignoring case. A seed ingredient missing from the app is added. A unit a
  new recipe line needs is added to the app's ingredient ("Einheiten aus Seed ergänzt"); units both
  have keep the app's grams.
- **Recipe**: a book recipe by source + page, so renaming it in the app keeps it matched; any other
  recipe by source + title.

The review lists everything added ("Neu hinzugefügt") and every difference between app and seed
("In App und Seed verschieden"); to take the seed's version, change the app by hand. The run fails
instead of adding a probable duplicate:

- a new recipe whose title the app already has (from another source or page);
- a new recipe whose title is in the Kochlog of a recipe deleted in the app: delete it from
  `RECIPES` too, or it comes back with every seed;
- a new ingredient whose barcode or USDA entry the app has under a name the seed doesn't know,
  probably renamed in the app: rename it in the seed too.

A recipe renamed in the app that has no page, or a deleted one never cooked, comes back as new:
check "Neu hinzugefügt".
