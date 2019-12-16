/*
 * PodcastPlayerActivity.kt
 * Implements the PodcastPlayerActivity class
 * PodcastPlayerActivity is Escapepod's main activity that hosts a list of podcast and a player sheet
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.y20k.escapepod.collection.CollectionAdapter
import org.y20k.escapepod.collection.CollectionViewModel
import org.y20k.escapepod.core.Collection
import org.y20k.escapepod.core.Episode
import org.y20k.escapepod.dialogs.*
import org.y20k.escapepod.extensions.isActive
import org.y20k.escapepod.helpers.*
import org.y20k.escapepod.ui.LayoutHolder
import org.y20k.escapepod.ui.PlayerState
import org.y20k.escapepod.xml.OpmlHelper
import java.io.File
import kotlin.coroutines.CoroutineContext


/*
 * PodcastPlayerActivity class
 */
class PodcastPlayerActivity: AppCompatActivity(), CoroutineScope,
        SharedPreferences.OnSharedPreferenceChangeListener,
        AddPodcastDialog.AddPodcastDialogListener,
        FindPodcastDialog.FindPodcastDialogListener,
        CollectionAdapter.CollectionAdapterListener,
        OpmlImportDialog.OpmlImportDialogListener,
        YesNoDialog.YesNoDialogListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PodcastPlayerActivity::class.java)


    /* Main class variables */
    private lateinit var backgroundJob: Job
    private lateinit var mediaBrowser: MediaBrowserCompat
    private lateinit var collectionViewModel: CollectionViewModel
    private lateinit var layout: LayoutHolder
    private lateinit var collectionAdapter: CollectionAdapter
    private var collection: Collection = Collection()
    private var playerServiceConnected: Boolean = false
    private var onboarding: Boolean = false
    private var playerState: PlayerState = PlayerState()
    private var listLayoutState: Parcelable? = null
    private var opmlCreatedObserver: FileObserver? = null
    private var tempOpmlUriString: String = String()
    private val handler: Handler = Handler()


    /* Overrides coroutineContext variable */
    override val coroutineContext: CoroutineContext get() = backgroundJob + Dispatchers.Main


    /* Overrides onCreate from Activity*/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // initialize background job
        backgroundJob = Job()

        // create view model and observe changes in collection view model
        collectionViewModel = ViewModelProviders.of(this).get(CollectionViewModel::class.java)

        // create collection adapter
        collectionAdapter = CollectionAdapter(this)

        // Create MediaBrowserCompat
        mediaBrowser = MediaBrowserCompat(this, ComponentName(this, PlayerService::class.java), mediaBrowserConnectionCallback, null)

        // find views and set them up
        layout = LayoutHolder(this)
        initializeViews()

        // Create an observer for OPML files in collection folder
        opmlCreatedObserver = createOpmlCreatedObserver(Keys.FOLDER_COLLECTION)

        // start worker that periodically updates the podcast collection
        WorkerHelper.schedulePeriodicUpdateWorker()
    }


    /* Overrides onResume from Activity */
    public override fun onStart() {
        super.onStart()
        // connect to PlayerService
        mediaBrowser.connect()
    }


    /* Overrides onSaveInstanceState from Activity */
    override fun onSaveInstanceState(outState: Bundle) {
        // save current state of podcast list
        listLayoutState = layout.layoutManager.onSaveInstanceState()
        outState.putParcelable(Keys.KEY_SAVE_INSTANCE_STATE_PODCAST_LIST, listLayoutState)
        // always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(outState)
    }


    /* Overrides onRestoreInstanceState from Activity */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        // always call the superclass so it can restore the view hierarchy
        super.onRestoreInstanceState(savedInstanceState)
        // restore state of podcast list
        listLayoutState = savedInstanceState.getParcelable(Keys.KEY_SAVE_INSTANCE_STATE_PODCAST_LIST)
    }


    /* Overrides onResume from Activity */
    override fun onResume() {
        super.onResume()
        // assign volume buttons to music volume
        volumeControlStream = AudioManager.STREAM_MUSIC
        // try to recreate player state
        playerState = PreferencesHelper.loadPlayerState(this)
        // setup ui
        setupPlayer()
        setupList()
        // start watching for changes in shared preferences
        PreferencesHelper.registerPreferenceChangeListener(this)
        // toggle download progress indicator
        layout.toggleDownloadProgressIndicator(this)
    }


    /* Overrides onPause from Activity */
    override fun onPause() {
        super.onPause()
        // save player state
        PreferencesHelper.savePlayerState(this, playerState)
        // stop receiving playback progress updates
        handler.removeCallbacks(periodicProgressUpdateRequestRunnable)
        // stop watching for new opml files
        opmlCreatedObserver?.stopWatching()
        // stop watching for changes in shared preferences
        PreferencesHelper.unregisterPreferenceChangeListener(this)

    }


    /* Overrides onStop from Activity */
    public override fun onStop() {
        super.onStop()
        // (see "stay in sync with the MediaSession")
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(mediaControllerCallback)
        mediaBrowser.disconnect()
        playerServiceConnected = false
    }


    /* Overrides onRequestPermissionsResult from Activity */
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
                    Toast.makeText(this, R.string.toast_message_error_missing_storage_permission, Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    /* Overrides onSharedPreferenceChanged from OnSharedPreferenceChangeListener */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == Keys.PREF_ACTIVE_DOWNLOADS) {
            layout.toggleDownloadProgressIndicator(this)
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


    /* Overrides onFindPodcastDialog from FindPodcastDialog */
    override fun onFindPodcastDialog(remotePodcastFeedLocation: String) {
        super.onFindPodcastDialog(remotePodcastFeedLocation)
        val podcastUrl = remotePodcastFeedLocation.trim()
        if (CollectionHelper.isNewPodcast(podcastUrl, collection)) {
            downloadPodcastFeed(podcastUrl)
        } else {
            ErrorDialog().show(this, R.string.dialog_error_title_podcast_duplicate, R.string.dialog_error_message_podcast_duplicate, podcastUrl)
        }
    }


    /* Overrides onPlayButtonTapped from CollectionAdapterListener */
    override fun onPlayButtonTapped(mediaId: String, playbackState: Int) {
        when (playerState.playbackState) {
            // PLAYER STATE: PLAYING
            PlaybackStateCompat.STATE_PLAYING -> {
                when (mediaId) {
                    // tapped episode currently in player
                    playerState.episodeMediaId -> {
                        // stop playback
                        togglePlayback(false, mediaId, playbackState)
                    }
                    // tapped on episode already in the up next queue
                    playerState.upNextEpisodeMediaId -> {
                        // start playback
                        togglePlayback(true, mediaId, playbackState)
                        // clear up next
                        updateUpNext()
                    }
                    else -> {
                        // ask user: playback or add to Up Next
                        val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_add_up_next)}\n\n- ${CollectionHelper.getEpisode(collection, mediaId).title}"
                        YesNoDialog(this@PodcastPlayerActivity as YesNoDialog.YesNoDialogListener).show(context = this@PodcastPlayerActivity, type = Keys.DIALOG_ADD_UP_NEXT, messageString = dialogMessage, yesButton = R.string.dialog_yes_no_positive_button_add_up_next, noButton = R.string.dialog_yes_no_negative_button_add_up_next, payloadString = mediaId)
                    }
                }
            }
            // PLAYER STATE: NOT PLAYING
            else -> {
                // start playback
                togglePlayback(true, mediaId, playbackState)
            }

        }
    }


    /* Overrides onMarkListenedButtonTapped from CollectionAdapterListener */
    override fun onMarkListenedButtonTapped(mediaId: String) {
        MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).transportControls.pause()
        val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_mark_episode_played)}\n\n- ${CollectionHelper.getEpisode(collection, mediaId).title}"
        YesNoDialog(this@PodcastPlayerActivity as YesNoDialog.YesNoDialogListener).show(context = this@PodcastPlayerActivity, type = Keys.DIALOG_MARK_EPISODE_PLAYED, messageString = dialogMessage, yesButton = R.string.dialog_yes_no_positive_button_mark_episode_played, noButton = R.string.dialog_yes_no_negative_button_cancel, payloadString = mediaId)
    }


    /* Overrides onDownloadButtonTapped from CollectionAdapterListener */
    override fun onDownloadButtonTapped(episode: Episode) {
        downloadEpisode(episode)
    }


    /* Overrides onDeleteButtonTapped from CollectionAdapterListener */
    override fun onDeleteButtonTapped(episode: Episode) {
        MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).transportControls.pause()
        val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_delete_episode)}\n\n- ${episode.title}"
        YesNoDialog(this@PodcastPlayerActivity as YesNoDialog.YesNoDialogListener).show(context = this@PodcastPlayerActivity, type = Keys.DIALOG_DELETE_EPISODE, messageString = dialogMessage, yesButton = R.string.dialog_yes_no_positive_button_delete_episode, payloadString = episode.getMediaId())
    }


    /* Overrides onAddNewButtonTapped from CollectionAdapterListener */
    override fun onAddNewButtonTapped() {
//        AddPodcastDialog(this).show(this)
        FindPodcastDialog(this).show()
    }


    /* Overrides onDeleteAllButtonTapped from CollectionAdapterListener */
    override fun onDeleteAllButtonTapped() {
        MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).transportControls.pause()
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(50)
        // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
        YesNoDialog(this).show(context = this, type = Keys.DIALOG_DELETE_DOWNLOADS, message = R.string.dialog_yes_no_message_delete_downloads, yesButton = R.string.dialog_yes_no_positive_button_delete_downloads)
    }


    /* Overrides onOpmlImportDialog from OpmlImportDialogListener */
    override fun onOpmlImportDialog(feedUrls: Array<String>) {
        super.onOpmlImportDialog(feedUrls)
        downloadPodcastFeedsFromOpml(feedUrls)
    }


    /* Overrides onYesNoDialog from YesNoDialogListener */
    override fun onYesNoDialog(type: Int, dialogResult: Boolean, payload: Int, payloadString: String) {
        super.onYesNoDialog(type, dialogResult, payload, payloadString)
        when (type) {
            Keys.DIALOG_UPDATE_COLLECTION -> {
                when (dialogResult) {
                    // user tapped update collection
                    true -> {
                        if (CollectionHelper.hasEnoughTimePassedSinceLastUpdate(this)) {
                            updateCollection()
                        } else {
                            Toast.makeText(this, R.string.toast_message_collection_update_not_necessary, Toast.LENGTH_LONG).show()
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
                    true -> collectionAdapter.removePodcast(this@PodcastPlayerActivity, payload)
                    // user tapped cancel
                    false -> collectionAdapter.notifyItemChanged(payload)
                }
            }
            Keys.DIALOG_DELETE_EPISODE -> {
                when (dialogResult) {
                    // user tapped delete episode
                    true -> collectionAdapter.deleteEpisode(this@PodcastPlayerActivity, payloadString)
                }
            }
            Keys.DIALOG_DELETE_DOWNLOADS -> {
                when (dialogResult) {
                    // user tapped: delete all downloads
                    true -> collectionAdapter.deleteAllEpisodes(this@PodcastPlayerActivity)
                }
            }
            Keys.DIALOG_MARK_EPISODE_PLAYED -> {
                when (dialogResult) {
                    // user tapped: mark episode played
                    true -> collectionAdapter.markEpisodePlayed(this@PodcastPlayerActivity, payloadString)
                }
            }
            Keys.DIALOG_ADD_UP_NEXT -> {
                val episode = CollectionHelper.getEpisode(collection, payloadString)
                when (dialogResult) {
                    // user tapped: start playback
                    true -> togglePlayback(true, episode.getMediaId(), episode.playbackState)
                    // user tapped: add to up next
                    false -> updateUpNext(episode)
                }
            }
            Keys.DIALOG_DOWNLOAD_EPISODE_WITHOUT_WIFI -> {
                when (dialogResult) {
                    true -> {
                        Toast.makeText(this, R.string.toast_message_downloading_episode, Toast.LENGTH_LONG).show()
                        DownloadHelper.downloadEpisode(this, payloadString, ignoreWifiRestriction = true, manuallyDownloaded = true)
                    }
                }
            }
        }
    }


    /* Sets up views and connects tap listeners - first run */
    private fun initializeViews() {
        // set adapter data source
        layout.recyclerView.adapter = collectionAdapter

        // enable swipe to delete
        val swipeHandler = object : UiHelper.SwipeToDeleteCallback(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // ask user
                val adapterPosition: Int = viewHolder.adapterPosition
                val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_remove_podcast)}\n\n- ${collection.podcasts[adapterPosition].name}"
                YesNoDialog(this@PodcastPlayerActivity as YesNoDialog.YesNoDialogListener).show(context = this@PodcastPlayerActivity, type = Keys.DIALOG_REMOVE_PODCAST, messageString = dialogMessage, yesButton = R.string.dialog_yes_no_positive_button_remove_podcast, payload = adapterPosition)
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(layout.recyclerView)

        // enable for swipe to refresh
        layout.swipeRefreshLayout.setOnRefreshListener {
            // ask user to confirm update
            YesNoDialog(this@PodcastPlayerActivity as YesNoDialog.YesNoDialogListener).show(context = this@PodcastPlayerActivity, type = Keys.DIALOG_UPDATE_COLLECTION, message = R.string.dialog_yes_no_message_update_collection, yesButton = R.string.dialog_yes_no_positive_button_update_collection)
            layout.swipeRefreshLayout.isRefreshing = false
        }

        // set up sleep timer start button
        layout.sheetSleepTimerStartButtonView.setOnClickListener {
            val playbackState: PlaybackStateCompat = MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).playbackState
            when (playbackState.isActive) {
                true -> MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).sendCommand(Keys.CMD_START_SLEEP_TIMER, null, null)
                false -> Toast.makeText(this, R.string.toast_message_sleep_timer_unable_to_start, Toast.LENGTH_LONG).show()
            }
        }

        // set up sleep timer start button - long press (night mode switch)
        layout.sheetSleepTimerStartButtonView.setOnLongClickListener {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(50)
            // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
            NightModeHelper.switchMode(this)
            recreate()
            return@setOnLongClickListener true
        }

        // set up sleep timer cancel button
        layout.sheetSleepTimerCancelButtonView.setOnClickListener {
            MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).sendCommand(Keys.CMD_CANCEL_SLEEP_TIMER, null, null)
            layout.sleepTimerRunningViews.visibility = View.GONE
        }

        // set up the debug log toggle switch
        layout.sheetDebugToggleButtonView.setOnClickListener {
            LogHelper.toggleDebugLogFileCreation(this@PodcastPlayerActivity)
        }

    }


    /* Builds playback controls - used after connected to player service */
    @SuppressLint("ClickableViewAccessibility") // it is probably okay to suppress this warning - the OnTouchListener on the time played view does only toggle the time duration / remaining display
    private fun buildPlaybackControls() {

        // get player state
        playerState = PreferencesHelper.loadPlayerState(this)

        // get reference to media controller
        val mediaController = MediaControllerCompat.getMediaController(this@PodcastPlayerActivity)

        // main play/pause button
        layout.playButtonView.setOnClickListener {
            onPlayButtonTapped(playerState.episodeMediaId, mediaController.playbackState.state)
        }

        // bottom sheet play/pause button
        layout.sheetPlayButtonView.setOnClickListener {
            when (mediaController.playbackState.state) {
                PlaybackStateCompat.STATE_PLAYING -> mediaController.transportControls.pause()
                else -> mediaController.transportControls.playFromMediaId(playerState.episodeMediaId, null)
            }
        }

        // bottom sheet skip back button
        layout.sheetSkipBackButtonView.setOnClickListener {
            when (playerState.playbackState == PlaybackStateCompat.STATE_PLAYING) {
                true -> mediaController.transportControls.rewind()
                false -> Toast.makeText(this, R.string.toast_message_skipping_disabled, Toast.LENGTH_LONG).show()
            }
        }

        // bottom sheet skip forward button
        layout.sheetSkipForwardButtonView.setOnClickListener {
            when (playerState.playbackState == PlaybackStateCompat.STATE_PLAYING) {
                true -> mediaController.transportControls.fastForward()
                false -> Toast.makeText(this, R.string.toast_message_skipping_disabled, Toast.LENGTH_LONG).show()
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
                MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).transportControls.seekTo(position.toLong())
            }
        })

        // bottom sheet time played display
        layout.sheetTimePlayedView.setOnTouchListener { view, motionEvent ->
            view.performClick()
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    // show time remaining while touching the time played view
                    layout.displayTimeRemaining = true
                    MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).sendCommand(Keys.CMD_REQUEST_PERIODIC_PROGRESS_UPDATE, null, resultReceiver)
                }
                MotionEvent.ACTION_UP -> {
                    // show episode duration when not touching the time played view anymore
                    layout.displayTimeRemaining = false
                    val duration = DateTimeHelper.convertToMinutesAndSeconds(playerState.episodeDuration)
                    layout.sheetDurationView.text = duration
                    layout.sheetDurationView.contentDescription = "${getString(R.string.descr_expanded_episode_length)}: ${duration}"
                }
                else -> return@setOnTouchListener false
            }
            return@setOnTouchListener true
        }

        // bottom sheet start button for Up Next queue
        layout.sheetUpNextName.setOnClickListener {
            // start episode in up next queue
            MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).transportControls.playFromMediaId(playerState.upNextEpisodeMediaId, null)
            Toast.makeText(this, R.string.toast_message_up_next_start_playback, Toast.LENGTH_LONG).show()
        }

        // bottom sheet clear button for Up Next queue
        layout.sheetUpNextClearButton.setOnClickListener {
            // clear up next
            updateUpNext()
            Toast.makeText(this, R.string.toast_message_up_next_removed_episode, Toast.LENGTH_LONG).show()
        }

        // bottom sheet playback speed button
        layout.sheetPlaybackSpeedButtonView.setOnClickListener {
            // request playback speed change
            MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).sendCommand(Keys.CMD_CHANGE_PLAYBACK_SPEED, null, resultReceiver)
        }
        layout.sheetPlaybackSpeedButtonView.setOnLongClickListener {
            if (playerState.playbackSpeed != 1f) {
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(50)
                // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
                Toast.makeText(this, R.string.toast_message_playback_speed_reset, Toast.LENGTH_LONG).show()
                // request playback speed reset
                MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).sendCommand(Keys.CMD_RESET_PLAYBACK_SPEED, null, resultReceiver)
            }
            return@setOnLongClickListener true
        }

        // register a callback to stay in sync
        mediaController.registerCallback(mediaControllerCallback)
    }


    /* Sets up the player */
    private fun setupPlayer() {
        // toggle player visibility
        if (layout.togglePlayerVisibility(this, playerState.playbackState)) {
            // CASE: player is visible - update player views
            layout.togglePlayButtons(playerState.playbackState)
            if (playerState.episodeMediaId.isNotEmpty()) {
                val episode: Episode = CollectionHelper.getEpisode(collection, playerState.episodeMediaId)
                layout.updatePlayerViews(this, episode)
                layout.updateProgressbar(this@PodcastPlayerActivity, episode.playbackPosition, episode.duration)
                layout.updatePlaybackSpeedView(this@PodcastPlayerActivity, playerState.playbackSpeed)
            }
        }
    }


    /* Sets up state of list podcast list */
    private fun setupList() {
        if (listLayoutState != null) {
            layout.layoutManager.onRestoreInstanceState(listLayoutState)
        }
    }


    /* Starts / pauses playback */
    private fun togglePlayback(startPlayback: Boolean, mediaId: String, playbackState: Int) {
        playerState.episodeMediaId = mediaId
        playerState.playbackState = playbackState // = current state BEFORE desired startPlayback action
        // setup ui
        val episode: Episode = CollectionHelper.getEpisode(collection, playerState.episodeMediaId)
        layout.updatePlayerViews(this, episode)
        // start / pause playback
        when (startPlayback) {
            true -> MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).transportControls.playFromMediaId(mediaId, null)
            false -> MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).transportControls.pause()
        }
    }


    /* Updates the Up Next queue */
    private fun updateUpNext(episode: Episode = Episode()) {
        playerState.upNextEpisodeMediaId = episode.getMediaId()
        layout.updateUpNextViews(episode)
        if (episode.getMediaId().isNotEmpty()) {
            Toast.makeText(this, R.string.toast_message_up_next_added_episode, Toast.LENGTH_LONG).show()
        }
        PreferencesHelper.savePlayerState(this, playerState)
        MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).sendCommand(Keys.CMD_RELOAD_PLAYER_STATE, null, null)
    }


    /* Updates podcast collection */
    private fun updateCollection() {
        if (NetworkHelper.isConnectedToNetwork(this)) {
            Toast.makeText(this, R.string.toast_message_updating_collection, Toast.LENGTH_LONG).show()
            DownloadHelper.updateCollection(this)
        } else {
            ErrorDialog().show(this, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }


    /* Updates podcast collection */
    private fun downloadEpisode(episode: Episode) {
        if (NetworkHelper.isConnectedToWifi(this)) {
            Toast.makeText(this, R.string.toast_message_downloading_episode, Toast.LENGTH_LONG).show()
            DownloadHelper.downloadEpisode(this, episode.getMediaId(), ignoreWifiRestriction = true, manuallyDownloaded = true)
        } else if (NetworkHelper.isConnectedToCellular(this)) {
            YesNoDialog(this).show(context = this, type = Keys.DIALOG_DOWNLOAD_EPISODE_WITHOUT_WIFI, message = R.string.dialog_yes_no_message_non_wifi_download, yesButton = R.string.dialog_yes_no_positive_button_non_wifi_download, payloadString = episode.getMediaId())
        } else if (NetworkHelper.isConnectedToVpn(this))  {
            YesNoDialog(this).show(context = this, type = Keys.DIALOG_DOWNLOAD_EPISODE_WITHOUT_WIFI, message = R.string.dialog_yes_no_message_vpn_download, yesButton = R.string.dialog_yes_no_positive_button_vpn_download, payloadString = episode.getMediaId())
        } else {
            ErrorDialog().show(this, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }


    /* Download podcast feed using async co-routine */
    private fun downloadPodcastFeed(feedUrl: String) {
        if (!feedUrl.startsWith("http")) {
            ErrorDialog().show(this, R.string.dialog_error_title_podcast_invalid_feed, R.string.dialog_error_message_podcast_invalid_feed, feedUrl)
        } else if (NetworkHelper.isConnectedToNetwork(this@PodcastPlayerActivity)) {
            launch {
                // detect content type on background thread
                val deferred: Deferred<NetworkHelper.ContentType> = async(Dispatchers.Default) { NetworkHelper.detectContentTypeSuspended(feedUrl) }
                // wait for result
                val contentType: NetworkHelper.ContentType = deferred.await()
                if ((contentType.type in Keys.MIME_TYPES_RSS) || (contentType.type in Keys.MIME_TYPES_ATOM)) {
                    Toast.makeText(this@PodcastPlayerActivity, R.string.toast_message_adding_podcast, Toast.LENGTH_LONG).show()
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
                Toast.makeText(this@PodcastPlayerActivity, R.string.toast_message_adding_podcast, Toast.LENGTH_LONG).show()
                DownloadHelper.downloadPodcasts(this, CollectionHelper.removeDuplicates(collection, feedUrls))
                PreferencesHelper.saveLastUpdateCollection(this)
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
                    // save uri for later use in onRequestPermissionsResult
                    tempOpmlUriString = opmlUri.toString()
                    // permission is not granted - request it
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), Keys.PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
                } else {
                    // permission granted - call readOpmlFile again
                    readOpmlFile(opmlUri, false)
                }
            }
            false -> {
                // reset temp url string
                tempOpmlUriString = String()
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
        intent.action = ""
    }


    /* Handles ACTION_SHOW_PLAYER request from notification */
    private fun handleShowPlayer() {
        LogHelper.i(TAG, "Tap on notification registered.")
        // todo implement
    }


    /* Handles ACTION_VIEW request to add Podcast or import OPML */
    private fun handleViewIntent() {
        val contentUri: Uri? = intent.data
        if (contentUri != null) {
            val scheme: String = contentUri.scheme ?: String()
            when {
                // download new podcast
                scheme.startsWith("http") -> downloadPodcastFeed(contentUri.toString()) // todo implement podcast download + dialog and stuff
                // readSuspended opml from content uri
                scheme.startsWith("content") -> readOpmlFile(contentUri, false) // todo implement OPML readSuspended + dialog and stuff
                // readSuspended opml from file uri
                scheme.startsWith("file") -> readOpmlFile(contentUri, true) // todo implement OPML readSuspended + dialog and stuff
            }
        }
    }


    /* Toggle periodic request of playback position from player service */
    private fun togglePeriodicProgressUpdateRequest(playbackState: PlaybackStateCompat) {
        when (playbackState.isActive) {
            true -> {
                handler.removeCallbacks(periodicProgressUpdateRequestRunnable)
                handler.postDelayed(periodicProgressUpdateRequestRunnable, 0)
            }
            false -> {
                handler.removeCallbacks(periodicProgressUpdateRequestRunnable)
                layout.sleepTimerRunningViews.visibility = View.GONE
            }
        }
    }


    /* Observe view model of podcast collection */
    private fun observeCollectionViewModel() {
        collectionViewModel.collectionLiveData.observe(this, Observer<Collection> { it ->
            // update collection
            collection = it
            // updates current episode in player views
            playerState = PreferencesHelper.loadPlayerState(this@PodcastPlayerActivity)
            // toggle onboarding layout
            toggleOnboarding()
            // toggle visibility of player
            if (layout.togglePlayerVisibility(this@PodcastPlayerActivity, playerState.playbackState)) {
                // update player view, if player is visible
                val episode: Episode = CollectionHelper.getEpisode(collection, playerState.episodeMediaId)
                layout.updatePlayerViews(this@PodcastPlayerActivity, episode)
            }
            // updates the up next queue in player views
            val upNextEpisode: Episode = CollectionHelper.getEpisode(collection, playerState.upNextEpisodeMediaId)
            layout.updateUpNextViews(upNextEpisode)
            // handle start intent
            handleStartIntent()
       })
    }


    /* toggle onboarding layou on/off and try to offer an OPML import */
    private fun toggleOnboarding() {
        // toggle onboading layout
        onboarding = layout.toggleOnboarding(this@PodcastPlayerActivity, collection.podcasts.size)
        // start / stop watching for OPML
        if (onboarding) {
            // try to offer import
            tryToOfferOpmlImport()
            // start watching for files to be created in collection folder
            opmlCreatedObserver?.startWatching()
        } else {
            opmlCreatedObserver?.stopWatching()
        }
    }


    /* Offers to import podcasts if an OPML file was found in collection folder (probably from a restore via Play Services) */
    private fun tryToOfferOpmlImport() {
        val opmlFile: File? = File(getExternalFilesDir(Keys.FOLDER_COLLECTION), Keys.COLLECTION_OPML_FILE)
        if (FileHelper.getCollectionFolderSize(this) == 1 && opmlFile!= null && opmlFile.exists() && opmlFile.length() > 0L) {
            LogHelper.i(TAG, "Found an OPML file in the otherwise empty Collection folder. Size: ${opmlFile.length()} bytes")
            readOpmlFile(opmlFile.toUri(), permissionCheckNeeded = false)
        }
    }


    /* Creates an observer for OPML files in collection folder */
    private fun createOpmlCreatedObserver(folderString: String): FileObserver? {
        val folder: File? = getExternalFilesDir(folderString)
        var fileObserver: FileObserver? = null
        // check if valid folder
        if (folder != null && folder.isDirectory) {
            // create the observer - Note: constructor is deprecated, but the one that it replaces is API 29+
            fileObserver = object: FileObserver(folder.path, CREATE) {
                override fun onEvent(event: Int, path: String?) {
                    // a file file was created in the collection folder
                    tryToOfferOpmlImport()
                }
            }
        }
        return fileObserver
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

            // request current playback position
            MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).sendCommand(Keys.CMD_REQUEST_PERIODIC_PROGRESS_UPDATE, null, resultReceiver)

            // start requesting position updates if playback is running
            if (playerState.playbackState == PlaybackStateCompat.STATE_PLAYING) {
                handler.removeCallbacks(periodicProgressUpdateRequestRunnable)
                handler.postDelayed(periodicProgressUpdateRequestRunnable, 0)
            }

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
            LogHelper.d(TAG, "Session ready. Update UI.")
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            LogHelper.d(TAG, "Metadata changed. Update UI.")
        }

        override fun onPlaybackStateChanged(playbackState: PlaybackStateCompat) {
            LogHelper.d(TAG, "Playback State changed. Update UI.")
            playerState.playbackState = playbackState.state
            if (layout.togglePlayerVisibility(this@PodcastPlayerActivity, playbackState.state)) {
                // CASE: Player is visible
                layout.animatePlaybackButtonStateTransition(this@PodcastPlayerActivity, playbackState.state)
            }
            togglePeriodicProgressUpdateRequest(playbackState)
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            mediaBrowserConnectionCallback.onConnectionSuspended()
        }
    }
    /*
     * End of callback
     */


    /*
     * Runnable: Periodically requests playback position (and sleep timer if running)
     */
    private val periodicProgressUpdateRequestRunnable: Runnable = object : Runnable {
        override fun run() {
            // request current playback position
            MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).sendCommand(Keys.CMD_REQUEST_PERIODIC_PROGRESS_UPDATE, null, resultReceiver)
            // use the handler to start runnable again after specified delay
            handler.postDelayed(this, 500)
        }
    }
    /*
     * End of declaration
     */


    /*
     * ResultReceiver: Handles results from commands send to player
     * eg. MediaControllerCompat.getMediaController(this@PodcastPlayerActivity).sendCommand(Keys.CMD_REQUEST_PERIODIC_PROGRESS_UPDATE, null, resultReceiver)
     */
    var resultReceiver: ResultReceiver = object: ResultReceiver(Handler()) {
        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
            when (resultCode) {
                Keys.RESULT_CODE_PERIODIC_PROGRESS_UPDATE -> {
                    if (resultData != null && resultData.containsKey(Keys.RESULT_DATA_PLAYBACK_PROGRESS)) {
                        layout.updateProgressbar(this@PodcastPlayerActivity, resultData.getLong(Keys.RESULT_DATA_PLAYBACK_PROGRESS, 0L), playerState.episodeDuration)
                    }
                    if (resultData != null && resultData.containsKey(Keys.RESULT_DATA_SLEEP_TIMER_REMAINING)) {
                        layout.updateSleepTimer(this@PodcastPlayerActivity, resultData.getLong(Keys.RESULT_DATA_SLEEP_TIMER_REMAINING, 0L))
                    }
                }
                Keys.RESULT_CODE_PLAYBACK_SPEED -> {
                    if (resultData != null && resultData.containsKey(Keys.RESULT_DATA_PLAYBACK_SPEED)) {
                        playerState.playbackSpeed = resultData.getFloat(Keys.RESULT_DATA_PLAYBACK_SPEED, 1f)
                        layout.updatePlaybackSpeedView(this@PodcastPlayerActivity, playerState.playbackSpeed)
                    }
                }
            }
        }
    }
    /*
     * End of ResultReceiver
     */

}
