/*
 * PodcastPlayerActivity.kt
 * Implements the PodcastPlayerActivity class
 * PodcastPlayerActivity is Escapepod's main activity that hosts a list of podcast and a player sheet
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.*
import org.y20k.escapepods.adapter.CollectionAdapter
import org.y20k.escapepods.adapter.CollectionViewModel
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.core.Episode
import org.y20k.escapepods.dialogs.*
import org.y20k.escapepods.helpers.*
import org.y20k.escapepods.ui.PlayerState
import org.y20k.escapepods.xml.OpmlHelper
import kotlin.coroutines.CoroutineContext


/*
 * PodcastPlayerActivity class
 */
class PodcastPlayerActivity: AppCompatActivity(), CoroutineScope,
        AddPodcastDialog.AddPodcastDialogListener,
        CollectionAdapter.CollectionAdapterListener,
        MeteredNetworkDialog.MeteredNetworkDialogListener,
        OpmlImportDialog.OpmlImportDialogListener,
        YesNoDialog.YesNoDialogListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PodcastPlayerActivity::class.java)


    /* Main class variables */
    private lateinit var backgroundJob: Job
    private lateinit var mediaBrowser: MediaBrowserCompat
    private lateinit var collectionViewModel: CollectionViewModel
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var bottomSheet: ConstraintLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>
    private lateinit var playerViews: Group
    private lateinit var coverView: ImageView
    private lateinit var podcastNameView: TextView
    private lateinit var episodeTitleView: TextView
    private lateinit var playButtonView: ImageView
    private lateinit var sheetCoverView: ImageView
    private lateinit var sheetEpisodeTitleView: TextView
    private lateinit var sheetPlayButtonView: ImageView
    private lateinit var sheetSleepButtonView: ImageView
    private lateinit var collectionAdapter: CollectionAdapter
    private var collection: Collection = Collection()
    private var playerServiceConnected = false
    private var playerState: PlayerState = PlayerState()
    private var tempOpmlUriString: String = String()


    /* Overrides coroutineContext variable */
    override val coroutineContext: CoroutineContext get() = backgroundJob + Dispatchers.Main


    /* Overrides onCreate */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // try to recreate player state
        if (savedInstanceState != null) {
            with(savedInstanceState) {
                playerState = getParcelable(Keys.SAVED_INSTANCE_PLAYER_STATE) ?: PlayerState()
            }
        }

        // initialize background job
        backgroundJob = Job()

        // clear temp folder
        FileHelper.clearFolder(getExternalFilesDir(Keys.FOLDER_TEMP), 0)

        // create view model and observe changes in collection view model
        collectionViewModel = ViewModelProviders.of(this).get(CollectionViewModel::class.java)

        // create collection adapter
        collectionAdapter = CollectionAdapter(this)

        // Create MediaBrowserCompat
        mediaBrowser = MediaBrowserCompat(this, ComponentName(this, PlayerService::class.java), mediaBrowserConnectionCallback, null)

        // find views
        setContentView(R.layout.activity_podcast_player)
        recyclerView = findViewById(R.id.recyclerview_list)
        bottomSheet = findViewById(R.id.bottom_sheet)
        playerViews = findViewById(R.id.player_views)
        coverView = findViewById(R.id.player_podcast_cover)
        podcastNameView = findViewById(R.id.player_podcast_name)
        episodeTitleView = findViewById(R.id.player_episode_title)
        playButtonView = findViewById(R.id.player_play_button)
        sheetCoverView = findViewById(R.id.sheet_large_podcast_cover)
        sheetEpisodeTitleView = findViewById(R.id.sheet_episode_title)
        sheetPlayButtonView = findViewById(R.id.sheet_play_button)
        sheetSleepButtonView = findViewById(R.id.sleep_timer_button)

        // set up views
        initializeViews()

        // set up additional buttons
        setupAdditionalButtons()

        // start worker that periodically updates the podcast collection
        WorkerHelper.schedulePeriodicUpdateWorker()
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
        // assign volume buttons to music volume
        volumeControlStream = AudioManager.STREAM_MUSIC
        // handle start intent
        handleStartIntent()
        // setup player ui
        setupPlayerState()
    }


    /* Overrides onPause */
    override fun onPause() {
        super.onPause()
    }


    /* Overrides onStop */
    public override fun onStop() {
        super.onStop()
        // (see "stay in sync with the MediaSession")
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(mediaControllerCallback)
        mediaBrowser.disconnect()
        playerServiceConnected = false
    }


    /* Overrides onSaveInstanceState */
    override fun onSaveInstanceState(outState: Bundle?) {
        // try to save player state
        outState?.run {
            putParcelable(Keys.SAVED_INSTANCE_PLAYER_STATE, playerState)
        }
        super.onSaveInstanceState(outState)
    }


    /* Overrides onRequestPermissionsResult */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            Keys.PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission granted
                    if (tempOpmlUriString.isNotEmpty()) {
                        readOpmlFile(Uri.parse(tempOpmlUriString), false)
                    }
                } else {
                    // permission denied
                    Toast.makeText(this, getString(R.string.toast_message_error_missing_storage_permission), Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    /* Overrides onAddPodcastDialog from AddPodcastDialogListener */
    override fun onAddPodcastDialog(textInput: String) {
        super.onAddPodcastDialog(textInput)
        val podcastUrl = textInput.trim()
        if (CollectionHelper.isNewPodcast(podcastUrl, collection)) {
            downloadPodcastFeed(podcastUrl)
        } else {
            ErrorDialog().show(this, R.string.dialog_error_title_podcast_duplicate, R.string.dialog_error_message_podcast_duplicate, podcastUrl)
        }
    }


    /* Overrides onPlayButtonTapped from CollectionAdapterListener */
    override fun onPlayButtonTapped(mediaId: String, playbackState: Int) {
        playerState.episodeMediaId = mediaId
        playerState.playbackState = playbackState
        // setup ui
        updatePlayerViews(mediaId)
        // start / pause playback (playbackState = state BEFORE tapping the button)
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).transportControls.pause()
            else -> MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).transportControls.playFromMediaId(mediaId, null)
        }
    }


    /* Overrides onDownloadButtonTapped from CollectionAdapterListener */
    override fun onDownloadButtonTapped(episode: Episode) {
        downloadEpisode(episode)
    }


    /* Overrides onDeleteButtonTapped from CollectionAdapterListener */
    override fun onDeleteButtonTapped(episode: Episode) {
        val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_delete_episode)}\n\n- ${episode.title}"
        YesNoDialog(this@PodcastPlayerActivity as YesNoDialog.YesNoDialogListener).show(this@PodcastPlayerActivity, Keys.DIALOG_DELETE_EPISODE, R.string.dialog_yes_no_title_delete_episode, dialogMessage, R.string.dialog_yes_no_positive_button_delete_episode, dialogPayloadString = episode.getMediaId())
    }


    /* Overrides onMeteredNetworkDialog from MeteredNetworkDialogListener */
    override fun onMeteredNetworkDialog(dialogType: Int, payload: String) {
        super.onMeteredNetworkDialog(dialogType, payload)
        when (dialogType) {
            Keys.DIALOG_DOWNLOAD_EPISODE_WITHOUT_WIFI -> {
                Toast.makeText(this, getString(R.string.toast_message_downloading_episode), Toast.LENGTH_LONG).show()
                DownloadHelper.downloadEpisode(this, payload, true)
            }
        }
    }


    /* Overrides onOpmlImportDialog from OpmlImportDialogListener */
    override fun onOpmlImportDialog(feedUrls: Array<String>) {
        super.onOpmlImportDialog(feedUrls)
        downloadPodcastFeedsFromOpml(feedUrls)
    }


    /* Overrides onYesNoDialog from YesNoDialogListener */
    override fun onYesNoDialog(dialogType: Int, dialogResult: Boolean, dialogPayloadInt: Int, dialogPayloadString: String) {
        super.onYesNoDialog(dialogType, dialogResult, dialogPayloadInt, dialogPayloadString)
        when (dialogType) {
            // handle result of remove dialog
            Keys.DIALOG_REMOVE_PODCAST -> {
                when (dialogResult) {
                    // user tapped remove
                    true -> collectionAdapter.remove(this@PodcastPlayerActivity, dialogPayloadInt)
                    // user tapped cancel
                    false -> collectionAdapter.notifyItemChanged(dialogPayloadInt)
                }
            }
            Keys.DIALOG_DELETE_EPISODE -> {
                when (dialogResult) {
                    // user tapped delete
                    true -> deleteEpisode(dialogPayloadString)
                }
            }
        }
    }


    /* Sets up views and connects tap listeners - first run */
    private fun initializeViews() {
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
                YesNoDialog(this@PodcastPlayerActivity as YesNoDialog.YesNoDialogListener).show(this@PodcastPlayerActivity, Keys.DIALOG_REMOVE_PODCAST, R.string.dialog_yes_no_title_remove_podcast, dialogMessage, R.string.dialog_yes_no_positive_button_remove_podcast, dialogPayloadInt = adapterPosition)
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // show / hide the small player
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
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

        // listen for swipe to refresh event
        swipeRefreshLayout = findViewById(R.id.layout_swipe_refresh)
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

        // get reference to media controller
        val mediaController = MediaControllerCompat.getMediaController(this@PodcastPlayerActivity)

//        // set up the play button - to offer play or pause
//        setupPlayButtons(mediaController.playbackState.state)

        // main play/pause button
        playButtonView.setOnClickListener {
            when (mediaController.playbackState.state) {
                PlaybackStateCompat.STATE_PLAYING -> mediaController.transportControls.pause()
                else -> mediaController.transportControls.play()
            }
        }

        // bottom sheet play/pause button
        sheetPlayButtonView.setOnClickListener {
            when (mediaController.playbackState.state) {
                PlaybackStateCompat.STATE_PLAYING -> mediaController.transportControls.pause()
                else -> mediaController.transportControls.play()
            }
        }

        // display the initial state
        val metadata = mediaController.metadata
        val pbState = mediaController.playbackState

        // register a callback to stay in sync
        mediaController.registerCallback(mediaControllerCallback)
    }


    /* Set up additions user interface elements */
    private fun setupAdditionalButtons() {
        // set up sleep timer button - long press
        sheetSleepButtonView.setOnLongClickListener {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(50)
            // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
            NightModeHelper.switchMode(this)
            recreate()
            true
        }
    }


    /* Initiates the rotation animation of the play button  */
    private fun animatePlaybackButtonStateTransition(playbackState: Int) {
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> {
                val rotateClockwise = AnimationUtils.loadAnimation(this, R.anim.rotate_clockwise_slow)
                rotateClockwise.setAnimationListener(createAnimationListener(playbackState))
                when (bottomSheetBehavior.state) {
                    BottomSheetBehavior.STATE_COLLAPSED -> playButtonView.startAnimation(rotateClockwise)
                    BottomSheetBehavior.STATE_DRAGGING -> setupPlayButtons(playbackState)
                    BottomSheetBehavior.STATE_EXPANDED -> sheetPlayButtonView.startAnimation(rotateClockwise)
                    BottomSheetBehavior.STATE_HALF_EXPANDED ->  setupPlayButtons(playbackState)
                    BottomSheetBehavior.STATE_SETTLING -> setupPlayButtons(playbackState)
                    BottomSheetBehavior.STATE_HIDDEN -> setupPlayButtons(playbackState)
                }
            }

            else -> {
                val rotateCounterClockwise = AnimationUtils.loadAnimation(this, R.anim.rotate_counterclockwise_fast)
                rotateCounterClockwise.setAnimationListener(createAnimationListener(playbackState))
                when (bottomSheetBehavior.state) {
                    BottomSheetBehavior.STATE_COLLAPSED -> playButtonView.startAnimation(rotateCounterClockwise)
                    BottomSheetBehavior.STATE_DRAGGING -> setupPlayButtons(playbackState)
                    BottomSheetBehavior.STATE_EXPANDED -> sheetPlayButtonView.startAnimation(rotateCounterClockwise)
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


    /*  */
    private fun setupPlayerState() {
        setupPlayerVisibility(playerState.playbackState)
        setupPlayButtons(playerState.playbackState)
        if (playerState.episodeMediaId.isNotEmpty()) {
            updatePlayerViews(playerState.episodeMediaId)
        }
    }


    /* Set up play/pause buttons */
    private fun setupPlayButtons(playbackState: Int) {
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> {
                playButtonView.setImageResource(R.drawable.ic_pause_symbol_white_36dp)
                sheetPlayButtonView.setImageResource(R.drawable.ic_pause_symbol_white_36dp)
            }
            else -> {
                playButtonView.setImageResource(R.drawable.ic_play_symbol_white_36dp)
                sheetPlayButtonView.setImageResource(R.drawable.ic_play_symbol_white_36dp)
            }
        }
    }


    /* Sets visibility of player depending on playback state - hiding it when playback is stopped (not paused or playing) */
    private fun setupPlayerVisibility(playbackState: Int) {
        when (playbackState) {
            PlaybackStateCompat.STATE_STOPPED -> hidePlayer()
            PlaybackStateCompat.STATE_NONE -> hidePlayer()
            PlaybackStateCompat.STATE_ERROR -> hidePlayer()
            else -> showPlayer()
        }
    }


    /* Shows player */
    private fun showPlayer() {
        UiHelper.setViewMargins(this, swipeRefreshLayout, 0,0,0, Keys.BOTTOM_SHEET_PEEK_HEIGHT)
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED)
        }
    }


    /* Hides player */
    private fun hidePlayer() {
        UiHelper.setViewMargins(this, swipeRefreshLayout, 0,0,0, 0)
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN)
    }


    /* Updates player views with Episode */
    private fun updatePlayerViews(mediaId: String) {
        val episode: Episode = CollectionHelper.getEpisode(collection, mediaId)
        val coverUri = Uri.parse(episode.cover)
        coverView.setImageURI(coverUri)
        coverView.clipToOutline = true // apply rounded corner mask to covers
        coverView.contentDescription = "${getString(R.string.descr_player_podcast_cover)}: ${episode.podcastName}"
        podcastNameView.text = episode.podcastName
        episodeTitleView.text = episode.title
        sheetCoverView.setImageURI(coverUri)
        sheetCoverView.clipToOutline = true // apply rounded corner mask to covers
        sheetCoverView.contentDescription = "${getString(R.string.descr_expanded_player_podcast_cover)}: ${episode.podcastName}"
        sheetEpisodeTitleView.text = episode.title
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
        if (NetworkHelper.isConnectedToNetwork(this)) {
            Toast.makeText(this, getString(R.string.toast_message_updating_collection), Toast.LENGTH_LONG).show()
            DownloadHelper.updateCollection(this)
        } else {
            ErrorDialog().show(this, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }


    /* Deletes an episode */
    private fun deleteEpisode(mediaId: String) {
        // todo implement
        LogHelper.v(TAG, "Deleting: $mediaId") // todo remove
    }


    /* Updates podcast collection */
    private fun downloadEpisode(episode: Episode) {
        if (NetworkHelper.isConnectedToWifi(this)) {
            Toast.makeText(this, getString(R.string.toast_message_downloading_episode), Toast.LENGTH_LONG).show()
            DownloadHelper.downloadEpisode(this, episode.getMediaId(), true)
        } else if (NetworkHelper.isConnectedToCellular(this)) {
            MeteredNetworkDialog(this).show(this, Keys.DIALOG_DOWNLOAD_EPISODE_WITHOUT_WIFI, R.string.dialog_metered_download_episode_title, R.string.dialog_metered_download_episode_message, R.string.dialog_metered_download_episode_button_okay, episode.getMediaId())
        } else {
            ErrorDialog().show(this, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }


    /* Download podcast feed using async co-routine */
    private fun downloadPodcastFeed(feedUrl: String) {
        if (NetworkHelper.isConnectedToNetwork(this@PodcastPlayerActivity)) {
            launch {
                // detect content type on background thread
                val deferred: Deferred<NetworkHelper.ContentType> = async(Dispatchers.Default) { NetworkHelper.detectContentTypeSuspended(feedUrl) }
                // wait for result
                val contentType: NetworkHelper.ContentType = deferred.await()
                if ((contentType.type in Keys.MIME_TYPES_RSS) || (contentType.type in Keys.MIME_TYPES_ATOM)) {
                    Toast.makeText(this@PodcastPlayerActivity, getString(R.string.toast_message_adding_podcast), Toast.LENGTH_LONG).show()
                    DownloadHelper.downloadPodcasts(this@PodcastPlayerActivity, arrayOf(feedUrl))
                } else {
                    ErrorDialog().show(this@PodcastPlayerActivity, R.string.dialog_error_title_podcast_invalid_feed, R.string.dialog_error_message_podcast_invalid_feed, feedUrl)
                }
            }
        } else {
            ErrorDialog().show(this@PodcastPlayerActivity, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }


    /* Download podcast feed using async co-routine */
    private fun downloadPodcastFeedsFromOpml(feedUrls: Array<String>) {
        if (NetworkHelper.isConnectedToNetwork(this@PodcastPlayerActivity)) {
            val urls = CollectionHelper.removeDuplicates(collection, feedUrls)
            if (urls.isNotEmpty()) {
                Toast.makeText(this@PodcastPlayerActivity, getString(R.string.toast_message_adding_podcast), Toast.LENGTH_LONG).show()
                DownloadHelper.downloadPodcasts(this, CollectionHelper.removeDuplicates(collection, feedUrls))
            }
        } else {
            ErrorDialog().show(this@PodcastPlayerActivity, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }


    /* Read OPML file */
    private fun readOpmlFile(opmlUri: Uri, permissionCheckNeeded: Boolean)  {
        when (permissionCheckNeeded) {
            true -> {
                // permission check
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    // permission is not granted - request it
                    tempOpmlUriString = opmlUri.toString()
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), Keys.PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
                } else {
                    // permission granted - call readOpmlFile again
                    readOpmlFile(opmlUri, false)
                }
            }
            false -> {
                // read opml
                launch {
                    // readSuspended OPML on background thread
                    val deferred: Deferred<Array<String>> = async(Dispatchers.Default) { OpmlHelper().readSuspended(this@PodcastPlayerActivity, opmlUri) }
                    // wait for result and update collection
                    val feedUrls: Array<String> = deferred.await()
                    OpmlImportDialog(this@PodcastPlayerActivity).show(this@PodcastPlayerActivity, feedUrls)
                }
            }
        }
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
        val contentUri: Uri? = intent.data
        if (contentUri != null && contentUri.scheme != null) {
            when {
                // download new podcast
                contentUri.scheme.startsWith("http") -> downloadPodcastFeed(contentUri.toString()) // todo implement podcast download + dialog and stuff
                // readSuspended opml from content uri
                contentUri.scheme.startsWith("content") -> readOpmlFile(contentUri, false) // todo implement OPML readSuspended + dialog and stuff
                // readSuspended opml from file uri
                contentUri.scheme.startsWith("file") -> readOpmlFile(contentUri, true) // todo implement OPML readSuspended + dialog and stuff
            }
        }
    }


    /* Observe view model of podcast collection*/
    private fun observeCollectionViewModel() {
        collectionViewModel.collectionLiveData.observe(this, Observer<Collection> { it ->
            // update collection
            collection = it
            // toast podcast count - just a test // todo remove
            Toast.makeText(this, createCollectionInfoString(), Toast.LENGTH_LONG).show()

            // updates current episode in player views
            updatePlayerViews(playerState.episodeMediaId)
        })
    }


    /*
     * Defines callbacks for media browser service connection
     */
    private val mediaBrowserConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            // get the token for the MediaSession
            mediaBrowser.sessionToken.also { token ->
                // create a MediaControllerCompat
                val mediaController = MediaControllerCompat(this@PodcastPlayerActivity, token)
                // save the controller
                MediaControllerCompat.setMediaController(this@PodcastPlayerActivity, mediaController)
            }
            playerServiceConnected = true

            mediaBrowser.subscribe(Keys.MEDIA_ID_ROOT, mediaBrowserSubscriptionCallback)

            // finish building the UI
            buildPlaybackControls()

//            // show / hide player
//            setupPlayerVisibility(MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).playbackState.state)

            // begin looking for changes in collection
            observeCollectionViewModel()
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
     * End of callback
     */


    /*
     * Defines callbacks for media browser service subscription
     */
    private val mediaBrowserSubscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
            super.onChildrenLoaded(parentId, children)
        }

        override fun onError(parentId: String) {
            super.onError(parentId)
        }
    }
    /*
     * End of callback
     */


    /*
     * Defines callbacks for state changes of player service
     */
    private var mediaControllerCallback = object : MediaControllerCompat.Callback() {

        override fun onSessionReady() {
            super.onSessionReady()
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            LogHelper.d(TAG, "Metadata changed. Update UI.")
        }

        override fun onPlaybackStateChanged(playbackState: PlaybackStateCompat) {
            LogHelper.d(TAG, "Playback State changed. Update UI.")
            playerState.playbackState = playbackState.state
            animatePlaybackButtonStateTransition(playbackState.state)
            setupPlayerVisibility(playbackState.state)
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            mediaBrowserConnectionCallback.onConnectionSuspended()
        }
    }
    /*
     * End of callback
     */

}