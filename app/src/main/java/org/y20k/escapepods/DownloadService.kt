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
import org.y20k.escapepods.helpers.DownloadHelper
import org.y20k.escapepods.helpers.Keys
import org.y20k.escapepods.helpers.LogHelper


/*
 * DownloadService class
 */
class DownloadService(): Service() {

    /* Interface used to communicate back to activity */
    interface DownloadServiceListener {
        fun onDownloadFinished(downloadID: Long) {
        }
    }


    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(DownloadService::class.java)


    /* Main class variables */
    lateinit var downloadServiceListener: DownloadServiceListener
    var activeDownloads: ArrayList<Long> = ArrayList<Long>()
    private val downloadServiceBinder: LocalBinder = LocalBinder()


    /* Overrides onCreate */
    override fun onCreate() {
        super.onCreate()
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


    /* Enqueues an Array of files in DownloadManager */
    fun download(context: Context, uris: Array<Uri>, type: Int, subDirectory: String): LongArray {

        // determine destination folder
        var folder: String
        when (type) {
            Keys.FILE_TYPE_RSS -> folder = Keys.TEMP_FOLDER
            Keys.FILE_TYPE_AUDIO -> folder = Keys.AUDIO_FOLDER + "/" + subDirectory
            Keys.FILE_TYPE_IMAGE -> folder = Keys.IMAGE_FOLDER + "/" + subDirectory
            else -> folder = "/"
        }

        // determine allowed network type
        val downloadOverMobile = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Keys.PREF_DOWNLOAD_OVER_MOBILE, false);
        var allowedNetworkTypes:Int =  (DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        when (type) {
            Keys.FILE_TYPE_AUDIO -> if (!downloadOverMobile) allowedNetworkTypes = DownloadManager.Request.NETWORK_WIFI
        }

        // enqueues downloads
        val downloadManager: DownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadIDs = LongArray(uris.size)
        for (i in uris.indices)  {
            val request: DownloadManager.Request = DownloadManager.Request(uris[i])
                    .setAllowedNetworkTypes(allowedNetworkTypes)
                    .setAllowedOverRoaming(false)
                    .setTitle(uris[i].lastPathSegment)
                    .setDestinationInExternalFilesDir(context, folder, uris[i].lastPathSegment)
                    .setMimeType(DownloadHelper().determineMimeType(uris[i].toString()))
            downloadIDs[i] = downloadManager.enqueue(request)
            activeDownloads.add(downloadIDs[i])
        }
        return downloadIDs
    }


    /* Updates podcast collection */
    fun updatePodcastCollection() {
        Toast.makeText(this, getString(R.string.toast_message_updating_collection), Toast.LENGTH_LONG).show();
    }



    /* Get size of downloaded file so far */
    fun getFileSizeSoFar(context: Context, downloadID: Long): Long {
        val downloadManager: DownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var bytesSoFar: Long = -1L
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            bytesSoFar = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        }
        return bytesSoFar
    }


    /* Check if download is completed */
    fun isCompleted(context: Context, downloadID: Long): Boolean {
        val downloadManager: DownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var downloadStatus: Long = -1L
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            downloadStatus = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
        }
        return (downloadStatus >= DownloadManager.STATUS_SUCCESSFUL)
    }


    /* Savely remove given download ID from active downloads */
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


    /* Just a test */ // todo remove
    fun queryStatus (context: Context, downloadID: Long) {
        val downloadManager: DownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
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
            // get ID of download
            val downloadID: Long = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            // remove ID from active downloads
            removeFromActiveDownloads(downloadID)
            // hand over id to activity
            downloadServiceListener.onDownloadFinished(downloadID)
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