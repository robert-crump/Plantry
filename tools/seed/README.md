# Seed and bulk import

One-time offline job: turn photos of the cookbooks into a complete Plantry backup file
(ingredients + recipes) that is imported via Settings → Datensicherung → Importieren.

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
```

- Recipe photos must show title, servings, cooking time and the complete ingredient list.
  A recipe spread over two pages: one photo per page (`p123.jpg`, `p124.jpg`).
- Sharp, flat, well lit; no need to crop.

## 2. Pipeline

1. **Extract** all ingredients (index pages + recipe lines) and recipes.
2. **Calibrate**: ~20 ambiguous ingredients, top USDA candidates each; the user picks, the picks
   refine [`matching-rules.md`](matching-rules.md).
3. **Apply** the rules to all ingredients; only low-confidence matches are flagged for review.
4. **Review** the full table (name, USDA entry, plant points, store section, buy unit, buy-as).
5. **Generate** `output/YYMMDD-plantry.json` in the backup format (`BackupFile`, format version 1)
   and import it in the app. This replaces all app data; the API key is kept.
