package com.example.obivapp2.viewModel

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "links") // Définit la table Room
data class LinkData(
    @PrimaryKey val url: String, // L'URL sera la clé primaire (m3u8 URL)
    val text: String,
    val filePath: String? = null,
    val lastWatchedPosition: Long = 0L,
    val totalDuration: Long = 0L,
    val lastWatchedTimestamp: Long = 0L,
    val imageUrl: String? = null
)
