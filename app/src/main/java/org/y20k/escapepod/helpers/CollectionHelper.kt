/*
 * CollectionHelper.kt
 * Implements the CollectionHelper object
 * A CollectionHelper provides helper methods for the podcast collection
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.y20k.escapepod.Keys
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.database.wrappers.PodcastWithAllEpisodesWrapper
import java.io.File
import java.util.*


/*
 * CollectionHelper object
 */
object CollectionHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(CollectionHelper::class.java)


    /* Check if a rss podcast (= download result) has cover and audio files */
    fun validateRssPodcast(remoteImageFileLocationEmpty: Boolean, epsiodesEmpty: Boolean): Int {
        var result: Int = Keys.PODCAST_VALIDATION_SUCESS
        // check for cover url
        if (remoteImageFileLocationEmpty)  {
            result = Keys.PODCAST_VALIDATION_MISSING_COVER
        }
        // check for audio files
        if (epsiodesEmpty) {
            result = Keys.PODCAST_VALIDATION_NO_VALID_EPISODES
        }
        return result
    }


    /* Checks if enough time passed since last update */
    fun hasEnoughTimePassedSinceLastUpdate(): Boolean {
        val lastUpdate: Date = PreferencesHelper.loadLastUpdateCollection()
        val currentDate: Date = Calendar.getInstance().time
        return currentDate.time - lastUpdate.time  > Keys.MINIMUM_TIME_BETWEEN_UPDATES
    }


    /* Returns if podcast has episodes that can be downloaded */
    fun hasDownloadableEpisodes(episodes: List<Episode>): Boolean = episodes.isNotEmpty() && episodes[0].audio.isEmpty() && !episodes[0].manuallyDeleted


    /* Copies over episode states from old episode list to new episode list */
    fun updateEpisodeList(podcast: Podcast, oldEpisodes: List<Episode>, newEpisodes: List<Episode>): List<Episode> {
        val updatedEpisodeList: MutableList<Episode> = mutableListOf()
        newEpisodes.forEach { newEpisode ->
            // try to find matching old episode
            var isNew: Boolean = true
            oldEpisodes.forEach { oldEpisode ->
                // matching old episode found - update old episode and add to list
                if (oldEpisode.mediaId == newEpisode.mediaId) {
                    isNew = false
                    val updatedEpisode: Episode = Episode(newEpisode,
                            audio = oldEpisode.audio,
                            cover = podcast.cover,
                            smallCover = podcast.smallCover,
                            isPlaying = oldEpisode.isPlaying,
                            playbackPosition = oldEpisode.playbackPosition,
                            duration = oldEpisode.duration,
                            manuallyDeleted = oldEpisode.manuallyDeleted,
                            manuallyDownloaded = oldEpisode.manuallyDownloaded)
                    updatedEpisodeList.add(updatedEpisode)
                }
            }
            // no matching old episode found - add new episode to list
            if (isNew) {
                val updatedEpisode: Episode = Episode(newEpisode,
                        cover = podcast.cover,
                        smallCover = podcast.smallCover)
                updatedEpisodeList.add(updatedEpisode)
            }
        }
        return updatedEpisodeList
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
    fun removeDuplicates(podcastList: List<Podcast>, feedUrls: Array<String>): Array<String> {
        val feedUrlList: MutableList<String> = feedUrls.toMutableList()
        podcastList.forEach { podcast ->
            feedUrlList.remove(podcast.remotePodcastFeedLocation)
        }
        return feedUrlList.toTypedArray()
    }


    /* Export podcast collection as OPML */
    fun exportCollectionOpml(context: Context, podcastList: List<Podcast>) {
        LogHelper.v(TAG, "Exporting podcast collection as OPML")
        // export collection as OPML - launch = fire & forget (no return value from save collection)
        CoroutineScope(IO).launch { FileHelper.backupCollectionAsOpmlSuspended(context, podcastList) }
    }


    /* Creates a MediaItem with MediaMetadata for a single episode - used to prapare player */
    fun buildMediaItem(episode: Episode, streaming: Boolean = false): MediaItem {
        // get the correct source for streaming / local playback
        val source: String = if (streaming) episode.remoteAudioFileLocation else episode.audio
        // build MediaMetadata
        val metadata:MediaMetadata = MediaMetadata.Builder().apply {
            setAlbumTitle(episode.podcastName)
            setTitle(episode.title)
//            setArtist(artist)
//            setGenre(genre)
//            setFolderType(folderType)
//            setIsPlayable(isPlayable)
            setArtworkUri(episode.cover.toUri())
            setMediaUri(source.toUri())
        }.build()
        // build MediaItem and return it
        return MediaItem.Builder().apply {
            setMediaId(episode.mediaId)
            setMediaMetadata(metadata)
            setUri(source.toUri())
        }.build()
    }


//    /* Creates MediaMetadata for a single episode - used in media session */
//    fun buildEpisodeMediaMetadata(context: Context, episode: Episode): MediaMetadataCompat {
//        return MediaMetadataCompat.Builder().apply {
//            putString(MediaMetadataCompat.METADATA_KEY_TITLE, episode.title)
//            putString(MediaMetadataCompat.METADATA_KEY_ARTIST, episode.podcastName)
//            putString(MediaMetadataCompat.METADATA_KEY_ALBUM, episode.podcastName)
//            putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, episode.cover)
//            putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, episode.audio)
//            putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, ImageHelper.getScaledPodcastCover(context, episode.cover, Keys.SIZE_COVER_LOCK_SCREEN))
//            putLong(MediaMetadataCompat.METADATA_KEY_DURATION, episode.duration)
//        }.build()
//    }
//
//
//
//    /* Creates description for a single episode - used in MediaSessionConnector */
//    fun buildEpisodeMediaDescription(context: Context, episode: Episode): MediaDescriptionCompat {
//        val coverBitmap: Bitmap = ImageHelper.getScaledPodcastCover(context, episode.cover, Keys.SIZE_COVER_LOCK_SCREEN)
//        val extras: Bundle = Bundle()
//        extras.putParcelable(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBitmap)
//        extras.putParcelable(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, coverBitmap)
//        return MediaDescriptionCompat.Builder().apply {
//            setMediaId(episode.mediaId)
//            setIconBitmap(coverBitmap)
//            setIconUri(episode.cover.toUri())
//            setTitle(episode.title)
//            setSubtitle(episode.podcastName)
//            //setDescription(episode.podcastName)
//            setExtras(extras)
//        }.build()
//    }


    /* Deletes audio files that are no longer needed - but keep episodes */
    fun deleteUnneededAudioFiles(podcast: PodcastWithAllEpisodesWrapper): List<Episode> {
        val episodes: List<Episode> = podcast.episodes.sortedByDescending { episode -> episode.publicationDate }.toMutableList()
        val updatedEpisodes: MutableList<Episode> = mutableListOf()
        val numberOfAudioFilesToKeep: Int = PreferencesHelper.numberOfAudioFilesToKeep()
        val numberOfEpisodes: Int = episodes.size

        if (numberOfEpisodes > numberOfAudioFilesToKeep) {
            for (i in numberOfEpisodes -1 downTo numberOfAudioFilesToKeep) {
                val episode: Episode = episodes[i]
                // check if episode can be deleted
                if (canBeDeleted(episode)) {
                    // delete audio file
                    try {
                        episode.audio.toUri().toFile().delete()
                    } catch (e: Exception) {
                        LogHelper.e(TAG, "Unable to delete file. File has probably been deleted manually. Stack trace: $e")
                    }
                    // remove audio reference
                    val updatedEpisode: Episode = Episode(episode, audio = String(), isPlaying = false, playbackPosition = 0L, duration = 0L)
                    // add to updated list
                    updatedEpisodes.add(updatedEpisode)
                }
            }
        }
        return updatedEpisodes
    }


    /* Deletes all files in the audio folders of a collection */
    fun deleteAllAudioFiles(context: Context) {
        // get main audio directory
        val audioFolder: File? = context.getExternalFilesDir(Keys.FOLDER_AUDIO)
        if (audioFolder != null && audioFolder.exists() && audioFolder.isDirectory) {
            // list sub-directories
            audioFolder.listFiles()?.forEach { podcastAudioFolder ->
                if (podcastAudioFolder != null && podcastAudioFolder.exists() && podcastAudioFolder.isDirectory) {
                    FileHelper.clearFolder(podcastAudioFolder, 0, false)
                }
            }
        }
    }


    /* Deletes an episode audio file - used when user presses the delete button */
    fun deleteEpisodeAudioFile(episode: Episode, manuallyDeleted: Boolean = false): Episode {
        // delete audio file
        LogHelper.d(TAG, "Deleting audio file for episode: ${episode.title}")
        try {
            episode.audio.toUri().toFile().delete()
        } catch (e: Exception) {
            LogHelper.e(TAG, "Unable to delete file. File has probably been deleted manually. Stack trace: $e")
        }
        return Episode(episode, manuallyDeleted = manuallyDeleted, manuallyDownloaded = false, audio = String())
    }


    /* Delete files in audio folder that are not referenced in collection - used for housekeeping */
    fun deleteUnReferencedAudioFiles(context: Context, episodeList: List<Episode>) {
        val audioFileReferences: List<String> = getAllAudioFileReferences(episodeList)
        // get main audio directory
        val audioFolder: File? = context.getExternalFilesDir(Keys.FOLDER_AUDIO)
        if (audioFolder != null && audioFolder.exists() && audioFolder.isDirectory)  {
            // list sub-directories
            audioFolder.listFiles()?.forEach { podcastAudioFolder ->
                // look for un-referenced files in each subfolder
                podcastAudioFolder.listFiles()?.forEach { audioFile ->
                    val fileUriString: String = Uri.fromFile(audioFile).toString()
                    if (!(audioFileReferences.contains(fileUriString))) {
                        audioFile.delete()
                    }
                }

            }
        }
    }


    /* Determines if an episode can be deleted */
    private fun canBeDeleted(episode: Episode): Boolean {
        if (episode.guid == PreferencesHelper.loadUpNextMediaId()) {
            // episode is in Up Next queue
            return false
        } else if (episode.hasBeenStarted() && !episode.isFinished()) {
            // episode has been started but not finished
            return false
        } else if (episode.isPlaying && !episode.isFinished()) { // todo test where playbackState is set top stopped
            // episode is paused or playing
            return false
        } else if (episode.manuallyDownloaded) {
            // episode was manually downloaded
            return false
        } else if (episode.audio.isEmpty()) {
            // episode has no audio reference
            return false
        } else {
            // none of the above: episode may be deleted
            return true
        }
    }


    /* Extracts all audio file references from a collection */
    private fun getAllAudioFileReferences(episodeList: List<Episode>): ArrayList<String> {
        val audioFileReferences: ArrayList<String> = arrayListOf()
        episodeList.forEach { episode ->
            if (episode.audio.isNotBlank()) {
                audioFileReferences.add(episode.audio)
            }
        }
        return audioFileReferences
    }

}
