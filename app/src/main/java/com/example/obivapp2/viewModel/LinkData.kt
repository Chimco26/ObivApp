package com.example.obivapp2.viewModel

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "links") // Définit la table Room
data class LinkData(
    @PrimaryKey val url: String, // L'URL sera la clé primaire
    val text: String
)