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
import kotlinx.coroutines.*
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.core.Episode
import org.y20k.escapepods.core.Podcast
import org.y20k.escapepods.xml.RssHelper
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


    /* Interface used to notify finished initialization */
    interface OnInitializedListener {
        fun onInitialized() { }
    }


    /* Overrides onReceive - reacts to android.intent.action.DOWNLOAD_COMPLETE */
    override fun onReceive(c: Context, intent: Intent) {
        // do main job of onReceive after initialization
        val onInitializedListener: DownloadHelper.OnInitializedListener = object : DownloadHelper.OnInitializedListener {
            override fun onInitialized() {
                super.onInitialized()
                // process the finished download
                processDownload(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L))
            }
        }
        val initializer = Initializer()
        initializer.initialize(c, onInitializedListener)
    }


    /* Download a podcast */
    fun downloadPodcast(c: Context, feedUrl: String) {
        // do main job of downloadPodcast after initialization
        val onInitializedListener: DownloadHelper.OnInitializedListener = object : DownloadHelper.OnInitializedListener {
            override fun onInitialized() {
                super.onInitialized()
                // enqueue podcast
                val uris = Array(1) {feedUrl.toUri()}
                enqueueDownload(uris, Keys.FILE_TYPE_RSS)
            }
        }
        val initializer = Initializer()
        initializer.initialize(c, onInitializedListener)
    }


    /* Download an episode */
    fun downloadEpisode(c: Context, mediaID: String, ignoreWifiRestriction: Boolean) {
        // do main job of downloadEpisode after initialization
        val onInitializedListener: DownloadHelper.OnInitializedListener = object : DownloadHelper.OnInitializedListener {
            override fun onInitialized() {
                super.onInitialized()
                // enqueue episode
                val episode: Episode = CollectionHelper.setManuallyDownloaded(context, collection, mediaID)
                val uris = Array(1) {episode.remoteAudioFileLocation.toUri()}
                enqueueDownload(uris, Keys.FILE_TYPE_AUDIO, episode.podcastName, ignoreWifiRestriction)
            }
        }
        val initializer = Initializer()
        initializer.initialize(c, onInitializedListener)
    }


    /* Refresh cover of given podcast */
    fun refreshCover(c: Context, podcast: Podcast) {
        // do main job of refreshCover after initialization
        val onInitializedListener: DownloadHelper.OnInitializedListener = object : DownloadHelper.OnInitializedListener {
            override fun onInitialized() {
                super.onInitialized()
                // start to download podcast cover
                CollectionHelper.clearImagesFolder(context, podcast)
                val uris: Array<Uri>  = Array(1) {podcast.remoteImageFileLocation.toUri()}
                enqueueDownload(uris, Keys.FILE_TYPE_IMAGE, podcast.name)
            }
        }
        val initializer = Initializer()
        initializer.initialize(c, onInitializedListener)
    }


    /* Updates podcast collection */
    fun updateCollection(c: Context) {
        // do main job of updateCollection after initialization
        val onInitializedListener: DownloadHelper.OnInitializedListener = object : DownloadHelper.OnInitializedListener {
            override fun onInitialized() {
                super.onInitialized()
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
        }
        val initializer = Initializer()
        initializer.initialize(c, onInitializedListener)
    }


    /* Processes a given download ID */
    private fun processDownload(downloadID: Long) {
        // get local Uri in content://downloads/all_downloads/ for startDownload ID
        val localFileUri: Uri = downloadManager.getUriForDownloadedFile(downloadID)
        // get remote URL for startDownload ID
        val remoteFileLocation: String = getRemoteFileLocation(downloadManager, downloadID)
        // determine what to
        val fileType = FileHelper.getFileType(context, localFileUri)
        // Log completed startDownload // todo remove
        LogHelper.v(TAG, "Download complete: ${FileHelper.getFileName(context, localFileUri)} | ${FileHelper.getReadableByteCount(FileHelper.getFileSize(context, localFileUri), true)} | $fileType") // todo remove
        if (fileType in Keys.MIME_TYPES_RSS) readPodcastFeed(localFileUri, remoteFileLocation)
        if (fileType in Keys.MIME_TYPES_ATOM) LogHelper.w(TAG, "ATOM Feeds are not yet supported")
        if (fileType in Keys.MIME_TYPES_AUDIO) setEpisodeMediaUri(localFileUri, remoteFileLocation)
        if (fileType in Keys.MIME_TYPES_IMAGE) setPodcastImage(localFileUri, remoteFileLocation)
        // remove ID from active downloads
        removeFromActiveDownloads(downloadID)
    }


    /* Enqueues an Array of files in DownloadManager */
    private fun enqueueDownload(uris: Array<Uri>, type: Int, podcastName: String = String(), ignoreWifiRestriction: Boolean = false) {
        // determine destination folder and allowed network types
        val folder: String = FileHelper.determineDestinationFolderPath(type, podcastName)
        val allowedNetworkTypes: Int = determineAllowedNetworkTypes(context, type, ignoreWifiRestriction)
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
        collection.podcasts.forEach { podcast ->
            podcast.episodes.forEach { episode ->
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
    private fun readPodcastFeed(localFileUri: Uri, remoteFileLocation: String) {
        GlobalScope.launch() {
            LogHelper.v(TAG, "Reading podcast RSS file ($remoteFileLocation) - Thread: ${Thread.currentThread().name}")
            // async: read xml
            val deferred: Deferred<Podcast> = async { RssHelper().read(context, localFileUri, remoteFileLocation) }
            // wait for result and create podcast
            var podcast: Podcast = deferred.await()
            podcast = CollectionHelper.fillEmptyEpisodeCovers(podcast)
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
    }


    /* Reads podcast collection from storage using GSON */
    private fun loadCollection() {
        LogHelper.v(TAG, "Loading podcast collection from storage")
        val backgroundJob = Job()
        val uiScope = CoroutineScope(Dispatchers.Main + backgroundJob)
        uiScope.launch {
            // load collection on background thread
            val result = async { FileHelper.readCollection(context) }
            // wait for result and update collection
            collection = result.await()
            backgroundJob.cancel()
        }
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
    private fun determineAllowedNetworkTypes(context: Context, type: Int, ignoreWifiRestriction: Boolean): Int {
        val downloadOverMobile = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Keys.PREF_DOWNLOAD_OVER_MOBILE, Keys.DEFAULT_DOWNLOAD_OVER_MOBILE);
        var allowedNetworkTypes:Int =  (DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        when (type) {
            Keys.FILE_TYPE_AUDIO -> {
                if (!downloadOverMobile or !ignoreWifiRestriction) {
                    allowedNetworkTypes = DownloadManager.Request.NETWORK_WIFI
                }
            }
        }
        return allowedNetworkTypes
    }


    /*
     * Inner class: Initializes the main class variables of DownloadHelper
     */
    inner class Initializer() {
        fun initialize(c: Context, onInitializedListener: OnInitializedListener) {
            LogHelper.v(TAG, "Initializing the DownloadHelper")
            context = c
            downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
            activeDownloads = loadActiveDownloads(context, downloadManager)
            val backgroundJob = Job()
            val uiScope = CoroutineScope(Dispatchers.Main + backgroundJob)
            uiScope.launch {
                // load collection on background thread
                val result = async { FileHelper.readCollection(context) }
                // wait for result and update collection
                collection = result.await()
                backgroundJob.cancel()
                // set callback - initialization finished
                onInitializedListener.onInitialized()
            }
        }
    }
    /*
     * End of inner class
     */

}