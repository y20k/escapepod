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

import android.content.*
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.y20k.escapepods.adapter.CollectionAdapter
import org.y20k.escapepods.adapter.CollectionViewModel
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.dialogs.*
import org.y20k.escapepods.helpers.*
import org.y20k.escapepods.xml.OpmlHelper


/*
 * PodcastPlayerActivity class
 */
class PodcastPlayerActivity: AppCompatActivity(),
        AddPodcastDialog.AddPodcastDialogListener,
        MeteredNetworkDialog.MeteredNetworkDialogListener,
        OpmlImportDialog.OpmlImportDialogListener,
        YesNoDialog.YesNoDialogListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PodcastPlayerActivity::class.java)


    /* Main class variables */
    private lateinit var mediaBrowser: MediaBrowserCompat
    private lateinit var collectionViewModel: CollectionViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var bottomSheet: ConstraintLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    private lateinit var playerViews: Group
    private lateinit var playButton: ImageView
    private lateinit var cover: ImageView
    private lateinit var sheetPlayButton: ImageView
    private lateinit var sheetCover: ImageView
    private lateinit var collectionAdapter: CollectionAdapter
    private var collection: Collection = Collection()
    private var playerServiceConnected = false


    /* Overrides onCreate */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // clear temp folder
        FileHelper.clearFolder(getExternalFilesDir(Keys.FOLDER_TEMP), 0)

        // create view model and observe changes in collection view model
        collectionViewModel = ViewModelProviders.of(this).get(CollectionViewModel::class.java)
        observeCollectionViewModel()

        // create collection adapter
        collectionAdapter = CollectionAdapter(this)

        // Create MediaBrowserCompat
        mediaBrowser = MediaBrowserCompat(this, ComponentName(this, PlayerService::class.java), mediaBrowserConnectionCallbacks, null)

        // find views
        setContentView(R.layout.activity_podcast_player)
        recyclerView = findViewById(R.id.recyclerview_list)
        bottomSheet = findViewById(R.id.bottom_sheet)
        playerViews = findViewById(R.id.player_views)
        playButton = findViewById(R.id.player_play_button)
        cover = findViewById(R.id.player_podcast_cover)
        sheetPlayButton = findViewById(R.id.sheet_play_button)
        sheetCover = findViewById(R.id.sheet_large_podcast_cover)

        // set up views
        setUpViews()
    }


    /* Overrides onResume */
    public override fun onStart() {
        super.onStart()
        // connect to PlayerService
        mediaBrowser.connect()
    }



    /* Overrides onResume */
    override fun onResume() {
        super.onResume()
//        // bind to PlayerService
//        bindService(Intent(this, PlayerService::class.java), playerServiceConnection, Context.BIND_AUTO_CREATE)
        // assign volume buttons to music volume
        volumeControlStream = AudioManager.STREAM_MUSIC
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
//        // unbind PlayerService
//        unbindService(playerServiceConnection)
    }


    public override fun onStop() {
        super.onStop()
        // (see "stay in sync with the MediaSession")
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()
        playerServiceConnected = false
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


    /* Overrides onYesNoDialog from YesNoDialog */
    override fun onYesNoDialog(dialogType: Int, dialogResult: Boolean, dialogPayload: Int) {
        super.onYesNoDialog(dialogType, dialogResult, dialogPayload)
        when (dialogType) {
            // handle result of remove dialog
            Keys.DIALOG_REMOVE_PODCAST -> {
                when (dialogResult) {
                    // user tapped remove
                    true -> collectionAdapter.remove(this@PodcastPlayerActivity, dialogPayload)
                    // user tapped cancel
                    false -> collectionAdapter.notifyItemChanged(dialogPayload)
                }
            }
        }
    }


    /* Sets up views and connects tap listeners */
    private fun setUpViews() {
        // set up recycler view
        val layoutManager: LinearLayoutManager = object: LinearLayoutManager(this) {
            override fun supportsPredictiveItemAnimations(): Boolean {
                return true
            }
        }
        recyclerView.setLayoutManager(layoutManager)
        recyclerView.setItemAnimator(DefaultItemAnimator())
        recyclerView.setAdapter(collectionAdapter)
        // enable swipe to delete in recycler view
        val swipeHandler = object : UiHelper.SwipeToDeleteCallback(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // ask user
                val adapterPosition: Int = viewHolder.adapterPosition
                val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_remove_podcast)}\n\n- ${collection.podcasts[adapterPosition].name}"
                YesNoDialog(this@PodcastPlayerActivity as YesNoDialog.YesNoDialogListener).show(this@PodcastPlayerActivity, Keys.DIALOG_REMOVE_PODCAST, adapterPosition,  R.string.dialog_yes_no_title_remove_podcast, dialogMessage, R.string.dialog_yes_no_positive_button_remove_podcast)
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // show / hide the small player
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.setBottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(view: View, slideOffset: Float) {
                if (slideOffset < 0.25f) {
                    playerViews.setVisibility(View.VISIBLE);
                } else {
                    playerViews.setVisibility(View.GONE);
                }
            }
            override fun onStateChanged(view: View, state: Int) {
                when (state) {
                    BottomSheetBehavior.STATE_COLLAPSED -> playerViews.setVisibility(View.VISIBLE)
                    BottomSheetBehavior.STATE_DRAGGING -> Unit // do nothing
                    BottomSheetBehavior.STATE_EXPANDED -> playerViews.setVisibility(View.GONE)
                    BottomSheetBehavior.STATE_HALF_EXPANDED ->  Unit // do nothing
                    BottomSheetBehavior.STATE_SETTLING -> Unit // do nothing
                    BottomSheetBehavior.STATE_HIDDEN -> playerViews.setVisibility(View.VISIBLE)
                }
            }
        })
        // toggle collapsed state on tap
        bottomSheet.setOnClickListener {
            when (bottomSheetBehavior.state) {
                BottomSheetBehavior.STATE_COLLAPSED -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                else -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        // apply rounded corner mask to covers
        cover.setClipToOutline(true)
        sheetCover.setClipToOutline(true)

        // listen for swipe to refresh event
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


    /* Builds playback controls - used after connected to player service */
    private fun buildPlaybackControls() {
        val mediaController: MediaControllerCompat = MediaControllerCompat.getMediaController(this@PodcastPlayerActivity)
        val playbackState = mediaController.playbackState

        // set up the play button - to offer play or pause
        setupPlayButtons(playbackState.state)

        // main play/pause button
        playButton.setOnClickListener {
            LogHelper.v(TAG, "Tap on main play button registered") // todo remove
            when (mediaController.playbackState.state) {
                PlaybackStateCompat.STATE_PLAYING -> mediaController.transportControls.pause()
                else -> mediaController.transportControls.play()
            }
        }

        // bottom sheet play/pause button
        sheetPlayButton.setOnClickListener {
            LogHelper.v(TAG, "Tap on play button in sheet registered") // todo remove
            LogHelper.v(TAG, "Tap on main play button registered") // todo remove
            when (mediaController.playbackState.state) {
                PlaybackStateCompat.STATE_PLAYING -> mediaController.transportControls.pause()
                else -> mediaController.transportControls.play()
            }
        }

        // display the initial state
        val metadata = mediaController.metadata
        val pbState = mediaController.playbackState

        // register a callback to stay in sync
        mediaController.registerCallback(controllerCallback)
    }


    /* Initiates the rotation animation of the play button  */
    private fun animatePlaybackButtonStateTransition(playbackState: Int) {
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> {
                val rotateClockwise = AnimationUtils.loadAnimation(this, R.anim.rotate_clockwise_slow)
                rotateClockwise.setAnimationListener(createAnimationListener(playbackState))
                when (bottomSheetBehavior.state) {
                    BottomSheetBehavior.STATE_COLLAPSED -> playButton.startAnimation(rotateClockwise)
                    BottomSheetBehavior.STATE_DRAGGING -> setupPlayButtons(playbackState)
                    BottomSheetBehavior.STATE_EXPANDED -> sheetPlayButton.startAnimation(rotateClockwise)
                    BottomSheetBehavior.STATE_HALF_EXPANDED ->  setupPlayButtons(playbackState)
                    BottomSheetBehavior.STATE_SETTLING -> setupPlayButtons(playbackState)
                    BottomSheetBehavior.STATE_HIDDEN -> setupPlayButtons(playbackState)
                }
            }

            else -> {
                val rotateCounterClockwise = AnimationUtils.loadAnimation(this, R.anim.rotate_counterclockwise_fast)
                rotateCounterClockwise.setAnimationListener(createAnimationListener(playbackState))
                when (bottomSheetBehavior.state) {
                    BottomSheetBehavior.STATE_COLLAPSED -> playButton.startAnimation(rotateCounterClockwise)
                    BottomSheetBehavior.STATE_DRAGGING -> setupPlayButtons(playbackState)
                    BottomSheetBehavior.STATE_EXPANDED -> sheetPlayButton.startAnimation(rotateCounterClockwise)
                    BottomSheetBehavior.STATE_HALF_EXPANDED ->  setupPlayButtons(playbackState)
                    BottomSheetBehavior.STATE_SETTLING -> setupPlayButtons(playbackState)
                    BottomSheetBehavior.STATE_HIDDEN -> setupPlayButtons(playbackState)
                }
            }

        }
    }


    /* Creates AnimationListener for play button */
    private fun createAnimationListener(playbackState: Int): Animation.AnimationListener {
        return object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                // set up button symbol and playback indicator afterwards
                setupPlayButtons(playbackState)
            }
            override fun onAnimationRepeat(animation: Animation) {}
        }
    }


    /* Set up play/pause buttons */
    private fun setupPlayButtons(playbackState: Int ) {
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> {
                playButton.setImageResource(R.drawable.ic_pause_symbol_white_36dp)
                sheetPlayButton.setImageResource(R.drawable.ic_pause_symbol_white_36dp)
            }
            else -> {
                playButton.setImageResource(R.drawable.ic_play_symbol_white_36dp)
                sheetPlayButton.setImageResource(R.drawable.ic_play_symbol_white_36dp)
            }
        }
    }


    /* Updates user interface */
    private fun updateUserInterface() {
        // update podcast counter - just a test // todo remove
        val podcastCounter: TextView = findViewById(R.id.player_episode_name)
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
                Keys.ACTION_SHOW_PLAYER -> handleShowPlayer()
                Intent.ACTION_VIEW -> handleViewIntent()
            }
        }
        // clear intent action to prevent double calls
        intent.setAction("")
    }


    /* Handles ACTION_SHOW_PLAYER request from notification */
    private fun handleShowPlayer() {
        LogHelper.i(TAG, "Tap on notification registered.")
        // todo implement
    }


    /* Handles ACTION_VIEW request to add Podcast or import OPML */
    private fun handleViewIntent() {
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
//            // hand over collection to player service
//            if (playerServiceConnected && collection.podcasts.isNotEmpty()) {
//                playerService.updateCollection(collection)
//            }
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


    /*
     * Defines callbacks for media browser service connection
     */
    private val mediaBrowserConnectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            // get the token for the MediaSession
            mediaBrowser.sessionToken.also { token ->
                // create a MediaControllerCompat
                val mediaController = MediaControllerCompat(this@PodcastPlayerActivity, token)
                // save the controller
                MediaControllerCompat.setMediaController(this@PodcastPlayerActivity, mediaController)
            }
            playerServiceConnected = true
            // finish building the UI
            buildPlaybackControls()
        }

        override fun onConnectionSuspended() {
            playerServiceConnected = false
            // service has crashed. Disable transport controls until it automatically reconnects
        }

        override fun onConnectionFailed() {
            playerServiceConnected = false
            // service has refused our connection
        }
    }


    /*
     * Defines callbacks for media browser service subscription
     */
    private val mediaBrowserSubscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
            super.onChildrenLoaded(parentId, children)
            // todo get collection from here and fill recycler adapter - first load children in service!!
        }

        override fun onError(parentId: String) {
            super.onError(parentId)
        }
    }


    /*
     * Defines callbacks for state changes of player service
     */
    private var controllerCallback = object : MediaControllerCompat.Callback() {

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            LogHelper.d(TAG, "Metadata changed. Update UI.")
        }

        override fun onPlaybackStateChanged(playbackState: PlaybackStateCompat) {
            LogHelper.d(TAG, "Playback State changed. Update UI.")
            animatePlaybackButtonStateTransition(playbackState.state)
        }
    }



//    /*
//     * Defines callbacks for service binding, passed to bindService()
//     */
//    private val playerServiceConnection = object: ServiceConnection {
//
//        override fun onServiceConnected(className: ComponentName, service: IBinder) {
//            // get service from binder
//            val binder = service as PlayerService.LocalBinder
//            playerService = binder.getService()
//            playerService.initialize(this@PodcastPlayerActivity, false)
//            playerServiceConnected = true
//            // hand over collection to service
//            if (collection.podcasts.isNotEmpty()) {
//                playerService.updateCollection(collection)
//            }
//        }
//
//        override fun onServiceDisconnected(arg0: ComponentName) {
//            playerServiceConnected = false
//        }
//    }

}