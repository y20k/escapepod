/*
 * PlayerService.kt
 * Implements the PlayerService class
 * PlayerService is Escapepod's foreground service that plays podcast audio and handles playback controls
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.media.session.PlaybackState
import android.os.*
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.*
import org.y20k.escapepod.collection.CollectionProvider
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.extensions.isActive
import org.y20k.escapepod.helpers.*
import org.y20k.escapepod.ui.PlayerState
import java.util.*
import kotlin.coroutines.CoroutineContext


/*
 * PlayerService class
 */
class PlayerService(): MediaBrowserServiceCompat(), Player.EventListener, CoroutineScope, SharedPreferences.OnSharedPreferenceChangeListener {


    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PlayerService::class.java)


    /* Main class variables */
    private lateinit var collectionDatabase: CollectionDatabase
    private var collectionProvider: CollectionProvider = CollectionProvider()
    private var isForegroundService: Boolean = false
    private var upNextEpisode: Episode? = null
    private lateinit var episode: Episode
    private lateinit var player: SimpleExoPlayer
    private lateinit var playerState: PlayerState
    private lateinit var backgroundJob: Job
    private lateinit var packageValidator: PackageValidator
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaController: MediaControllerCompat
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var userAgent: String
    private lateinit var sleepTimer: CountDownTimer
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var sleepTimerTimeRemaining: Long = 0L


    /* Overrides coroutineContext variable */
    override val coroutineContext: CoroutineContext get() = backgroundJob + Dispatchers.Main


    /* Overrides onCreate from Service */
    override fun onCreate() {
        super.onCreate()

        // initialize background job
        backgroundJob = Job()

        // set user agent
        userAgent = Util.getUserAgent(this, Keys.APPLICATION_NAME)

        // get the package validator // todo can be local?
        packageValidator = PackageValidator(this, R.xml.allowed_media_browser_callers)

        // fetch the player state
        playerState = PreferencesHelper.loadPlayerState(this)

        // create player
        player = createPlayer()

        // create a new MediaSession
        mediaSession = createMediaSession()
        sessionToken = mediaSession.sessionToken

        // because ExoPlayer will manage the MediaSession, add the service as a callback for state changes
        mediaController = MediaControllerCompat(this, mediaSession).also {
            it.registerCallback(MediaControllerCallback())
        }

        // initialize notification helper and notification manager
        notificationHelper = NotificationHelper(this)
        notificationManager = NotificationManagerCompat.from(this)

        // get instance of database
        collectionDatabase = CollectionDatabase.getInstance(application)

        // start watching for changes in shared preferences
        PreferencesHelper.registerPreferenceChangeListener(this, this as SharedPreferences.OnSharedPreferenceChangeListener)
    }


    /* Overrides onStartCommand from Service */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent != null && intent.action == Keys.ACTION_STOP && player.isPlaying) {
            stopPlayback()
        }

        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return Service.START_NOT_STICKY
    }


    /* Overrides onTaskRemoved from Service */
    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        // kill service, if MainActivity was canceled through task switcher
        //stopSelf()
    }


    /* Overrides onDestroy from Service */
    override fun onDestroy() {
        // set playback state
        if (playerState.playbackState == PlaybackStateCompat.STATE_PLAYING) {
            handlePlaybackChange(PlaybackStateCompat.STATE_PAUSED)
        }
        // release media session
        mediaSession.run {
            isActive = false
            release()
        }
        // cancel background job
        backgroundJob.cancel()
        // release player
        player.removeAnalyticsListener(analyticsListener)
        player.release()
        // stop watching for changes in shared preferences
        PreferencesHelper.unregisterPreferenceChangeListener(this, this as SharedPreferences.OnSharedPreferenceChangeListener)
    }


    /* Overrides onGetRoot from MediaBrowserService */ // todo: implement a hierarchical structure -> https://github.com/googlesamples/android-UniversalMusicPlayer/blob/47da058112cee0b70442bcd0370c1e46e830c66b/media/src/main/java/com/example/android/uamp/media/library/BrowseTree.kt
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        // Credit: https://github.com/googlesamples/android-UniversalMusicPlayer (->  MusicService)
        // LogHelper.d(TAG, "OnGetRoot: clientPackageName=$clientPackageName; clientUid=$clientUid ; rootHints=$rootHints")
        // to ensure you are not allowing any arbitrary app to browse your app's contents, you need to check the origin
        if (!packageValidator.isKnownCaller(clientPackageName, clientUid)) {
            // request comes from an untrusted package
            LogHelper.i(TAG, "OnGetRoot: Browsing NOT ALLOWED for unknown caller. "
                    + "Returning empty browser root so all apps can use MediaController."
                    + clientPackageName)
            return BrowserRoot(Keys.MEDIA_ID_EMPTY_ROOT, null)
        } else {
            // content style extras: see https://developer.android.com/training/cars/media#apply_content_style
            val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
            val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
            val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
            val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1
            val CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 2
            val extras = Bundle()
            extras.putBoolean(CONTENT_STYLE_SUPPORTED, true)
            extras.putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
            extras.putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
            return BrowserRoot(Keys.MEDIA_ID_ROOT, extras)
        }
    }


    /* Overrides onLoadChildren from MediaBrowserService */
    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        if (!collectionProvider.isInitialized()) {
            // use result.detach to allow calling result.sendResult from another thread:
            result.detach()
            collectionProvider.retrieveMedia(collectionDatabase, object: CollectionProvider.CollectionProviderCallback {
                override fun onEpisodeListReady(success: Boolean) {
                    if (success) {
                        loadChildren(parentId, result)
                    }
                }
            })
        } else {
            // if music catalog is already loaded/cached, load them into result immediately
            loadChildren(parentId, result)
        }
    }


    /* Overrides onPlayerStateChanged from Player.EventListener */
    override fun onPlayerStateChanged(playWhenReady: Boolean, playerState: Int) {
        when (playWhenReady) {
            // CASE: playWhenReady = true
            true -> {
                if (playerState == Player.STATE_READY) {
                    // active playback: update media session and save state
                    handlePlaybackChange(PlaybackStateCompat.STATE_PLAYING)
                } else if (playerState == Player.STATE_ENDED) {
                    // playback reached end: stop / end playback
                    handlePlaybackEnded()
                } else {
                    // not playing because the player is buffering, stopped or failed - check playbackState and player.getPlaybackError for details)
                }
            }
            // CASE: playWhenReady = false
            false -> {
                if (playerState == Player.STATE_READY) {
                    // stopped by app: update media session and save state
                    handlePlaybackChange(PlaybackStateCompat.STATE_PAUSED)
                } else if (playerState == Player.STATE_ENDED) {
                    // ended by app: update media session and save state
                    handlePlaybackChange(PlaybackStateCompat.STATE_STOPPED)
                }
                // stop sleep timer - if running
                cancelSleepTimer()
            }
        }
    }


    /* Overrides onSharedPreferenceChanged from SharedPreferences.OnSharedPreferenceChangeListener */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID -> {
                GlobalScope.launch {
                    val mediaId: String = sharedPreferences?.getString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, String()) ?: String()
                    playerState.upNextEpisodeMediaId = mediaId
                    upNextEpisode = collectionDatabase.episodeDao().findByMediaId(mediaId)
                    LogHelper.e(TAG, "UPNEXT => ${upNextEpisode?.title}") // todo remove
                }
            }
        }
    }


    /* Updates media session and save state */
    private fun handlePlaybackChange(playbackState: Int) {
        // update playback state and position
        GlobalScope.launch {
            withContext(Dispatchers.Main) {
                episode = Episode(episode, playbackState = playbackState, playbackPosition = player.currentPosition)
            }
            withContext(Dispatchers.IO) {
                collectionDatabase.episodeDao().upsert(episode)
                collectionDatabase.episodeDao().setPlaybackStateForAllEpisodes(playbackState = PlaybackState.STATE_STOPPED, exclude = episode.mediaId)
                // update player state
                updatePlayerState(playbackState)
                // update media session
                mediaSession.setPlaybackState(createPlaybackState(playbackState, episode.playbackPosition))
                mediaSession.setActive(playbackState != PlaybackStateCompat.STATE_STOPPED)
            }
            withContext(Dispatchers.Main) {
                // start/stop updating playback position
                if (player.isPlaying) {
                    handler.removeCallbacks(periodicPlaybackPositionUpdateRunnable)
                    handler.postDelayed(periodicPlaybackPositionUpdateRunnable, 0)
                } else {
                    handler.removeCallbacks(periodicPlaybackPositionUpdateRunnable)
                }
            }
        }
    }


    /* End of episode: stop playback or start episode from up-next queue */
    private fun handlePlaybackEnded() {
        GlobalScope.launch {
            // update playback state and position
            episode = Episode(episode, playbackState = PlaybackStateCompat.STATE_STOPPED, playbackPosition = episode.duration)
            collectionDatabase.episodeDao().upsert(episode)
            // CASE: Up next episode available
            if (upNextEpisode != null) {
                // get up next episode
                episode = upNextEpisode as Episode
                // clear up-next
                upNextEpisode = null
                // start playback
                withContext(Dispatchers.Main) { startPlayback() }
            }
            // CASE: Up next episode NOT available
            else {
                // clear up-next
                upNextEpisode = null
                // stop playback
                withContext(Dispatchers.Main) { stopPlayback() }
            }
        }
    }


    /* Creates a new MediaSession */
    private fun createMediaSession(): MediaSessionCompat {
        val initialPlaybackState: Int = PreferencesHelper.loadPlayerPlaybackState(this)
        val sessionActivityPendingIntent =
                packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                    PendingIntent.getActivity(this, 0, sessionIntent, 0)
                }
        return MediaSessionCompat(this, TAG)
                .apply {
                    setSessionActivity(sessionActivityPendingIntent)
                    setCallback(mediaSessionCallback)
                    setPlaybackState(createPlaybackState(initialPlaybackState, 0))
                }
    }


    /* Creates a simple exo player */
    private fun createPlayer(): SimpleExoPlayer {
        if (this::player.isInitialized) {
            player.removeAnalyticsListener(analyticsListener)
            player.release()
        }
        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
                .setContentType(C.CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()
        val player = SimpleExoPlayer.Builder(this)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setMediaSourceFactory(ProgressiveMediaSource.Factory(DefaultDataSourceFactory(this, userAgent))) // check if necessary
                .build()
        player.addListener(this@PlayerService)
        player.addAnalyticsListener(analyticsListener)
        return player
    }


    /* Prepares player with media source created from current episode */
    private fun preparePlayer() {
        // todo only prepare if not already prepared
        // build and set media item
        val mediaItem: MediaItem = MediaItem.fromUri(episode.audio)
        // set media item
        player.setMediaItem(mediaItem, episode.playbackPosition)
        // prepare
        player.prepare()
    }



    /* Start playback with current episode */
    private fun startPlayback() {
        // sanity check
        if (!this::episode.isInitialized) {
            LogHelper.e(TAG, "Unable to start playback. No episode has been loaded.")
            return
        }
        LogHelper.d(TAG, "Starting Playback. Position: ${episode.playbackPosition}. Duration: ${episode.duration}")
        // reset playback position if necessary
        if (episode.isFinished()) {
            episode = Episode(episode, playbackState = PlaybackStateCompat.STATE_STOPPED, playbackPosition = 0L)
        }
        // check if episode is in up-next queue - reset up next
        if (playerState.upNextEpisodeMediaId == episode.mediaId){
            upNextEpisode = null
        }
        // update metadata
        mediaSession.setMetadata(CollectionHelper.buildEpisodeMediaMetadata(this@PlayerService, episode))
        // prepare player
        preparePlayer()
        // start playback
        player.playWhenReady = true
    }


    /* Stop playback */
    private fun stopPlayback() {
        LogHelper.d(TAG, "Stopping Playback")
        // update playback position
        if (this::episode.isInitialized) {
            episode = Episode(episode, playbackPosition = player.contentPosition, playbackState = PlaybackStateCompat.STATE_PAUSED)
        }
        // pause playback
        player.playWhenReady = false
    }


    /* Creates playback state - actions for playback state to be used in media session callback */
    private fun createPlaybackState(state: Int, position: Long): PlaybackStateCompat {
        val skipActions: Long = PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_REWIND or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO
        when(state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                return PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PLAYING, position, 1f)
                        .setActions(PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or skipActions)
                        .build()
            }
            PlaybackStateCompat.STATE_PAUSED -> {
                return PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PAUSED, position, 0f)
                        .setActions(PlaybackStateCompat.ACTION_PLAY)
                        .build()
            }
            else -> {
                return PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_STOPPED, position, 0f)
                        .setActions(PlaybackStateCompat.ACTION_PLAY)
                        .build()
            }
        }
    }


    /* Starts sleep timer / adds default duration to running sleeptimer */
    private fun startSleepTimer() {
        // stop running timer
        if (sleepTimerTimeRemaining > 0L && this::sleepTimer.isInitialized) {
            sleepTimer.cancel()
        }
        // initialize timer
        sleepTimer = object:CountDownTimer (Keys.SLEEP_TIMER_DURATION + sleepTimerTimeRemaining, Keys.SLEEP_TIMER_INTERVAL) {
            override fun onFinish() {
                LogHelper.v(TAG, "Sleep timer finished. Sweet dreams.")
                // reset time remaining
                sleepTimerTimeRemaining = 0L
                // stop playback
                stopPlayback()
            }
            override fun onTick(millisUntilFinished: Long) {
                sleepTimerTimeRemaining = millisUntilFinished
            }
        }
        // start timer
        sleepTimer.start()
    }


    /* Cancels sleep timer */
    private fun cancelSleepTimer() {
        if (this::sleepTimer.isInitialized) {
            sleepTimerTimeRemaining = 0L
            sleepTimer.cancel()
        }
    }


    /* Updates / increases the playback speed */
    private fun updatePlaybackSpeed(currentSpeed: Float = 1f): Float {
        var newSpeed: Float = 1f
        // circle through the speed presets
        val iterator = Keys.PLAYBACK_SPEEDS.iterator()
        while (iterator.hasNext()) {
            // found current speed in array
            if (iterator.next() == currentSpeed) {
                if (iterator.hasNext()) {
                    newSpeed = iterator.next()
                }
                break
            }
        }
        // apply new speed
        setPlaybackSpeed(newSpeed)
        return newSpeed
    }


    /* Sets playback speed */
    private fun setPlaybackSpeed(speed: Float = 1f) {
        // update playback parameters - speed up playback
        player.setPlaybackParameters(PlaybackParameters(speed))
        // save speed
        playerState.playbackSpeed = speed
        PreferencesHelper.savePlayerPlaybackSpeed(this, speed)
    }



    /* Loads media items into result - assumes that collectionProvider is initialized */
    private fun loadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
        when (parentId) {
            Keys.MEDIA_ID_ROOT -> {
                collectionProvider.episodeListByDate.forEach { item ->
                    mediaItems.add(item)
                }
            }
            Keys.MEDIA_ID_EMPTY_ROOT -> {
                // do nothing
            }
            else -> {
                // log error
                LogHelper.w(TAG, "Skipping unmatched parentId: $parentId")
            }
        }
        result.sendResult(mediaItems)
    }


    /* Updates and saves the state of the player ui */
    private fun updatePlayerState (playbackState: Int) {
        playerState.episodeMediaId = episode.mediaId
        playerState.playbackState = playbackState
        playerState.upNextEpisodeMediaId = upNextEpisode?.mediaId ?: String()
        // playerState.playbackSpeed is updated separately
        PreferencesHelper.savePlayerState(this, playerState)
    }


    /*
     * Custom AnalyticsListener that enables AudioFX equalizer integration
     */
    private var analyticsListener = object: AnalyticsListener {
        override fun onAudioSessionId(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            super.onAudioSessionId(eventTime, audioSessionId)
            // integrate with system equalizer (AudioFX)
            val intent: Intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            sendBroadcast(intent)
        }
    }
    /*
     * End of declaration
     */


    /*
    * Runnable: Periodically requests playback position (and sleep timer if running)
    */
    private val periodicPlaybackPositionUpdateRunnable: Runnable = object : Runnable {
        override fun run() {
            if (this@PlayerService::episode.isInitialized) {
                GlobalScope.launch {
                    val playbackPosition: Long
                    withContext(Dispatchers.Main) { playbackPosition = player.currentPosition }
                    withContext(Dispatchers.IO) {collectionDatabase.episodeDao().updatePlaybackPosition(mediaId = episode.mediaId, playbackPosition = playbackPosition)}
                }
            }
            // use the handler to start runnable again after specified delay (every 20 seconds)
            handler.postDelayed(this, 20000)
        }
    }
    /*
     * End of declaration
     */


    /*
     * Callback: Defines callbacks for active media session
     */
    private var mediaSessionCallback = object: MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (!this@PlayerService::episode.isInitialized) {
                // get current media id and hand over to onPlayFromMediaId
                val currentEpisodeMediaId: String = PreferencesHelper.loadCurrentMediaId(this@PlayerService)
                onPlayFromMediaId(currentEpisodeMediaId, null)
            } else {
                // start playback
                startPlayback()
            }
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            GlobalScope.launch {
                // get episode
                val newEpisode: Episode? = withContext(Dispatchers.IO) { collectionDatabase.episodeDao().findByMediaId(mediaId ?: String())}
                // start playback
                if (newEpisode != null) {
                    withContext(Dispatchers.Main) {
                        // stop playback if necessary
                        if (player.isPlaying) {
                            stopPlayback()
                        }
                        episode = newEpisode
                        startPlayback() }
                }
            }
        }

        override fun onPause() {
            stopPlayback()
        }

        override fun onStop() {
            stopPlayback()
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            // SPECIAL CASE: Empty query - user provided generic string e.g. 'Play music'
            if (query.isNullOrEmpty()) {
                // try to get newest episode
                val episodeMediaItem: MediaBrowserCompat.MediaItem? = collectionProvider.getNewestEpisode()
                if (episodeMediaItem != null) {
                    onPlayFromMediaId(episodeMediaItem.mediaId, null)
                } else {
                    // unable to get the first episode - notify user
                    Toast.makeText(this@PlayerService, R.string.toast_message_error_no_podcast_found, Toast.LENGTH_LONG).show()
                    LogHelper.e(TAG, "Unable to start playback. Please add a podcast and download an Episode first.")
                }
            }
            // NORMAL CASE: Try to match podcast name and voice query
            else {
                val queryLowercase: String = query.toLowerCase(Locale.getDefault())
                collectionProvider.episodeListByDate.forEach { mediaItem ->
                    // get podcast name (here -> subtitle)
                    val podcastName: String = mediaItem.description.subtitle.toString().toLowerCase(Locale.getDefault())
                    // FIRST: try to match the whole query
                    if (podcastName == queryLowercase) {
                        // start playback of newest podcast episode
                        onPlayFromMediaId(mediaItem.description.mediaId, null)
                        return
                    }
                    // SECOND: try to match parts of the query
                    else {
                        val words: List<String> = queryLowercase.split(" ")
                        words.forEach { word ->
                            if (podcastName.contains(word)) {
                                // start playback of newest podcast episode
                                onPlayFromMediaId(mediaItem.description.mediaId, null)
                                return
                            }
                        }
                    }
                }
                // NO MATCH: unable to match query - notify user
                Toast.makeText(this@PlayerService, R.string.toast_message_error_no_podcast_matches_search, Toast.LENGTH_LONG).show()
                LogHelper.e(TAG, "Unable to find a podcast that matches your search query: $query")
            }
        }

        override fun onFastForward() {
            LogHelper.d(TAG, "Skipping forward")
            // update position
            var position: Long = player.currentPosition + Keys.SKIP_FORWARD_TIME_SPAN
            if (position > episode.duration) {
                position =  episode.duration
            }
            player.seekTo(position)
        }

        override fun onRewind() {
            LogHelper.d(TAG, "Skipping back")
            var position: Long = player.currentPosition - Keys.SKIP_BACK_TIME_SPAN
            if (position < 0L) {
                position = 0L
            }
            player.seekTo(position)
        }

        override fun onSkipToPrevious() {
            // note: rewind is the new skip to previous ^o^
            onRewind()
        }

        override fun onSkipToNext() {
            // note: fast forward is the new skip to next ^o^
            onFastForward()
        }

        override fun onSeekTo(position: Long) {
            episode = Episode(episode, playbackPosition = position, playbackState = episode.playbackState)
            player.seekTo(position)
        }

        override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
            when (command) {
                Keys.CMD_RELOAD_PLAYER_STATE -> {
                    playerState = PreferencesHelper.loadPlayerState(this@PlayerService)
                }
                Keys.CMD_REQUEST_PROGRESS_UPDATE -> {
                    if (cb != null) {
                        // check if episode has been prepared - assumes that then the player has been prepared as well
                        if (this@PlayerService::episode.isInitialized) {
                            val playbackProgressBundle: Bundle = bundleOf(Keys.RESULT_DATA_PLAYBACK_PROGRESS to player.currentPosition)
                            if (sleepTimerTimeRemaining > 0L) {
                                playbackProgressBundle.putLong(Keys.RESULT_DATA_SLEEP_TIMER_REMAINING, sleepTimerTimeRemaining)
                            }
                            cb.send(Keys.RESULT_CODE_PROGRESS_UPDATE, playbackProgressBundle)
                        }
                    }
                }
                Keys.CMD_START_SLEEP_TIMER -> {
                    startSleepTimer()
                }
                Keys.CMD_CANCEL_SLEEP_TIMER -> {
                    cancelSleepTimer()
                }
                Keys.CMD_CHANGE_PLAYBACK_SPEED -> {
                    if (cb != null) {
                        // change player speed
                        val newPlaybackSpeed: Float = updatePlaybackSpeed(playerState.playbackSpeed)
                        // send back new playback speed
                        val playbackSpeedBundle: Bundle = bundleOf(Keys.RESULT_DATA_PLAYBACK_SPEED to newPlaybackSpeed)
                        cb.send(Keys.RESULT_CODE_PLAYBACK_SPEED, playbackSpeedBundle)
                    }
                }
                Keys.CMD_RESET_PLAYBACK_SPEED -> {
                    if (cb != null) {
                        // change player speed
                        setPlaybackSpeed(1f)
                        // send back new playback speed
                        val playbackSpeedBundle: Bundle = bundleOf(Keys.RESULT_DATA_PLAYBACK_SPEED to 1f)
                        cb.send(Keys.RESULT_CODE_PLAYBACK_SPEED, playbackSpeedBundle)
                    }
                }
            }
        }

    }
    /*
     * End of callback
     */


    /*
     * Inner class: Class to receive callbacks about state changes to the MediaSessionCompat - handles notification
     * Source: https://github.com/googlesamples/android-UniversalMusicPlayer/blob/master/common/src/main/java/com/example/android/uamp/media/MusicService.kt
     */
    private inner class MediaControllerCallback : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            mediaController.playbackState?.let { updateNotification(it) }
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            state?.let { updateNotification(it) }
        }

        private fun updateNotification(state: PlaybackStateCompat) {
            // skip building a notification when state is "none" and metadata is null
            // val notification = if (mediaController.metadata != null && state.state != PlaybackStateCompat.STATE_NONE) {
            val notification = if (state.state != PlaybackStateCompat.STATE_NONE) {
                notificationHelper.buildNotification(mediaSession.sessionToken, episode)
            } else {
                null
            }

            when (state.isActive) {
                // CASE: Playback has started
                true -> {
                    /**
                     * This may look strange, but the documentation for [Service.startForeground]
                     * notes that "calling this method does *not* put the service in the started
                     * state itself, even though the name sounds like it."
                     */
                    if (notification != null) {
                        notificationManager.notify(Keys.NOTIFICATION_NOW_PLAYING_ID, notification)
                        if (!isForegroundService) {
                            ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, this@PlayerService.javaClass))
                            startForeground(Keys.NOTIFICATION_NOW_PLAYING_ID, notification)
                            isForegroundService = true
                        }
                    }
                }
                // CASE: Playback has stopped
                false -> {
                    if (isForegroundService) {
                        stopForeground(false)
                        isForegroundService = false

                        // if playback has ended, also stop the service.
                        if (state.state == PlaybackStateCompat.STATE_NONE) {
                            stopSelf()
                        }

                        if (notification != null && state.state != PlaybackStateCompat.STATE_STOPPED) {
                            notificationManager.notify(Keys.NOTIFICATION_NOW_PLAYING_ID, notification)
                        } else {
                            // remove notification - playback ended (or buildNotification failed)
                            stopForeground(true)
                        }
                    }

                }
            }
        }
    }

}