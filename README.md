# Plantry

Android app (Jetpack Compose) for planning a weekly menu of home-cooked recipes.

## Nutrition data

Ingredient nutrition values and portion weights come from the
[USDA FoodData Central](https://fdc.nal.usda.gov/) **SR Legacy** dataset (April 2018 release),
bundled in the app as `app/src/main/assets/usda_sr_legacy.tsv` and searched offline; the app makes
no USDA API calls.

> U.S. Department of Agriculture, Agricultural Research Service. FoodData Central, 2019.
> fdc.nal.usda.gov.

USDA FoodData Central data is in the public domain
([CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)).

To regenerate the asset, download the SR Legacy CSV release from
<https://fdc.nal.usda.gov/download-datasets>, unzip it and run:

```
python tools/usda_to_asset.py path/to/FoodData_Central_sr_legacy_food_csv_2018-04
```
