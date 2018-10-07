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
import android.media.MediaMetadata
import android.preference.PreferenceManager
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.core.Episode
import org.y20k.escapepods.core.Podcast
import java.io.File
import java.util.*


/*
 * CollectionHelper class
 */
class CollectionHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(CollectionHelper::class.java)


    /* Creates a MediaMetadata for given Episode */
    fun createMediaMetadata(episode: Episode, podcast: Podcast): MediaMetadata {
        return MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, episode.remoteAudioFileLocation.hashCode().toString())
                .putString(MediaMetadata.METADATA_KEY_MEDIA_URI, episode.audio)
                .putString(MediaMetadata.METADATA_KEY_GENRE, "Podcast")
                .putString(MediaMetadata.METADATA_KEY_TITLE, episode.title)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, podcast.name)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, podcast.name)
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI,  podcast.cover)
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


    /* Check if podcast has cover and audio files */
    fun validatePodcast(podcast: Podcast): Boolean {
        var isValid: Boolean = true
        // check for cover url
        if (podcast.remoteImageFileLocation.isEmpty())  {
            LogHelper.e("Validation failed: Missing cover.")
            isValid = false
        }
        // check for audio files
        podcast.episodes.forEach {
            if (it.remoteAudioFileLocation.isEmpty()) {
                LogHelper.e("Validation failed: Missing audio file.")
                isValid = false
            }
        }
        return isValid
    }


    /* Checks if enough time passed since last update */
    fun hasEnoughTimePassedSinceLastUpdate(context: Context): Boolean {
        val lastUpdate = PreferenceManager.getDefaultSharedPreferences(context).getLong(Keys.PREF_LAST_UPDATE_COLLECTION, 0L)
        val currentDate: Date = Calendar.getInstance().time
//        return currentDate.time - lastUpdate  > Keys.FIVE_MINUTES_IN_MILLISECONDS // todo uncomment for production
        return currentDate.time - lastUpdate  > Keys.ONE_MINUTE_IN_MILLISECONDS
    }


    /* Replaces a podcast within collection and  retains audio references */
    fun replacePodcast(context: Context, collection: Collection, newPodcast: Podcast): Collection {
        val numberOfAudioFilesToKeep: Int = PreferenceManager.getDefaultSharedPreferences(context).getInt(Keys.PREF_NUMBER_OF_AUDIO_FILES_TO_KEEP, Keys.DEFAULT_NUMBER_OF_AUDIO_FILES_TO_KEEP);
        val oldPodcast: Podcast = getPodcastFromCollection(collection, newPodcast)
        // check for existing downloaded audio file references
        for (i in numberOfAudioFilesToKeep -1 downTo 0) {
            if (i < oldPodcast.episodes.size) {
                val oldAudio: String = oldPodcast.episodes[i].audio
                if (oldAudio.length > 0) {
                    // found an existing downloaded audio file reference
                    val newEpisodeId: Int = getEpisodeIdFromPodcast(newPodcast, oldPodcast.episodes[i].remoteAudioFileLocation)
                    // set audio file reference
                    newPodcast.episodes[newEpisodeId].audio = oldAudio
                }
            }
        }
        // replace podcast
        collection.podcasts.set(getPodcastIdFromCollection(collection, newPodcast), newPodcast)
        return collection
    }


    /* Checks if podcast has episodes that can be downloaded */
    fun podcastHasDownloadableEpisodes(collection: Collection, newPodcast: Podcast): Boolean {
        // Step 1: New episode check -> compare GUIDs
        val oldPodcast = getPodcastFromCollection(collection, newPodcast)
        val newPodcastLatestEpisode: String = newPodcast.episodes[0].guid
        val oldPodcastLatestEpisode: String = oldPodcast.episodes[0].guid
        if (newPodcastLatestEpisode != oldPodcastLatestEpisode) {
            return true
        }
        // Step 2: Not yet downloaded episode check -> test if audio field is empty
        if (oldPodcast.episodes[0].audio.isEmpty()) {
            return true
        }
        // Default - no result in step 1 or 2
        return false
    }


    /* Clears an audio folder for a given podcast */
    fun clearAudioFolder(context: Context, collection: Collection) {
        val numberOfAudioFilesToKeep: Int = PreferenceManager.getDefaultSharedPreferences(context).getInt(Keys.PREF_NUMBER_OF_AUDIO_FILES_TO_KEEP, Keys.DEFAULT_NUMBER_OF_AUDIO_FILES_TO_KEEP);
        // clear folders in audio folder
        val audioFolder: File? = context.getExternalFilesDir(Keys.FOLDER_AUDIO)
        if (audioFolder != null && audioFolder.isDirectory) {
            for (podcastFolder: File in audioFolder.listFiles()) {
                FileHelper().clearFolder(podcastFolder, numberOfAudioFilesToKeep)
            }
        }
    }


    /* Cleans up unused audio references in collection */
    fun removeUnusedAudioReferences(context: Context, collection: Collection): Collection {
        val numberOfAudioFilesToKeep: Int = PreferenceManager.getDefaultSharedPreferences(context).getInt(Keys.PREF_NUMBER_OF_AUDIO_FILES_TO_KEEP, Keys.DEFAULT_NUMBER_OF_AUDIO_FILES_TO_KEEP);
        for (podcast: Podcast in collection.podcasts) {
            val podcastSize = podcast.episodes.size
            for (i in podcastSize - 1 downTo numberOfAudioFilesToKeep - 1) {
                podcast.episodes[i].audio = ""
            }
        }
        return collection
    }


    /* Clears an image folder for a given podcast */
    fun clearImagesFolder(context: Context, podcast: Podcast) {
        // clear image folder
        val imagesFolder: File = File(context.getExternalFilesDir(Keys.FOLDER_IMAGES), FileHelper().determineDestinationFolderPath(Keys.FILE_TYPE_IMAGE, podcast.name))
        FileHelper().clearFolder(imagesFolder, 0)
    }


    /* Get the ID from collection for given podcast */
    fun getPodcastIdFromCollection(collection: Collection, podcast: Podcast): Int {
        collection.podcasts.indices.forEach {
            if (podcast.remotePodcastFeedLocation == collection.podcasts[it].remotePodcastFeedLocation) return it
        }
        return -1
    }


    /* Get the counterpart from collection for given podcast */
    private fun getPodcastFromCollection(collection: Collection, podcast: Podcast): Podcast {
        collection.podcasts.forEach {
            if (podcast.remotePodcastFeedLocation == it.remotePodcastFeedLocation) return it
        }
        return Podcast()
    }


    /* Get the ID from podcast for given episode */
    fun getEpisodeIdFromPodcast(podcast: Podcast, episode: Episode): Int {
        podcast.episodes.indices.forEach {
            if (episode.remoteAudioFileLocation == podcast.episodes[it].remoteAudioFileLocation) return it
        }
        return -1
    }


    /* Get the ID from podcast for given remote audio file location */
    fun getEpisodeIdFromPodcast(podcast: Podcast, remoteAudioFileLocation: String): Int {
        podcast.episodes.indices.forEach {
            if (remoteAudioFileLocation == podcast.episodes[it].remoteAudioFileLocation) return it
        }
        return -1
    }


    /* Get the counterpart from podcast for given episode */
    private fun getEpisodeFromPodcast(podcast: Podcast, episode: Episode): Episode {
        podcast.episodes.forEach {
            if (episode.remoteAudioFileLocation == it.remoteAudioFileLocation) return it
        }
        return Episode()
    }


}