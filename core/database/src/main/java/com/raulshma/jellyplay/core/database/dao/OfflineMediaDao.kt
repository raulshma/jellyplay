package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineMediaDao {

    @Query("SELECT * FROM offline_media WHERE mediaType IN ('SERIES', 'MOVIE', 'AUDIO', 'MUSIC') ORDER BY createdAt DESC")
    fun getTopLevelItems(): Flow<List<OfflineMediaEntity>>

    @Query("SELECT * FROM offline_media WHERE seriesId = :seriesId AND mediaType = 'SEASON' ORDER BY seasonNumber ASC")
    fun getSeasonsForSeries(seriesId: String): Flow<List<OfflineMediaEntity>>

    @Query("SELECT * FROM offline_media WHERE seasonId = :seasonId AND mediaType = 'EPISODE' ORDER BY episodeNumber ASC")
    fun getEpisodesForSeason(seasonId: String): Flow<List<OfflineMediaEntity>>

    @Query("SELECT * FROM offline_media WHERE id = :id")
    suspend fun getById(id: String): OfflineMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OfflineMediaEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<OfflineMediaEntity>)

    @Query("DELETE FROM offline_media WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM offline_media WHERE seriesId = :seriesId")
    suspend fun deleteBySeriesId(seriesId: String)

    @Query("DELETE FROM offline_media WHERE seasonId = :seasonId")
    suspend fun deleteBySeasonId(seasonId: String)

    @Query("DELETE FROM offline_media WHERE mediaType = 'SEASON' AND id NOT IN (SELECT DISTINCT seasonId FROM offline_media WHERE mediaType = 'EPISODE' AND seasonId IS NOT NULL)")
    suspend fun deleteOrphanedSeasons()

    @Query("DELETE FROM offline_media WHERE mediaType = 'SERIES' AND id NOT IN (SELECT DISTINCT seriesId FROM offline_media WHERE mediaType IN ('SEASON', 'EPISODE') AND seriesId IS NOT NULL)")
    suspend fun deleteOrphanedSeries()

    @Transaction
    suspend fun cleanupOrphans() {
        deleteOrphanedSeasons()
        deleteOrphanedSeries()
    }

    @Query("SELECT COUNT(*) FROM offline_media WHERE mediaType IN ('SERIES', 'MOVIE', 'AUDIO', 'MUSIC')")
    fun getOfflineItemCount(): Flow<Int>
}
