package com.example.obivapp2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    @Query("SELECT * FROM links")
    fun getAllLinks(): Flow<List<LinkData>> // Utilisation de Flow pour l'observer en direct

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: LinkData) // La fonction doit être `suspend` pour Room

//    @Query("DELETE FROM links WHERE url = :url")
//    suspend fun deleteByUrl(url: String): Int// Supprime un lien par URL
}
