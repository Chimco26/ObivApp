package com.example.obivapp2.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "links") // Déclare cette classe comme une table Room
data class LinkData(
    @PrimaryKey val url: String, // URL comme clé primaire pour éviter les doublons
    val text: String
)
