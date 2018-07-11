/*
 * CollectionHelper.kt
 * Implements the CollectionHelper class
 * A CollectionHelper provides helper methods for the podcast collection
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import android.content.Context
import android.preference.PreferenceManager
import android.support.v4.media.MediaMetadataCompat
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.core.Episode
import org.y20k.escapepods.core.Podcast
import java.util.*


/*
 * CollectionHelper class
 */
class CollectionHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(CollectionHelper::class.java)


    /* Creates a MediaMetadata for given Episode */
    fun createMediaMetadata(episode: Episode, podcast: Podcast): MediaMetadataCompat {
        return MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, episode.remoteAudioFileLocation.hashCode().toString())
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, episode.audio)
                .putString(MediaMetadataCompat.METADATA_KEY_GENRE, "Radio")
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, episode.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, podcast.name)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, podcast.name)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,  podcast.cover)
//                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber)
//                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, totalTrackCount)
                .build();
    }


    /* Checks if feed is already in collection */
    fun isNewPodcast(remotePodcastFeedLocation: String, collection: Collection): Boolean {
        for (podcast in collection.podcasts) {
            if (podcast.remotePodcastFeedLocation == remotePodcastFeedLocation) return false
        }
        return true
    }


    /* Checks if enough time passed since last update */
    fun hasEnoughTimePassedSinceLastUpdate(collection: Collection): Boolean {
        val currentDate: Date = Calendar.getInstance().time
        return true // todo remove
//        return currentDate.time - collection.lastUpdate.time  > FIVE_MINUTES_IN_MILLISECONDS
    }


    /* Checks if podcast has new episodes */
    fun podcastHasNewEpisodes(collection: Collection, newPodcast: Podcast): Boolean {
        val oldPodcast = getPodcastFromCollection(collection, newPodcast)
        val newPodcastLatestEpisode: Date = newPodcast.episodes[0].publicationDate
        val oldPodcastLatestEpisode: Date = oldPodcast.episodes[0].publicationDate
        return newPodcastLatestEpisode != oldPodcastLatestEpisode
    }


    /* Clears the audio folder */
    fun clearAudioFolder(context: Context, podcast: Podcast) {
        // determine number of episodes to keep
        var numberOfEpisodesToKeep = PreferenceManager.getDefaultSharedPreferences(context).getInt(Keys.PREF_NUMBER_OF_EPISODES_TO_DOWNLOAD, Keys.DEFAULT_DOWNLOAD_NUMBER_OF_EPISODES_TO_DOWNLOAD);
        if (podcast.episodes.size < numberOfEpisodesToKeep) {
            numberOfEpisodesToKeep = podcast.episodes.size
        }
        // clear audio folder
        FileHelper().clearFolder(context.getExternalFilesDir(Keys.FOLDER_AUDIO), numberOfEpisodesToKeep)

        for (episodeIndex: Int in podcast.episodes.indices) {
            if (episodeIndex > numberOfEpisodesToKeep) {
                podcast.episodes[episodeIndex].audio = ""
            }
        }
        LogHelper.e(TAG, "Result of clearing audio folder:\n$podcast") // todo remove
    }



    /* Returns folder name for podcast */
    fun getPodcastSubDirectory(podcast: Podcast): String {
        return podcast.name.replace("[:/]", "_")
    }


    /* Get the ID from collection for given podcast */
    fun getPodcastIdFromCollection(collection: Collection, podcast: Podcast): Int {
        collection.podcasts.indices.forEach {
            if (podcast.remotePodcastFeedLocation == collection.podcasts[it].remotePodcastFeedLocation) return it
        }
        return 0
    }


    /* Get the counterpart from collection for given podcast */
    private fun getPodcastFromCollection(collection: Collection, podcast: Podcast): Podcast {
        collection.podcasts.forEach {
            if (podcast.remotePodcastFeedLocation == it.remotePodcastFeedLocation) return it
        }
        return Podcast()
    }

}