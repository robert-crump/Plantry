package com.example.plantry.data

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

class Converters {

    @TypeConverter
    fun unitWeightsToJson(units: List<UnitWeight>): String = Json.encodeToString(units)

    @TypeConverter
    fun unitWeightsFromJson(json: String): List<UnitWeight> = Json.decodeFromString(json)
}
