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
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.y20k.escapepods.core.Collection


/*
 * DownloadWorker class
 */
class DownloadWorker(context : Context, params : WorkerParameters): Worker(context, params) {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(DownloadHelper::class.java)


    /* Main class variables */
    private var collection: Collection = Collection()


    /* Overrides doWork */
    override fun doWork(): Result {

        // determine what kind of download is requested
        when(inputData.getInt(Keys.KEY_DOWNLOAD_WORK_REQUEST,0)) {
            Keys.REQUEST_UPDATE_COLLECTION -> triggerCollectionUpdate(inputData.getLong(Keys.KEY_LAST_UPDATE_COLLECTION, 0))
            Keys.REQUEST_ADD_PODCAST -> true // todo implement
        }

        // indicate success or failure
        return Result.SUCCESS
        // (Returning RETRY tells WorkManager to try this task again later; FAILURE says not to try again.)
    }


    /* Starts the collection update */
    fun triggerCollectionUpdate(lastUpdate: Long) {
        // todo implement the download stuff
        // todo grab the methods from download service

        // on success set output data to true
        val output: Data = workDataOf(Keys.KEY_RESULT_NEW_COLLECTION to true)
        setOutputData(output)
    }

}