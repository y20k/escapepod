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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.y20k.escapepods.adapter.CollectionViewModel
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.dialogs.AddPodcastDialog
import org.y20k.escapepods.dialogs.ErrorDialog
import org.y20k.escapepods.helpers.*


/*
 * PodcastPlayerActivity class
 */
class PodcastPlayerActivity: AppCompatActivity(), AddPodcastDialog.AddPodcastDialogListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PodcastPlayerActivity::class.java)


    /* Main class variables */
    private lateinit var collectionViewModel: CollectionViewModel
    private var collection: Collection = Collection()


    /* Overrides onCreate */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // clear temp folder
        FileHelper.clearFolder(getExternalFilesDir(Keys.FOLDER_TEMP), 0)

        // create view model and observe changes in collection view model
        collectionViewModel = ViewModelProviders.of(this).get(CollectionViewModel::class.java)
        observeCollectionViewModel()

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
            // update podcast collection and observe download work
            if (CollectionHelper.hasEnoughTimePassedSinceLastUpdate(this)) {
                Toast.makeText(this, getString(R.string.toast_message_updating_collection), Toast.LENGTH_LONG).show()
                WorkerHelper.startOneTimeUpdateWorker(collection.lastUpdate.time)
            } else {
                Toast.makeText(this, getString(R.string.toast_message_collection_update_not_necessary), Toast.LENGTH_LONG).show()
            }
            swipeRefreshLayout.isRefreshing = false
        }
    }


    /* Overrides onResume */
    override fun onResume() {
        super.onResume()
        // listen for collection changes initiated by DownloadHelper
        LocalBroadcastManager.getInstance(this).registerReceiver(collectionChangedReceiver, IntentFilter(Keys.ACTION_COLLECTION_CHANGED))
        // reload collection // todo check if necessary
        collectionViewModel.reload()
    }


    /* Overrides onPause */
    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(collectionChangedReceiver)
    }


    /* Overrides onAddPodcastDialog from AddPodcastDialog */
    override fun onAddPodcastDialog(textInput: String) {
        super.onAddPodcastDialog(textInput)
        val podcastUrl = textInput.trim()
        if (CollectionHelper.isNewPodcast(podcastUrl, collection)) {
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
        if (FileHelper.determineMimeType(feedUrl) == Keys.MIME_TYPE_XML) {
            Toast.makeText(this, getString(R.string.toast_message_adding_podcast), Toast.LENGTH_LONG).show()
            // start download and observe download work
            WorkerHelper.startOneTimeAddPodcastWorker(feedUrl)
        } else {
            ErrorDialog().show(this, getString(R.string.dialog_error_title_podcast_invalid_feed),
                    getString(R.string.dialog_error_message_podcast_invalid_feed),
                    feedUrl)
        }
    }


    /* Observe view model of podcast collection*/
    private fun observeCollectionViewModel() {
        collectionViewModel.getCollection().observe(this, Observer<Collection> { it ->
            // update collection
            collection = it
            // update ui
            updateUserInterface()
            // start worker that updates the podcast collection and observe download work
            WorkerHelper.schedulePeriodicUpdateWorker(collection.lastUpdate.time)
        })
    }


    /* Observe changes made by DownloadHelper */
    private val collectionChangedReceiver = object: BroadcastReceiver(){
        override fun onReceive(context: Context, intent: Intent) {
            // reload collection
            collectionViewModel.reload()
        }
    }


}