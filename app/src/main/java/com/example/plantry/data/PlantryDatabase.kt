package com.example.plantry.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.DeleteTable
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.plantry.data.backup.BackupDao

@Database(
    entities = [Recipe::class, Ingredient::class, RecipeIngredient::class, CookLog::class, IngredientAlias::class, PlannedRecipe::class],
    version = 15,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9, spec = PlantryDatabase.DropWeekPlan::class),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13, spec = PlantryDatabase.DropShoppingAttributes::class),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
    ],
)
@TypeConverters(Converters::class)
abstract class PlantryDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao

    abstract fun ingredientDao(): IngredientDao

    abstract fun cookLogDao(): CookLogDao

    abstract fun plannedRecipeDao(): PlannedRecipeDao

    abstract fun backupDao(): BackupDao

    /** The week plan and the shopping list are gone; their data is not carried over. */
    @DeleteTable(tableName = "week_plan_slots")
    @DeleteTable(tableName = "shopping_ticks")
    class DropWeekPlan : AutoMigrationSpec

    /** Without the shopping list, the ingredients' buying attributes, staple flag and buy-as link are gone. */
    @DeleteColumn(tableName = "ingredients", columnName = "unitWeights")
    @DeleteColumn(tableName = "ingredients", columnName = "buyUnit")
    @DeleteColumn(tableName = "ingredients", columnName = "packSizeGrams")
    @DeleteColumn(tableName = "ingredients", columnName = "staple")
    @DeleteColumn(tableName = "ingredients", columnName = "buyAsIngredientId")
    @DeleteColumn(tableName = "ingredients", columnName = "buyAsYieldFactor")
    class DropShoppingAttributes : AutoMigrationSpec

    companion object {
        fun create(context: Context): PlantryDatabase =
            Room.databaseBuilder(context, PlantryDatabase::class.java, "plantry.db")
                .addMigrations(MIGRATION_9_10)
                .build()

        /**
         * Cooking log entries get a snapshot of their recipe (title, plant points, protein and
         * carbs per portion), backfilled from the recipes as they are now, and survive deleting
         * the recipe. Same calculation as [RecipeStats.of], frozen here in SQL.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cook_log_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`recipeId` INTEGER, `cookedOn` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                        "`plantPoints` REAL NOT NULL, `proteinPerPortion` REAL NOT NULL, `carbsPerPortion` REAL NOT NULL, " +
                        "FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
                )
                db.execSQL(
                    """
                    WITH RECURSIVE
                    chain(start, id, next, depth) AS (
                        SELECT id, id, buyAsIngredientId, 0 FROM ingredients
                        UNION ALL
                        SELECT chain.start, i.id, i.buyAsIngredientId, chain.depth + 1
                        FROM chain JOIN ingredients i ON i.id = chain.next
                        WHERE chain.depth < 50
                    ),
                    roots(start, root) AS (SELECT start, id FROM chain WHERE next IS NULL),
                    points(recipeId, value) AS (
                        SELECT used.recipeId, SUM(
                            CASE i.plantPoints WHEN 'ONE' THEN 1.0 WHEN 'QUARTER' THEN 0.25 ELSE 0.0 END
                        )
                        FROM (
                            SELECT DISTINCT ri.recipeId, roots.root
                            FROM recipe_ingredients ri JOIN roots ON roots.start = ri.ingredientId
                        ) used JOIN ingredients i ON i.id = used.root
                        GROUP BY used.recipeId
                    ),
                    nutrition(recipeId, protein, carbs) AS (
                        SELECT ri.recipeId, SUM(ri.grams * i.protein) / 100.0, SUM(ri.grams * i.carbs) / 100.0
                        FROM recipe_ingredients ri JOIN ingredients i ON i.id = ri.ingredientId
                        GROUP BY ri.recipeId
                    )
                    INSERT INTO cook_log_new (id, recipeId, cookedOn, title, plantPoints, proteinPerPortion, carbsPerPortion)
                    SELECT l.id, l.recipeId, l.cookedOn, r.title, COALESCE(p.value, 0.0),
                        COALESCE(n.protein, 0.0) / r.ourServings, COALESCE(n.carbs, 0.0) / r.ourServings
                    FROM cook_log l
                    JOIN recipes r ON r.id = l.recipeId
                    LEFT JOIN points p ON p.recipeId = l.recipeId
                    LEFT JOIN nutrition n ON n.recipeId = l.recipeId
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE cook_log")
                db.execSQL("ALTER TABLE cook_log_new RENAME TO cook_log")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cook_log_recipeId` ON `cook_log` (`recipeId`)")
            }
        }
    }
}
