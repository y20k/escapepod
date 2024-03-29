/*
 * DownloadWorker.kt
 * Implements the DownloadWorker class
 * A DownloadWorker is a worker that triggers download actions when the app is not in use
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.y20k.escapepod.Keys


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
            Keys.REQUEST_UPDATE_COLLECTION -> {
                doOneTimeHousekeeping()
                updateCollection()
            }
        }
        return Result.success()
        // (Returning Result.retry() tells WorkManager to try this task again later; Result.failure() says not to try again.)
    }


    /* Updates podcast collection */
    private fun updateCollection() {
        if (!CollectionHelper.hasEnoughTimePassedSinceLastUpdate()) {
            LogHelper.w(TAG, "Background update not initiated: not enough time has passed since last update.")
        } else if (!PreferencesHelper.loadBackgroundRefresh()) {
            LogHelper.w(TAG, "Background update not initiated: Background refresh has been set to manual.")
        } else {
            DownloadHelper.updateCollection(applicationContext, clearTempFolder = true)
        }
    }


    /* Execute one-time housekeeping */
    private fun doOneTimeHousekeeping() {
        if (PreferencesHelper.isHouseKeepingNecessary()) {
            /* add whatever housekeeping is necessary here */
            // housekeeping finished - save state
            PreferencesHelper.saveHouseKeepingNecessaryState()
        }
    }

}
