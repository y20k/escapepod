/*
 * EpisodeDao.kt
 * Implements the EpisodeDao interface
 * An EpisodeDao interface provides methods for accessing episodes within the collection database
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.database.daos

import androidx.lifecycle.LiveData
import androidx.room.*
import org.y20k.escapepod.database.objects.Episode


/*
 * EpisodeDao interface
 */
@Dao
interface EpisodeDao {
    @Query("SELECT COUNT(*) FROM episodes")
    fun getSize(): Int


    @Query("SELECT * FROM episodes")
    fun getAll(): LiveData<List<Episode>>


    @Query("SELECT * FROM episodes ORDER BY publication_date DESC LIMIT :limit")
    fun getChronological(limit: Int): List<Episode>


    @Query("SELECT * FROM episodes WHERE episode_remote_podcast_feed_location IS :episodeRemotePodcastFeedLocation ORDER BY publication_date DESC LIMIT 1")
    fun getLatest(episodeRemotePodcastFeedLocation: String): Episode


    @Query("SELECT title FROM episodes WHERE media_id IS :mediaId LIMIT 1")
    fun getTitle(mediaId: String): String?


    @Query("SELECT * FROM episodes WHERE media_id IS :mediaId LIMIT 1")
    fun findByMediaId(mediaId: String): Episode?


    @Query("SELECT * FROM episodes WHERE media_id IS :mediaId LIMIT 1")
    suspend fun findByMediaIdSuspended(mediaId: String): Episode?


    @Query("SELECT * FROM episodes WHERE episode_remote_podcast_feed_location IS :episodeRemotePodcastFeedLocation")
    fun findByEpisodeRemotePodcastFeedLocation(episodeRemotePodcastFeedLocation: String): List<Episode>


    @Query("SELECT * FROM episodes WHERE remote_audio_file_location IS :remoteAudioFileLocation LIMIT 1")
    fun findByRemoteAudioFileLocation(remoteAudioFileLocation: String): Episode?


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(episode: Episode): Long


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(episodes: List<Episode>): List<Long>


    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(episode: Episode)


    @Delete
    fun delete(episode: Episode)


    @Query("DELETE from episodes where episode_remote_podcast_feed_location IS :remotePodcastFeedLocation")
    fun deleteAll(remotePodcastFeedLocation: String): Int


    @Transaction
    fun upsert(episode: Episode): Boolean {
        val rowId = insert(episode)
        if (rowId == -1L) {
            update(episode)
            // false = episode was NOT NEW (= update)
            return false
        }
        // true = episode was NEW (= insert)
        return true
    }


    @Transaction
    fun upsertAll(episodes: List<Episode>) {
        val rowIds = insertAll(episodes)
        val episodesToUpdate = rowIds.mapIndexedNotNull { index, rowId ->
            if (rowId == -1L) {
                // result -1 means that insert operation was not successful
                episodes[index]
            } else {
                null
            }
        }
        episodesToUpdate.forEach { update(it) }
    }


    /* Updates epsiode cover and small cover */
    @Query("UPDATE episodes SET cover = :cover , small_cover = :smallCover WHERE episode_remote_podcast_feed_location IS :episodeRemotePodcastFeedLocation")
    fun updateCover(episodeRemotePodcastFeedLocation: String, cover: String, smallCover: String): Int


    /* Updates episode audio and duration */
    @Query("UPDATE episodes SET audio = :audio , duration = :duration WHERE remote_audio_file_location IS :remoteAudioFileLocation")
    fun updateAudioRemoteAudioFileLocation(remoteAudioFileLocation: String, audio: String, duration: Long): Int


    /* Updates episode playback position */
    @Query("UPDATE episodes SET playback_position = :playbackPosition WHERE media_id IS :mediaId")
    fun updatePlaybackPosition(mediaId: String, playbackPosition: Long): Int


    /* Updates episode audio and duration */
    @Query("UPDATE episodes SET audio = :audio , duration = :duration WHERE remote_audio_file_name IS :localFileName")
    fun updateAudioByFileName(localFileName: String, audio: String, duration: Long): Int


    /* Set episode playback position to it's duration - marking it as played */
    // https://developer.android.com/reference/kotlin/android/support/v4/media/session/PlaybackStateCompat#state_stopped
    @Query("UPDATE episodes SET playback_position = duration, playback_state = 1 WHERE media_id IS :mediaId")
    fun markPlayed(mediaId: String): Int


    /* Resets local audio reference - used when user taps on trashcan */
    // https://developer.android.com/reference/kotlin/android/support/v4/media/session/PlaybackStateCompat#state_stopped
    @Query("UPDATE episodes SET audio = '', playback_position = 0, duration = 0, playback_state = 1, manually_deleted = :manuallyDeleted WHERE media_id IS :mediaId")
    fun resetLocalAudioReference(mediaId: String, manuallyDeleted: Boolean): Int


    /* Resets local audio references of all episodes */
    // https://developer.android.com/reference/kotlin/android/support/v4/media/session/PlaybackStateCompat#state_stopped
    @Query("UPDATE episodes SET audio = '', playback_position = 0, duration = 0, playback_state = 1, manually_deleted = 1")
    fun resetLocalAudioReferencesForAllEpisodes()


    /* set playback state for all episodes - except of the one indicated by "exclude" */
    @Query("UPDATE episodes SET playback_state = :playbackState WHERE media_id IS NOT :exclude")
    fun setPlaybackStateForAllEpisodes(playbackState: Int, exclude: String): Int

}