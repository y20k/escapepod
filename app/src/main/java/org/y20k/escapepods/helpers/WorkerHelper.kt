/*
 * WorkerHelper.kt
 * Implements the WorkerHelper object
 * A WorkerHelper provides helper methods for starting work jobs
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import androidx.work.*
import java.util.*
import java.util.concurrent.TimeUnit


/*
 * WorkerHelper object
 */
object WorkerHelper {


    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(WorkerHelper::class.java)

    /* Schedules a DownloadWorker that triggers a one time background update of the collection */
    fun startOneTimeAddPodcastWorker(podcastUrl: String): UUID {
        val requestData: Data = Data.Builder()
                .putInt(Keys.KEY_DOWNLOAD_WORK_REQUEST, Keys.REQUEST_ADD_PODCAST)
                .putString(Keys.KEY_NEW_PODCAST_URL, podcastUrl)
                .build()
        val unmeteredConstraint = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
        val addPodcastOneTimeWork = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(requestData)
                .setConstraints(unmeteredConstraint)
                .build()
        WorkManager.getInstance().enqueue(addPodcastOneTimeWork)

        return addPodcastOneTimeWork.id
    }


    /* Schedules a OneTimeCollectionUpdateWorker that triggers a one time background update of the collection */
    fun startOneTimeUpdateWorker(lastUpdate: Long): UUID {
        val requestData: Data = Data.Builder()
                .putInt(Keys.KEY_DOWNLOAD_WORK_REQUEST, Keys.REQUEST_UPDATE_COLLECTION)
                .putLong(Keys.KEY_LAST_UPDATE_COLLECTION, lastUpdate)
                .build()
        val unmeteredConstraint = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
        val updateCollectionOneTimeWork = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(requestData)
                .setConstraints(unmeteredConstraint)
                .build()
        WorkManager.getInstance().enqueue(updateCollectionOneTimeWork)

        return updateCollectionOneTimeWork.id
    }


    /* Schedules a OneTimeCollectionUpdateWorker that triggers background updates of the collection periodically */
    fun schedulePeriodicUpdateWorker(lastUpdate: Long): UUID {
        val requestData: Data = Data.Builder()
                .putInt(Keys.KEY_DOWNLOAD_WORK_REQUEST, Keys.REQUEST_UPDATE_COLLECTION)
                .putLong(Keys.KEY_LAST_UPDATE_COLLECTION, lastUpdate)
                .build()
        val unmeteredConstraint = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
        val updateCollectionPeriodicWork = PeriodicWorkRequestBuilder<DownloadWorker>(4, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
                .setInputData(requestData)
                .setConstraints(unmeteredConstraint)
                .build()
        WorkManager.getInstance().enqueueUniquePeriodicWork(Keys.NAME_PERIODIC_COLLECTION_UPDATE_WORK,  ExistingPeriodicWorkPolicy.KEEP, updateCollectionPeriodicWork)

        return updateCollectionPeriodicWork.id
    }


}