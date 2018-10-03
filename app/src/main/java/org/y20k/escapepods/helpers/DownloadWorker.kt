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

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.y20k.escapepods.DownloadService


/*
 * DownloadWorker class
 */
class DownloadWorker(context : Context, params : WorkerParameters): Worker(context, params) {


    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(DownloadHelper::class.java)


    /* Overrides doWork */
    override fun doWork(): Result {

        // triggers an update of the collection
        triggerCollectionUpdate(inputData.getLong(Keys.KEY_LAST_UPDATE_COLLECTION, 0))

        // indicate success or failure
        return Result.SUCCESS
        // (Returning RETRY tells WorkManager to try this task again later; FAILURE says not to try again.)
    }


    /* Starts the download service */
    fun triggerCollectionUpdate(lastUpdate: Long) {
        val intent: Intent = Intent(applicationContext, DownloadService::class.java)
        intent.setAction(Keys.ACTION_UPDATE_COLLECTION)
        intent.putExtra(Keys.EXTRA_LAST_UPDATE_COLLECTION, lastUpdate)
        applicationContext.startService(intent)
    }

}