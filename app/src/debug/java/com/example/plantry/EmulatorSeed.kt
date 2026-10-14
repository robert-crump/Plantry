package com.example.plantry

import android.content.Context.MODE_PRIVATE
import android.os.Build
import android.util.Log
import com.example.plantry.data.backup.EmulatorSeeder
import com.example.plantry.data.settings.SharedPreferencesStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * On an emulator, debug builds start with the test data in assets/emulator-fixture.json, so the
 * app is usable right after an install or wipe; see [EmulatorSeeder]. Never runs on a phone.
 */
object EmulatorSeed {

    fun start(app: PlantryApplication, scope: CoroutineScope) {
        if (!isEmulator()) return
        scope.launch(Dispatchers.IO) {
            try {
                val fixture = app.assets.open(FIXTURE).bufferedReader().use { it.readText() }
                val seeder = EmulatorSeeder(
                    hasData = app.backupRepository::hasData,
                    import = app.backupRepository::import,
                    storage = SharedPreferencesStorage(app.getSharedPreferences(PREFS, MODE_PRIVATE)),
                )
                seeder.seed(fixture)?.let { Log.i(TAG, "seeded fixture ${it.fingerprint.take(12)}, shifted ${it.daysMoved} days") }
            } catch (e: Exception) {
                Log.e(TAG, "seeding failed", e)
            }
        }
    }

    private fun isEmulator() =
        Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" || Build.FINGERPRINT.contains("generic")

    private const val FIXTURE = "emulator-fixture.json"
    private const val PREFS = "emulator_seed"
    private const val TAG = "EmulatorSeed"
}
