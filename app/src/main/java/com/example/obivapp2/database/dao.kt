package com.example.obivapp2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.obivapp2.viewModel.LinkData
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: LinkData)

    @Update
    suspend fun update(link: LinkData)

    @Query("SELECT * FROM links")
    fun getAllLinks(): Flow<List<LinkData>>

    // On affiche les vidéos entamées (plus de 2s) et pas encore finies (reste plus de 10s)
    @Query("SELECT * FROM links WHERE lastWatchedPosition > 2000 AND (totalDuration <= 0 OR lastWatchedPosition < totalDuration - 10000) ORDER BY lastWatchedTimestamp DESC")
    fun getContinueWatchingLinks(): Flow<List<LinkData>>

    @Query("SELECT * FROM links WHERE url = :url LIMIT 1")
    suspend fun getLinkByUrl(url: String): LinkData?

    @Query("SELECT EXISTS(SELECT * FROM links WHERE url = :url)")
    fun isDownloaded(url: String): Flow<Boolean>

    @Query("DELETE FROM links WHERE url = :url")
    suspend fun delete(url: String)

    @Query("UPDATE links SET lastWatchedPosition = :position, totalDuration = :duration, lastWatchedTimestamp = :timestamp WHERE url = :url")
    suspend fun updateProgress(url: String, position: Long, duration: Long, timestamp: Long)
}
