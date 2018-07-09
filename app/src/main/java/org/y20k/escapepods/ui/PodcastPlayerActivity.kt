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


package org.y20k.escapepods.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.experimental.Runnable
import org.y20k.escapepods.DownloadService
import org.y20k.escapepods.R
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.dialogs.AddPodcastDialog
import org.y20k.escapepods.dialogs.ErrorDialog
import org.y20k.escapepods.helpers.*


/*
 * PodcastPlayerActivity class
 */
class PodcastPlayerActivity: AppCompatActivity(),
                             AddPodcastDialog.AddPodcastDialogListener,
                             DownloadService.DownloadServiceListener {
//class PodcastPlayerActivity: BaseActivity(), AddPodcastDialog.AddPodcastDialogListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PodcastPlayerActivity::class.java)


    /* Main class variables */
    private lateinit var downloadService: DownloadService
    private var downloadServiceBound = false
    private val downloadProgressHandler = Handler()
    private var collection: Collection = Collection()


    /* Overrides onCreate */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // clear temp folder
        FileHelper().clearFolder(getExternalFilesDir(Keys.FOLDER_TEMP), 0)

        // set layout
        setContentView(R.layout.activity_podcast_player)

        // get button and listen for clicks
        val addButton: Button = findViewById(R.id.add_button)
        addButton.setOnClickListener(View.OnClickListener {
            // show the add podcast dialog
            AddPodcastDialog(this).show(this)
        })

        // get button and listen for clicks
        val updateButton: Button = findViewById(R.id.update_button)
        updateButton.setOnClickListener(View.OnClickListener {
            // update podcast collection
            if (downloadServiceBound && CollectionHelper().hasEnoughTimePassedSinceLastUpdate(collection))
                downloadService.updateCollection()
        })
    }



    /* Overrides onResume */
    override fun onResume() {
        super.onResume()
        // bind to DownloadService
        bindService(Intent(this, DownloadService::class.java), downloadServiceConnection, Context.BIND_AUTO_CREATE)
    }


    /* Overrides onPause */
    override fun onPause() {
        super.onPause()
        // unbind DownloadService
        unbindService(downloadServiceConnection)
    }


    /* Overrides onAddPodcastDialog from AddPodcastDialog */
    override fun onAddPodcastDialog(textInput: String) {
        super.onAddPodcastDialog(textInput)
        if (CollectionHelper().isNewPodcast(textInput, collection)) {
            downloadPodcastFeed(textInput)
        } else {
            ErrorDialog().show(this, getString(R.string.dialog_error_title_podcast_duplicate),
                    getString(R.string.dialog_error_message_podcast_duplicate),
                    textInput)
        }
    }


    /* Overrides onDownloadFinished from DownloadService */
    override fun onDownloadFinished(newCollection: Collection) {
        LogHelper.v(TAG, "Received a new collection via onDownloadFinished. Updating UI.")
        collection= newCollection
        updateUserInterface()
    }


    /* Updates user interface */
    private fun updateUserInterface() {
        // update podcast counter - just a test // todo remove
        val podcastCounter: TextView = findViewById(R.id.podcast_counter)
        podcastCounter.text = "${collection.podcasts.size} podcasts in your collection"
    }


    /* Download podcast feed */
    private fun downloadPodcastFeed(feedUrl : String) {
        if (DownloadHelper().determineMimeType(feedUrl) == Keys.MIME_TYPE_XML) {
            Toast.makeText(this, getString(R.string.toast_message_adding_podcast), Toast.LENGTH_LONG).show()
            val uris = Array(1) {Uri.parse(feedUrl)}
            downloadService.downloadPodcast(uris)
        } else {
            ErrorDialog().show(this, getString(R.string.dialog_error_title_podcast_invalid_feed),
                    getString(R.string.dialog_error_message_podcast_invalid_feed),
                    feedUrl)
        }
    }


    /* Runnable that updates the startDownload progress every second */
    private val downloadProgressRunnable = object: Runnable {
        override fun run() {
            for (activeDownload in downloadService.activeDownloads) {
                val size = downloadService.getFileSizeSoFar(activeDownload)
                // TODO update UI
                LogHelper.i(TAG, "DownloadID = " + activeDownload + " | Size so far: " + FileHelper().getReadableByteCount(size, true) + " " + downloadService.activeDownloads.isEmpty()) // todo remove
            }
            downloadProgressHandler.postDelayed(this, Keys.ONE_SECOND_IN_MILLISECONDS)
        }
    }


    /*
     * Defines callbacks for service binding, passed to bindService()
     */
    private val downloadServiceConnection = object: ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // get service from binder
            val binder = service as DownloadService.LocalBinder
            downloadService = binder.getService()
            downloadService.registerListener(this@PodcastPlayerActivity)
            downloadServiceBound = true
            // check if downloads are in progress and update UI while service is connected
            if (!downloadService.activeDownloads.isEmpty()) {
                downloadProgressRunnable.run()
            }
            // handles the intent that started the activity
            if (Intent.ACTION_VIEW == intent.action) {
                LogHelper.v(TAG, "Received an Intent request to add a new podcast subscription.")
                downloadPodcastFeed(intent.data.toString())
                intent.action == ""
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            downloadServiceBound = false
        }
    }
}