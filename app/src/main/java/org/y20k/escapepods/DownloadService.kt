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
import android.preference.PreferenceManager
import android.widget.Toast
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.core.Podcast
import org.y20k.escapepods.helpers.*


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
    private lateinit var downloadServiceListener: DownloadServiceListener
    private val downloadServiceBinder: LocalBinder = LocalBinder()
    private lateinit var downloadManager: DownloadManager


    /* Overrides onCreate */
    override fun onCreate() {
        super.onCreate()
        // load collection
        loadCollectionAsync()
        // get download manager
        downloadManager = getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
        // listen for completed downloads
        registerReceiver(onCompleteReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }


    /* Overrides onDestroy */
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onCompleteReceiver)
    }


    /* Overrides onBind */
    override fun onBind(intent: Intent): IBinder? {
        return downloadServiceBinder
    }


    /* Overrides onUnbind */
    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }


    /* Initializes the listener -> must ALWAYS be called */
    fun registerListener(listener: DownloadServiceListener) {
        downloadServiceListener = listener
    }


    /* Download a podcast */
    fun downloadPodcast (uris: Array<Uri>) {
        startDownload(uris, Keys.FILE_TYPE_RSS, Keys.NO_SUB_DIRECTORY)
    }


    /* Updates podcast collection */
    fun updatePodcastCollection() {
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


    /* Check if startDownload is completed */
    fun isCompleted(downloadID: Long): Boolean {
        var downloadStatus: Long = -1L
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            downloadStatus = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
        }
        return (downloadStatus >= DownloadManager.STATUS_SUCCESSFUL)
    }


    /* Enqueues an Array of files in DownloadManager */
    private fun startDownload(uris: Array<Uri>, type: Int, subDirectory: String) {

        // determine destination folder
        val folder: String
        when (type) {
            Keys.FILE_TYPE_RSS -> folder = Keys.FOLDER_TEMP
            Keys.FILE_TYPE_AUDIO -> folder = Keys.FOLDER_AUDIO + "/" + subDirectory
            Keys.FILE_TYPE_IMAGE -> folder = Keys.FOLDER_IMAGES + "/" + subDirectory
            else -> folder = "/"
        }

        // determine allowed network type
        val downloadOverMobile = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Keys.PREF_DOWNLOAD_OVER_MOBILE, Keys.DEFAULT_DOWNLOAD_OVER_MOBILE);
        var allowedNetworkTypes:Int =  (DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        when (type) {
            Keys.FILE_TYPE_AUDIO -> if (!downloadOverMobile) allowedNetworkTypes = DownloadManager.Request.NETWORK_WIFI
        }

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
    }


    /* Savely remove given startDownload ID from active downloads */
    private fun removeFromActiveDownloads(downloadID: Long): Boolean {
        val iterator: MutableIterator<Long> = activeDownloads.iterator()
        while (iterator.hasNext()) {
            val activeDownload = iterator.next()
            if (activeDownload.equals(downloadID)) {
                iterator.remove()
                return true
            }
        }
        return false
    }


    /* Refreshes episodes and podcast cover */
    private fun refreshPodcast(podcast: Podcast, refreshCover: Boolean) {
        // startDownload podcast cover
        val subDirectory: String = CollectionHelper().getPodcastSubDirectory(podcast)
        if (refreshCover) {
            val coverUris: Array<Uri>  = Array(1) {Uri.parse(podcast.remoteImageFileLocation)}
            startDownload(coverUris, Keys.FILE_TYPE_IMAGE, subDirectory)
        }

        // determine number of episodes to download
        var numberOfEpisodesToDownload = PreferenceManager.getDefaultSharedPreferences(this).getInt(Keys.PREF_DOWNLOAD_NUMBER_OF_EPISODES_TO_DOWNLOAD, Keys.DEFAULT_DOWNLOAD_NUMBER_OF_EPISODES_TO_DOWNLOAD);
        if (podcast.episodes.size > numberOfEpisodesToDownload) {
            numberOfEpisodesToDownload = podcast.episodes.size
        }
        // download episodes
        val episodeUris: Array<Uri> = Array(numberOfEpisodesToDownload, { it -> Uri.parse(podcast.episodes[it].getString(Keys.METADATA_CUSTOM_KEY_AUDIO_LINK_URL))})
        startDownload(episodeUris, Keys.FILE_TYPE_AUDIO, subDirectory)
    }



    /* Adds podcast to podcast collection*/
    private fun addPodcast(podcast: Podcast) {
        collection.podcasts.add(podcast)
        collection.podcasts.sortBy { it.name }
        saveCollectionAsync()
        // savely hand over new collection to activity
        downloadServiceListener.let{
            it.onDownloadFinished(collection)
        }
    }


    /* Sets podcast cover */
    private fun setPodcastImage(localFileUri: Uri, remoteFileLocation: String) {
        for (podcast in collection.podcasts) {
            if (podcast.remoteImageFileLocation == remoteFileLocation) {
                podcast.cover = localFileUri.toString()
                LogHelper.v(TAG, podcast.toString()) // todo remove
            }
        }


        // todo set METADATA_KEY_ALBUM_ART_URI for all episodes


        saveCollectionAsync()
        // savely hand over new collection to activity
        downloadServiceListener.let{
            it.onDownloadFinished(collection)
        }
    }



    /* Sets Media Uri in Episode */
    private fun setEpisodeMediaUri(localFileUri: Uri, remoteFileLocation: String) {
        for (podcast in collection.podcasts) {
            for (episode in podcast.episodes) {
                if (episode.getString(Keys.METADATA_CUSTOM_KEY_AUDIO_LINK_URL) == remoteFileLocation) {


                    // todo set METADATA_KEY_MEDIA_URI


                    LogHelper.v(TAG, podcast.toString()) // todo remove
                }
            }
        }
    }


    /* Async via coroutine: Reads podcast feed */
    private fun readPodcastFeedAsync(localFileUri: Uri, remoteFileLocation: String) {
        launch(UI) {
            // launch XmlReader for result and await
            val result: Podcast = async(CommonPool) {
                XmlReader().read(this@DownloadService, localFileUri, remoteFileLocation)
            }.await()
            // afterwards: add podcast as new podcast or update existing podcast
            if (CollectionHelper().isNewPodcast(result.remotePodcastFeedLocation, collection)) {
                // NEW: add and refresh
                addPodcast(result)
                refreshPodcast(result, true)
            } else {
                // NOT NEW: just refresh
                refreshPodcast(result, false)
            }
        }
    }


    /* Async via coroutine: Reads collection from storage using GSON */
    private fun loadCollectionAsync() {
        // launch XmlReader for result and await
        launch(UI) {
            val result = async(CommonPool) {
                // get JSON from text file
                FileHelper().readCollection(this@DownloadService)
            }.await()
            // afterwards: update collection
            collection = result
            // savely hand over new collection to activity
            downloadServiceListener.let {
                it.onDownloadFinished(collection)
            }
        }
    }


    /* Async via coroutine: Saves podcast collection */
    private fun saveCollectionAsync() {
        launch(UI) {
            // launch FileHelper for result and await
            val result = async(CommonPool) {
                FileHelper().saveCollection(this@DownloadService, collection)
            }.await()
            // afterwards: do nothing
        }
    }


    /* Just a test */ // todo remove
    fun queryStatus (context: Context, downloadID: Long) {
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
            // get ID of startDownload
            val downloadID: Long = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)

            // get local Uri in content://downloads/all_downloads/ for startDownload ID
            val localFileUri: Uri = downloadManager.getUriForDownloadedFile(downloadID)

            // get remote URL for startDownload ID
            var remoteFileLocation: String = ""
            val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
            if (cursor.count > 0) {
                cursor.moveToFirst()
                remoteFileLocation = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI))
            }

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