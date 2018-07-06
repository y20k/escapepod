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

import android.app.DownloadManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Runnable
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.y20k.escapepods.DownloadService
import org.y20k.escapepods.R
import org.y20k.escapepods.XmlReader
import org.y20k.escapepods.adapter.CollectionViewModel
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.core.Podcast
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
    private lateinit var collectionViewModel : CollectionViewModel
    private var collection: Collection = Collection()
    private var downloadServiceBound = false
    private val downloadProgressHandler = Handler()
    private var downloadIDs = longArrayOf(-1L)


    /* Overrides onCreate */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // clear temp folder
        FileHelper().clearFolder(getExternalFilesDir(Keys.FOLDER_TEMP), 0)

        // observe changes in LiveData
        collectionViewModel = ViewModelProviders.of(this).get(CollectionViewModel::class.java);
        collectionViewModel.collectionLiveData.observe(this, createCollectionObserver());
        collectionViewModel.loadCollectionAsync()

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
            // update podcast collection - just a test // todo remove
            collectionViewModel.collectionLiveData.value
            if (downloadServiceBound && CollectionHelper().hasEnoughTimePassedSinceLastUpdate(collection))
                downloadService.updatePodcastCollection()
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


    /* Overrides onAddPodcastDialogFinish from AddPodcastDialog */
    override fun onAddPodcastDialogFinish(textInput: String) {
        super.onAddPodcastDialogFinish(textInput)
        if (CollectionHelper().isNewPodcast(textInput, collection)) {
            ErrorDialog().show(this, getString(R.string.dialog_error_title_podcast_duplicate),
                    getString(R.string.dialog_error_message_podcast_duplicate),
                    textInput)
        } else {
            downloadPodcastFeed(textInput)
        }
    }


    /* Overrides onDownloadFinished from DownloadService */
    override fun onDownloadFinished(downloadID: Long) {
        // get a download manager
        val downloadManager: DownloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // get local Uri in content://downloads/all_downloads/ for download ID
        val localFileUri: Uri = downloadManager.getUriForDownloadedFile(downloadID)

        // get remote URL for download ID
        var remoteFileLocation: String = ""
        val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            remoteFileLocation = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI))
        }

        // determine what to
        when (FileHelper().getFileType(this, localFileUri)) {
            Keys.MIME_TYPE_XML -> readPodcastFeedAsync(localFileUri, remoteFileLocation)
            Keys.MIME_TYPE_MP3 -> { }
            Keys.MIME_TYPE_JPG -> setPodcastImage(localFileUri, remoteFileLocation)
            Keys.MIME_TYPE_PNG -> setPodcastImage(localFileUri, remoteFileLocation)
            else -> {}
        }

        // Log completed download // todo remove
        LogHelper.v(TAG, "Download complete: " + FileHelper().getFileName(this@PodcastPlayerActivity, localFileUri) +
                " | " + FileHelper().getReadableByteCount(FileHelper().getFileSize(this@PodcastPlayerActivity, localFileUri), true)) // todo remove

        // cancel periodic UI update if possible
        if (downloadService.activeDownloads.isEmpty()) {
            downloadProgressHandler.removeCallbacks(downloadProgressRunnable)
        }
    }


    /* Download podcast feed */
    private fun downloadPodcastFeed(feedUrl : String) {
        if (DownloadHelper().determineMimeType(feedUrl) == Keys.MIME_TYPE_XML) {
            Toast.makeText(this, getString(R.string.toast_message_adding_podcast), Toast.LENGTH_LONG).show()
            val uris = Array(1) {Uri.parse(feedUrl)}
            downloadIDs = startDownload(uris, Keys.FILE_TYPE_RSS, Keys.NO_SUB_DIRECTORY)
        } else {
            ErrorDialog().show(this, getString(R.string.dialog_error_title_podcast_invalid_feed),
                    getString(R.string.dialog_error_message_podcast_invalid_feed),
                    feedUrl)
        }
    }


    /* Start download in DownloadService */
    private fun startDownload(uris: Array<Uri>, type: Int, subDirectory: String): LongArray {
        if (downloadServiceBound) {
            // start download
            val newIDs: LongArray = downloadService.download(this, uris, type, subDirectory)
            if (downloadIDs[0] == -1L) downloadIDs = newIDs
            else downloadIDs = newIDs.plus(downloadIDs)
        }
        return downloadIDs
    }


    /* Refreshes episodes and podcast cover */
    private fun refreshPodcast(podcast: Podcast, refreshCover: Boolean) {
        // download podcast cover
        val subDirectory: String = CollectionHelper().getPodcastSubDirectory(podcast)
        if (refreshCover) {
            val uris = Array(1) {Uri.parse(podcast.remoteImageFileLocation)}
            startDownload(uris, Keys.FILE_TYPE_IMAGE, subDirectory)
        }
        // todo download the first two episodes
    }


    /* Sets podcast cover */
    private fun setPodcastImage(localFileUri: Uri, remoteFileLocation: String) {
        for (podcast in collection.podcasts) {
            if (podcast.remoteImageFileLocation == remoteFileLocation) {
                podcast.cover = localFileUri.toString()
                LogHelper.v(TAG, podcast.toString()) // todo remove
            }
        }
        collectionViewModel.collectionLiveData.setValue(collection)
    }


    /* Adds podcast to podcast collection*/
    private fun addPodcast(podcast: Podcast) {
        collection.podcasts.add(podcast)
        collection.podcasts.sortBy { it.name }
        collectionViewModel.collectionLiveData.setValue(collection)
    }


    /* Async via coroutine: Reads podcast feed */
    private fun readPodcastFeedAsync(localFileUri: Uri, remoteFileLocation: String) {
        launch(UI) {
            // launch XmlReader for result and await
            val result: Podcast = async(CommonPool) {
                XmlReader().read(this@PodcastPlayerActivity, localFileUri, remoteFileLocation)
            }.await()
            // afterwards: update existing podcast or add podcast as new podcast
            if (CollectionHelper().isNewPodcast(result.remotePodcastFeedLocation, collection)) {
                refreshPodcast(result, false)
            } else {
                addPodcast(result)
                refreshPodcast(result, true)
            }
       }
    }


    /* Async via coroutine: Saves podcast collection */
    private fun saveCollectionAsync() {
        launch(UI) {
            // launch FileHelper for result and await
            val result = async(CommonPool) {
                FileHelper().saveCollection(this@PodcastPlayerActivity, collection)
            }.await()
            // afterwards: do nothing
        }
    }


    /* Observer for Collection stored as Live Data */
    private fun createCollectionObserver(): Observer<Collection> {
        return Observer<Collection> { newCollection ->
            newCollection?.let {
                collection = it
                // update podcast counter - just a test // todo remove
                val podcastCounter: TextView = findViewById(R.id.podcast_counter)
                podcastCounter.text = "${it.podcasts.size} podcasts in your collection"
                // save collection
                saveCollectionAsync()
            }
        }
    }


    /* Runnable that updates the download progress every second */
    private val downloadProgressRunnable = object: Runnable {
        override fun run() {
            for (activeDownload in downloadService.activeDownloads) {
                val size = downloadService.getFileSizeSoFar(this@PodcastPlayerActivity, activeDownload)
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
                downloadPodcastFeed(intent.data.toString())
                intent.action == ""
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            downloadServiceBound = false
        }
    }


}