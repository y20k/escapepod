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
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.y20k.escapepods.adapter.CollectionAdapter
import org.y20k.escapepods.adapter.CollectionViewModel
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.dialogs.AddPodcastDialog
import org.y20k.escapepods.dialogs.ErrorDialog
import org.y20k.escapepods.dialogs.MeteredNetworkDialog
import org.y20k.escapepods.dialogs.OpmlImportDialog
import org.y20k.escapepods.helpers.*
import org.y20k.escapepods.xml.OpmlHelper


/*
 * PodcastPlayerActivity class
 */
class PodcastPlayerActivity: AppCompatActivity(), AddPodcastDialog.AddPodcastDialogListener, MeteredNetworkDialog.MeteredNetworkDialogListener, OpmlImportDialog.OpmlImportDialogListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PodcastPlayerActivity::class.java)


    /* Main class variables */
    private lateinit var collectionViewModel: CollectionViewModel
    private lateinit var recyclerView: RecyclerView
    private var collection: Collection = Collection()


    /* Overrides onCreate */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // clear temp folder
        FileHelper.clearFolder(getExternalFilesDir(Keys.FOLDER_TEMP), 0)

        // create view model and observe changes in collection view model
        collectionViewModel = ViewModelProviders.of(this).get(CollectionViewModel::class.java)
        observeCollectionViewModel()

        //
        val collectionAdapter: CollectionAdapter = CollectionAdapter(this)

        // find views
        setContentView(R.layout.activity_podcast_player)
        recyclerView = findViewById(R.id.recyclerview_list)

        // set up recycler view
        val layoutManager: LinearLayoutManager = object: LinearLayoutManager(this) {
            override fun supportsPredictiveItemAnimations(): Boolean {
                return true
            }
        }
        recyclerView.setLayoutManager(layoutManager)
        recyclerView.setItemAnimator(DefaultItemAnimator())
        recyclerView.setAdapter(collectionAdapter)


        // get button and listen for clicks
        val swipeRefreshLayout: SwipeRefreshLayout = findViewById(R.id.layout_swipe_refresh)
        swipeRefreshLayout.setOnRefreshListener {
            // update podcast collection and observe download work
            if (CollectionHelper.hasEnoughTimePassedSinceLastUpdate(this)) {
                updateCollection()
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
        // handle start intent
        handleStartIntent()
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
            ErrorDialog().show(this, R.string.dialog_error_title_podcast_duplicate, R.string.dialog_error_message_podcast_duplicate, podcastUrl)
        }
    }


    /* Overrides onMeteredNetworkDialog from MeteredNetworkDialog */
    override fun onMeteredNetworkDialog(dialogType: Int) {
        super.onMeteredNetworkDialog(dialogType)
        when (dialogType) {
            Keys.DIALOG_UPDATE_WITHOUT_WIFI -> {
                Toast.makeText(this, getString(R.string.toast_message_updating_collection), Toast.LENGTH_LONG).show()
                WorkerHelper.startOneTimeUpdateWorker(collection.lastUpdate.time, true) // todo implement ignoreWifiRestriction in DownloadHelper
            }
            Keys.DIALOG_DOWNLOAD_EPISODE_WITHOUT_WIFI -> {
                // todo implement
            }
        }
    }


    /* Overrides onOpmlImportDialog from OpmlImportDialog */
    override fun onOpmlImportDialog(feedUrlList: ArrayList<String>) {
        super.onOpmlImportDialog(feedUrlList)
        feedUrlList.forEach {
            LogHelper.e(TAG, "$it \n")
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


    /* Updates podcast collection */
    private fun updateCollection() {
        if (NetworkHelper.isConnectedToWifi(this)) {
            Toast.makeText(this, getString(R.string.toast_message_updating_collection), Toast.LENGTH_LONG).show()
            WorkerHelper.startOneTimeUpdateWorker(collection.lastUpdate.time)
        } else if (NetworkHelper.isConnectedToCellular(this)) {
            MeteredNetworkDialog(this).show(this, Keys.DIALOG_UPDATE_WITHOUT_WIFI, R.string.dialog_metered_update_title, R.string.dialog_metered_update_message, R.string.dialog_metered_update_button_okay)
        } else {
            ErrorDialog().show(this, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }



    /* Download podcast feed */
    private fun downloadPodcastFeed(feedUrl : String) {
        if (FileHelper.determineMimeType(feedUrl) == Keys.MIME_TYPE_XML) {
            Toast.makeText(this, getString(R.string.toast_message_adding_podcast), Toast.LENGTH_LONG).show()
            WorkerHelper.startOneTimeAddPodcastWorker(feedUrl)
        } else {
            ErrorDialog().show(this, R.string.dialog_error_title_podcast_invalid_feed, R.string.dialog_error_message_podcast_invalid_feed, feedUrl)
        }
    }


    /* Read OPML file */
    private fun readOpmlFile(opmlUri: Uri) = runBlocking<Unit> {
        val result = async { OpmlHelper().read(this@PodcastPlayerActivity, opmlUri) }
        // wait for result and update collection
        var feedUrlList: ArrayList<String> = result.await()
        feedUrlList.forEach {
            LogHelper.v(TAG, it) // todo remove
        }
        feedUrlList = CollectionHelper.removeDuplicates(collection, feedUrlList)
        OpmlImportDialog(this@PodcastPlayerActivity).show(this@PodcastPlayerActivity, feedUrlList)
        LogHelper.v(TAG, "${feedUrlList.size} new podcasts found!") // todo remove
    }


    /* Handles this activity's start intent */
    private fun handleStartIntent() {
        if (intent.action != null) {
            when (intent.action) {
                Keys.ACTION_SHOW_PLAYER -> handlesShowPlayer()
                Intent.ACTION_VIEW -> handleViewIntent()
            }
        }
    }


    /* Handles ACTION_SHOW_PLAYER request from notification */
    private fun handlesShowPlayer() {
        // todo check if intent action needs to be cleared

        // todo implement
    }


    /* Handles ACTION_VIEW request to add Podcast or import OPML */
    private fun handleViewIntent() {
        // todo check if intent action needs to be cleared
        val contentUri: Uri = intent.data
        if (contentUri != null && contentUri.scheme != null) {
            when {
                // download new podcast
                contentUri.scheme.startsWith("http") -> downloadPodcastFeed(contentUri.toString()) // todo implement podcast download + dialog and stuff
                // read opml from file
                contentUri.scheme.startsWith("content") -> readOpmlFile(contentUri) // todo implement OPML read + dialog and stuff
            }
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