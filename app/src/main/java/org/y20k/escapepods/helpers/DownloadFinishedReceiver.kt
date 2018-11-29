/*
 * DownloadFinishedReceiver.kt
 * Implements the DownloadFinishedReceiver class
 * A DownloadFinishedReceiver listens for finished downloads
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
import kotlinx.coroutines.*
import org.y20k.escapepods.core.Collection


/*
 * DownloadFinishedReceiver class
 */
class DownloadFinishedReceiver(): BroadcastReceiver() {


    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(DownloadFinishedReceiver::class.java)


    /* Main class variables */
    private lateinit var collection: Collection


    /* Overrides onReceive */
    override fun onReceive(context: Context, intent: Intent) {
        LogHelper.v(TAG, "Loading podcast collection from storage")
        val backgroundJob = Job()
        val uiScope = CoroutineScope(Dispatchers.Main + backgroundJob)
        uiScope.launch {
            // load collection on background thread
            val deferred: Deferred<Collection> = async { FileHelper.readCollectionSuspended(context) }
            // wait for result
            val collection = deferred.await()
            // process the finished download
            DownloadHelper.processDownload(context, collection, intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L))
            // cancel background job
            backgroundJob.cancel()
        }
    }
}