/*
 * CollectionHelper.kt
 * Implements the CollectionHelper object
 * A CollectionHelper provides helper methods for the podcast collection
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.preference.PreferenceManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import org.y20k.escapepods.Keys
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
        collection.podcasts.forEach { podcast ->
            if (podcast.remotePodcastFeedLocation == remotePodcastFeedLocation) return false
        }
        return true
    }


    /* Check if podcast has cover and audio files */
    fun validatePodcast(podcast: Podcast): Int {
        var result: Int = Keys.PODCAST_VALIDATION_SUCESS
        // check for cover url
        if (podcast.remoteImageFileLocation.isEmpty())  {
            result = Keys.PODCAST_VALIDATION_MISSING_COVER
        }
        // check for audio files
        if (podcast.episodes.isEmpty()) {
            result = Keys.PODCAST_VALIDATION_NO_AUDIO_FILES
        }
        return result
    }


    /* Checks if enough time passed since last update */
    fun hasEnoughTimePassedSinceLastUpdate(context: Context): Boolean {
        val lastSavedUpdate: Date = PreferencesHelper.loadLastUpdateCollection(context)
        val currentDate: Date = Calendar.getInstance().time
//        return currentDate.time - lastUpdate  > Keys.FIVE_MINUTES_IN_MILLISECONDS // todo uncomment for production
        return currentDate.time - lastSavedUpdate.time  > Keys.MINIMUM_TIME_BETWEEN_UPDATES
    }


    /* Checks if a newer collection is available on storage */
    fun isNewerCollectionAvailable(context: Context, lastUpdate: Date): Boolean {
        var newerCollectionAvailable = false
        val lastSavedUpdateString: String = PreferenceManager.getDefaultSharedPreferences(context).getString(Keys.PREF_LAST_UPDATE_COLLECTION, Keys.DEFAULT_RFC2822_DATE)!!
        val lastSavedUpdate: Date = DateTimeHelper.convertFromRfc2822(lastSavedUpdateString)
        if (lastSavedUpdate.after(lastUpdate) || lastSavedUpdateString.equals(Keys.DEFAULT_RFC2822_DATE)) {
            newerCollectionAvailable = true
        }
        return newerCollectionAvailable
    }


    /* Adds new podcast to collection */
    fun addPodcast(collection: Collection, podcast: Podcast): Collection {
        if (podcast.episodes.isNotEmpty()) {
            // update last update
            podcast.lastUpdate = podcast.episodes[0].publicationDate
            // add podcast
            collection.podcasts.add(podcast)
            // return sorted collection
            return sortCollectionByDate(collection)
        } else {
            // nothing to do: return collection
            return collection
        }
    }


    /* Updates the episodes of a podcast in a given collection */
    fun updatePodcast(context: Context, collection: Collection, newPodcast: Podcast): Collection {
        val newEpisodes: MutableList<Episode> = mutableListOf<Episode>()
        collection.podcasts.forEach { podcast ->
            // look for the matching podcast
            if (podcast.getPodcastId() == newPodcast.getPodcastId()) {
                val lastUpdate = podcast.lastUpdate
                // look for episodes newer than last update
                newPodcast.episodes.forEach { episode ->
                    // found a new episode within podcast
                    if (lastUpdate < episode.publicationDate) {
                        newEpisodes.add(episode)
                    }
                }
                if (newEpisodes.isNotEmpty()) {
                    // sort new episodes by publication date
                    newEpisodes.sortByDescending { it.publicationDate }
                    // update last update
                    podcast.lastUpdate = newEpisodes[0].publicationDate
                    // add covers
                    newEpisodes.forEach { episode ->
                        if (episode.cover == Keys.LOCATION_DEFAULT_COVER) {
                            episode.cover = podcast.cover
                        }
                    }
                }
                // add all new episodes
                podcast.episodes.addAll(newEpisodes)
            }
        }
        return sortCollectionByDate(collection)
    }


    /* Checks if podcast has episodes that can be downloaded */
    fun checkPodcastState(collection: Collection, newPodcast: Podcast): Int {
        // get podcast from collection
        val oldPodcast = getPodcast(collection, newPodcast)
        // check if podcast is new
        if (oldPodcast.episodes.isEmpty()) {
            return Keys.PODCAST_STATE_NEW_PODCAST
        }
        // Step 1: New episode check -> compare GUIDs of latest episode
        if (newPodcast.episodes[0].guid == oldPodcast.episodes[0].guid) {
            return Keys.PODCAST_STATE_PODCAST_UNCHANGED
        }
        // Step 2: Not yet downloaded episode check -> test if audio field is empty
        if (getEpisode(collection, newPodcast.episodes[0].getMediaId()).audio.isEmpty()) {
            return Keys.PODCAST_STATE_HAS_NEW_EPISODES
        }
        // Default return
        return Keys.PODCAST_STATE_PODCAST_UNCHANGED
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
    fun removeDuplicates(collection: Collection, feedUrls: Array<String>): Array<String> {
        val feedUrlList: MutableList<String> = feedUrls.toMutableList()
        collection.podcasts.forEach { podcast ->
            LogHelper.e(TAG, "Trying to remove: ${podcast.remotePodcastFeedLocation}") // todo remove
            feedUrlList.remove(podcast.remotePodcastFeedLocation)
        }
        return feedUrlList.toTypedArray()
    }


    /* Get the counterpart from collection for given podcast */
    private fun getPodcast(collection: Collection, podcast: Podcast): Podcast {
        collection.podcasts.forEach {
            if (podcast.remotePodcastFeedLocation == it.remotePodcastFeedLocation) return it
        }
        return Podcast()
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


    /* Get the Id from collection for given media ID String */
    fun getEpisodeId(collection: Collection, mediaId: String): Int {
        collection.podcasts.forEach { podcast ->
            podcast.episodes.forEachIndexed { episodeId, episode ->
                if (episode.getMediaId() == mediaId) {
                    return episodeId
                }
            }
        }
        return -1
    }


    /* Sets the flag "manually downloaded" in Episode for given media ID String */
    fun setManuallyDownloaded(collection: Collection, mediaId: String, manuallyDownloaded: Boolean): Collection {
        collection.podcasts.forEach { podcast ->
            podcast.episodes.forEach { episode ->
                if (episode.getMediaId() == mediaId) {
                    episode.manuallyDownloaded = manuallyDownloaded
                }
            }
        }
        return collection
    }


    /* Saves the playback state of a given episode */
    fun savePlaybackState(context: Context, collection: Collection, episode: Episode = Episode(), playbackState: Int): Collection {
        // set playback state of given episode
        collection.podcasts.forEach { podcast ->
            podcast.episodes.forEach {
                // reset playback state everywhere
                it.playbackState = PlaybackStateCompat.STATE_STOPPED
                // set playback true state at this episode
                if (it.getMediaId() == episode.getMediaId()) {
                    it.playbackState = playbackState
                }
            }
        }
        // save collection
        saveCollection(context, collection)
        // save playback state of PlayerService
        PreferencesHelper.savePlayerPlayBackState(context, playbackState)
        return collection
    }


    /* Saves podcast collection */
    fun saveCollection (context: Context, collection: Collection, lastUpdate: Date = Calendar.getInstance().time, async: Boolean = true) {
        LogHelper.v(TAG, "Saving podcast collection to storage. Async = ${async}. Size = ${collection.podcasts.size}")
        // set last update in collection
        collection.lastUpdate = lastUpdate
        // save last update to shared preferences
        PreferencesHelper.saveLastUpdateCollection(context, lastUpdate)
        // save collection to storage
        when (async) {
            true -> {
                val backgroundJob = Job()
                val uiScope = CoroutineScope(Dispatchers.Main + backgroundJob)
                uiScope.launch {
                    // save collection on background thread
                    val deferred = async(Dispatchers.Default) { FileHelper.saveCollectionSuspended(context, collection) }
                    // wait for result
                    deferred.await()
                    // broadcast collection update
                    sendCollectionBroadcast(context, lastUpdate)
                    backgroundJob.cancel()
                }
            }
            false -> {
                // save collection
                FileHelper.saveCollection(context, collection)
                // broadcast collection update
                sendCollectionBroadcast(context, lastUpdate)
            }
        }
    }


    /* Export podcast collection as OPML */
    fun exportCollection(context: Context, collection: Collection) {
        LogHelper.v(TAG, "Exporting podcast collection as OPML")
        // export collection as OPML - launch = fire & forget (no return value from save collection)
        GlobalScope.launch { FileHelper.exportCollectionSuspended(context, collection) }
    }


    /* Sends a broadcast containing the collection as parcel */
    private fun sendCollectionBroadcast(context: Context, lastUpdate: Date) {
        LogHelper.v(TAG, "Broadcasting that collection has changed.")
        val lastUpdateString: String = DateTimeHelper.convertToRfc2822(lastUpdate)
        val collectionChangedIntent = Intent()
        collectionChangedIntent.action = Keys.ACTION_COLLECTION_CHANGED
        collectionChangedIntent.putExtra(Keys.EXTRA_LAST_UPDATE_COLLECTION, lastUpdateString)
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


    /* Deletes unneeded episodes in collection */
    fun trimPodcastEpisodeLists(context: Context, collection: Collection): Collection {
        collection.podcasts.forEach { podcast ->
            // remove episodes without audio
            podcast.episodes.removeIf {
                episode -> episode.remoteAudioFileLocation.isEmpty()
            }
            // determine number of episodes to keep
            val podcastSize: Int = podcast.episodes.size
            var numberOfEpisodesToKeep = PreferenceManager.getDefaultSharedPreferences(context).getInt(Keys.PREF_NUMBER_OF_EPISODES_TO_KEEP, Keys.DEFAULT_NUMBER_OF_EPISODES_TO_KEEP)
            // delete audio files, if necessary
            when (podcastSize > numberOfEpisodesToKeep) {
                true -> {
                    // from the last episode down to default number of episode that the app keeps
                    for (i in podcastSize -1 downTo numberOfEpisodesToKeep) {
                        val audioUri: String = podcast.episodes[i].audio
                        if (audioUri.isNotEmpty()) {
                            LogHelper.d(TAG, "Trim list of episodes (delete if > 5): Deleting audio file for episode no. ${i+1} - ${podcast.episodes[i].title}")
                            LogHelper.save(context, TAG, "Trim list of episodes (delete if > 5): Deleting audio file for episode no. ${i+1} - ${podcast.episodes[i].title}") // todo remove
                            try {
                                context.contentResolver.delete(Uri.parse(podcast.episodes[i].audio), null, null)
                            } catch (e: Exception) {
                                LogHelper.e(TAG, "Unable to delete file. File has probably been deleted manually. Stack trace: $e")
                            }
                        }
                    }
                }
                false -> numberOfEpisodesToKeep = podcastSize
            }
            // create a trimmed version of the episode list
            val episodesTrimmed: MutableList<Episode> = podcast.episodes.subList(0, numberOfEpisodesToKeep)
            podcast.episodes = episodesTrimmed
        }
        return collection
    }


    /* Deletes audio files that are no longer needed */
    fun deleteUnneededAudioFiles(context: Context, collection: Collection): Collection {
        val numberOfAudioFilesToKeep: Int = PreferenceManager.getDefaultSharedPreferences(context).getInt(Keys.PREF_NUMBER_OF_AUDIO_FILES_TO_KEEP, Keys.DEFAULT_NUMBER_OF_AUDIO_FILES_TO_KEEP)
        for (podcast: Podcast in collection.podcasts) {
            val podcastSize = podcast.episodes.size
            // from the last episode down to default number of audio files that the app keeps - skip empty podcasts
            if (podcastSize > numberOfAudioFilesToKeep) {
                for (i in podcastSize -1 downTo numberOfAudioFilesToKeep) {
                    // check if episode can be deleted
                    if (canBeDeleted(context, podcast.episodes[i])) {
                        LogHelper.d(TAG, "Deleting unneeded audio files (delete if > 2): Deleting audio file for episode no. ${i+1} - ${podcast.episodes[i].title} ")
                        LogHelper.save(context, TAG, "Deleting unneeded audio files (delete if > 2):: Deleting audio file for episode no. ${i+1} - ${podcast.episodes[i].title}") // todo remove
                        // delete audio file
                        try {
                            context.contentResolver.delete(Uri.parse(podcast.episodes[i].audio), null, null)
                        } catch (e: Exception) {
                            LogHelper.e(TAG, "Unable to delete file. File has probably been deleted manually. Stack trace: $e")
                        }
                        // remove audio reference
                        podcast.episodes[i].audio = String()
                        // reset manually downloaded state
                        podcast.episodes[i].manuallyDownloaded = false
                    }
                }
            }
        }
        return collection
    }


    /* Deletes all files in the audio folders of a collection */
    fun deleteAllAudioFile(context: Context, collection: Collection): Collection {
        collection.podcasts.forEach { podcast ->
            // delete all files in podcastS audio
            val audioFolder: File = File(context.getExternalFilesDir(""), FileHelper.determineDestinationFolderPath(Keys.FILE_TYPE_AUDIO, podcast.name))
            FileHelper.clearFolder(audioFolder, 0, false)
            podcast.episodes.forEach { episode ->
                // remove audio reference
                episode.audio = String()
                // reset manually downloaded state
                episode.manuallyDownloaded = false
            }
        }
        return collection
    }


    /* Deletes an episode - used when user presses the delete button */
    fun deleteEpisodeFile(context: Context, collection: Collection, mediaId: String): Collection {
        collection.podcasts.forEach { podcast ->
            podcast.episodes.forEach { episode ->
                if (episode.getMediaId() == mediaId) {
                    // delete audio file
                    LogHelper.d(TAG, "Deleting audio file for episode: ${episode.title}")
                    try {
                        context.getContentResolver().delete(Uri.parse(episode.audio), null, null)
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Unable to delete file. File has probably been deleted manually. Stack trace: $e")
                    }
                    // remove audio reference
                    episode.audio = String()
                    // reset manually downloaded state
                    episode.manuallyDownloaded = false
                }
            }
        }
        return collection
    }





    /* Determines if an episode can be deleted */
    private fun canBeDeleted(context: Context, episode: Episode): Boolean {
        if (episode.getMediaId() == PreferencesHelper.loadUpNextMediaId(context)) {
            // episode is in Up Next queue
            return false
        } else if (episode.playbackState != PlaybackStateCompat.STATE_STOPPED || episode.isFinished()) {
            // episode is paused or playing
            return false
        } else if (episode.audio.isEmpty()) {
            // episode has no audio reference
            return false
        } else if (episode.manuallyDownloaded) {
            // episode was manually downloaded
            return false
        } else {
            // episode may be deleted
            return true
        }
    }


    /* Sorts podcasts - the one with freshest episode first */
    private fun sortCollectionByDate(collection: Collection): Collection {
        // sort episodes by publication date
        collection.podcasts.forEach { podcast ->
            podcast.episodes.sortByDescending { it.publicationDate }
        }
        // sort podcasts in collection by last update
        collection.podcasts.sortByDescending { it.lastUpdate }
        return collection
    }


    /* Sorts episodes by date of publication */
    private fun sortEpisodeByDate(podcast: Podcast): Podcast {
        podcast.episodes.sortByDescending { it.publicationDate}
        return podcast
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