/*
 * PlayerFragment.kt
 * Implements the PlayerFragment class
 * PlayerFragment is the fragment that hosts Escapepod's list of podcasts and a player sheet
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import org.y20k.escapepod.collection.CollectionAdapter
import org.y20k.escapepod.collection.CollectionViewModel
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.dialogs.ErrorDialog
import org.y20k.escapepod.dialogs.FindPodcastDialog
import org.y20k.escapepod.dialogs.OpmlImportDialog
import org.y20k.escapepod.dialogs.YesNoDialog
import org.y20k.escapepod.extensions.*
import org.y20k.escapepod.helpers.*
import org.y20k.escapepod.ui.LayoutHolder
import org.y20k.escapepod.ui.PlayerState
import org.y20k.escapepod.xml.OpmlHelper


/*
 * PlayerFragment class
 */
class PlayerFragment: Fragment(),
        SharedPreferences.OnSharedPreferenceChangeListener,
        FindPodcastDialog.FindPodcastDialogListener,
        CollectionAdapter.CollectionAdapterListener,
        OpmlImportDialog.OpmlImportDialogListener,
        YesNoDialog.YesNoDialogListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PlayerFragment::class.java)


    /* Main class variables */
    private lateinit var collectionDatabase: CollectionDatabase
    private lateinit var collectionViewModel: CollectionViewModel
    private lateinit var layout: LayoutHolder
    private lateinit var collectionAdapter: CollectionAdapter
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController?
        get() = if (controllerFuture.isDone) controllerFuture.get() else null // defines the Getter for the MediaController
    private var playerState: PlayerState = PlayerState()
    private var listLayoutState: Parcelable? = null
    private val handler: Handler = Handler(Looper.getMainLooper())


    /* Overrides onCreate from Fragment */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // handle back tap/gesture
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // minimize player sheet - or if already minimized let activity handle back
                if (isEnabled && this@PlayerFragment::layout.isInitialized && !layout.minimizePlayerIfExpanded()) {
                    isEnabled = false
                    activity?.onBackPressed()
                }
            }
        })

        // load player state
        playerState = PreferencesHelper.loadPlayerState()

        // create view model and observe changes in collection view model
        collectionViewModel = ViewModelProvider(this).get(CollectionViewModel::class.java)

        // get instance of database
        collectionDatabase = CollectionDatabase.getInstance(activity as Context)

        // create collection adapter
        collectionAdapter = CollectionAdapter(activity as Context, collectionDatabase, this as CollectionAdapter.CollectionAdapterListener)

        // start worker that periodically updates the podcast collection
        WorkerHelper.schedulePeriodicUpdateWorker(activity as Context)

    }


    /* Overrides onCreate from Fragment*/
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // find views and set them up
        val rootView: View = inflater.inflate(R.layout.fragment_podcast_player, container, false)
        layout = LayoutHolder(rootView, collectionDatabase)
        initializeViews()
        // hide action bar
        (activity as AppCompatActivity).supportActionBar?.hide()
        return rootView
    }


    /* Overrides onStart from Fragment */
    override fun onStart() {
        super.onStart()
        // initialize MediaController - connect to PlayerService
        initializeController()
    }


    /* Overrides onSaveInstanceState from Fragment */
    override fun onSaveInstanceState(outState: Bundle) {
        if (this::layout.isInitialized) {
            // save current state of podcast list
            listLayoutState = layout.layoutManager.onSaveInstanceState()
            outState.putParcelable(Keys.KEY_SAVE_INSTANCE_STATE_PODCAST_LIST, listLayoutState)
        }
        // always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(outState)
    }


    /* Overrides onRestoreInstanceState from Activity */
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        // always call the superclass so it can restore the view hierarchy
        super.onActivityCreated(savedInstanceState)        // restore state of podcast list
        listLayoutState = savedInstanceState?.getParcelable(Keys.KEY_SAVE_INSTANCE_STATE_PODCAST_LIST)
    }


    /* Overrides onResume from Fragment */
    override fun onResume() {
        super.onResume()
        // assign volume buttons to music volume
        activity?.volumeControlStream = AudioManager.STREAM_MUSIC
        // load player state
        playerState = PreferencesHelper.loadPlayerState()
        // recreate player ui
        CoroutineScope(IO).launch {
            // get current and up-next episode
            val currentEpisode = collectionDatabase.episodeDao().findByMediaId(playerState.currentEpisodeMediaId)
            val upNextEpisode = collectionDatabase.episodeDao().findByMediaId(playerState.upNextEpisodeMediaId)
            // setup player and user interface
            withContext(Main) {
                setupPlaybackSheet(currentEpisode, upNextEpisode)
                setupList()
            }
        }
        // handle navigation arguments
        handleNavigationArguments()
        // handle start intent - if started via tap on rss link
        handleStartIntent()
        // start watching for changes in shared preferences
        PreferencesHelper.registerPreferenceChangeListener(this as SharedPreferences.OnSharedPreferenceChangeListener)
    }


    /* Overrides onPause from Fragment */
    override fun onPause() {
        super.onPause()
        // save player state
        PreferencesHelper.savePlayerState(playerState)
        // stop receiving playback progress updates
        handler.removeCallbacks(periodicProgressUpdateRequestRunnable)
        // stop watching for changes in shared preferences
        PreferencesHelper.unregisterPreferenceChangeListener(this as SharedPreferences.OnSharedPreferenceChangeListener)
    }


    /* Overrides onStop from Fragment */
    override fun onStop() {
        super.onStop()
        // release MediaController - cut connection to PlayerService
        releaseController()
    }


    /* Overrides onSharedPreferenceChanged from OnSharedPreferenceChangeListener */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            Keys.PREF_ACTIVE_DOWNLOADS -> {
                layout.toggleDownloadProgressIndicator()
            }
            Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID -> {
                val mediaId: String = sharedPreferences?.getString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, String()) ?: String()
                if (playerState.currentEpisodeMediaId != mediaId) {
                    CoroutineScope(IO).launch {
                        playerState.currentEpisodeMediaId = mediaId
                        // todo prepare player and update ui with new episode
//                        currentEpisode = collectionDatabase.episodeDao().findByMediaId(mediaId)
//                        withContext(Main) { layout.updatePlayerViews(activity as Context, currentEpisode) } // todo check if onSharedPreferenceChanged can be triggered before layout has been initialized
                    }
                }
            }
            Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID -> {
                CoroutineScope(IO).launch {
                    val mediaId: String = sharedPreferences?.getString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, String()) ?: String()
                    playerState.upNextEpisodeMediaId = mediaId
                    // todo prepare player and update ui with next episode
//                    upNextEpisode = collectionDatabase.episodeDao().findByMediaId(mediaId)
//                    withContext(Main) { layout.updateUpNextViews(upNextEpisode) } // todo check if onSharedPreferenceChanged can be triggered before layout has been initialized
                }
            }
        }
    }


    /* Overrides onPlayButtonTapped from CollectionAdapterListener */
    override fun onPlayButtonTapped(selectedEpisode: Episode, streaming: Boolean) {
        // store streaming state
        playerState.streaming = streaming

        val selectedEpisodeMediaId: String = selectedEpisode.mediaId
        when (controller?.isPlaying) {
            // CASE: Playback is active
            true -> {
                when (selectedEpisodeMediaId) {
                    // CASE: Selected episode is currently in player
                    playerState.currentEpisodeMediaId -> {
                        // pause playback
                        controller?.pause()
                    }
                    // CASE: selected episode is already in the up next queue
                    playerState.upNextEpisodeMediaId -> {
                        // start playback of up next
                        startPlayback(selectedEpisode)
                        // clear up next
                        updateUpNext(String())
                    }
                    // CASE: tapped on episode that is not the current one and not the up next one
                    else -> {
                        // ask user: playback or add to Up Next
                        val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_add_up_next)}\n\n- ${selectedEpisode.title}"
                        YesNoDialog(this@PlayerFragment as YesNoDialog.YesNoDialogListener).show(context = activity as Context, type = Keys.DIALOG_ADD_UP_NEXT, messageString = dialogMessage, yesButton = R.string.dialog_yes_no_positive_button_add_up_next, noButton = R.string.dialog_yes_no_negative_button_add_up_next, payloadString = selectedEpisode.mediaId)
                    }
                }

            }
            // CASE: Playback is NOT active
            else -> {
                // start playback
                startPlayback(selectedEpisode)
            }
        }
    }


    /* Overrides onMarkListenedButtonTapped from CollectionAdapterListener */
    override fun onMarkListenedButtonTapped(selectedEpisode: Episode) {
        if (playerState.currentEpisodeMediaId == selectedEpisode.mediaId) {
            controller?.pause()
        }
        val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_mark_episode_played)}\n\n- ${selectedEpisode.title}"
        YesNoDialog(this@PlayerFragment as YesNoDialog.YesNoDialogListener).show(context = activity as Context, type = Keys.DIALOG_MARK_EPISODE_PLAYED, messageString = dialogMessage, yesButton = R.string.dialog_yes_no_positive_button_mark_episode_played, noButton = R.string.dialog_yes_no_negative_button_cancel, payloadString = selectedEpisode.mediaId)
    }


    /* Overrides onDownloadButtonTapped from CollectionAdapterListener */
    override fun onDownloadButtonTapped(selectedEpisode: Episode) {
        downloadEpisode(selectedEpisode.mediaId)
    }


    /* Overrides onDeleteButtonTapped from CollectionAdapterListener */
    override fun onDeleteButtonTapped(selectedEpisode: Episode) {
        if (playerState.currentEpisodeMediaId == selectedEpisode.mediaId) controller?.pause()
        val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_delete_episode)}\n\n- ${selectedEpisode.title}"
        YesNoDialog(this@PlayerFragment as YesNoDialog.YesNoDialogListener).show(context = activity as Context, type = Keys.DIALOG_DELETE_EPISODE, messageString = dialogMessage, yesButton = R.string.dialog_yes_no_positive_button_delete_episode, payloadString = selectedEpisode.mediaId)
    }


    /* Overrides onAddNewButtonTapped from CollectionAdapterListener */
    override fun onAddNewButtonTapped() {
        FindPodcastDialog(activity as Activity, this as FindPodcastDialog.FindPodcastDialogListener).show()
    }


    /* Overrides onOpmlImportDialog from OpmlImportDialogListener */
    override fun onOpmlImportDialog(feedUrls: Array<String>) {
        super.onOpmlImportDialog(feedUrls)
        downloadPodcastFeedsFromOpml(feedUrls)
    }


    /* Overrides onFindPodcastDialog from FindPodcastDialog */
    override fun onFindPodcastDialog(remotePodcastFeedLocation: String) {
        super.onFindPodcastDialog(remotePodcastFeedLocation)
        // try to add podcast
        val podcastUrl: String = remotePodcastFeedLocation.trim()
        downloadPodcastFeed(podcastUrl)
    }


    /* Overrides onYesNoDialog from YesNoDialogListener */
    override fun onYesNoDialog(type: Int, dialogResult: Boolean, payload: Int, payloadString: String, dialogCancelled: Boolean) {
        super.onYesNoDialog(type, dialogResult, payload, payloadString, dialogCancelled)
        when (type) {
            Keys.DIALOG_UPDATE_COLLECTION -> {
                when (dialogResult) {
                    // user tapped update collection
                    true -> {
                        if (CollectionHelper.hasEnoughTimePassedSinceLastUpdate()) {
                            updateCollection()
                        } else {
                            Toast.makeText(activity as Context, R.string.toast_message_collection_update_not_necessary, Toast.LENGTH_LONG).show()
                        }
                    }
                    // user tapped cancel - for dev purposes: refresh the podcast list view // todo check if that can be helpful
                    false -> {
                        // collectionAdapter.notifyDataSetChanged() // can be removed
                    }
                }
            }
            // handle result of remove dialog
            Keys.DIALOG_REMOVE_PODCAST -> {
                when (dialogResult) {
                    // user tapped remove podcast
                    true -> collectionAdapter.removePodcast(activity as Context, payload)
                    // user tapped cancel
                    false -> collectionAdapter.notifyItemChanged(payload)
                }
            }
            Keys.DIALOG_DELETE_EPISODE -> {
                when (dialogResult) {
                    // user tapped delete episode
                    true -> collectionAdapter.deleteEpisode(activity as Context, payloadString)
                }
            }
            Keys.DIALOG_MARK_EPISODE_PLAYED -> {
                when (dialogResult) {
                    // user tapped: mark episode played
                    true -> collectionAdapter.markEpisodePlayed(activity as Context, payloadString)
                }
            }
            Keys.DIALOG_ADD_UP_NEXT -> {
                when (dialogResult) {
                    // user tapped: start playback
                    // true -> togglePlayback(true, payloadString) // todo implement
                    // user tapped: add to up next (only if dialog has not been cancelled)
                    false -> if (!dialogCancelled) updateUpNext(payloadString)
                }
            }
            Keys.DIALOG_DOWNLOAD_EPISODE_WITHOUT_WIFI -> {
                when (dialogResult) {
                    true -> {
                        Toast.makeText(activity as Context, R.string.toast_message_downloading_episode, Toast.LENGTH_LONG).show()
                        DownloadHelper.downloadEpisode(activity as Context, payloadString, ignoreWifiRestriction = true, manuallyDownloaded = true)
                    }
                }
            }
        }
    }


    /* Initializes the MediaController - handles connection to PlayerService under the hood */
    private fun initializeController() {
        controllerFuture = MediaController.Builder(activity as Context, SessionToken(activity as Context, ComponentName(activity as Context, PlayerService::class.java))).buildAsync()
        controllerFuture.addListener({ setController() }, MoreExecutors.directExecutor())
    }


    /* Releases MediaController */
    private fun releaseController() {
        MediaController.releaseFuture(controllerFuture)
    }


    /* Sets up the MediaController */
    private fun setController() {
        val controller = this.controller ?: return
        controller.addListener(playerListener)
    }


    /* Sets up views and connects tap listeners - first run */
    private fun initializeViews() {
        // set adapter data source
        layout.recyclerView.adapter = collectionAdapter

        // enable swipe to delete
        val swipeHandler = object : UiHelper.SwipeToDeleteCallback(activity as Context) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val adapterPosition: Int = viewHolder.adapterPosition
//                val adapterPosition: Int = viewHolder.bindingAdapterPosition
                val podcast = collectionAdapter.getPodcast(adapterPosition)
                // stop playback, if necessary
                podcast.episodes.forEach { if (it.data.mediaId == playerState.currentEpisodeMediaId) controller?.pause() }
                // ask user
                val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_remove_podcast)}\n\n- ${podcast.data.name}"
                YesNoDialog(this@PlayerFragment as YesNoDialog.YesNoDialogListener).show(context = activity as Context, type = Keys.DIALOG_REMOVE_PODCAST, messageString = dialogMessage, yesButton = R.string.dialog_yes_no_positive_button_remove_podcast, payload = adapterPosition)
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(layout.recyclerView)

        // enable for swipe to refresh
        layout.swipeRefreshLayout.setOnRefreshListener {
            // ask user to confirm update
            YesNoDialog(this@PlayerFragment as YesNoDialog.YesNoDialogListener).show(context = activity as Context, type = Keys.DIALOG_UPDATE_COLLECTION, message = R.string.dialog_yes_no_message_update_collection, yesButton = R.string.dialog_yes_no_positive_button_update_collection)
            layout.swipeRefreshLayout.isRefreshing = false
        }

        // set up sleep timer start button
        layout.sheetSleepTimerStartButtonView.setOnClickListener {
            when (controller?.isPlaying) {
                true -> controller?.startSleepTimer()
                else -> Toast.makeText(activity as Context, R.string.toast_message_sleep_timer_unable_to_start, Toast.LENGTH_LONG).show()
            }
        }

        // set up sleep timer cancel button
        layout.sheetSleepTimerCancelButtonView.setOnClickListener {
            controller?.cancelSleepTimer()
        }

        // set up the debug log toggle switch
        layout.sheetDebugToggleButtonView.setOnClickListener {
            LogHelper.toggleDebugLogFileCreation(activity as Context)
        }
    }


    /* Sets up the playback views */
    private fun setupPlaybackSheet(currentEpisode: Episode?, upNextEpisode: Episode?) {
        if (currentEpisode != null) {
            layout.togglePlayButtons(playerState.isPlaying)
            layout.updatePlayerViews(activity as Context, currentEpisode)
            layout.updatePlaybackSpeedView(activity as Context, playerState.playbackSpeed)
            layout.updateUpNextViews(upNextEpisode)
            setupPlaybackControls(currentEpisode, upNextEpisode)
            layout.showPlayer(activity as Context)
        } else {
            layout.hidePlayer(activity as Context)
        }
    }


    /* Builds playback controls */
    @SuppressLint("ClickableViewAccessibility") // it is probably okay to suppress this warning - the OnTouchListener on the time played view does only toggle the time duration / remaining display
    private fun setupPlaybackControls(currentEpisode: Episode?, upNextEpisode: Episode?) {

        // main play/pause button and bottom sheet play/pause button
        if (currentEpisode != null) {
            layout.playButtonView.setOnClickListener { onPlayButtonTapped(currentEpisode, playerState.streaming) }
            layout.sheetPlayButtonView.setOnClickListener { onPlayButtonTapped(currentEpisode, playerState.streaming) }
        }

        // bottom sheet start button for Up Next queue
        if (upNextEpisode != null) {
            layout.sheetUpNextName.setOnClickListener {
                onPlayButtonTapped(upNextEpisode, playerState.streaming) // todo implement as skip to next
                Toast.makeText(activity as Context, R.string.toast_message_up_next_start_playback, Toast.LENGTH_LONG).show()
            }
        }

        // bottom sheet clear button for Up Next queue
        layout.sheetUpNextClearButton.setOnClickListener {
            // clear up next
            updateUpNext(String())
            Toast.makeText(activity as Context, R.string.toast_message_up_next_removed_episode, Toast.LENGTH_LONG).show()
        }

        // bottom sheet skip back button
        layout.sheetSkipBackButtonView.setOnClickListener {
            when (playerState.isPlaying) {
                true -> controller?.skipBack()
                false -> Toast.makeText(activity as Context, R.string.toast_message_skipping_disabled, Toast.LENGTH_LONG).show()
            }
        }

        // bottom sheet skip forward button
        layout.sheetSkipForwardButtonView.setOnClickListener {
            when (playerState.isPlaying) {
                true -> controller?.skipForward()
                false -> Toast.makeText(activity as Context, R.string.toast_message_skipping_disabled, Toast.LENGTH_LONG).show()
            }
        }

        // bottom sheet playback progress bar
        layout.sheetProgressBarView.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            var position: Int = 0
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                position = progress
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (layout.sheetProgressBarView.max > 0L) controller?.seekTo(position.toLong())
            }
        })

        // bottom sheet time played display
        layout.sheetTimePlayedView.setOnTouchListener { view, motionEvent ->
            view.performClick()
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    // show time remaining while touching the time played view
                    layout.displayTimeRemaining = true
                    updateProgressBar()
                }
                MotionEvent.ACTION_UP -> {
                    // show episode duration when not touching the time played view anymore
                    layout.displayTimeRemaining = false
                    val duration = DateTimeHelper.convertToMinutesAndSeconds(currentEpisode?.duration ?: 0L)
                    layout.sheetDurationView.text = duration
                    layout.sheetDurationView.contentDescription = "${getString(R.string.descr_expanded_episode_length)}: $duration"
                }
                else -> return@setOnTouchListener false
            }
            return@setOnTouchListener true
        }

        // bottom sheet playback speed button
        layout.sheetPlaybackSpeedButtonView.setOnClickListener {
            // change playback speed
            val newSpeed: Float = this@PlayerFragment.controller?.changePlaybackSpeed() ?: 1f
            // update state and UI
            playerState.playbackSpeed = newSpeed
            layout.updatePlaybackSpeedView(activity as Context, playerState.playbackSpeed)
        }
        layout.sheetPlaybackSpeedButtonView.setOnLongClickListener {
            if (playerState.playbackSpeed != 1f) {
                val v = activity?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(50)
                // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
                Toast.makeText(activity as Context, R.string.toast_message_playback_speed_reset, Toast.LENGTH_LONG).show()
                // reset playback speed
                val newSpeed: Float = this@PlayerFragment.controller?.resetPlaybackSpeed() ?: 1f
                // update state and UI
                playerState.playbackSpeed = newSpeed
                layout.updatePlaybackSpeedView(activity as Context, playerState.playbackSpeed)
            }
            return@setOnLongClickListener true
        }
    }


    /* Sets up state of list podcast list */
    private fun setupList() {
        layout.toggleDownloadProgressIndicator()
        if (listLayoutState != null) {
            layout.layoutManager.onRestoreInstanceState(listLayoutState)
        }
    }


    /* Start playback */
    private fun startPlayback(selectedEpisode: Episode) {
        val selectedEpisodeMediaId: String = selectedEpisode.mediaId
        when (selectedEpisodeMediaId) {
            // CASE: Episode is already in player (playback is probably paused)
            controller?.currentMediaId() -> {
                controller?.play()
            }
            // CASE: New episode was selected
            else -> {
                // save state
                playerState.currentEpisodeMediaId = selectedEpisode.mediaId
                // update user interface
                layout.updatePlayerViews(activity as Context, selectedEpisode)
                // update buttons // todo move in own function
                layout.playButtonView.setOnClickListener { onPlayButtonTapped(selectedEpisode, playerState.streaming) }
                layout.sheetPlayButtonView.setOnClickListener { onPlayButtonTapped(selectedEpisode, playerState.streaming) }
                // get playback position and start playback
                CoroutineScope(IO).launch {
                    val position: Long = collectionDatabase.episodeDao().getPlaybackPosition(selectedEpisodeMediaId)
                    LogHelper.e(TAG, "PlayerFragment.startPlayback() -> position =>  $position") // todo remove
                    withContext(Main) { controller?.play(Episode(selectedEpisode, playbackPosition = position), playerState.streaming) }
                }
            }
        }
    }


    /* Updates the Up Next queue */
    private fun updateUpNext(upNextEpisodeMediaId: String) {
        PreferencesHelper.saveUpNextMediaId(upNextEpisodeMediaId)
        if (upNextEpisodeMediaId.isNotEmpty()) {
            Toast.makeText(activity as Context, R.string.toast_message_up_next_added_episode, Toast.LENGTH_LONG).show()
        }
    }


    /* Updates the progress bar */
    private fun updateProgressBar() {
        // update progress bar
        layout.updateProgressbar(activity as Context, controller?.currentPosition ?: 0L)
    }


    /* Updates podcast collection */
    private fun updateCollection() {
        if (NetworkHelper.isConnectedToNetwork(activity as Context)) {
            Toast.makeText(activity as Context, R.string.toast_message_updating_collection, Toast.LENGTH_LONG).show()
            DownloadHelper.updateCollection(activity as Context)
        } else {
            ErrorDialog().show(activity as Context, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }


    /* Download an episode podcast collection */
    private fun downloadEpisode(episodeMediaId: String) {
        if (NetworkHelper.isConnectedToWifi(activity as Context)) {
            Toast.makeText(activity as Context, R.string.toast_message_downloading_episode, Toast.LENGTH_LONG).show()
            DownloadHelper.downloadEpisode(activity as Context, episodeMediaId, ignoreWifiRestriction = true, manuallyDownloaded = true)
        } else if (NetworkHelper.isConnectedToCellular(activity as Context) && PreferencesHelper.loadEpisodeDownloadOverMobile()) {
            Toast.makeText(activity as Context, R.string.toast_message_downloading_episode, Toast.LENGTH_LONG).show()
            DownloadHelper.downloadEpisode(activity as Context, episodeMediaId, ignoreWifiRestriction = true, manuallyDownloaded = true)
        } else if (NetworkHelper.isConnectedToCellular(activity as Context)) {
            YesNoDialog(this).show(context = activity as Context, type = Keys.DIALOG_DOWNLOAD_EPISODE_WITHOUT_WIFI, message = R.string.dialog_yes_no_message_non_wifi_download, yesButton = R.string.dialog_yes_no_positive_button_non_wifi_download, payloadString = episodeMediaId)
        } else if (NetworkHelper.isConnectedToVpn(activity as Context))  {
            YesNoDialog(this).show(context = activity as Context, type = Keys.DIALOG_DOWNLOAD_EPISODE_WITHOUT_WIFI, message = R.string.dialog_yes_no_message_vpn_download, yesButton = R.string.dialog_yes_no_positive_button_vpn_download, payloadString = episodeMediaId)
        } else {
            ErrorDialog().show(activity as Context, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }


    /* Download podcast feed using async co-routine */
    private fun downloadPodcastFeed(feedUrl: String) {
        if (!feedUrl.startsWith("http")) {
            ErrorDialog().show(activity as Context, R.string.dialog_error_title_podcast_invalid_feed, R.string.dialog_error_message_podcast_invalid_feed, feedUrl)
        } else if (!NetworkHelper.isConnectedToNetwork(activity as Context)) {
            ErrorDialog().show(activity as Context, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        } else {
            CoroutineScope(IO).launch {
                val existingPodcast: Podcast? = collectionDatabase.podcastDao().findByRemotePodcastFeedLocation(feedUrl)
                if (existingPodcast != null) {
                    // not adding podcast, because podcast is duplicate
                    withContext(Main) { ErrorDialog().show(activity as Context, R.string.dialog_error_title_podcast_duplicate, R.string.dialog_error_message_podcast_duplicate, feedUrl) }
                } else {
                    // detect content type on background thread
                    val deferred: Deferred<NetworkHelper.ContentType> = async(Dispatchers.Default) { NetworkHelper.detectContentTypeSuspended(feedUrl) }
                    val contentType: NetworkHelper.ContentType = deferred.await()
                    if ((contentType.type !in Keys.MIME_TYPES_RSS) && contentType.type !in Keys.MIME_TYPES_ATOM) {
                        withContext(Main) { ErrorDialog().show(activity as Context, R.string.dialog_error_title_podcast_invalid_feed, R.string.dialog_error_message_podcast_invalid_feed, feedUrl) }
                    } else {
                        withContext(Main) { Toast.makeText(activity as Context, R.string.toast_message_adding_podcast, Toast.LENGTH_LONG).show() }
                        DownloadHelper.downloadPodcasts(activity as Context, arrayOf(feedUrl))
                    }
                }
            }
        }
    }


    /* Download podcast feed using async co-routine */
    private fun downloadPodcastFeedsFromOpml(feedUrls: Array<String>) {
        if (NetworkHelper.isConnectedToNetwork(activity as Context)) {
            CoroutineScope(IO).launch {
                val podcastList: List<Podcast> = collectionDatabase.podcastDao().getAll()
                val urls = CollectionHelper.removeDuplicates(podcastList, feedUrls)
                if (urls.isNotEmpty()) {
                    withContext(Main) { Toast.makeText(activity as Context, R.string.toast_message_adding_podcast, Toast.LENGTH_LONG).show() }
                    DownloadHelper.downloadPodcasts(activity as Context, urls)
                    PreferencesHelper.saveLastUpdateCollection()
                }
            }
        } else {
            ErrorDialog().show(activity as Context, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }


    /* Read OPML file */
    private fun readOpmlFile(opmlUri: Uri)  {
        // read opml
        CoroutineScope(IO).launch {
            // readSuspended OPML on background thread
            val deferred: Deferred<Array<String>> = async(Dispatchers.Default) { OpmlHelper.readSuspended(activity as Context, opmlUri) }
            // wait for result and update collection
            val feedUrls: Array<String> = deferred.await()
            withContext(Main) {
                OpmlImportDialog(this@PlayerFragment).show(activity as Context, feedUrls)
            }
        }
    }


    /* Handles this activity's start intent */
    private fun handleStartIntent() {
        if ((activity as Activity).intent.action != null) {
            when ((activity as Activity).intent.action) {
                //Keys.ACTION_SHOW_PLAYER -> handleShowPlayer()
                Intent.ACTION_VIEW -> handleViewIntent()
            }
        }
        // clear intent action to prevent double calls
        (activity as Activity).intent.action = ""
    }


    /* Handles ACTION_SHOW_PLAYER request from notification */
    private fun handleShowPlayer() {
        LogHelper.i(TAG, "Tap on notification registered.")
        // todo implement
    }


    /* Handles ACTION_VIEW request to add podcast or import OPML */
    private fun handleViewIntent() {
        val contentUri: Uri? = (activity as Activity).intent.data
        if (contentUri != null) {
            val scheme: String = contentUri.scheme ?: String()
            when {
                // download new podcast
                scheme.startsWith("http") -> downloadPodcastFeed(contentUri.toString())
                // readSuspended opml from content uri
                scheme.startsWith("content") -> readOpmlFile(contentUri)
            }
        }
    }


    /* Toggle periodic request of playback position from player service */
    private fun togglePeriodicProgressUpdateRequest() {
        when (controller?.isPlaying) {
            true -> {
                handler.removeCallbacks(periodicProgressUpdateRequestRunnable)
                handler.postDelayed(periodicProgressUpdateRequestRunnable, 0)
            }
            else -> {
                handler.removeCallbacks(periodicProgressUpdateRequestRunnable)
                // request current playback position and sleep timer state once
//                MediaControllerCompat.getMediaController(activity as Activity).sendCommand(Keys.CMD_REQUEST_PROGRESS_UPDATE, null, resultReceiver) // TODO
            }
        }
    }


    /* Observe view model of podcast collection */
    private fun observeCollectionViewModel() {
        collectionViewModel.numberOfPodcastsLiveData.observe(this, Observer<Int> { numberOfPodcasts ->
            layout.toggleOnboarding(activity as Context, numberOfPodcasts)
            CoroutineScope(IO).launch {
                CollectionHelper.exportCollectionOpml(activity as Context, collectionDatabase.podcastDao().getAll() )
            }
        } )
    }


    /* Handles arguments handed over by navigation (from SettingsFragment) */
    private fun handleNavigationArguments() {
        val opmlFileString: String? = arguments?.getString(Keys.ARG_OPEN_OPML)
        if (!opmlFileString.isNullOrEmpty()) {
            readOpmlFile(opmlFileString.toUri())
            arguments?.clear()
        }
    }



    /*
     * Runnable: Periodically requests playback position (and sleep timer if running)
     */
    private val periodicProgressUpdateRequestRunnable: Runnable = object : Runnable {
        override fun run() {
            // update progress bar
            updateProgressBar()
            // update sleep timer view
            // todo implement
            // use the handler to start runnable again after specified delay
            handler.postDelayed(this, 500)
        }
    }
    /*
     * End of declaration
     */


    /*
     * Player.Listener: Called when one or more player states changed.
     */
    private var playerListener: Player.Listener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            // store state of playback
            playerState.isPlaying = isPlaying
            // animate state transition of play button(s)
            layout.animatePlaybackButtonStateTransition(activity as Context, isPlaying, controller?.playbackState)
            // toggle periodic playback position updates
            togglePeriodicProgressUpdateRequest()

            if (isPlaying) {
                // playback is active
                layout.showPlayer(activity as Context)
                layout.showBufferingIndicator(buffering = false)
            } else {
                // playback is not active
                // Not playing because playback is paused, ended, suppressed, or the player
                // is buffering, stopped or failed. Check player.getPlayWhenReady,
                // player.getPlaybackState, player.getPlaybackSuppressionReason and
                // player.getPlaybackError for details.
                when (controller?.playbackState) {
                    // player is able to immediately play from its current position
                    Player.STATE_READY -> {
                        layout.showBufferingIndicator(buffering = false)
                    }
                    // buffering - data needs to be loaded
                    Player.STATE_BUFFERING -> {
                        layout.showBufferingIndicator(buffering = true)
                    }
                    // player finished playing all media
                    Player.STATE_ENDED -> {
                        layout.hidePlayer(activity as Context)
                        layout.showBufferingIndicator(buffering = false)
                    }
                    // initial state or player is stopped or playback failed
                    Player.STATE_IDLE -> {
                        layout.hidePlayer(activity as Context)
                        layout.showBufferingIndicator(buffering = false)
                    }
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (!playWhenReady) {
                if (controller?.mediaItemCount == 0) {
                    // stopSelf()
                }
                when (reason) {
                    Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> {
                        // playback reached end: stop / end playback
                        // tryToStartUpNextEpisode()
                    }
                    else -> {
                        // playback has been paused by user or OS: update media session and save state
                        // PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST or
                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS or
                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY or
                        // PLAY_WHEN_READY_CHANGE_REASON_REMOTE
                        // handlePlaybackChange(PlaybackStateCompat.STATE_PAUSED)
                    }
                }
            }

        }



    }
    /*
     * End of declaration
     */

}
