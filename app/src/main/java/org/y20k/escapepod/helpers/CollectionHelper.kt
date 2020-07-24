/*
 * CollectionHelper.kt
 * Implements the CollectionHelper object
 * A CollectionHelper provides helper methods for the podcast collection
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.net.toFile
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import org.y20k.escapepod.Keys
import org.y20k.escapepod.core.Collection
import org.y20k.escapepod.core.Episode
import org.y20k.escapepod.core.Podcast
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
            result = Keys.PODCAST_VALIDATION_NO_VALID_EPISODES
        }
        return result
    }


    /* Checks if enough time passed since last update */
    fun hasEnoughTimePassedSinceLastUpdate(context: Context): Boolean {
        val lastUpdate: Date = PreferencesHelper.loadLastUpdateCollection(context)
        val currentDate: Date = Calendar.getInstance().time
        return currentDate.time - lastUpdate.time  > Keys.MINIMUM_TIME_BETWEEN_UPDATES
    }


    /* Checks if a newer collection is available on storage */
    fun isNewerCollectionAvailable(context: Context, date: Date): Boolean {
        var newerCollectionAvailable = false
        val modificationDate: Date = PreferencesHelper.loadCollectionModificationDate(context)
        if (modificationDate.after(date) || date == Keys.DEFAULT_DATE) {
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
    fun updatePodcast(collection: Collection, newPodcast: Podcast): Collection {
        val newEpisodes: MutableList<Episode> = mutableListOf<Episode>()
        collection.podcasts.forEach { podcast ->
            // look for the matching podcast
            if (podcast.getPodcastId() == newPodcast.getPodcastId()) {
                val newestEpisodePublicationDate: Date = podcast.episodes[0].publicationDate
                val newestEpisodeRemoteAudioFileLocation: String = podcast.episodes[0].remoteAudioFileLocation
                // look for newer episodes
                newPodcast.episodes.forEach { episode ->
                    // found a new episode within podcast
                    if (episode.publicationDate.after(newestEpisodePublicationDate) && episode.publicationDate != newestEpisodePublicationDate) {
                        if (episode.remoteAudioFileLocation != newestEpisodeRemoteAudioFileLocation) {
                            newEpisodes.add(episode)
                        }
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
                        if (episode.smallCover == Keys.LOCATION_DEFAULT_COVER) {
                            episode.smallCover = podcast.smallCover
                        }
                    }
                }
                // add all new episodes
                podcast.episodes.addAll(newEpisodes)
                // update podcast website
                podcast.website = newPodcast.website
                podcast.episodes.forEach { episode -> episode.podcastWebsite = podcast.website } // todo remove again in a couple of month ^o^
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
            when (!oldPodcast.episodes[0].manuallyDeleted && oldPodcast.episodes[0].audio.isEmpty()) {
                true -> return Keys.PODCAST_STATE_HAS_NEW_EPISODES     // same podcast - but first episode not downloaded yet
                false -> return Keys.PODCAST_STATE_PODCAST_UNCHANGED   // same podcast
            }
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


    /* Gets a list of all episodes in collection in chronological order */
    fun getAllEpisodesChronological(collection: Collection): MutableList<Episode> {
        val episodesChronological: MutableList<Episode> = mutableListOf()
        collection.podcasts.forEach { podcast ->
            podcast.episodes.forEach { episode ->
                episodesChronological.add(episode)
            }
        }
        episodesChronological.sortByDescending { it.publicationDate}
        return episodesChronological
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


    /* Marks Episode for given media ID String as played - sets position of playback to episode end (= to duration) */
    fun markEpisodePlayed(collection: Collection, mediaId: String): Collection {
        collection.podcasts.forEach { podcast ->
            podcast.episodes.forEach { episode ->
                if (episode.getMediaId() == mediaId) {
                    episode.playbackPosition = episode.duration
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
        // save collection and store modification date
        collection.modificationDate = saveCollection(context, collection)
        // save playback state of PlayerService
        PreferencesHelper.savePlayerPlaybackState(context, playbackState)
        return collection
    }


    /* Saves podcast collection */
    fun saveCollection (context: Context, collection: Collection, async: Boolean = true): Date {
        LogHelper.v(TAG, "Saving podcast collection to storage. Async = ${async}. Size = ${collection.podcasts.size}")
        // get modification date
        val date: Date = Calendar.getInstance().time
        collection.modificationDate = date
        // save collection to storage
        when (async) {
            true -> {
                val backgroundJob = Job()
                val uiScope = CoroutineScope(Dispatchers.Main + backgroundJob)
                uiScope.launch {
                    // save collection on background thread
                    val deferred = async(Dispatchers.Default) { FileHelper.saveCollectionSuspended(context, collection, date) }
                    // wait for result
                    deferred.await()
                    // broadcast collection update
                    sendCollectionBroadcast(context, date)
                    backgroundJob.cancel()
                }
            }
            false -> {
                // save collection
                FileHelper.saveCollection(context, collection, date)
                // broadcast collection update
                sendCollectionBroadcast(context, date)
            }
        }
        // return modification date
        return date
    }


    /* Export podcast collection as OPML */
    fun exportCollection(context: Context, collection: Collection) {
        LogHelper.v(TAG, "Exporting podcast collection as OPML")
        // export collection as OPML - launch = fire & forget (no return value from save collection)
        GlobalScope.launch { FileHelper.backupCollectionAsOpmlSuspended(context, collection) }
    }


    /* Adds the website of an updated podcast to all of the episodes of its counterpart in collection */
    fun addPodcastWebsiteToEpisodes(context: Context, collection: Collection, newPodcast: Podcast): Collection {
        collection.podcasts.forEach { oldPodcast ->
            if (oldPodcast.getPodcastId() == newPodcast.getPodcastId()) {
                oldPodcast.episodes.forEach { oldEpisode ->
                    oldEpisode.podcastWebsite = newPodcast.website
                }
            }
        }
        return collection
    }


    /* Extracts all audio file references from a collection */
    private fun getAllAudioFileReferences(collection: Collection): ArrayList<String> {
        val audioFileReferences: ArrayList<String> = arrayListOf()
        collection.podcasts.forEach { podcast ->
            podcast.episodes.forEach { episode ->
                if (episode.audio.isNotBlank()) {
                    audioFileReferences.add(episode.audio)
                }
            }
        }
        return audioFileReferences
    }


    /* Sends a broadcast containing the collection as parcel */
    private fun sendCollectionBroadcast(context: Context, modificationDate: Date) {
        LogHelper.v(TAG, "Broadcasting that collection has changed.")
        val collectionChangedIntent = Intent()
        collectionChangedIntent.action = Keys.ACTION_COLLECTION_CHANGED
        collectionChangedIntent.putExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE, modificationDate.time)
        LocalBroadcastManager.getInstance(context).sendBroadcast(collectionChangedIntent)
    }


    /* Creates MediaMetadata for a single episode - used in media session*/
    fun buildEpisodeMediaMetadata(context: Context, episode: Episode): MediaMetadataCompat {
        return MediaMetadataCompat.Builder().apply {
            putString(MediaMetadataCompat.METADATA_KEY_TITLE, episode.title)
            putString(MediaMetadataCompat.METADATA_KEY_ARTIST, episode.podcastName)
            putString(MediaMetadataCompat.METADATA_KEY_ALBUM, episode.podcastName)
            putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, episode.cover)
            putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, episode.audio)
            putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, ImageHelper.getScaledPodcastCover(context, episode.cover, Keys.SIZE_COVER_LOCK_SCREEN))
            putLong(MediaMetadataCompat.METADATA_KEY_DURATION, episode.duration)
        }.build()
    }


    /* Creates MediaItem for a single episode - used by collection provider */
    fun buildEpisodeMediaMetaItem(episode: Episode): MediaBrowserCompat.MediaItem {
        val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
        mediaDescriptionBuilder.setMediaId(episode.getMediaId())
        mediaDescriptionBuilder.setTitle(episode.title)
        mediaDescriptionBuilder.setSubtitle(episode.podcastName)
        mediaDescriptionBuilder.setDescription(episode.podcastName)
        //mediaDescriptionBuilder.setIconUri(Uri.parse(episode.cover))
        return MediaBrowserCompat.MediaItem(mediaDescriptionBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }


    /* Creates MediaItem for a single episode - used by collection provider */
    fun buildPodcastMediaMetaItem(podcast: Podcast): MediaBrowserCompat.MediaItem {
        val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
        mediaDescriptionBuilder.setTitle(podcast.name)
        mediaDescriptionBuilder.setIconUri(Uri.parse(podcast.cover))
        return MediaBrowserCompat.MediaItem(mediaDescriptionBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }


    /* Deletes unneeded episodes in collection - removes audio files first and then trims episode list afterwards */
    fun trimPodcastEpisodeLists(context: Context, collection: Collection): Collection {
        collection.podcasts.forEach { podcast ->
            // remove episodes without a remote audio location available
            podcast.episodes.removeIf {
                episode -> episode.remoteAudioFileLocation.isEmpty()
            }
            // determine number of episodes to keep
            val podcastSize: Int = podcast.episodes.size
            var numberOfEpisodesToKeep = PreferenceManager.getDefaultSharedPreferences(context).getInt(Keys.PREF_NUMBER_OF_EPISODES_TO_KEEP, Keys.DEFAULT_NUMBER_OF_EPISODES_TO_KEEP)
            // delete audio files, if necessary
            when (podcastSize > numberOfEpisodesToKeep) {
                true -> {
                    // from the oldest episode down to default number of episode that the app keeps
                    for (i in podcastSize -1 downTo numberOfEpisodesToKeep) {
                        val audioUri: String = podcast.episodes[i].audio
                        if (audioUri.isNotEmpty()) {
                            try {
                                Uri.parse(podcast.episodes[i].audio).toFile().delete()
                            } catch (e: Exception) {
                                LogHelper.e(TAG, "Unable to delete file. File has probably been deleted manually. Stack trace: $e")
                            }
                        }
                    }
                }
                false -> numberOfEpisodesToKeep = podcastSize
            }
            // afterwards create a trimmed version of the episode list
            val episodesTrimmed: MutableList<Episode> = podcast.episodes.subList(0, numberOfEpisodesToKeep)
            podcast.episodes = episodesTrimmed
        }
        return collection
    }


    /* Deletes audio files that are no longer needed - but keep episodes */
    fun deleteUnneededAudioFiles(context: Context, collection: Collection): Collection {
        val numberOfAudioFilesToKeep: Int = PreferenceManager.getDefaultSharedPreferences(context).getInt(Keys.PREF_NUMBER_OF_AUDIO_FILES_TO_KEEP, Keys.DEFAULT_NUMBER_OF_AUDIO_FILES_TO_KEEP)
        for (podcast: Podcast in collection.podcasts) {
            val podcastSize = podcast.episodes.size
            // from the last episode down to default number of audio files that the app keeps - skip empty podcasts
            if (podcastSize > numberOfAudioFilesToKeep) {
                for (i in podcastSize -1 downTo numberOfAudioFilesToKeep) {
                    // check if episode can be deleted
                    if (canBeDeleted(context, podcast.episodes[i])) {
                        // delete audio file
                        try {
                            Uri.parse(podcast.episodes[i].audio).toFile().delete()
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



    /* Delete files in audio folder that are not referenced in collection - used for housekeeping */
    fun deleteUnReferencedAudioFiles(context: Context, collection: Collection) {
        val audioFileReferences: ArrayList<String> = getAllAudioFileReferences(collection)
        val audioFolder: File? = context.getExternalFilesDir(Keys.FOLDER_AUDIO)
        if (audioFolder != null && audioFolder.exists()) {
            val subFolders: Array<File>? = audioFolder.listFiles()
            subFolders?.forEach { folder ->

                // look for un-referenced files in each subfolder
                val files: Array<File>? = folder.listFiles()
                files?.forEach { file ->
                    val fileUriString: String = Uri.fromFile(file).toString()
                    if (!(audioFileReferences.contains(fileUriString))) {
                        file.delete()
                    }
                }

            }
        }
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
    fun deleteEpisodeAudioFile(context: Context, collection: Collection, mediaId: String, manuallyDeleted: Boolean = false): Collection {
        collection.podcasts.forEach { podcast ->
            podcast.episodes.forEach { episode ->
                if (episode.getMediaId() == mediaId) {
                    // delete audio file
                    LogHelper.d(TAG, "Deleting audio file for episode: ${episode.title}")
                    try {
                        Uri.parse(episode.audio).toFile().delete()
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Unable to delete file. File has probably been deleted manually. Stack trace: $e")
                    }
                    // remove audio reference
                    episode.audio = String()
                    // store manually deleted state
                    episode.manuallyDeleted = manuallyDeleted
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
        } else if (episode.hasBeenStarted() && !episode.isFinished()) {
            // episode has been started but not finished
            return false
        } else if (episode.playbackState != PlaybackStateCompat.STATE_STOPPED && !episode.isFinished()) {
            // episode is paused or playing
            return false
        } else if (episode.manuallyDownloaded) {
            // episode was manually downloaded
            return false
        } else if (episode.audio.isEmpty()) {
            // episode has no audio reference
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