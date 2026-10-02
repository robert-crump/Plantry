"""Converts the USDA FoodData Central SR Legacy CSV download into the compact TSV asset
bundled with the app (app/src/main/assets/usda_sr_legacy.tsv).

Usage: python tools/usda_to_asset.py <path to FoodData_Central_sr_legacy_food_csv_2018-04>

Columns: fdc_id, description, kcal, protein, carbs, sugar, fat, fibre (per 100 g; empty when
USDA has no value), portions ("label=grams" per one unit, joined by "|").
"""
import csv
import sys
from collections import defaultdict
from pathlib import Path

NUTRIENTS = {"1008": 0, "1003": 1, "1005": 2, "2000": 3, "1004": 4, "1079": 5}
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/usda_sr_legacy.tsv"


def clean(text):
    return " ".join(text.replace("\t", " ").replace("|", "/").replace("=", "-").split())


def number(value):
    return f"{float(value):g}"


def main(src):
    src = Path(src)
    foods = {}
    with open(src / "food.csv", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            foods[row["fdc_id"]] = clean(row["description"])

    nutrients = defaultdict(lambda: [""] * len(NUTRIENTS))
    with open(src / "food_nutrient.csv", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            index = NUTRIENTS.get(row["nutrient_id"])
            if index is not None and row["amount"]:
                nutrients[row["fdc_id"]][index] = number(row["amount"])

    portions = defaultdict(list)
    with open(src / "food_portion.csv", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            amount, grams = float(row["amount"] or 1), float(row["gram_weight"] or 0)
            label = clean(row["modifier"] or row["portion_description"])
            if label and amount > 0 and grams > 0:
                portions[row["fdc_id"]].append(f"{label}={round(grams / amount, 1):g}")

    with open(OUT, "w", encoding="utf-8", newline="\n") as out:
        for fdc_id, description in sorted(foods.items(), key=lambda item: item[1].lower()):
            fields = [fdc_id, description, *nutrients[fdc_id], "|".join(portions[fdc_id])]
            out.write("\t".join(fields) + "\n")
    print(f"Wrote {len(foods)} foods to {OUT}")


if __name__ == "__main__":
    main(sys.argv[1])
