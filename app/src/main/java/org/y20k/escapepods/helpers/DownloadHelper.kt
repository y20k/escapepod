/*
 * DownloadHelper.kt
 * Implements the DownloadHelper class
 * A DownloadHelper provides helper methods for downloading files
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
import android.database.Cursor
import android.net.Uri
import android.preference.PreferenceManager
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.core.Episode
import org.y20k.escapepods.core.Podcast
import org.y20k.escapepods.xml.RssHelper
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.*


/*
 * DownloadHelper class
 */
class DownloadHelper(): BroadcastReceiver() {

    /* Main class variables */
    private lateinit var context: Context
    private lateinit var downloadManager: DownloadManager
    private lateinit var collection: Collection
    private lateinit var activeDownloads: ArrayList<Long>


    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(DownloadHelper::class.java)


    /* Initializes the main class variables of DownloadHelper */
    private fun initialize(c: Context) {
        context = c
        downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
        activeDownloads =  loadActiveDownloads(context, downloadManager)
        loadCollection()
    }


    /* Overrides onReceive - reacts to android.intent.action.DOWNLOAD_COMPLETE */
    override fun onReceive(c: Context, intent: Intent) {
        // set main class variables
        initialize(c)
        // process the finished download
        processDownload(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L))
    }


    /* Download a podcast */
    fun downloadPodcast(c: Context, feedUrl: String) {
        // set main class variables
        initialize(c)
        // enqueue podcast
        val uris = Array(1) {feedUrl.toUri()}
        enqueueDownload(uris, Keys.FILE_TYPE_RSS)
    }


    /* Download an episode */
    fun downloadEpisode(c: Context, remoteAudioFileLocation: String, podcast: Podcast) {
        // set main class variables
        initialize(c)
        // enqueue episode
        val uris = Array(1) {remoteAudioFileLocation.toUri()}
        enqueueDownload(uris, Keys.FILE_TYPE_AUDIO, podcast.name)
    }


    /* Refresh cover of given podcast */
    fun refreshCover(c: Context, podcast: Podcast) {
        // set main class variables
        initialize(c)
        // start to download podcast cover
        CollectionHelper.clearImagesFolder(context, podcast)
        val uris: Array<Uri>  = Array(1) {podcast.remoteImageFileLocation.toUri()}
        enqueueDownload(uris, Keys.FILE_TYPE_IMAGE, podcast.name)
    }


    /* Updates podcast collection */
    fun updateCollection(c: Context) {
        // set main class variables
        initialize(c)
        // re-download all podcast xml episode lists
        if (CollectionHelper.hasEnoughTimePassedSinceLastUpdate(context)) {
            val uris: Array<Uri> = Array(collection.podcasts.size) { it ->
                collection.podcasts[it].remotePodcastFeedLocation.toUri()
            }
            enqueueDownload(uris, Keys.FILE_TYPE_RSS)
        } else {
            LogHelper.v(TAG, "Update not initiated: not enough time has passed since last update.")
        }
    }


    /* Processes a given download ID */
    private fun processDownload(downloadID: Long) {
        // get local Uri in content://downloads/all_downloads/ for startDownload ID
        val localFileUri: Uri = downloadManager.getUriForDownloadedFile(downloadID)
        // get remote URL for startDownload ID
        val remoteFileLocation: String = getRemoteFileLocation(downloadManager, downloadID)

        // Log completed startDownload // todo remove
        LogHelper.v(TAG, "Download complete: " + FileHelper.getFileName(context, localFileUri) +
                " | " + FileHelper.getReadableByteCount(FileHelper.getFileSize(context, localFileUri), true)) // todo remove

        // determine what to
        when (FileHelper.getFileType(context, localFileUri)) {
            Keys.MIME_TYPE_XML -> readPodcastFeedAsync(localFileUri, remoteFileLocation)
            Keys.MIME_TYPE_MP3 -> setEpisodeMediaUri(localFileUri, remoteFileLocation)
            Keys.MIME_TYPE_JPG -> setPodcastImage(localFileUri, remoteFileLocation)
            Keys.MIME_TYPE_PNG -> setPodcastImage(localFileUri, remoteFileLocation)
            else -> {}
        }
        // remove ID from active downloads
        removeFromActiveDownloads(downloadID)
    }


    /* Enqueues an Array of files in DownloadManager */
    private fun enqueueDownload(uris: Array<Uri>, type: Int, podcastName: String = String()) {
        // determine destination folder and allowed network types
        val folder: String = FileHelper.determineDestinationFolderPath(type, podcastName)
        val allowedNetworkTypes:Int = determineAllowedNetworkTypes(context, type)
        // enqueues downloads
        val newIDs = LongArray(uris.size)
        for (i in uris.indices)  {
            LogHelper.v(TAG, "DownloadManager enqueue: ${uris[i]}")
            if (uris[i].scheme.startsWith("http")) {
                val request: DownloadManager.Request = DownloadManager.Request(uris[i])
                        .setAllowedNetworkTypes(allowedNetworkTypes)
                        .setAllowedOverRoaming(false)
                        .setTitle(uris[i].lastPathSegment)
                        .setDestinationInExternalFilesDir(context, folder, uris[i].lastPathSegment)
                        .setMimeType(FileHelper.determineMimeType(uris[i].toString()))
                newIDs[i] = downloadManager.enqueue(request)
                activeDownloads.add(newIDs[i])
            }
        }
        saveActiveDownloads(context, activeDownloads)
    }


    /*  episode and podcast cover */
    private fun enqueuePodcastMediaFiles(podcast: Podcast, isNew: Boolean) {
        if (isNew) {
            // start to download podcast cover
            CollectionHelper.clearImagesFolder(context, podcast)
            val coverUris: Array<Uri>  = Array(1) {podcast.remoteImageFileLocation.toUri()}
            enqueueDownload(coverUris, Keys.FILE_TYPE_IMAGE, podcast.name)
        }
        // start to download latest episode audio file
        val episodeUris: Array<Uri> = Array(1) {podcast.episodes[0].remoteAudioFileLocation.toUri()}
        enqueueDownload(episodeUris, Keys.FILE_TYPE_AUDIO, podcast.name)
    }


    /* Adds podcast to podcast collection*/
    private fun addPodcast(podcast: Podcast, isNew: Boolean) {
        if (isNew)  {
            // add new podcast
            collection = CollectionHelper.addPodcast(context, collection, podcast)
        } else {
            // replace existing podcast
            collection = CollectionHelper.replacePodcast(context, collection, podcast)
        }
        // sort collection
        collection.podcasts.sortBy { it.name }
        // export collection as OPML
        CollectionHelper.exportCollection(context, collection)
        // save collection
        CollectionHelper.saveCollection(context, collection)
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
        CollectionHelper.saveCollection(context, collection)
    }


    /* Sets Media Uri in Episode */
    private fun setEpisodeMediaUri(localFileUri: Uri, remoteFileLocation: String) {
        for (podcast: Podcast in collection.podcasts) {
            for (episode: Episode in podcast.episodes) {
                if (episode.remoteAudioFileLocation == remoteFileLocation) {
                    episode.audio = localFileUri.toString()
                }
            }
        }
        // remove unused audio references from collection
        collection = CollectionHelper.removeUnusedAudioReferences(context, collection)
        // clear audio folder
        CollectionHelper.clearAudioFolder(context, collection)
        // save collection
        CollectionHelper.saveCollection(context, collection)
    }


    /* Savely remove given startDownload ID from active downloads */
    private fun removeFromActiveDownloads(downloadID: Long): Boolean {
        val iterator: MutableIterator<Long> = activeDownloads.iterator()
        while (iterator.hasNext()) {
            val activeDownload = iterator.next()
            if (activeDownload.equals(downloadID)) {
                iterator.remove()
                saveActiveDownloads(context, activeDownloads)
                return true
            }
        }
        return false
    }


    /* Async via coroutine: Reads podcast feed */
    private fun readPodcastFeedAsync(localFileUri: Uri, remoteFileLocation: String) = runBlocking<Unit> {
        LogHelper.v(TAG, "Reading podcast RSS file async: $remoteFileLocation")
        // async: read xml
        val result = async { RssHelper().read(context, localFileUri, remoteFileLocation) }
        // wait for result and create podcast
        val podcast = result.await()
        if (CollectionHelper.validatePodcast(podcast)) {
            // check if new
            val isNew: Boolean = CollectionHelper.isNewPodcast(podcast.remotePodcastFeedLocation, collection)
            // check if media download is necessary
            if (isNew || CollectionHelper.podcastHasDownloadableEpisodes(collection, podcast)) {
                enqueuePodcastMediaFiles(podcast, isNew)
                addPodcast(podcast, isNew)
            } else {
                LogHelper.v(TAG, "No new media files to download.")
            }
        }
    }


    /* Reads podcast collection from storage using GSON */
    private fun loadCollection() = runBlocking<Unit> {
        LogHelper.v(TAG, "Loading podcast collection from storage")
        // get JSON from text file async
        val result = async { FileHelper.readCollection(context) }
        // wait for result and update collection
        collection = result.await()
    }


    /* Saves active downloads (IntArray) to shared preferences */
    private fun saveActiveDownloads(context: Context, activeDownloads: ArrayList<Long>) {
        val builder = StringBuilder()
        for (i in activeDownloads.indices) {
            builder.append(activeDownloads[i]).append(",")
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit {putString(Keys.PREF_ACTIVE_DOWNLOADS, builder.toString())}
    }


    /* Loads active downloads (IntArray) from shared preferences */
    private fun loadActiveDownloads(context: Context, downloadManager: DownloadManager): ArrayList<Long> {
        val activeDownloadsString: String = PreferenceManager.getDefaultSharedPreferences(context).getString(Keys.PREF_ACTIVE_DOWNLOADS, "")
        val count = activeDownloadsString.split(",").size - 1
        val tokenizer = StringTokenizer(activeDownloadsString, ",")
        val activeDownloads: ArrayList<Long> = arrayListOf<Long>()
        repeat(count) {
            val token = tokenizer.nextToken().toLong()
            if (isDownloadActive(downloadManager, token)) {
                activeDownloads.add(token) }
        }
        return activeDownloads
    }


    /* Determines the remote file location (the original URL) */
    private fun getRemoteFileLocation(downloadManager: DownloadManager, downloadID: Long): String {
        var remoteFileLocation: String = ""
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            remoteFileLocation = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI))
        }
        return remoteFileLocation
    }


    /* Checks if a given download ID represents a finished download */
    private fun isDownloadFinished(downloadManager: DownloadManager, downloadID: Long): Boolean {
        var downloadStatus: Int = -1
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            downloadStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
        }
        return (downloadStatus == DownloadManager.STATUS_SUCCESSFUL)
    }


    /* Checks if a given download ID represents a finished download */
    private fun isDownloadActive(downloadManager: DownloadManager, downloadID: Long): Boolean {
        var downloadStatus: Int = -1
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            downloadStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
        }
        return (downloadStatus == DownloadManager.STATUS_RUNNING)
    }


    /* Determine allowed network type */
    private fun determineAllowedNetworkTypes(context: Context, type: Int): Int {
        val downloadOverMobile = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Keys.PREF_DOWNLOAD_OVER_MOBILE, Keys.DEFAULT_DOWNLOAD_OVER_MOBILE);
        var allowedNetworkTypes:Int =  (DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        when (type) {
            Keys.FILE_TYPE_AUDIO -> if (!downloadOverMobile) allowedNetworkTypes = DownloadManager.Request.NETWORK_WIFI
        }
        return allowedNetworkTypes
    }


    /* Just a test */
    private fun identifyFileType(feedUrl: String): String {
        var fileType = "Undetermined"
        try {
            val url = URL(feedUrl)
            val connection = url.openConnection()
            fileType = connection.contentType
        } catch (badUrlEx: MalformedURLException) {
            LogHelper.w("ERROR: Bad URL - $badUrlEx")
        } catch (ioEx: IOException) {
            LogHelper.w("Cannot access URLConnection - $ioEx")
        }
        if (fileType == "application/rss+xml") {
            return Keys.MIME_TYPE_XML
        } else {
            return Keys.MIME_TYPE_UNSUPPORTED
        }
    }
}