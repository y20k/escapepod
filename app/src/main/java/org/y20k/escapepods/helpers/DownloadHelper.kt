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
import android.content.Context
import android.database.Cursor
import android.net.Uri


/**
 * DownloadHelper class
 */
class DownloadHelper (private val downloadManager : DownloadManager) {

    /* Define log tag */
    private val TAG : String = LogHelper.makeLogTag(DownloadHelper::class.java.name)


    /* Enqueues an Array of files in DownloadManager */
    fun download(context: Context, uris: Array<Uri>, type : Int) : LongArray {

        // determine destination folder
        val folder : String
        when (type) {
            Keys.RSS -> folder = Keys.TEMP_FOLDER
            Keys.AUDIO -> folder = Keys.AUDIO_FOLDER
            Keys.IMAGE -> folder = Keys.IMAGE_FOLDER
            else -> folder = "/"
        }

        // enqueues downloads
        val downloadIds = LongArray(uris.size)
        for (i in uris.indices) downloadIds[i] = downloadManager.enqueue(DownloadManager.Request(uris[i])
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                .setAllowedOverRoaming(false)
                .setTitle(uris[i].lastPathSegment)
                .setDestinationInExternalFilesDir(context, folder, uris[i].lastPathSegment))
        return downloadIds
    }


    /* Get size of downloaded file so far */
    fun getFileSizeSoFar (downloadId: Long) : Long {
        var bytesSoFar : Long = -1L
        val cursor : Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            bytesSoFar = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        }
        return bytesSoFar
    }


    /* Just a test */ // todo remove
    fun queryStatus (downloadId: Long) {
        val cursor : Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            LogHelper.i(TAG, "COLUMN_ID: "+
                    cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_ID)))
            LogHelper.i(TAG, "COLUMN_BYTES_DOWNLOADED_SO_FAR: "+
                    cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)))
            LogHelper.i(TAG, "COLUMN_LAST_MODIFIED_TIMESTAMP: "+
                    cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)))
            LogHelper.i(TAG, "COLUMN_LOCAL_URI: "+
                    cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)))
            LogHelper.i(TAG, "COLUMN_STATUS: "+
                    cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)))
            LogHelper.i(TAG, "COLUMN_REASON: "+
                    cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON)))
        }
    }

}