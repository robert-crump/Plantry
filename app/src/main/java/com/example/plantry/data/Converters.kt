package com.example.plantry.data

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {

    /** Dates are stored as epoch days, so they sort and compare correctly in SQL. */
    @TypeConverter
    fun localDateToEpochDay(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun localDateFromEpochDay(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
}
