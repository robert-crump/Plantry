package com.example.plantry.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val source: String,
    val page: Int?,
    val bookServings: Int,
    val ourServings: Int,
    val cookingTimeMinutes: Int,
)

/** User input for creating or updating a [Recipe]; [ourServings] falls back to [bookServings]. */
data class RecipeDraft(
    val title: String,
    val source: String,
    val page: Int?,
    val bookServings: Int,
    val ourServings: Int?,
    val cookingTimeMinutes: Int,
)
