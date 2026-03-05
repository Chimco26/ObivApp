package com.example.obivapp2.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites")
    fun getAllFavorites(): Flow<List<FavoriteMovie>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteMovie)

    @Delete
    suspend fun delete(favorite: FavoriteMovie)

    @Query("SELECT EXISTS(SELECT * FROM favorites WHERE url = :url)")
    fun isFavorite(url: String): Flow<Boolean>
}
