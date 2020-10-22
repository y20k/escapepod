/*
 * EpisodeDescriptionDao.kt
 * Implements the EpisodeDescriptionDao interface
 * An EpisodeDescriptionDao interface provides methods for accessing episode descriptions within the collection database
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.database.daos

import androidx.room.*
import org.y20k.escapepod.database.objects.EpisodeDescription


/*
 * EpisodeDescriptionDao interface
 */
@Dao
interface EpisodeDescriptionDao {

    @Query("SELECT * FROM episode_descriptions WHERE media_id IS :mediaId LIMIT 1")
    fun findByMediaId(mediaId: String): EpisodeDescription?


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(episodeDescription: EpisodeDescription): Long


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(episodeDescriptions: List<EpisodeDescription>): List<Long>


    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(episodeDescription: EpisodeDescription)


    @Delete
    fun delete(episodeDescription: EpisodeDescription)

    @Query("DELETE from episode_descriptions where episode_remote_podcast_feed_location IS :episodeRemotePodcastFeedLocation")
    fun deleteAll(episodeRemotePodcastFeedLocation: String): Int


    @Transaction
    fun upsert(episode: EpisodeDescription): Boolean {
        val rowId = insert(episode)
        if (rowId == -1L) {
            update(episode)
            // episode was NOT NEW (= update)
            return false
        }
        // episode was NEW (= insert)
        return true
    }


    @Transaction
    fun upsertAll(episodeDescriptions: List<EpisodeDescription>) {
        val rowIds = insertAll(episodeDescriptions)
        val episodeDescriptionsToUpdate = rowIds.mapIndexedNotNull { index, rowId ->
            if (rowId == -1L) null else episodeDescriptions[index] }
        episodeDescriptionsToUpdate.forEach { update(it) }
    }

}