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
import android.net.Uri


/**
 * DownloadHelper class
 */
class DownloadHelper (private val downloadManager : DownloadManager) {

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

}