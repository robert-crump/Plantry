package com.example.plantry.data

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import java.time.LocalDate

class Converters {

    @TypeConverter
    fun unitWeightsToJson(units: List<UnitWeight>): String = Json.encodeToString(units)

    @TypeConverter
    fun unitWeightsFromJson(json: String): List<UnitWeight> = Json.decodeFromString(json)

    /** Dates are stored as epoch days, so they sort and compare correctly in SQL. */
    @TypeConverter
    fun localDateToEpochDay(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun localDateFromEpochDay(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
}
