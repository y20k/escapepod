/*
 * DownloadWorker.kt
 * Implements the DownloadWorker class
 * A DownloadWorker is a worker that triggers actions when the app is not in use
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.y20k.escapepods.XmlReader
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.core.Episode
import org.y20k.escapepods.core.Podcast
import java.util.*


/*
 * DownloadWorker class
 */
class DownloadWorker(context : Context, params : WorkerParameters): Worker(context, params) {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(DownloadWorker::class.java)


    /* Main class variables */
    private var collection: Collection = Collection()
    private val downloadManager = applicationContext.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
    private var activeDownloads: ArrayList<Long> = DownloadHelper().loadActiveDownloads(applicationContext, downloadManager)


    /* Overrides doWork */
    override fun doWork(): Result {
        // load collection
        loadCollectionAsync()
        // listen for completed downloads
        applicationContext.registerReceiver(onCompleteReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        // determine what kind of download is requested
        when(inputData.getInt(Keys.KEY_DOWNLOAD_WORK_REQUEST,0)) {
            Keys.REQUEST_UPDATE_COLLECTION -> updateCollection()
            Keys.REQUEST_ADD_PODCAST -> downloadPodcast(inputData.getString(Keys.KEY_NEW_PODCAST_URL).toString())
        }
        // indicate success or failure
        return Result.SUCCESS
        // (Returning RETRY tells WorkManager to try this task again later; FAILURE says not to try again.)
    }


    /* Download a podcast */
    fun downloadPodcast (feedUrl: String) {
        val uris = Array(1) {feedUrl.toUri()}
        enqueueDownload(uris, Keys.FILE_TYPE_RSS)
    }


    /* Updates podcast collection */
    fun updateCollection() {
        if (CollectionHelper().hasEnoughTimePassedSinceLastUpdate(applicationContext)) {
            // re-download all podcast xml episode lists
            val uris: Array<Uri> = Array(collection.podcasts.size) { it ->
                collection.podcasts[it].remotePodcastFeedLocation.toUri()
            }
            enqueueDownload(uris, Keys.FILE_TYPE_RSS)
        } else {
            LogHelper.v(TAG, "Update not initiated: not enough time has passed since last update.")
        }
    }


    /* Enqueues an Array of files in DownloadManager */
    private fun enqueueDownload(uris: Array<Uri>, type: Int, podcastName: String = String()) {
        // determine destination folder and allowed network types
        val folder: String = FileHelper().determineDestinationFolderPath(type, podcastName)
        val allowedNetworkTypes:Int = DownloadHelper().determineAllowedNetworkTypes(applicationContext, type)
        // enqueues downloads
        val newIDs = LongArray(uris.size)
        for (i in uris.indices)  {
            LogHelper.v(TAG, "DownloadManager enqueue: ${uris[i]}")
            if (uris[i].scheme.startsWith("http")) {
                val request: DownloadManager.Request = DownloadManager.Request(uris[i])
                        .setAllowedNetworkTypes(allowedNetworkTypes)
                        .setAllowedOverRoaming(false)
                        .setTitle(uris[i].lastPathSegment)
                        .setDestinationInExternalFilesDir(applicationContext, folder, uris[i].lastPathSegment)
                        .setMimeType(DownloadHelper().determineMimeType(uris[i].toString()))
                newIDs[i] = downloadManager.enqueue(request)
                activeDownloads.add(newIDs[i])
            }
        }
        DownloadHelper().saveActiveDownloads(applicationContext, activeDownloads)
    }


    /*  episode and podcast cover */
    private fun enqueuePodcastMediaFiles(podcast: Podcast, isNew: Boolean) {
        // start to download podcast cover
        if (isNew) {
            CollectionHelper().clearImagesFolder(applicationContext, podcast)
            val coverUris: Array<Uri>  = Array(1) {podcast.remoteImageFileLocation.toUri()}
            enqueueDownload(coverUris, Keys.FILE_TYPE_IMAGE, podcast.name)
        }
        // start to download latest episode audio file
        val episodeUris: Array<Uri> = Array(1) {podcast.episodes[0].remoteAudioFileLocation.toUri()}
        enqueueDownload(episodeUris, Keys.FILE_TYPE_AUDIO, podcast.name)
    }


    /* Processes a given download ID */
    private fun processDownload(downloadID: Long) {
        // get local Uri in content://downloads/all_downloads/ for startDownload ID
        val localFileUri: Uri = downloadManager.getUriForDownloadedFile(downloadID)
        // get remote URL for startDownload ID
        val remoteFileLocation: String = DownloadHelper().getRemoteFileLocation(downloadManager, downloadID)

        // Log completed startDownload // todo remove
        LogHelper.v(TAG, "Download complete: " + FileHelper().getFileName(applicationContext, localFileUri) +
                " | " + FileHelper().getReadableByteCount(FileHelper().getFileSize(applicationContext, localFileUri), true)) // todo remove

        // determine what to
        when (FileHelper().getFileType(applicationContext, localFileUri)) {
            Keys.MIME_TYPE_XML -> readPodcastFeedAsync(localFileUri, remoteFileLocation)
            Keys.MIME_TYPE_MP3 -> setEpisodeMediaUri(localFileUri, remoteFileLocation)
            Keys.MIME_TYPE_JPG -> setPodcastImage(localFileUri, remoteFileLocation)
            Keys.MIME_TYPE_PNG -> setPodcastImage(localFileUri, remoteFileLocation)
            else -> {}
        }
        // remove ID from active downloads
        removeFromActiveDownloads(downloadID)
    }


    /* Adds podcast to podcast collection*/
    private fun addPodcast(podcast: Podcast, isNew: Boolean) {
        if (isNew)  {
            // add new
            collection.podcasts.add(podcast)
        } else {
            // replace existing podcast
            collection = CollectionHelper().replacePodcast(applicationContext, collection, podcast)
        }
        // sort collection
        collection.podcasts.sortBy { it.name }
        // save collection
        saveCollectionAsync()
    }


    /* Sets podcast cover */
    private fun setPodcastImage(localFileUri: Uri, remoteFileLocation: String) {
        for (podcast in collection.podcasts) {
            if (podcast.remoteImageFileLocation == remoteFileLocation) {
                podcast.cover = localFileUri.toString()
                for (episode in podcast.episodes) {
                    episode.cover = localFileUri.toString()
                }
            }
        }
        // save collection
        saveCollectionAsync()
    }


    /* Sets Media Uri in Episode */
    private fun setEpisodeMediaUri(localFileUri: Uri, remoteFileLocation: String) {
        for (podcast: Podcast in collection.podcasts) {
            for (episode: Episode in podcast.episodes) {
                if (episode.remoteAudioFileLocation == remoteFileLocation) {
                    LogHelper.e(TAG, "DING") // todo remove
                    episode.audio = localFileUri.toString()
                }
            }
        }
        // remove unused audio references from collection
        collection = CollectionHelper().removeUnusedAudioReferences(applicationContext, collection)
        // clear audio folder
        CollectionHelper().clearAudioFolder(applicationContext, collection)
        // save collection
        saveCollectionAsync()
        // notify activity: on success set output data to true
        val output: Data = workDataOf(Keys.KEY_RESULT_NEW_COLLECTION to true)
        setOutputData(output)
    }


    /* Savely remove given startDownload ID from active downloads */
    private fun removeFromActiveDownloads(downloadID: Long): Boolean {
        val iterator: MutableIterator<Long> = activeDownloads.iterator()
        while (iterator.hasNext()) {
            val activeDownload = iterator.next()
            if (activeDownload.equals(downloadID)) {
                iterator.remove()
                DownloadHelper().saveActiveDownloads(applicationContext, activeDownloads)
                return true
            }
        }
        return false
    }


    /* Async via coroutine: Reads podcast feed */
    private fun readPodcastFeedAsync(localFileUri: Uri, remoteFileLocation: String) = runBlocking<Unit> {
        LogHelper.v(TAG, "Reading podcast RSS file async: $remoteFileLocation")
        // async: read xml
        val result = async { XmlReader().read(applicationContext, localFileUri, remoteFileLocation) }
        // wait for result and create podcast
        val podcast = result.await()
        if (CollectionHelper().validatePodcast(podcast)) {
            // check if new
            val isNew: Boolean = CollectionHelper().isNewPodcast(podcast.remotePodcastFeedLocation, collection)
            // check if media download is necessary
            if (isNew || CollectionHelper().podcastHasDownloadableEpisodes(collection, podcast)) {
                enqueuePodcastMediaFiles(podcast, isNew)
                addPodcast(podcast, isNew)
            } else {
                LogHelper.v(TAG, "No new media files to download.")
            }
        }
    }


    /* Async via coroutine: Reads collection from storage using GSON */
    private fun loadCollectionAsync() = runBlocking<Unit> {
        LogHelper.v(TAG, "Loading podcast collection from storage async")
        // async: get JSON from text file
        val result = async { FileHelper().readCollection(applicationContext) }
        collection = result.await()
    }


    /* Async via coroutine: Saves podcast collection */
    private fun saveCollectionAsync() = runBlocking<Unit> {
        LogHelper.v(TAG, "Saving podcast collection to storage async")
        val result = async { FileHelper().saveCollection(applicationContext, collection) }
        result.await()
        // afterwards: do nothing
    }


    /* BroadcastReceiver for completed downloads */
    private val onCompleteReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // process the finished download
            processDownload(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L))
        }
    }

}