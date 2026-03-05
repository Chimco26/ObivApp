package com.example.obivapp2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.obivapp2.viewModel.LinkData
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {

    // Insère un lien, remplace si déjà présent (ex: mise à jour du filePath)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: LinkData)

    // Récupère tous les liens depuis la base de données
    @Query("SELECT * FROM links")
    fun getAllLinks(): Flow<List<LinkData>>

    @Query("SELECT EXISTS(SELECT * FROM links WHERE url = :url)")
    fun isDownloaded(url: String): Flow<Boolean>

    @Query("DELETE FROM links WHERE url = :url")
    suspend fun delete(url: String)
}
