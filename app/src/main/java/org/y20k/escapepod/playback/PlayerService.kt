/*
 * PlayerService.kt
 * Implements the PlayerService class
 * PlayerService is Escapepod's foreground service that plays podcast audio
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.media.session.PlaybackState
import android.net.Uri
import android.os.*
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import org.y20k.escapepod.collection.CollectionProvider
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.helpers.*
import org.y20k.escapepod.ui.PlayerState
import java.util.*
import kotlin.collections.ArrayList


/*
 * PlayerService class
 */
class PlayerService: MediaBrowserServiceCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PlayerService::class.java)


    /* Main class variables */
    private lateinit var collectionDatabase: CollectionDatabase
    private var collectionProvider: CollectionProvider = CollectionProvider()
    private var isForegroundService: Boolean = false
    private var streaming: Boolean = false
    private var upNextEpisode: Episode? = null
    private lateinit var episode: Episode
    private lateinit var playerState: PlayerState
    private lateinit var packageValidator: PackageValidator
    protected lateinit var mediaSession: MediaSessionCompat
    protected lateinit var mediaSessionConnector: MediaSessionConnector
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var userAgent: String
    private lateinit var sleepTimer: CountDownTimer
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var sleepTimerTimeRemaining: Long = 0L

    private val attributes = AudioAttributes.Builder()
            .setContentType(C.CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

    private val player: SimpleExoPlayer by lazy {
        SimpleExoPlayer.Builder(this).build().apply {
            setAudioAttributes(attributes, true)
            setHandleAudioBecomingNoisy(true)
            setPauseAtEndOfMediaItems(true)
            addListener(playerListener)
            addAnalyticsListener(analyticsListener)
        }
    }


    /* Overrides onCreate from Service */
    override fun onCreate() {
        super.onCreate()
        // set user agent
        userAgent = Util.getUserAgent(this, Keys.APPLICATION_NAME)

        // get the package validator // todo can be local?
        packageValidator = PackageValidator(this, R.xml.allowed_media_browser_callers)

        // fetch the player state
        playerState = PreferencesHelper.loadPlayerState()

        // create MediaSession
        createMediaSession()

        // ExoPlayer manages MediaSession
        mediaSessionConnector = MediaSessionConnector(mediaSession)
        mediaSessionConnector.setPlaybackPreparer(preparer)
        mediaSessionConnector.setMediaButtonEventHandler(buttonEventHandler)
        mediaSessionConnector.setQueueNavigator(object : TimelineQueueNavigator(mediaSession) {
            override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
                // create media description - used in notification
                return CollectionHelper.buildEpisodeMediaDescription(this@PlayerService, episode)
            }
        })

        // initialize notification helper
        notificationHelper = NotificationHelper(this, mediaSession.sessionToken, notificationListener)
        notificationHelper.showNotificationForPlayer(player)

        // get instance of database
        collectionDatabase = CollectionDatabase.getInstance(application)

        // get up next episode
        CoroutineScope(IO).launch {
            upNextEpisode = collectionDatabase.episodeDao().findByMediaId(playerState.upNextEpisodeMediaId)
        }

        // start watching for changes in shared preferences
        PreferencesHelper.registerPreferenceChangeListener(this as SharedPreferences.OnSharedPreferenceChangeListener)
    }


    /* Overrides onTaskRemoved from Service */
    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        // kill service, if MainActivity was canceled through task switcher
        //stopSelf()
    }


    /* Overrides onDestroy from Service */
    override fun onDestroy() {
        // set playback state if possible / necessary
        if (this::episode.isInitialized && (player.isPlaying)) {
            handlePlaybackChange(PlaybackStateCompat.STATE_PAUSED)
        }
        // release media session
        mediaSession.run {
            isActive = false
            release()
        }
        // release player
        player.removeAnalyticsListener(analyticsListener)
        player.removeListener(playerListener)
        player.release()
        // stop watching for changes in shared preferences
        PreferencesHelper.unregisterPreferenceChangeListener(this as SharedPreferences.OnSharedPreferenceChangeListener)
    }


    /* Overrides onGetRoot from MediaBrowserService */ // todo: implement a hierarchical structure -> https://github.com/googlesamples/android-UniversalMusicPlayer/blob/47da058112cee0b70442bcd0370c1e46e830c66b/media/src/main/java/com/example/android/uamp/media/library/BrowseTree.kt
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        LogHelper.d(TAG, "onGetRoot $rootHints| is recent request = ${rootHints?.getBoolean(BrowserRoot.EXTRA_RECENT) ?: false}") // todo remove
        // Credit: https://github.com/googlesamples/android-UniversalMusicPlayer (->  MusicService)
        // LogHelper.d(TAG, "OnGetRoot: clientPackageName=$clientPackageName; clientUid=$clientUid ; rootHints=$rootHints")
        // to ensure you are not allowing any arbitrary app to browse your app's contents, you need to check the origin
        if (!packageValidator.isKnownCaller(clientPackageName, clientUid)) {
            // request comes from an untrusted package
            LogHelper.i(TAG, "OnGetRoot: Browsing NOT ALLOWED for unknown caller. "
                    + "Returning empty browser root so all apps can use MediaController."
                    + clientPackageName)
            return BrowserRoot(Keys.MEDIA_EMPTY_ROOT, null)
        } else {
            // content style extras: see https://developer.android.com/training/cars/media#apply_content_style
            val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
            val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
            val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
            val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1
            val CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 2
            val extras: Bundle = bundleOf(
                    CONTENT_STYLE_SUPPORTED to true,
                    CONTENT_STYLE_BROWSABLE_HINT to CONTENT_STYLE_GRID_ITEM_HINT_VALUE,
                    CONTENT_STYLE_PLAYABLE_HINT to CONTENT_STYLE_LIST_ITEM_HINT_VALUE
            )
            val isRecentRequest = rootHints?.getBoolean(BrowserRoot.EXTRA_RECENT) ?: false
            val browserRootPath = if (isRecentRequest) Keys.MEDIA_RECENT_ROOT else Keys.MEDIA_BROWSABLE_ROOT
            return BrowserRoot(browserRootPath, extras)
        }
    }


    /* Overrides onLoadChildren from MediaBrowserService */
    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        if (!collectionProvider.isInitialized()) {
            // use result.detach to allow calling result.sendResult from another thread:
            result.detach()
            collectionProvider.retrieveMedia(collectionDatabase, object : CollectionProvider.CollectionProviderCallback {
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


    /* Overrides onSharedPreferenceChanged from SharedPreferences.OnSharedPreferenceChangeListener */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID -> {
                CoroutineScope(IO).launch {
                    // update up next episode
                    val mediaId: String = sharedPreferences?.getString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, String())
                            ?: String()
                    playerState.upNextEpisodeMediaId = mediaId
                    upNextEpisode = collectionDatabase.episodeDao().findByMediaId(mediaId)
                }
            }
        }
    }


    /* Updates media session and save state */
    private fun handlePlaybackChange(playbackState: Int, playbackPosition: Long = player.currentPosition) {
        // update episode
        episode = Episode(episode, playbackState = playbackState, playbackPosition = playbackPosition)
        // update player state
        updatePlayerState(playbackState)
        // toggle updating playback position
        if (player.isPlaying) {
            handler.removeCallbacks(periodicPlaybackPositionUpdateRunnable)
            handler.postDelayed(periodicPlaybackPositionUpdateRunnable, 0)
            notificationHelper.showNotificationForPlayer(player)
            LogHelper.d(TAG, "Playback Started. Position: ${episode.playbackPosition}. Duration: ${episode.duration}")
        } else {
            handler.removeCallbacks(periodicPlaybackPositionUpdateRunnable)
            episode = Episode(episode, playbackPosition = playbackPosition, playbackState = PlaybackStateCompat.STATE_PAUSED)
            // notification is hidden via CMD_DISMISS_NOTIFICATION
            LogHelper.d(TAG, "Playback Stopped. Position: ${episode.playbackPosition}. Duration: ${episode.duration}")
        }
        // save episode
        CoroutineScope(IO).launch {
            collectionDatabase.episodeDao().upsert(episode)
            collectionDatabase.episodeDao().setPlaybackStateForAllEpisodes(playbackState = PlaybackState.STATE_STOPPED, exclude = episode.mediaId)
        }
        // stop sleep timer - if running
        if (!player.playWhenReady) {
            cancelSleepTimer()
        }
    }


    /* Try to start episode from up-next queue */
    private fun tryToStartUpNextEpisode() {
        if (upNextEpisode != null) {
            // get up next episode
            episode = upNextEpisode as Episode
            // clear up-next
            upNextEpisode = null
            // prepare player and start playback
            preparePlayer(true)
        } else {
            notificationHelper.hideNotification()
        }
    }


    /* Creates a new MediaSession */
    private fun createMediaSession() {
        val sessionActivityPendingIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
            PendingIntent.getActivity(this, 0, sessionIntent, 0)
        }
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setSessionActivity(sessionActivityPendingIntent)
            isActive = true
        }
        sessionToken = mediaSession.sessionToken
    }


    /* Prepares player with media source created from current episode */
    private fun preparePlayer(playWhenReady: Boolean) {
        // sanity check
        if (!this::episode.isInitialized) {
            LogHelper.e(TAG, "Unable to start playback. No episode has been loaded.")
            return
        }

        // TODO check if episode is in up-next queue - reset up next - save PreferencesHelper.saveUpNextMediaId

        // reset playback position if necessary // todo
        if (episode.duration != 0L && episode.isFinished()) {
            episode = Episode(episode, playbackState = PlaybackStateCompat.STATE_STOPPED, playbackPosition = 0L)
            player.seekTo(0L)
        }

        when (episode.audio.isEmpty()) {
            // CASE: Streaming Playback
            true -> {
                // check if not already prepared
                if (player.currentMediaItem?.playbackProperties?.uri.toString() != episode.remoteAudioFileLocation) {
                    player.setMediaItem(MediaItem.fromUri(episode.remoteAudioFileLocation))
                }
                if (episode.duration == 0L) {
                    CoroutineScope(IO).launch {
                        val duration: Long = AudioHelper.getDuration(episode.remoteAudioFileLocation)
                        episode = Episode(episode, duration = duration)
                        collectionDatabase.episodeDao().update(episode)
                    }
                }
            }
            // CASE: Offline Playback (default)
            false -> {
                // check if not already prepared
                if (player.currentMediaItem?.playbackProperties?.uri.toString() != episode.audio) {
                    player.setMediaItem(MediaItem.fromUri(episode.audio))
                }
            }
        }

        // jump to playback position
        player.seekTo(episode.playbackPosition)

        // prepare
        player.prepare()

        // update media session connector
        mediaSessionConnector.setPlayer(player)

        // set playWhenReady state
        player.playWhenReady = playWhenReady
    }


    /* Starts sleep timer / adds default duration to running sleeptimer */
    private fun startSleepTimer() {
        // stop running timer
        if (sleepTimerTimeRemaining > 0L && this::sleepTimer.isInitialized) {
            sleepTimer.cancel()
        }
        // initialize timer
        sleepTimer = object: CountDownTimer(Keys.SLEEP_TIMER_DURATION + sleepTimerTimeRemaining, Keys.SLEEP_TIMER_INTERVAL) {
            override fun onFinish() {
                LogHelper.v(TAG, "Sleep timer finished. Sweet dreams.")
                // reset time remaining
                sleepTimerTimeRemaining = 0L
                // pause playback
                player.pause()
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
        PreferencesHelper.savePlayerPlaybackSpeed(speed)
    }


    /* Loads media items into result - assumes that collectionProvider is initialized */
    private fun loadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
        when (parentId) {
            Keys.MEDIA_BROWSABLE_ROOT -> {
                collectionProvider.episodeListByDate.forEach { item ->
                    mediaItems.add(item)
                }
            }
            Keys.MEDIA_RECENT_ROOT -> {
                // todo implement
            }
            else -> {
                // log error
                LogHelper.w(TAG, "Skipping unmatched parentId: $parentId")
            }
        }
        result.sendResult(mediaItems)
    }


    /* Updates and saves the state of the player ui */
    private fun updatePlayerState(playbackState: Int) {
        playerState.episodeMediaId = episode.mediaId
        playerState.playbackState = playbackState
        playerState.upNextEpisodeMediaId = upNextEpisode?.mediaId ?: String()
        // playerState.playbackSpeed is updated separately
        PreferencesHelper.savePlayerState(playerState)
    }


    /*
     * Custom AnalyticsListener that enables AudioFX equalizer integration
     */
    private var analyticsListener = object: AnalyticsListener {
        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            super.onAudioSessionIdChanged(eventTime, audioSessionId)
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
                val playbackPosition: Long = player.currentPosition
                CoroutineScope(IO).launch {
                    collectionDatabase.episodeDao().updatePlaybackPosition(mediaId = episode.mediaId, playbackPosition = playbackPosition)
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
     * Player.Listener: Listens for ExoPlayer Events
     */
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean){
            if (isPlaying) {
                // active playback
                handlePlaybackChange(PlaybackStateCompat.STATE_PLAYING)
            } else {
                // handled in onPlayWhenReadyChanged
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (!playWhenReady) {
                // detect dismiss action
                if (player.mediaItemCount == 0) {
                    stopSelf()
                }
                when (reason) {
                    Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> {
                        // playback reached end: stop / end playback
                        handlePlaybackChange(PlaybackStateCompat.STATE_STOPPED, playbackPosition = episode.duration)
                        tryToStartUpNextEpisode()
                    }
                    else -> {
                        // playback has been paused by user or OS: update media session and save state
                        // PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST or
                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS or
                        // PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY or
                        // PLAY_WHEN_READY_CHANGE_REASON_REMOTE
                        handlePlaybackChange(PlaybackStateCompat.STATE_PAUSED)
                    }
                }
            }
        }
    }
    /*
     * End of declaration
     */


    /*
     * NotificationListener: handles foreground state of service
     */
    private val notificationListener = object : PlayerNotificationManager.NotificationListener {
        override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
            super.onNotificationCancelled(notificationId, dismissedByUser)
            stopForeground(true)
            isForegroundService = false
            stopSelf()
        }

        override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
            super.onNotificationPosted(notificationId, notification, ongoing)
            if (ongoing && !isForegroundService) {
                ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, this@PlayerService.javaClass))
                startForeground(Keys.NOW_PLAYING_NOTIFICATION_ID, notification)
                isForegroundService = true
            }
        }
    }
    /*
     * End of declaration
     */


    /*
     * MediaButtonEventHandler: overrides headphone next/previous button behavior
     */
    private val buttonEventHandler = object : MediaSessionConnector.MediaButtonEventHandler {

        override fun onMediaButtonEvent(player: Player, controlDispatcher: ControlDispatcher, mediaButtonEvent: Intent): Boolean {
            val event: KeyEvent? = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            when (event?.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    if (event.action == KeyEvent.ACTION_UP && player.isPlaying && this@PlayerService::episode.isInitialized) {
                        val episodeDuration: Long = episode.duration
                        var position: Long = player.currentPosition + Keys.SKIP_FORWARD_TIME_SPAN
                        if (position > episodeDuration && episodeDuration != 0L) position = episodeDuration
                        player.seekTo(position)
                    }
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    if (event.action == KeyEvent.ACTION_UP && player.isPlaying) {
                        var position: Long = player.currentPosition - Keys.SKIP_BACK_TIME_SPAN
                        if (position < 0L) position = 0L
                        player.seekTo(position)
                    }
                    return true
                }
                else -> return false
            }
        }
    }
    /*
     * End of declaration
     */


    /*
     * PlaybackPreparer: Handles prepare and play requests - as well as custom commands like sleep timer control
     */
    private val preparer = object : MediaSessionConnector.PlaybackPreparer {

        override fun getSupportedPrepareActions(): Long =
                PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                        PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH or
                        PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH

        override fun onPrepareFromUri(uri: Uri, playWhenReady: Boolean, extras: Bundle?) = Unit

        override fun onPrepare(playWhenReady: Boolean) {
            if (this@PlayerService::episode.isInitialized) {
                preparePlayer(playWhenReady)
            } else {
                val currentEpisodeMediaId: String = PreferencesHelper.loadCurrentMediaId()
                onPrepareFromMediaId(currentEpisodeMediaId, playWhenReady, null)
            }
        }

        override fun onPrepareFromMediaId(mediaId: String, playWhenReady: Boolean, extras: Bundle?) {
            // save state of current episode before starting playback of new one
            if (this@PlayerService::episode.isInitialized && (player.isPlaying)) {
                handlePlaybackChange(PlaybackStateCompat.STATE_PAUSED)
            }
            CoroutineScope(IO).launch {
                // get episode
                val newEpisode: Episode? = collectionDatabase.episodeDao().findByMediaId(mediaId)
                // start playback
                if (newEpisode != null) {
                    withContext(Main) {
                        episode = newEpisode
                        preparePlayer(playWhenReady)
                    }
                }
            }
        }

        override fun onPrepareFromSearch(query: String, playWhenReady: Boolean, extras: Bundle?) {

            // SPECIAL CASE: Empty query - user provided generic string e.g. 'Play music'
            if (query.isEmpty()) {
                // try to get newest episode
                val episodeMediaItem: MediaBrowserCompat.MediaItem? = collectionProvider.getNewestEpisode()
                if (episodeMediaItem != null) {
                    onPrepareFromMediaId(episodeMediaItem.mediaId!!, playWhenReady = true, extras = null)
                } else {
                    // unable to get the first episode - notify user
                    Toast.makeText(this@PlayerService, R.string.toast_message_error_no_podcast_found, Toast.LENGTH_LONG).show()
                    LogHelper.e(TAG, "Unable to start playback. Please add a podcast and download an Episode first.")
                }
            }
            // NORMAL CASE: Try to match podcast name and voice query
            else {
                val queryLowercase: String = query.lowercase(Locale.getDefault())
                collectionProvider.episodeListByDate.forEach { mediaItem ->
                    // get podcast name (here -> subtitle)
                    val podcastName: String = mediaItem.description.subtitle.toString().lowercase(Locale.getDefault())
                    // FIRST: try to match the whole query
                    if (podcastName == queryLowercase) {
                        // start playback of newest podcast episode
                        onPrepareFromMediaId(mediaItem.description.mediaId!!, playWhenReady = true, extras = null)
                        return
                    }
                    // SECOND: try to match parts of the query
                    else {
                        val words: List<String> = queryLowercase.split(" ")
                        words.forEach { word ->
                            if (podcastName.contains(word)) {
                                // start playback of newest podcast episode
                                onPrepareFromMediaId(mediaItem.description.mediaId!!, playWhenReady = true, extras = null)
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

        override fun onCommand(player: Player, controlDispatcher: ControlDispatcher, command: String, extras: Bundle?, cb: ResultReceiver?): Boolean {
            when (command) {
                Keys.CMD_RELOAD_PLAYER_STATE -> {
                    playerState = PreferencesHelper.loadPlayerState()
                    return true
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
                            return true
                        } else {
                            return false
                        }
                    } else {
                        return false
                    }
                }
                Keys.CMD_REQUEST_EPISODE_DURATION -> {
                    if (cb != null) {
                        // check if episode has been prepared - assumes that then the player has been prepared as well
                        if (this@PlayerService::episode.isInitialized) {
                            val playbackProgressBundle: Bundle = bundleOf(Keys.RESULT_DATA_EPISODE_DURATION to episode.duration)
                            cb.send(Keys.RESULT_CODE_EPISODE_DURATION, playbackProgressBundle)
                            return true
                        } else {
                            return false
                        }
                    } else {
                        return false
                    }
                }
                Keys.CMD_START_SLEEP_TIMER -> {
                    startSleepTimer()
                    return true
                }
                Keys.CMD_CANCEL_SLEEP_TIMER -> {
                    cancelSleepTimer()
                    return true
                }
                Keys.CMD_CHANGE_PLAYBACK_SPEED -> {
                    if (cb != null) {
                        // change player speed
                        val newPlaybackSpeed: Float = updatePlaybackSpeed(playerState.playbackSpeed)
                        // send back new playback speed
                        val playbackSpeedBundle: Bundle = bundleOf(Keys.RESULT_DATA_PLAYBACK_SPEED to newPlaybackSpeed)
                        cb.send(Keys.RESULT_CODE_PLAYBACK_SPEED, playbackSpeedBundle)
                        return true
                    } else {
                        return false
                    }

                }
                Keys.CMD_RESET_PLAYBACK_SPEED -> {
                    if (cb != null) {
                        // change player speed
                        setPlaybackSpeed(1f)
                        // send back new playback speed
                        val playbackSpeedBundle: Bundle = bundleOf(Keys.RESULT_DATA_PLAYBACK_SPEED to 1f)
                        cb.send(Keys.RESULT_CODE_PLAYBACK_SPEED, playbackSpeedBundle)
                        return true
                    } else {
                        return false
                    }
                }
                Keys.CMD_DISMISS_NOTIFICATION -> {
                    // stop service
                    player.pause()
                    notificationHelper.hideNotification()
                    return true
                }
                else -> {
                    return false
                }
            }
        }
    }
    /*
     * End of declaration
     */

}
