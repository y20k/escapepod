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
import org.y20k.escapepods.Keys
import java.util.*
import java.util.concurrent.TimeUnit


/*
 * WorkerHelper object
 */
object WorkerHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(WorkerHelper::class.java)


    /* Schedules a DownloadWorker that triggers a one time background download of podcasts */
    fun startOneTimeAddPodcastsWorker(podcastUrlStrings: Array<String>): UUID {
        LogHelper.v(TAG, "Starting one-time work: download podcasts")
        val requestData: Data = Data.Builder()
                .putInt(Keys.KEY_DOWNLOAD_WORK_REQUEST, Keys.REQUEST_ADD_PODCASTS)
                .putStringArray(Keys.KEY_PODCAST_URLS, podcastUrlStrings)
                .build()
        val addPodcastsOneTimeWork = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(requestData)
                .build()
        WorkManager.getInstance().enqueue(addPodcastsOneTimeWork)
        return addPodcastsOneTimeWork.id
    }


    /* Schedules a DownloadWorker that triggers a one time background download of an episode */
    fun startOneTimeEpisodeDownloadWorker(mediaId: String, ignoreWifiRestriction: Boolean = false): UUID {
        LogHelper.v(TAG, "Starting one-time work: download episode")
        val requestData: Data = Data.Builder()
                .putInt(Keys.KEY_DOWNLOAD_WORK_REQUEST, Keys.REQUEST_DOWNLOAD_EPISODE)
                .putString(Keys.KEY_EPISODE_MEDIA_ID, mediaId)
                .putBoolean(Keys.KEY_IGNORE_WIFI_RESTRICTION, ignoreWifiRestriction)
                .build()
        when (ignoreWifiRestriction) {
            true -> {
                // ignore wifi restrictions
                val updateCollectionOneTimeWork = OneTimeWorkRequestBuilder<DownloadWorker>()
                        .setInputData(requestData)
                        .build()
                WorkManager.getInstance().enqueue(updateCollectionOneTimeWork)
                return updateCollectionOneTimeWork.id
            }
            false -> {
                // respect wifi restrictions
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
        }
    }


    /* Schedules a DownloadWorker that triggers a one time background update of the collection */
    fun startOneTimeUpdateWorker(): UUID {
        LogHelper.v(TAG, "Starting one-time work: update collection")
        val requestData: Data = Data.Builder()
                .putInt(Keys.KEY_DOWNLOAD_WORK_REQUEST, Keys.REQUEST_UPDATE_COLLECTION)
                .build()
        val updateCollectionOneTimeWork = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(requestData)
                .build()
        WorkManager.getInstance().enqueue(updateCollectionOneTimeWork)
        return updateCollectionOneTimeWork.id
    }


    /* Schedules a DownloadWorker that triggers background updates of the collection periodically */
    fun schedulePeriodicUpdateWorker(): UUID {
        LogHelper.v(TAG, "Starting / Updating periodic work: update collection")
        val requestData: Data = Data.Builder()
                .putInt(Keys.KEY_DOWNLOAD_WORK_REQUEST, Keys.REQUEST_UPDATE_COLLECTION)
                .build()
        val unmeteredConstraint = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
        val updateCollectionPeriodicWork = PeriodicWorkRequestBuilder<DownloadWorker>(4, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
                .setInputData(requestData)
                .setConstraints(unmeteredConstraint)
                .build()
        WorkManager.getInstance().enqueueUniquePeriodicWork(Keys.NAME_PERIODIC_COLLECTION_UPDATE_WORK,  ExistingPeriodicWorkPolicy.REPLACE, updateCollectionPeriodicWork)
        return updateCollectionPeriodicWork.id
    }

}