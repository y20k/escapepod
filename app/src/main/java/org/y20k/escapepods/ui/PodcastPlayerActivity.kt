package org.y20k.escapepods.ui

import android.app.Activity
import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.view.View
import android.widget.Button
import org.y20k.escapepods.DownloadService
import org.y20k.escapepods.R
import org.y20k.escapepods.helpers.DialogAdd
import org.y20k.escapepods.helpers.FileHelper
import org.y20k.escapepods.helpers.Keys
import org.y20k.escapepods.helpers.LogHelper
import java.util.*


//class PodcastPlayerActivity : BaseActivity() {
class PodcastPlayerActivity : Activity() {

    /* Define log tag */
    private val TAG : String = LogHelper.makeLogTag(PodcastPlayerActivity::class.java)


    /* Main class variables */
    private lateinit var downloadService: DownloadService
    private var downloadServiceBound = false
    private val downloadProgressHandler = Handler()
    private var downloadIDs = longArrayOf(-1L)


    /* Implements onCreate */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // set layout
        setContentView(R.layout.activity_podcast_player)

        // get button and listen for clicks
        val addButton : Button = findViewById(R.id.add_button)
        addButton.setOnClickListener(View.OnClickListener {
            showAddDialog()
        })

        // get button and listen for clicks
        val downloadButton : Button = findViewById(R.id.domwload_button)
        downloadButton.setOnClickListener(View.OnClickListener {
            // just a test // todo remove
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


    /* Show add podcast dialog */
    private fun showAddDialog() {
        val dialogAdd : DialogAdd = DialogAdd(object : DialogAdd.AddDialogListener {
            override fun onFinish(textInput: String) {
                super.onFinish(textInput)
                LogHelper.v(TAG, "Text input from dialog: $textInput") // todo remove
            }
        })
        dialogAdd.show(this)
    }


    /* Start download in DownloadService */
    private fun startDownload(uris: Array<Uri>, type: Int): LongArray {

        // start download
        var downloadIDs = longArrayOf(-1L)
        if (downloadServiceBound) {
            downloadIDs = downloadService.download(this, uris, type)
        }

        // return download IDs
        return downloadIDs
    }


    /* Runnable that updates the download progress every second */
    private val downloadProgressRunnable = object : Runnable {
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
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            for (downloadID in downloadIDs) {
                if (downloadID == id) {
                    // get Uri if ID represented one of the enqueued downloads
                    val downloadManager = Objects.requireNonNull(getSystemService(Context.DOWNLOAD_SERVICE)) as DownloadManager
                    val uri = downloadManager.getUriForDownloadedFile(id)

                    // some tests // todo remove
                    val fileHelper = FileHelper()
                    LogHelper.i(TAG, "Download complete: " + fileHelper.getFileName(this@PodcastPlayerActivity, uri) +
                            " | " + fileHelper.getReadableByteCount(fileHelper.getFileSize(this@PodcastPlayerActivity, uri), true)) // todo remove
                }
            }

            // cancel periodic UI update if possible
            if (downloadService.activeDownloads.isEmpty()) {
                downloadProgressHandler.removeCallbacks(downloadProgressRunnable)
            }
        }
    }


    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private val downloadServiceConnection = object : ServiceConnection {

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