/*
 * PodcastPlayerActivity.kt
 * Implements the PodcastPlayerActivity class
 * PodcastPlayerActivity is Escapepod's main activity that hosts a list of podcast and a player sheet
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.*
import org.y20k.escapepods.adapter.CollectionViewModel
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.dialogs.AddPodcastDialog
import org.y20k.escapepods.dialogs.ErrorDialog
import org.y20k.escapepods.helpers.*
import java.util.*
import java.util.concurrent.TimeUnit


/*
 * PodcastPlayerActivity class
 */
class PodcastPlayerActivity: AppCompatActivity(), AddPodcastDialog.AddPodcastDialogListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PodcastPlayerActivity::class.java)


    /* Main class variables */
    private var collection: Collection = Collection()


    /* Overrides onCreate */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // clear temp folder
        FileHelper().clearFolder(getExternalFilesDir(Keys.FOLDER_TEMP), 0)

        // create view model for collection and observe changes
        val collectionViewModel = ViewModelProviders.of(this).get(CollectionViewModel::class.java)
        collectionViewModel.getCollection().observe(this, Observer<Collection> { it ->
            // update collection
            collection = it
            // update ui
            updateUserInterface()
            LogHelper.w(TAG, "CHANGES!!! \n${collection.toString()}") // todo remove
        })

        // start worker that updates the podcast collection at a defined interval
        schedulePeriodicUpdateWorker()

        // set layout
        setContentView(R.layout.activity_podcast_player)

        // get button and listen for clicks
        val addButton: Button = findViewById(R.id.button_add_new)
        addButton.setOnClickListener{
            // show the add podcast dialog
            AddPodcastDialog(this).show(this)
        }

        // get button and listen for clicks
        val swipeRefreshLayout: SwipeRefreshLayout = findViewById(R.id.layout_swipe_refresh)
        swipeRefreshLayout.setOnRefreshListener {
            // update podcast collection
            startOneTimeUpdateWorker()
            swipeRefreshLayout.isRefreshing = false
        }
    }



    /* Overrides onResume */
    override fun onResume() {
        super.onResume()

    }


    /* Overrides onPause */
    override fun onPause() {
        super.onPause()

    }


    /* Overrides onAddPodcastDialog from AddPodcastDialog */
    override fun onAddPodcastDialog(textInput: String) {
        super.onAddPodcastDialog(textInput)
        val podcastUrl = textInput.trim()
        if (CollectionHelper().isNewPodcast(podcastUrl, collection)) {
            downloadPodcastFeed(podcastUrl)
        } else {
            ErrorDialog().show(this, getString(R.string.dialog_error_title_podcast_duplicate),
                    getString(R.string.dialog_error_message_podcast_duplicate),
                    podcastUrl)
        }
    }


    /* Updates user interface */
    private fun updateUserInterface() {
        // update podcast counter - just a test // todo remove
        val podcastCounter: TextView = findViewById(R.id.text_podcast_counter)
        podcastCounter.text = createCollectionInfoString()
    }


    /* For debug purposes: create a string containing collection info */ // todo remove
    private fun createCollectionInfoString(): String {
        var episodesTotal: Int = 0
        collection.podcasts.forEach{
            it.episodes.forEach{
                if (it.audio.length > 0) {
                    episodesTotal++
                }
            }
        }
        val stringBuilder: StringBuilder = StringBuilder()
        stringBuilder.append("${collection.podcasts.size} podcasts & ")
        stringBuilder.append("$episodesTotal episodes")
        return stringBuilder.toString()
    }


    /* Download podcast feed */
    private fun downloadPodcastFeed(feedUrl : String) {
        if (DownloadHelper().determineMimeType(feedUrl) == Keys.MIME_TYPE_XML) {
            Toast.makeText(this, getString(R.string.toast_message_adding_podcast), Toast.LENGTH_LONG).show()
            val uris = Array(1) {feedUrl.toUri()}
            // todo start the worker with -> uris

        } else {
            ErrorDialog().show(this, getString(R.string.dialog_error_title_podcast_invalid_feed),
                    getString(R.string.dialog_error_message_podcast_invalid_feed),
                    feedUrl)
        }
    }


    /* Schedules a DownloadWorker that triggers background updates of the collection periodically */
    private fun schedulePeriodicUpdateWorker() {
        val requestData: Data = Data.Builder()
                .putInt(Keys.KEY_DOWNLOAD_WORK_REQUEST, Keys.REQUEST_UPDATE_COLLECTION)
                .putLong(Keys.KEY_LAST_UPDATE_COLLECTION, collection.lastUpdate.time)
                .build()
        val unmeteredConstraint = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
        val updateCollectionPeriodicWork = PeriodicWorkRequestBuilder<DownloadWorker>(4, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
                .setInputData(requestData)
                .setConstraints(unmeteredConstraint)
                .build()
        WorkManager.getInstance().enqueueUniquePeriodicWork(Keys.NAME_PERIODIC_COLLECTION_UPDATE_WORK,  ExistingPeriodicWorkPolicy.KEEP, updateCollectionPeriodicWork)

        observerCollectionUpdateWork(updateCollectionPeriodicWork.id)
    }


    /* Schedules a DownloadWorker that triggers a one time background update of the collection */
    private fun startOneTimeUpdateWorker() {
        val requestData: Data = Data.Builder()
                .putInt(Keys.KEY_DOWNLOAD_WORK_REQUEST, Keys.REQUEST_UPDATE_COLLECTION)
                .putLong(Keys.KEY_LAST_UPDATE_COLLECTION, collection.lastUpdate.time)
                .build()
        val unmeteredConstraint = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
        val updateCollectionOneTimeWork = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(requestData)
                .setConstraints(unmeteredConstraint)
                .build()
        WorkManager.getInstance().enqueue(updateCollectionOneTimeWork)

        observerCollectionUpdateWork(updateCollectionOneTimeWork.id)
    }


    /* observe result of update work */
    private fun observerCollectionUpdateWork(updateCollectionWorkStatus: UUID) {
        WorkManager.getInstance().getStatusById(updateCollectionWorkStatus)
                .observe(this, Observer { status ->
                    if (status != null && status.state.isFinished) {
                        val updateResult: Boolean = status.outputData.getBoolean(Keys.KEY_RESULT_NEW_COLLECTION, false)
                        if (updateResult) {
                            // todo: ping live data / view model to reload collection
                        }
                    }
                })
    }

}