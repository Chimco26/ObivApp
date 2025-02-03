package com.example.obivapp2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.obivapp2.viewModel.LinkData

@Dao
interface LinkDao {

    // Insère un lien, ignore les doublons
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: LinkData)

    // Récupère tous les liens depuis la base de données
    @Query("SELECT * FROM links")
    suspend fun getAllLinks(): List<LinkData>
}
