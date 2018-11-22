/*
 * CollectionHelper.kt
 * Implements the CollectionHelper object
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
import android.content.Intent
import android.preference.PreferenceManager
import android.support.v4.media.MediaMetadataCompat
import androidx.core.content.edit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.core.Episode
import org.y20k.escapepods.core.Podcast
import java.io.File
import java.util.*


/*
 * CollectionHelper object
 */
object CollectionHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(CollectionHelper::class.java)


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


    /* Adds new podcast to collection */
    fun addPodcast(context: Context, collection: Collection, podcast: Podcast): Collection {
        var newPodcast: Podcast = podcast
        // trim episode list
        newPodcast = trimEpisodeList(context, newPodcast)
        // add podcast
        collection.podcasts.add(newPodcast)
        return collection
    }


    /* Replaces a podcast within collection and  retains audio references */
    fun replacePodcast(context: Context, collection: Collection, podcast: Podcast): Collection {
        val numberOfAudioFilesToKeep: Int = PreferenceManager.getDefaultSharedPreferences(context).getInt(Keys.PREF_NUMBER_OF_AUDIO_FILES_TO_KEEP, Keys.DEFAULT_NUMBER_OF_AUDIO_FILES_TO_KEEP);
        var newPodcast: Podcast = podcast
        val oldPodcast: Podcast = getPodcastFromCollection(collection, newPodcast)
        // check for existing downloaded audio file references
        for (i in numberOfAudioFilesToKeep -1 downTo 0) {
            if (i < oldPodcast.episodes.size) {
                val oldAudio: String = oldPodcast.episodes[i].audio
                if (oldAudio.isEmpty()) {
                    // found an existing downloaded audio file reference
                    val newEpisodeId: Int = getEpisodeIdFromPodcast(newPodcast, oldPodcast.episodes[i])
                    // set audio file reference, if episode id was found
                    if (newEpisodeId > -1) {
                        newPodcast.episodes[newEpisodeId].audio = oldAudio
                    }
                }
            }
        }
        // check for existing cover
        if (oldPodcast.cover != Keys.LOCATION_DEFAULT_COVER) {
            newPodcast.cover = oldPodcast.cover
        }
        // trim episode list
        newPodcast = trimEpisodeList(context, newPodcast)
        // replace podcast
        collection.podcasts.set(getPodcastId(collection, newPodcast), newPodcast)
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


    /* Puts the podcast cover in episodes with no cover  */
    fun fillEmptyEpisodeCovers(podcast: Podcast): Podcast {
        podcast.episodes.forEach {episode ->
            if (episode.cover == Keys.LOCATION_DEFAULT_COVER) {
                episode.cover = podcast.cover
            }
        }
        return podcast
    }


    /* Clears an audio folder for a given podcast */
    fun clearAudioFolder(context: Context, collection: Collection) {
        val numberOfAudioFilesToKeep: Int = PreferenceManager.getDefaultSharedPreferences(context).getInt(Keys.PREF_NUMBER_OF_AUDIO_FILES_TO_KEEP, Keys.DEFAULT_NUMBER_OF_AUDIO_FILES_TO_KEEP);
        // clear folders in audio folder
        val audioFolder: File? = context.getExternalFilesDir(Keys.FOLDER_AUDIO)
        if (audioFolder != null && audioFolder.isDirectory) {
            for (podcastFolder: File in audioFolder.listFiles()) {
                FileHelper.clearFolder(podcastFolder, numberOfAudioFilesToKeep)
            }
        }
    }


    /* Cleans up unused audio references in collection */
    fun removeUnusedAudioReferences(context: Context, collection: Collection): Collection {
        val numberOfAudioFilesToKeep: Int = PreferenceManager.getDefaultSharedPreferences(context).getInt(Keys.PREF_NUMBER_OF_AUDIO_FILES_TO_KEEP, Keys.DEFAULT_NUMBER_OF_AUDIO_FILES_TO_KEEP);
        for (podcast: Podcast in collection.podcasts) {
            val podcastSize = podcast.episodes.size
            for (i in podcastSize - 1 downTo numberOfAudioFilesToKeep) {
                podcast.episodes[i].audio = ""
            }
        }
        return collection
    }


    /* Clears an image folder for a given podcast */
    fun clearImagesFolder(context: Context, podcast: Podcast) {
        // clear image folder
        val imagesFolder: File = File(context.getExternalFilesDir(""), FileHelper.determineDestinationFolderPath(Keys.FILE_TYPE_IMAGE, podcast.name))
        FileHelper.clearFolder(imagesFolder, 0)
    }


    /* Deletes Images and Audio folder of a given podcast */
    fun deletePodcastFolders(context: Context, podcast: Podcast) {
        // delete image folder
        val imagesFolder: File = File(context.getExternalFilesDir(""), FileHelper.determineDestinationFolderPath(Keys.FILE_TYPE_IMAGE, podcast.name))
        FileHelper.clearFolder(imagesFolder, 0, true)
        // delete audio folder
        val audioFolder: File = File(context.getExternalFilesDir(""), FileHelper.determineDestinationFolderPath(Keys.FILE_TYPE_AUDIO, podcast.name))
        FileHelper.clearFolder(audioFolder, 0, true)
    }


    /* Removes feeds that are already in the podcast collection */
    fun removeDuplicates(collection: Collection, feedUrlList: ArrayList<String>): ArrayList<String> {
        collection.podcasts.forEach { podcast ->
            feedUrlList.remove(podcast.remotePodcastFeedLocation)
        }
        return feedUrlList
    }


    /* Get the ID from collection for given podcast */
    fun getPodcastId(collection: Collection, podcast: Podcast): Int {
        collection.podcasts.indices.forEach {
            if (podcast.remotePodcastFeedLocation == collection.podcasts[it].remotePodcastFeedLocation) return it
        }
        return -1
    }


    /* Get Episode from collection for given media ID String */
    fun getEpisode(collection: Collection, mediaId: String): Episode {
        collection.podcasts.forEach { podcast ->
            podcast.episodes.forEach { episode ->
                if (episode.getMediaId() == mediaId) {
                    return episode
                }
            }
        }
        return Episode()
    }


    /* Sets the flag "manually downloaded" in Episode for given media ID String */
    fun setManuallyDownloaded(context: Context, collection: Collection, mediaId: String): Episode {
        collection.podcasts.forEach { podcast ->
            podcast.episodes.forEach { episode ->
                if (episode.getMediaId() == mediaId) {
                    episode.manuallyDownloaded = true
                    saveCollection(context, collection)
                    return episode
                }
            }
        }
        return Episode()
    }


    /* Saves the playpack state of a given episode */
    fun savePlaybackstate(context: Context, collection: Collection, episode: Episode = Episode(), isPlaying: Boolean): Collection {
        collection.podcasts.forEach {
            it.episodes.forEach {
                // reset playback state everywhere
                it.isPlaying = false
                // set playback true state at this episode
                if (it.getMediaId() == episode.getMediaId()) {
                    it.isPlaying = isPlaying
                }
            }
        }
        saveCollection(context, collection)
        return collection
    }


    /* Saves podcast collection */
    fun saveCollection (context: Context, collection: Collection) {
        LogHelper.v(TAG, "Saving podcast collection to storage")
        // save time of last update
        PreferenceManager.getDefaultSharedPreferences(context).edit {putLong(Keys.PREF_LAST_UPDATE_COLLECTION, Calendar.getInstance().timeInMillis)}
        val backgroundJob = Job()
        val uiScope = CoroutineScope(Dispatchers.Main + backgroundJob)
        uiScope.launch {
            // save collection on background thread
            val deferred = async(Dispatchers.Default) { FileHelper.saveCollection(context, collection) }
            // wait for result
            deferred.await()
            // broadcast collection update
            sendCollectionBroadcast(context)
            backgroundJob.cancel()
        }
    }


    /* Export podcast collection as OPML */
    fun exportCollection(context: Context, collection: Collection) {
        LogHelper.v(TAG, "Exporting podcast collection as OPML")
        // export collection as OPML - launch = fire & forget (no return value from save collection)
        GlobalScope.launch { FileHelper.exportCollection(context, collection) }
    }


    /* Sends a broadcast containing the collction as parcel */
    fun sendCollectionBroadcast(context: Context) {
        val collectionChangedIntent = Intent()
        collectionChangedIntent.action = Keys.ACTION_COLLECTION_CHANGED
        LocalBroadcastManager.getInstance(context).sendBroadcast(collectionChangedIntent)
    }


    /* Creates MediaMetadata for one episode */
    fun buildMediaMetadata(podcast: Podcast, episodeId: Int): MediaMetadataCompat {
        return MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, podcast.episodes[episodeId].remoteAudioFileLocation)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, podcast.episodes[episodeId].audio)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, podcast.name)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, Keys.APPLICATION_NAME)
                .putString(MediaMetadataCompat.METADATA_KEY_GENRE, Keys.MEDIA_GENRE)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, podcast.cover)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, podcast.episodes[episodeId].title)
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, podcast.episodes.size.toLong())
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, episodeId.toLong())
                .build()
    }


    /* Deletes unneeded episodes in podcast */
    private fun trimEpisodeList(context: Context, podcast: Podcast): Podcast {
        val podcastSize: Int = podcast.episodes.size
        var numberOfEpisodesToKeep = PreferenceManager.getDefaultSharedPreferences(context).getInt(Keys.PREF_NUMBER_OF_EPISODES_TO_KEEP, Keys.DEFAULT_NUMBER_OF_EPISODES_TO_KEEP);
        if (numberOfEpisodesToKeep > podcastSize) {
            numberOfEpisodesToKeep = podcastSize
        }
        val episodesTrimmed: MutableList<Episode> = podcast.episodes.subList(0, numberOfEpisodesToKeep)
        podcast.episodes = episodesTrimmed
        return podcast
    }


    /* Get the counterpart from collection for given podcast */
    private fun getPodcastFromCollection(collection: Collection, podcast: Podcast): Podcast {
        collection.podcasts.forEach {
            if (podcast.remotePodcastFeedLocation == it.remotePodcastFeedLocation) return it
        }
        return Podcast()
    }


    /* Get the ID from podcast for given episode */
    private fun getEpisodeIdFromPodcast(podcast: Podcast, episode: Episode): Int {
        podcast.episodes.indices.forEach {
            if (episode.remoteAudioFileLocation == podcast.episodes[it].remoteAudioFileLocation) return it
        }
        return -1
    }


    /* Get the ID from podcast for given remote audio file location */
    private fun getEpisodeIdFromPodcast(podcast: Podcast, remoteAudioFileLocation: String): Int {
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