/*
 * DownloadService.kt
 * Implements the DownloadService class
 * A DownloadService is a service that provides methods for downloading files
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods

import android.app.DownloadManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.widget.Toast
import androidx.core.net.toUri
import androidx.work.*
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.core.Episode
import org.y20k.escapepods.core.Podcast
import org.y20k.escapepods.helpers.*
import java.util.*
import java.util.concurrent.TimeUnit


/*
 * DownloadService class
 */
class DownloadService(): Service() {

    /* Interface used to communicate back to activity */
    interface DownloadServiceListener {
        fun onDownloadFinished(newCollection: Collection) {
        }
    }


    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(DownloadService::class.java)


    /* Main class variables */
    var activeDownloads: ArrayList<Long> = ArrayList<Long>()
    private var collection: Collection = Collection()
    private var downloadServiceListener: DownloadServiceListener? = null
    private val downloadServiceBinder: LocalBinder = LocalBinder()
    private lateinit var downloadManager: DownloadManager


    /* Overrides onCreate */
    override fun onCreate() {
        super.onCreate()
    }


    /* Overrides onDestroy */
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(onCompleteReceiver)
        } catch (e: IllegalArgumentException) {
            LogHelper.i(TAG, "Unable to unregister receiver for completed downloads.")
        }
    }

    /* Overrides onStartCommand */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // initialize the service
        initialize(null, true)

        // get last update
        var lastUpdate: Long = 0
        if (intent != null) {
            if (intent.hasExtra(Keys.EXTRA_LAST_UPDATE_COLLECTION))
                lastUpdate = intent.getLongExtra(Keys.EXTRA_LAST_UPDATE_COLLECTION, 0)
        }
        LogHelper.e(TAG, "!!! onStartCommand --> $lastUpdate") // todo remove

        return super.onStartCommand(intent, flags, startId)
    }

    /* Overrides onBind */
    override fun onBind(intent: Intent): IBinder? {
        return downloadServiceBinder
    }


    /* Overrides onUnbind */
    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }


    /* Initializes the service -> must ALWAYS be called */
    fun initialize(listener: DownloadServiceListener?, update: Boolean) {
        // set listener
        downloadServiceListener = listener
        // load collection
        loadCollectionAsync(update)
        // get download manager
        downloadManager = getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
        // load active downloads
        activeDownloads = DownloadHelper().loadActiveDownloads(this, downloadManager)
        // listen for completed downloads
        registerReceiver(onCompleteReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

    }


    /* Download a podcast */
    fun downloadPodcast (uris: Array<Uri>) {
        enqueueDownload(uris, Keys.FILE_TYPE_RSS)
    }


    /* Updates podcast collection */
    fun updateCollection() {
        val uris: Array<Uri> = Array(collection.podcasts.size) {it ->
            collection.podcasts[it].remotePodcastFeedLocation.toUri()
        }
        enqueueDownload(uris, Keys.FILE_TYPE_RSS)
        Toast.makeText(this, getString(R.string.toast_message_updating_collection), Toast.LENGTH_LONG).show();
    }


    /* Get size of downloaded file so far */
    fun getFileSizeSoFar(downloadID: Long): Long {
        var bytesSoFar: Long = -1L
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            bytesSoFar = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        }
        return bytesSoFar
    }


    /* Enqueues an Array of files in DownloadManager */
    private fun enqueueDownload(uris: Array<Uri>, type: Int, podcastName: String = String()) {
        // determine destination folder and allowed network types
        val folder: String = FileHelper().determineDestinationFolderPath(type, podcastName)
        val allowedNetworkTypes:Int = DownloadHelper().determineAllowedNetworkTypes(this, type)
        // enqueues downloads
        val newIDs = LongArray(uris.size)
        for (i in uris.indices)  {
            val request: DownloadManager.Request = DownloadManager.Request(uris[i])
                    .setAllowedNetworkTypes(allowedNetworkTypes)
                    .setAllowedOverRoaming(false)
                    .setTitle(uris[i].lastPathSegment)
                    .setDestinationInExternalFilesDir(this, folder, uris[i].lastPathSegment)
                    .setMimeType(DownloadHelper().determineMimeType(uris[i].toString()))
            newIDs[i] = downloadManager.enqueue(request)
            activeDownloads.add(newIDs[i])
        }
        DownloadHelper().saveActiveDownloads(this, activeDownloads)
        LogHelper.v(TAG, "${uris.size} new file(s) queued for download.")
    }


    /*  episode and podcast cover */
    private fun enqueuePodcastMediaFiles(podcast: Podcast, isNew: Boolean) {
        // start to download podcast cover
        if (isNew) {
            CollectionHelper().clearImagesFolder(this, podcast)
            val coverUris: Array<Uri>  = Array(1) {podcast.remoteImageFileLocation.toUri()}
            enqueueDownload(coverUris, Keys.FILE_TYPE_IMAGE, podcast.name)
        }
        // start to download latest episode audio file
        val episodeUris: Array<Uri> = Array(1) {podcast.episodes[0].remoteAudioFileLocation.toUri()}
        enqueueDownload(episodeUris, Keys.FILE_TYPE_AUDIO, podcast.name)
    }


    /* Processes finished downloads */
    private fun handleFinishedDownloads() {
        var downloadID: Long
        val iterator: Iterator<Long> = activeDownloads.iterator()
        val finishedDownloads: ArrayList<Long> = arrayListOf<Long>()
        while (iterator.hasNext()) {
            downloadID = iterator.next()
            if (DownloadHelper().isDownloadFinished(downloadManager, downloadID)) {
                finishedDownloads.add(downloadID)
            }
        }
        finishedDownloads.forEach { it -> processDownload(it) }
    }


    /* Processes a given download ID */
    private fun processDownload(downloadID: Long) {
        // get local Uri in content://downloads/all_downloads/ for startDownload ID
        val localFileUri: Uri = downloadManager.getUriForDownloadedFile(downloadID)

        // get remote URL for startDownload ID
        val remoteFileLocation: String = DownloadHelper().getRemoteFileLocation(downloadManager, downloadID)

        // determine what to
        when (FileHelper().getFileType(this@DownloadService, localFileUri)) {
            Keys.MIME_TYPE_XML -> readPodcastFeedAsync(localFileUri, remoteFileLocation)
            Keys.MIME_TYPE_MP3 -> setEpisodeMediaUri(localFileUri, remoteFileLocation)
            Keys.MIME_TYPE_JPG -> setPodcastImage(localFileUri, remoteFileLocation)
            Keys.MIME_TYPE_PNG -> setPodcastImage(localFileUri, remoteFileLocation)
            else -> {}
        }

        // Log completed startDownload // todo remove
        LogHelper.v(TAG, "Download complete: " + FileHelper().getFileName(this@DownloadService, localFileUri) +
                " | " + FileHelper().getReadableByteCount(FileHelper().getFileSize(this@DownloadService, localFileUri), true)) // todo remove

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
            collection = CollectionHelper().replacePodcast(this, collection, podcast)
        }

        // sort collection
        collection.podcasts.sortBy { it.name }

        // notify activity about changes
        notifyPodcastActivity()
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
        // notify activity about changes
        notifyPodcastActivity()
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
        collection = CollectionHelper().removeUnusedAudioReferences(this, collection)
        // clear audio folder
        CollectionHelper().clearAudioFolder(this, collection)
        // notify activity about changes
        notifyPodcastActivity()
    }


    /* Savely remove given startDownload ID from active downloads */
    private fun removeFromActiveDownloads(downloadID: Long): Boolean {
        val iterator: MutableIterator<Long> = activeDownloads.iterator()
        while (iterator.hasNext()) {
            val activeDownload = iterator.next()
            if (activeDownload.equals(downloadID)) {
                iterator.remove()
                DownloadHelper().saveActiveDownloads(this, activeDownloads)
                return true
            }
        }
        return false
    }


    /* Savely hand over new collection to activity */
    private fun notifyPodcastActivity() {
        collection.lastUpdate = Date()
        saveCollectionAsync()
        downloadServiceListener?.onDownloadFinished(collection)
    }


    /* Schedules a DownloadWorker that triggers background updates of the collection */
    private fun scheduleDownloadWorker() {
        val lastUpdateData: Data = Data.Builder()
                .putLong(Keys.KEY_LAST_UPDATE_COLLECTION, collection.lastUpdate.time)
                .build()
        val unmeteredConstraint = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
        val updateCollectionPeriodicWork = PeriodicWorkRequestBuilder<DownloadWorker>(4, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
                .setInputData(lastUpdateData)
                .setConstraints(unmeteredConstraint)
                .build()
        WorkManager.getInstance().enqueueUniquePeriodicWork(Keys.NAME_PERIODIC_COLLECTION_UPDATE_WORK,  ExistingPeriodicWorkPolicy.KEEP, updateCollectionPeriodicWork)
    }


    /* Async via coroutine: Reads podcast feed */
    private fun readPodcastFeedAsync(localFileUri: Uri, remoteFileLocation: String) = runBlocking<Unit> {
        LogHelper.v(TAG, "Reading podcast RSS file async")
        // async: read xml
        val result = async { XmlReader().read(this@DownloadService, localFileUri, remoteFileLocation) }
        // wait for result and create podcast
        val podcast = result.await()
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


    /* Async via coroutine: Reads collection from storage using GSON */
    private fun loadCollectionAsync(update: Boolean) = runBlocking<Unit> {
        LogHelper.v(TAG, "Loading podcast collection from storage async")
        // async: get JSON from text file
        val result = async { FileHelper().readCollection(this@DownloadService) }
        // wait for result and update collection
        collection = result.await()
        // savely hand over new collection to activity
        downloadServiceListener?.onDownloadFinished(collection)
        // check for unfinished business
        if (activeDownloads.isNotEmpty()) {
            handleFinishedDownloads()
        }
        if (update) {
            // if update requested -> update collection
            updateCollection()
        } else {
            // else -> schedule the DownloadWorker
            scheduleDownloadWorker()
        }
    }


    /* Async via coroutine: Saves podcast collection */
    private fun saveCollectionAsync() = runBlocking<Unit> {
        LogHelper.v(TAG, "Saving podcast collection to storage async")
        val result = async { FileHelper().saveCollection(this@DownloadService, collection) }
        result.await()
        // afterwards: do nothing
        LogHelper.e(TAG,collection.toString()) // todo remove
    }


    /* Just a test */ // todo remove
    private fun queryStatus (downloadID: Long) {
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            LogHelper.i(TAG, "COLUMN_ID: " +
                    cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_ID)))
            LogHelper.i(TAG, "COLUMN_BYTES_DOWNLOADED_SO_FAR: " +
                    cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)))
            LogHelper.i(TAG, "COLUMN_LAST_MODIFIED_TIMESTAMP: " +
                    cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)))
            LogHelper.i(TAG, "COLUMN_LOCAL_URI: " +
                    cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)))
            LogHelper.i(TAG, "COLUMN_STATUS: " +
                    cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)))
            LogHelper.i(TAG, "COLUMN_REASON: " +
                    cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON)))
        }
    }


    /* BroadcastReceiver for completed downloads */
    private val onCompleteReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // process the finished download
            processDownload(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L))
        }
    }


    /*
     * Inner class: Local Binder that returns this service
     */
    inner class LocalBinder: Binder() {
        fun getService(): DownloadService {
            // return this instance of DownloadService so clients can call public methods
            return this@DownloadService
        }
    }

}