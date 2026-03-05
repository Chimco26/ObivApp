package com.example.obivapp2.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteMovie(
    @PrimaryKey val url: String, // L'URL d'origine servant d'identifiant unique
    val title: String,
    val imageUrl: String?,
    val description: String?,
    val videoUrl: String?
)
