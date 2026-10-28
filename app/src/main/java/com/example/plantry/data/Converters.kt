package com.example.plantry.data

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {

    /** Dates are stored as epoch days, so they sort and compare correctly in SQL. */
    @TypeConverter
    fun localDateToEpochDay(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun localDateFromEpochDay(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    /** A counted unit is stored by the same name as in [IngredientUnit.unit], so lines join their unit row. */
    @TypeConverter
    fun lineUnitToCode(unit: LineUnit): String = unit.code

    @TypeConverter
    fun lineUnitFromCode(code: String): LineUnit = checkNotNull(LineUnit.fromCode(code)) { "unknown unit $code" }

    /** By its id, as the queries in [ProposalEventDao] write it. */
    @TypeConverter
    fun proposalOutcomeToId(outcome: ProposalOutcome?): String? = outcome?.id

    @TypeConverter
    fun proposalOutcomeFromId(id: String?): ProposalOutcome? = id?.let(ProposalOutcome::fromId)
}
