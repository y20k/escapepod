package org.y20k.escapepod.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface EpisodeDao {
    @Query("SELECT COUNT(*) FROM episodes")
    fun getSize(): Int

    @Query("SELECT * FROM episodes")
    fun getAll(): LiveData<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE eid IN (:eids)")
    fun loadAllByIds(eids: IntArray): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE episode_title LIKE :title LIMIT 1")
    fun findByTitle(title: String): EpisodeEntity

    @Query("SELECT * FROM episodes WHERE episode_guid IS :guid LIMIT 1")
    fun findByGuid(guid: String): EpisodeEntity

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(episode: EpisodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(episodes: List<EpisodeEntity>): List< Long>

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(episode: EpisodeEntity)

    @Delete
    fun delete(episode: EpisodeEntity)

    @Transaction
    fun upsert(episode: EpisodeEntity) {
        val rowId = insert(episode)
        if (rowId == -1L) {
            update(episode)
        }
    }

    @Transaction
    fun upsertAll(episodes: List<EpisodeEntity>) {
        val rowIds = insertAll(episodes)
        val episodesToUpdate = rowIds.mapIndexedNotNull { index, rowId ->
            if (rowId == -1L) null else episodes[index] }
        episodesToUpdate.forEach { update(it) }
    }

}