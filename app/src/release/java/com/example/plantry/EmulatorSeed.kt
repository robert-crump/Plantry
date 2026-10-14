package com.example.plantry

import kotlinx.coroutines.CoroutineScope

/** Release builds carry no test data; the debug build seeds emulators. */
object EmulatorSeed {

    @Suppress("UNUSED_PARAMETER")
    fun start(app: PlantryApplication, scope: CoroutineScope) = Unit
}
