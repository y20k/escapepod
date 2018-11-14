/*
 * DownloadWorker.kt
 * Implements the DownloadWorker class
 * A DownloadWorker is a worker that triggers download actions when the app is not in use
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
import androidx.work.Worker
import androidx.work.WorkerParameters

/*
 * DownloadWorker class
 */
class DownloadWorker(context : Context, params : WorkerParameters): Worker(context, params) {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(DownloadWorker::class.java)


    /* Overrides doWork */
    override fun doWork(): Result {
        // determine what type of download is requested
        when(inputData.getInt(Keys.KEY_DOWNLOAD_WORK_REQUEST,0)) {
            // CASE: update collection
            Keys.REQUEST_UPDATE_COLLECTION -> DownloadHelper().updateCollection(applicationContext)
            // CASE: add podcast to collection
            Keys.REQUEST_ADD_PODCAST -> DownloadHelper().downloadPodcast(applicationContext, inputData.getString(Keys.KEY_PODCAST_URL).toString())
            // CASE: download episode
            Keys.REQUEST_DOWNLOAD_EPISODE -> DownloadHelper().downloadEpisode(applicationContext, inputData.getString(Keys.KEY_EPISODE_MEDIA_ID).toString(), inputData.getBoolean(Keys.KEY_IGNORE_WIFI_RESTRICTION, false))
        }
        // indicate success or failure
        return Result.SUCCESS
        // (Returning RETRY tells WorkManager to try this task again later; FAILURE says not to try again.)
    }

}