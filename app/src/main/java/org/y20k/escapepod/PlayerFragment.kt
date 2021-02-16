/*
 * PlayerFragment.kt
 * Implements the PlayerFragment class
 * PlayerFragment is the fragment that hosts Escapepod's list of podcasts and a player sheet
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
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
import org.y20k.escapepod.extensions.isActive
import org.y20k.escapepod.helpers.*
import org.y20k.escapepod.legacy.ImportHelper
import org.y20k.escapepod.playback.PlayerController
import org.y20k.escapepod.playback.PlayerService
import org.y20k.escapepod.ui.LayoutHolder
import org.y20k.escapepod.ui.PlayerState
import org.y20k.escapepod.xml.OpmlHelper
import kotlin.coroutines.CoroutineContext


/*
 * PlayerFragment class
 */
class PlayerFragment: Fragment(), CoroutineScope,
        SharedPreferences.OnSharedPreferenceChangeListener,
        FindPodcastDialog.FindPodcastDialogListener,
        CollectionAdapter.CollectionAdapterListener,
        OpmlImportDialog.OpmlImportDialogListener,
        YesNoDialog.YesNoDialogListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PlayerFragment::class.java)


    /* Main class variables */
    private lateinit var backgroundJob: Job
    private lateinit var mediaBrowser: MediaBrowserCompat
    private lateinit var collectionDatabase: CollectionDatabase
    private lateinit var collectionViewModel: CollectionViewModel
    private lateinit var layout: LayoutHolder
    private lateinit var collectionAdapter: CollectionAdapter
    private lateinit var playerController: PlayerController
    private var episode: Episode? = null
    private var upNextEpisode: Episode? = null
    private var playerServiceConnected: Boolean = false
    private var playerState: PlayerState = PlayerState()
    private var listLayoutState: Parcelable? = null
    private var tempOpmlUriString: String = String()
    private val handler: Handler = Handler(Looper.getMainLooper())


    /* Overrides coroutineContext variable */
    override val coroutineContext: CoroutineContext get() = backgroundJob + Dispatchers.Main


    /* Overrides onCreate from Fragment*/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // initialize background job
        backgroundJob = Job()

        // create view model and observe changes in collection view model
        collectionViewModel = ViewModelProvider(this).get(CollectionViewModel::class.java)

        // get instance of database
        collectionDatabase = CollectionDatabase.getInstance(activity as Context)

        // create collection adapter
        collectionAdapter = CollectionAdapter(activity as Context, collectionDatabase, this as CollectionAdapter.CollectionAdapterListener)

        // Create MediaBrowserCompat
        mediaBrowser = MediaBrowserCompat(activity as Context, ComponentName(activity as Context, PlayerService::class.java), mediaBrowserConnectionCallback, null)

        // start worker that periodically updates the podcast collection
        WorkerHelper.schedulePeriodicUpdateWorker()

        // import old podcasts
        if (PreferencesHelper.isHouseKeepingNecessary(activity as Context)) {
            // import podcasts from json into database
            ImportHelper.importLegacyCollection(activity as Context, collectionDatabase)
            // housekeeping finished - save state
            PreferencesHelper.saveHouseKeepingNecessaryState(activity as Context)
        }

    }


    /* Overrides onCreate from Fragment*/
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        // find views and set them up
        val rootView: View = inflater.inflate(R.layout.fragment_podcast_player, container, false);
        layout = LayoutHolder(rootView, collectionDatabase)
        initializeViews()

        // hide action bar
        (activity as AppCompatActivity).supportActionBar?.hide()

        return rootView
    }

    /* Overrides onResume from Fragment */
    override fun onStart() {
        super.onStart()
        // connect to PlayerService
        mediaBrowser.connect()
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
        playerState = PreferencesHelper.loadPlayerState(activity as Context)
        // recreate player ui
        CoroutineScope(IO).launch {
            // get current and up-next episode
            episode = collectionDatabase.episodeDao().findByMediaId(playerState.episodeMediaId)
            upNextEpisode = collectionDatabase.episodeDao().findByMediaId(playerState.upNextEpisodeMediaId)
            // setup ui
            withContext(Main) {
                setupPlayer()
                setupList()
                layout.toggleDownloadProgressIndicator(activity as Context)
            }
        }
        // handle navigation arguments
        handleNavigationArguments()
        // start watching for changes in shared preferences
        PreferencesHelper.registerPreferenceChangeListener(activity as Context, this as SharedPreferences.OnSharedPreferenceChangeListener)
    }


    /* Overrides onPause from Fragment */
    override fun onPause() {
        super.onPause()
        // save player state
        PreferencesHelper.savePlayerState(activity as Context, playerState)
        // stop receiving playback progress updates
        handler.removeCallbacks(periodicProgressUpdateRequestRunnable)
        // stop watching for changes in shared preferences
        PreferencesHelper.unregisterPreferenceChangeListener(activity as Context, this as SharedPreferences.OnSharedPreferenceChangeListener)

    }


    /* Overrides onStop from Fragment */
    override fun onStop() {
        super.onStop()
        // (see "stay in sync with the MediaSession")
        playerController.unregisterCallback(mediaControllerCallback)
        mediaBrowser.disconnect()
        playerServiceConnected = false
    }


    /* Overrides onRequestPermissionsResult from Fragment */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            Keys.PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission granted
                    if (tempOpmlUriString.isNotEmpty()) {
                        readOpmlFile(tempOpmlUriString.toUri(), false)
                    }
                } else {
                    // permission denied
                    Toast.makeText(activity as Context, R.string.toast_message_error_missing_storage_permission, Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    /* Overrides onSharedPreferenceChanged from OnSharedPreferenceChangeListener */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            Keys.PREF_ACTIVE_DOWNLOADS -> {
                layout.toggleDownloadProgressIndicator(activity as Context)
            }
            Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID -> {
                CoroutineScope(IO).launch {
                    val mediaId: String = sharedPreferences?.getString(Keys.PREF_PLAYER_STATE_EPISODE_MEDIA_ID, String()) ?: String()
                    playerState.episodeMediaId = mediaId
                    LogHelper.v(TAG, "onSharedPreferenceChanged - current episode: $mediaId") // todo remove
                    episode = collectionDatabase.episodeDao().findByMediaId(mediaId)
                    withContext(Main) { layout.updatePlayerViews(activity as Context, episode) } // todo check if onSharedPreferenceChanged can be triggered before layout has been initialized
                }
            }
            Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID -> {
                CoroutineScope(IO).launch {
                    val mediaId: String = sharedPreferences?.getString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, String()) ?: String()
                    playerState.upNextEpisodeMediaId = mediaId
                    LogHelper.v(TAG, "onSharedPreferenceChanged - up next episode: $mediaId") // todo remove
                    upNextEpisode = collectionDatabase.episodeDao().findByMediaId(mediaId)
                    withContext(Main) { layout.updateUpNextViews(upNextEpisode) } // todo check if onSharedPreferenceChanged can be triggered before layout has been initialized
                }
            }
//            Keys.PREF_PLAYER_STATE_PLAYBACK_STATE -> {
//                playerState.playbackState = sharedPreferences?.getInt(Keys.PREF_PLAYER_STATE_PLAYBACK_STATE, PlaybackState.STATE_STOPPED) ?: PlaybackState.STATE_STOPPED
//                LogHelper.v(TAG, "onSharedPreferenceChanged - current state: ${playerState.playbackState}") // todo remove
//                //layout.togglePlayerVisibility(activity as Context, playerState.playbackState)
//            }
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
                        updateUpNext(String())
                    }
                    else -> {
                        // ask user: playback or add to Up Next
                        CoroutineScope(IO).launch {
                            val episodeTitle: String? = collectionDatabase.episodeDao().getTitle(mediaId)
                            if (episodeTitle != null) {
                                val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_add_up_next)}\n\n- $episodeTitle"
                                withContext(Main) { YesNoDialog(this@PlayerFragment as YesNoDialog.YesNoDialogListener).show(context = activity as Context, type = Keys.DIALOG_ADD_UP_NEXT, messageString = dialogMessage, yesButton = R.string.dialog_yes_no_positive_button_add_up_next, noButton = R.string.dialog_yes_no_negative_button_add_up_next, payloadString = mediaId) }
                            }
                        }
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
        if (mediaId == episode?.mediaId) {
            playerController.pause()
        }
        CoroutineScope(IO).launch {
            val tappedEpisode: Episode? = collectionDatabase.episodeDao().findByMediaId(mediaId)
            val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_mark_episode_played)}\n\n- ${tappedEpisode?.title}"
            withContext(Main) { YesNoDialog(this@PlayerFragment as YesNoDialog.YesNoDialogListener).show(context = activity as Context, type = Keys.DIALOG_MARK_EPISODE_PLAYED, messageString = dialogMessage, yesButton = R.string.dialog_yes_no_positive_button_mark_episode_played, noButton = R.string.dialog_yes_no_negative_button_cancel, payloadString = mediaId) }
        }
    }


    /* Overrides onDownloadButtonTapped from CollectionAdapterListener */
    override fun onDownloadButtonTapped(selectedEpisode: Episode) {
        downloadEpisode(selectedEpisode.mediaId)
    }


    /* Overrides onDeleteButtonTapped from CollectionAdapterListener */
    override fun onDeleteButtonTapped(selectedEpisode: Episode) {
        if (selectedEpisode.mediaId == episode?.mediaId) {
            playerController.pause()
        }
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
    override fun onYesNoDialog(type: Int, dialogResult: Boolean, payload: Int, payloadString: String) {
        super.onYesNoDialog(type, dialogResult, payload, payloadString)
        when (type) {
            Keys.DIALOG_UPDATE_COLLECTION -> {
                when (dialogResult) {
                    // user tapped update collection
                    true -> {
                        if (CollectionHelper.hasEnoughTimePassedSinceLastUpdate(activity as Context)) {
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
                    true -> togglePlayback(true, payloadString)
                    // user tapped: add to up next
                    false -> updateUpNext(payloadString)
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


    /* Sets up views and connects tap listeners - first run */
    private fun initializeViews() {
        // set adapter data source
        layout.recyclerView.adapter = collectionAdapter

        // enable swipe to delete
        val swipeHandler = object : UiHelper.SwipeToDeleteCallback(activity as Context) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val adapterPosition: Int = viewHolder.adapterPosition
                val podcast = collectionAdapter.getPodcast(adapterPosition)
                // stop playback, if necessary
                podcast.episodes.forEach { it ->
                    if (it.data.mediaId == episode?.mediaId) playerController.pause()
                }
                // ask user
                val dialogMessage: String = "${getString(R.string.dialog_yes_no_message_remove_podcast)}\n - ${podcast.data.name}"
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
            val playbackState: PlaybackStateCompat = MediaControllerCompat.getMediaController(activity as Activity).playbackState
            when (playbackState.isActive) {
                true -> playerController.startSleepTimer()
                false -> Toast.makeText(activity as Context, R.string.toast_message_sleep_timer_unable_to_start, Toast.LENGTH_LONG).show()
            }
        }

        // set up sleep timer cancel button
        layout.sheetSleepTimerCancelButtonView.setOnClickListener {
            playerController.cancelSleepTimer()
        }

        // set up the debug log toggle switch
        layout.sheetDebugToggleButtonView.setOnClickListener {
            LogHelper.toggleDebugLogFileCreation(activity as Context)
        }

    }


    /* Builds playback controls - used after connected to player service */
    @SuppressLint("ClickableViewAccessibility") // it is probably okay to suppress this warning - the OnTouchListener on the time played view does only toggle the time duration / remaining display
    private fun buildPlaybackControls() {
        CoroutineScope(IO).launch {
            // get player state
            playerState = PreferencesHelper.loadPlayerState(activity as Context)

            // get current and up-next episode
            episode = collectionDatabase.episodeDao().findByMediaId(playerState.episodeMediaId)
            upNextEpisode = collectionDatabase.episodeDao().findByMediaId(playerState.upNextEpisodeMediaId)

            withContext(Main) {
                // main play/pause button
                layout.playButtonView.setOnClickListener {
                    onPlayButtonTapped(playerState.episodeMediaId, playerController.getPlaybackState())
                }

                // bottom sheet play/pause button
                layout.sheetPlayButtonView.setOnClickListener {
                    when (playerController.getPlaybackState()) {
                        PlaybackStateCompat.STATE_PLAYING -> playerController.transportControls.pause()
                        else -> playerController.transportControls.playFromMediaId(playerState.episodeMediaId, null)
                    }
                }

                // bottom sheet skip back button
                layout.sheetSkipBackButtonView.setOnClickListener {
                    when (playerState.playbackState == PlaybackStateCompat.STATE_PLAYING) {
                        true -> playerController.skipBack()
                        false -> Toast.makeText(activity as Context, R.string.toast_message_skipping_disabled, Toast.LENGTH_LONG).show()
                    }
                }

                // bottom sheet skip forward button
                layout.sheetSkipForwardButtonView.setOnClickListener {
                    when (playerState.playbackState == PlaybackStateCompat.STATE_PLAYING) {
                        true -> playerController.skipForward(episode?.duration ?: 0L)
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
                        playerController.seekTo(position.toLong())
                    }
                })

                // bottom sheet time played display
                layout.sheetTimePlayedView.setOnTouchListener { view, motionEvent ->
                    view.performClick()
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // show time remaining while touching the time played view
                            layout.displayTimeRemaining = true
                            this@PlayerFragment.playerController.requestProgressUpdate(resultReceiver)
                        }
                        MotionEvent.ACTION_UP -> {
                            // show episode duration when not touching the time played view anymore
                            layout.displayTimeRemaining = false
                            val duration = DateTimeHelper.convertToMinutesAndSeconds(episode?.duration ?: 0L)
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
                    val upNextEpisodeMediaId: String = upNextEpisode?.mediaId ?: String()
                    this@PlayerFragment.playerController.play(upNextEpisodeMediaId)
                    // MediaControllerCompat.getMediaController(activity as Activity).transportControls.playFromMediaId(upNextEpisodeMediaId, null) // todo remove
                    Toast.makeText(activity as Context, R.string.toast_message_up_next_start_playback, Toast.LENGTH_LONG).show()
                }

                // bottom sheet clear button for Up Next queue
                layout.sheetUpNextClearButton.setOnClickListener {
                    // clear up next
                    updateUpNext(String())
                    Toast.makeText(activity as Context, R.string.toast_message_up_next_removed_episode, Toast.LENGTH_LONG).show()
                }

                // bottom sheet playback speed button
                layout.sheetPlaybackSpeedButtonView.setOnClickListener {
                    // request playback speed change
                    this@PlayerFragment.playerController.changePlaybackSpeed(resultReceiver)
                }
                layout.sheetPlaybackSpeedButtonView.setOnLongClickListener {
                    if (playerState.playbackSpeed != 1f) {
                        val v = activity?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        v.vibrate(50)
                        // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
                        Toast.makeText(activity as Context, R.string.toast_message_playback_speed_reset, Toast.LENGTH_LONG).show()
                        // request playback speed reset
                        this@PlayerFragment.playerController.resetPlaybackSpeed(resultReceiver)
                    }
                    return@setOnLongClickListener true
                }

                // register a callback to stay in sync
                playerController.registerCallback(mediaControllerCallback)
            }

        }

    }


    /* Sets up the player */
    private fun setupPlayer() {
        // toggle player visibility
        if (layout.togglePlayerVisibility(activity as Context, playerState.playbackState)) {
            // CASE: player is visible - update player views
            layout.togglePlayButtons(playerState.playbackState)
            if (playerState.episodeMediaId.isNotEmpty()) {
                layout.updatePlayerViews(activity as Context, episode)
                layout.updatePlaybackSpeedView(activity as Context, playerState.playbackSpeed)
            }
            layout.updateUpNextViews(upNextEpisode)
        }
    }


    /* Sets up state of list podcast list */
    private fun setupList() {
        if (listLayoutState != null) {
            layout.layoutManager.onRestoreInstanceState(listLayoutState)
        }
    }


    /* Starts / pauses playback */
    private fun togglePlayback(startPlayback: Boolean, mediaId: String, playbackState: Int = PlaybackStateCompat.STATE_STOPPED) {
        playerState.episodeMediaId = mediaId
        playerState.playbackState = playbackState // = current state BEFORE desired startPlayback action
        // start / pause playback
        when (startPlayback) {
            true -> playerController.play(mediaId)
            false -> playerController.pause()
        }
    }


    /* Updates the Up Next queue */
    private fun updateUpNext(upNextEpisodeMediaId: String) {
        PreferencesHelper.saveUpNextMediaId(activity as Context, upNextEpisodeMediaId)
        if (upNextEpisodeMediaId.isNotEmpty()) {
            Toast.makeText(activity as Context, R.string.toast_message_up_next_added_episode, Toast.LENGTH_LONG).show()
        }
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
        } else if (NetworkHelper.isConnectedToCellular(activity as Context) && PreferencesHelper.loadEpisodeDownloadOverMobile(activity as Context)) {
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
                    PreferencesHelper.saveLastUpdateCollection(activity as Context)
                }
            }
        } else {
            ErrorDialog().show(activity as Context, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }


    /* Read OPML file */
    private fun readOpmlFile(opmlUri: Uri, permissionCheckNeeded: Boolean)  {
        when (permissionCheckNeeded) {
            true -> {
                // permission check
                if (ContextCompat.checkSelfPermission(activity as Context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    // save uri for later use in onRequestPermissionsResult
                    tempOpmlUriString = opmlUri.toString()
                    // permission is not granted - request it
                    ActivityCompat.requestPermissions(activity as Activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), Keys.PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
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
                    val deferred: Deferred<Array<String>> = async(Dispatchers.Default) { OpmlHelper.readSuspended(activity as Context, opmlUri) }
                    // wait for result and update collection
                    val feedUrls: Array<String> = deferred.await()
                    OpmlImportDialog(this@PlayerFragment).show(activity as Context, feedUrls)
                }
            }
        }
    }


    /* Handles this activity's start intent */
    private fun handleStartIntent() {
        if ((activity as Activity).intent.action != null) {
            when ((activity as Activity).intent.action) {
                Keys.ACTION_SHOW_PLAYER -> handleShowPlayer()
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
                scheme.startsWith("content") -> readOpmlFile(contentUri, false)
                // readSuspended opml from file uri
                scheme.startsWith("file") -> readOpmlFile(contentUri, true)
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
                // request current playback position and sleep timer state once
                MediaControllerCompat.getMediaController(activity as Activity).sendCommand(Keys.CMD_REQUEST_PROGRESS_UPDATE, null, resultReceiver)
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
        if (opmlFileString != null) {
            readOpmlFile(opmlFileString.toUri(), permissionCheckNeeded = false)
        }
    }


    /*
     * Defines callbacks for media browser service connection
     */
    private val mediaBrowserConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            // begin looking for changes in collection
            observeCollectionViewModel()

            // get the token for the MediaSession
            mediaBrowser.sessionToken.also { token ->
                // create a MediaControllerCompat
                val mediaController = MediaControllerCompat(activity as Context, token)
                // save the controller
                MediaControllerCompat.setMediaController(activity as Activity, mediaController)
                // initialize playerController
                playerController = PlayerController(mediaController)
            }
            playerServiceConnected = true

            mediaBrowser.subscribe(Keys.MEDIA_ID_ROOT, mediaBrowserSubscriptionCallback)

            // finish building the UI
            buildPlaybackControls()

            if (playerState.playbackState == PlaybackStateCompat.STATE_PLAYING) {
                // start requesting continuous position updates
                handler.removeCallbacks(periodicProgressUpdateRequestRunnable)
                handler.postDelayed(periodicProgressUpdateRequestRunnable, 0)
            } else {
                // request current playback position and sleep timer state once
                playerController.requestProgressUpdate(resultReceiver)
            }
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
            if (layout.togglePlayerVisibility(activity as Context, playbackState.state)) {
                // CASE: Player is visible
                layout.animatePlaybackButtonStateTransition(activity as Context, playbackState.state)
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
            playerController.requestProgressUpdate(resultReceiver)
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
    var resultReceiver: ResultReceiver = object: ResultReceiver(Handler(Looper.getMainLooper())) {
        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
            when (resultCode) {
                Keys.RESULT_CODE_PROGRESS_UPDATE -> {
                    if (resultData != null && resultData.containsKey(Keys.RESULT_DATA_PLAYBACK_PROGRESS)) {
                        layout.updateProgressbar(activity as Context, resultData.getLong(Keys.RESULT_DATA_PLAYBACK_PROGRESS, 0L), episode?.duration ?: 0L)
                    }
                    if (resultData != null && resultData.containsKey(Keys.RESULT_DATA_SLEEP_TIMER_REMAINING)) {
                        layout.updateSleepTimer(activity as Context, resultData.getLong(Keys.RESULT_DATA_SLEEP_TIMER_REMAINING, 0L))
                    } else {
                        layout.updateSleepTimer(activity as Context)
                    }
                }
                Keys.RESULT_CODE_PLAYBACK_SPEED -> {
                    if (resultData != null && resultData.containsKey(Keys.RESULT_DATA_PLAYBACK_SPEED)) {
                        playerState.playbackSpeed = resultData.getFloat(Keys.RESULT_DATA_PLAYBACK_SPEED, 1f)
                        layout.updatePlaybackSpeedView(activity as Context, playerState.playbackSpeed)
                    }
                }
            }
        }
    }
    /*
     * End of ResultReceiver
     */

}
