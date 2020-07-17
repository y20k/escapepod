package org.y20k.escapepod.database

import androidx.room.*

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcast")
    fun getAll(): List<PodcastEntity>

    @Query("SELECT * FROM podcast WHERE pid IN (:pids)")
    fun loadAllByIds(pids: IntArray): List<PodcastEntity>

    @Query("SELECT * FROM podcast WHERE podcast_name LIKE :name LIMIT 1")
    fun findByName(name: String): PodcastEntity

    @Insert
    fun insert(podcast: PodcastEntity)

    @Insert
    fun insertAll(podcasts: List<PodcastEntity>)

    @Update
    fun add(podcast: PodcastEntity)

    @Delete
    fun delete(user: PodcastEntity)

    @Transaction
    @Query("SELECT * FROM podcast")
    fun getPodcastsWithEpisodes(): List<PodcastWithEpisodesEntity>

}