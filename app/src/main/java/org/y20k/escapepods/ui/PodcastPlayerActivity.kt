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
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.Toast
import org.y20k.escapepods.DownloadService
import org.y20k.escapepods.R
import org.y20k.escapepods.XmlReader
import org.y20k.escapepods.core.Podcast
import org.y20k.escapepods.core.PodcastCollection
import org.y20k.escapepods.dialogs.AddPodcastDialog
import org.y20k.escapepods.dialogs.ErrorDialog
import org.y20k.escapepods.helpers.FileHelper
import org.y20k.escapepods.helpers.Keys
import org.y20k.escapepods.helpers.LogHelper
import java.io.InputStream


/*
 * PodcastPlayerActivity class
 */
class PodcastPlayerActivity: AppCompatActivity(),
                             AddPodcastDialog.AddPodcastDialogListener,
                             XmlReader.XmlReaderListener {
//class PodcastPlayerActivity: BaseActivity(), AddPodcastDialog.AddPodcastDialogListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PodcastPlayerActivity::class.java)


    /* Main class variables */
    private lateinit var downloadService: DownloadService
    private var downloadServiceBound = false
    private val downloadProgressHandler = Handler()
    private var downloadIDs = longArrayOf(-1L)
    private var podcastCollection: PodcastCollection = PodcastCollection()


    /* Implements onCreate */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // set layout
        setContentView(R.layout.activity_podcast_player)

        // get button and listen for clicks
        val addButton: Button = findViewById(R.id.add_button)
        addButton.setOnClickListener(View.OnClickListener {
            // show the add podcast dialog
            AddPodcastDialog(this).show(this)
        })

        // get button and listen for clicks
        val downloadButton: Button = findViewById(R.id.domwload_button)
        downloadButton.setOnClickListener(View.OnClickListener {
            // start some downloads - just a test // todo remove
            val uris = arrayOf(Uri.parse("https://www.podtrac.com/pts/redirect.mp3/traffic.libsyn.com/radarrelay/undertheradar136.mp3"), Uri.parse("https://www.podtrac.com/pts/redirect.mp3/traffic.libsyn.com/radarrelay/undertheradar132.mp3"))
            downloadIDs = downloadService.download(this@PodcastPlayerActivity, uris, Keys.AUDIO)
            downloadProgressRunnable.run()
        })

    }


    /* Implements onResume */
    override fun onResume() {
        super.onResume()

        // listen for completed downloads
        registerReceiver(downloadCompleteReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

        // bind to DownloadService
        bindService(Intent(this, DownloadService::class.java), downloadServiceConnection, Context.BIND_AUTO_CREATE)
    }


    /* Implements onPause */
    override fun onPause() {
        super.onPause()

        // unregister download complete receiver
        unregisterReceiver(downloadCompleteReceiver)

        // unbind DownloadService
        unbindService(downloadServiceConnection)
    }


    /* Implements onAddPodcastDialogFinish from AddPodcastDialog */
    override fun onAddPodcastDialogFinish(textInput: String) {
        super.onAddPodcastDialogFinish(textInput)
        if (podcastCollection.isInCollection(textInput)) {
            ErrorDialog().show(this, getString(R.string.dialog_error_title_podcast_duplicate),
                    getString(R.string.dialog_error_message_podcast_duplicate),
                    textInput)
        } else {
            Toast.makeText(this, getString(R.string.toast_message_adding_podcast), Toast.LENGTH_LONG).show();
            downloadPodcastFeed(textInput)
        }
    }


    /* Implements onParseResult from XmlReader */
    override fun onParseResult(podcast: Podcast) {
        super.onParseResult(podcast)
        if (podcastCollection.isInCollection(podcast.remotePodcastFeedLocation)) {
            // update existing podcast
            LogHelper.e(TAG, "Updating: $podcast.remotePodcastFeedLocation") // todo remove
        } else {
            // add new podcast to podcast collection
            podcastCollection.podcasts.add(podcast)
            // save changes
            FileHelper().savePodcastCollection(podcastCollection)
        }
    }


    /* Download podcast feed */
    private fun downloadPodcastFeed(feedUrl : String) {
        var uri = Uri.parse(feedUrl)
        val uris = Array(1, {uri})
        downloadIDs = startDownload(uris, Keys.RSS)
    }


    /* Start download in DownloadService */
    private fun startDownload(uris: Array<Uri>, type: Int): LongArray {
        var downloadIDs = longArrayOf(-1L)
        if (downloadServiceBound) {
            // start download
            downloadIDs = downloadService.download(this, uris, type)
        }
        return downloadIDs
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


    /* BroadcastReceiver for completed downloads */
    private val downloadCompleteReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            for (downloadID in downloadIDs) {
                if (downloadID == id) {
                    val downloadManager: DownloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    // get Uri if ID represented one of the enqueued downloads
                    val uri = downloadManager.getUriForDownloadedFile(id)

                    var remotePodcastFeedLocation: String = ""
                    val cursor: Cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadID))
                    if (cursor.count > 0) {
                        cursor.moveToFirst()
                        remotePodcastFeedLocation = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI))
                    }

                    // some tests // todo remove
                    val fileHelper = FileHelper()
                    LogHelper.e(TAG, "Download complete: " + fileHelper.getFileName(this@PodcastPlayerActivity, uri) +
                            " | " + fileHelper.getReadableByteCount(fileHelper.getFileSize(this@PodcastPlayerActivity, uri), true)) // todo remove
                    val inputStream: InputStream = FileHelper().getTextFileStream(this@PodcastPlayerActivity, uri)
                    val xmlReader: XmlReader = XmlReader(this@PodcastPlayerActivity, remotePodcastFeedLocation)
                    xmlReader.execute(inputStream)
                }
            }

            // cancel periodic UI update if possible
            if (downloadService.activeDownloads.isEmpty()) {
                downloadProgressHandler.removeCallbacks(downloadProgressRunnable)
            }
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
            downloadServiceBound = true
            // check if downloads are in progress and update UI while service is connected
            if (!downloadService.activeDownloads.isEmpty()) {
                downloadProgressRunnable.run()
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            downloadServiceBound = false
        }
    }


}