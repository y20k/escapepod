/*
 * PlayerService.kt
 * Implements the PlayerService class
 * PlayerService is Escapepod's foreground service that plays podcast audio
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package org.y20k.escapepod

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.helpers.CollectionHelper
import org.y20k.escapepod.helpers.LogHelper
import org.y20k.escapepod.helpers.NotificationHelper
import org.y20k.escapepod.helpers.PreferencesHelper


/*
 * PlayerService class
 */
class PlayerService: MediaSessionService(), SharedPreferences.OnSharedPreferenceChangeListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PlayerService::class.java)


    /* Main class variables */
    private lateinit var player: Player
    private lateinit var mediaSession: MediaSession
    private lateinit var collectionDatabase: CollectionDatabase
    private lateinit var sleepTimer: CountDownTimer
    var sleepTimerTimeRemaining: Long = 0L
    var upNextEpisodeMediaId: String = String()


    /* Overrides onCreate from Service */
    override fun onCreate() {
        super.onCreate()
        // initialize player and session
        initializePlayer()
        initializeSession()
        setMediaNotificationProvider(CustomNotificationProvider())
        // get instance of database
        collectionDatabase = CollectionDatabase.getInstance(application)
        // get media id of the episode in Up Next queue
        upNextEpisodeMediaId = PreferencesHelper.loadUpNextMediaId()
    }


    /* Overrides onDestroy from Service */
    override fun onDestroy() {
        player.release()
        mediaSession.release()
        super.onDestroy()
    }


    /* Overrides onGetSession from MediaSessionService */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }


    /* Overrides onSharedPreferenceChanged from SharedPreferences.OnSharedPreferenceChangeListener */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID -> {
                CoroutineScope(Dispatchers.IO).launch {
                    // update Up Next episode
                    val mediaId: String = sharedPreferences?.getString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, String()) ?: String()
                    // upNextEpisode = collectionDatabase.episodeDao().findByMediaId(mediaId) // todo
                }
            }
        }
    }


    /* Initializes the ExoPlayer */
    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).apply {
            setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            setHandleAudioBecomingNoisy(true)
            setSeekBackIncrementMs(Keys.SKIP_BACK_TIME_SPAN)
            setSeekForwardIncrementMs(Keys.SKIP_FORWARD_TIME_SPAN)
            setPauseAtEndOfMediaItems(true) /* prevents the auto-repeat that is set below */
        }.build()
        player.addListener(playerListener)
        player.repeatMode = Player.REPEAT_MODE_ALL /* enables the "skip to next" command for a single item playlist */
//        player.addAnalyticsListener(analyticsListener)
    }


    /* Initializes the MediaSession */
    private fun initializeSession() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = TaskStackBuilder.create(this).run {
                addNextIntent(intent)
                getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        mediaSession = MediaSession.Builder(this, player).apply {
            setSessionActivity(pendingIntent)
            setSessionCallback(CustomSessionCallback())
            setMediaItemFiller(CustomMediaItemFiller())
        }.build()
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
                sleepTimerTimeRemaining = 0L
                player.pause()
            }
            override fun onTick(millisUntilFinished: Long) {
                sleepTimerTimeRemaining = millisUntilFinished
            }
        }
        // start timer
        sleepTimer.start()
        // store timer state
        PreferencesHelper.saveSleepTimerRunning(isRunning = true)
    }


    /* Cancels sleep timer */
    private fun cancelSleepTimer() {
        if (this::sleepTimer.isInitialized && sleepTimerTimeRemaining > 0L) {
            sleepTimerTimeRemaining = 0L
            sleepTimer.cancel()
        }
        // store timer state
        PreferencesHelper.saveSleepTimerRunning(isRunning = false)
    }


    /* Try to start episode from Up Next queue */
    private fun tryToStartUpNextEpisode() {
        LogHelper.e(TAG, "tryToStartUpNextEpisode => $upNextEpisodeMediaId") // todo remove
        if (upNextEpisodeMediaId.isNotEmpty()) {
            CoroutineScope(IO).launch {
                // get Up Next episode
                val upNextEpisode: Episode? = collectionDatabase.episodeDao().findByMediaId(upNextEpisodeMediaId)
                if (upNextEpisode != null) {
                    // start playback
                    withContext(Main) {
                        val position: Long = if (upNextEpisode.isFinished()) 0L else upNextEpisode.playbackPosition
                        player.pause()
                        player.setMediaItem(CollectionHelper.buildMediaItem(upNextEpisode, streaming = false), position)
                        player.prepare()
                        player.play()
                    }
                }
                // clear Up Next queue
//                upNextEpisodeMediaId = String()
//                PreferencesHelper.saveUpNextMediaId()
            }
        } else {
            // todo stop playback & hide notification
        }
    }


    /* Creates a ForwardingPlayer that overrides default exoplayer behavior */
    private fun createForwardingPlayer(exoPlayer: ExoPlayer) : ForwardingPlayer {
        return object : ForwardingPlayer(exoPlayer) {
//            override fun stop(reset: Boolean) {
//                stop()
//            }
//            override fun stop() {
//                player.pause()
//                notificationHelper.hideNotification()
//            }
//            override fun seekForward() {
//                val episodeDuration: Long = episode.duration
//                var position: Long = player.currentPosition + Keys.SKIP_FORWARD_TIME_SPAN
//                if (position > episodeDuration && episodeDuration != 0L) position = episodeDuration
//                player.seekTo(position)
//            }
//            override fun seekBack() {
//                var position: Long = player.currentPosition - Keys.SKIP_BACK_TIME_SPAN
//                if (position < 0L) position = 0L
//                player.seekTo(position)
//            }
//            override fun seekToNext() {
//                /* Note: seekToNext() is only called from MediaController if Player.hasNextMediaItem() ist true */
//                seekForward()
//            }
//            override fun seekToPrevious() {
//                seekBack()
//            }
        }
    }


    /*
     * Custom MediaItemFiller needed to prevent a NullPointerException with MediaItems created in PlayerFragment // todo check if this is only occurs in the alpha versions of media3
     * Credit: https://stackoverflow.com/a/70103460
     */
    private inner class CustomMediaItemFiller: MediaSession.MediaItemFiller {
        override fun fillInLocalConfiguration(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItem: MediaItem): MediaItem {
            // return the media item that it will be played
            return MediaItem.Builder().apply {
                // use the metadata values to fill our media item
                setMediaId(mediaItem.mediaId)
                setUri(mediaItem.mediaMetadata.mediaUri)
                setMediaMetadata(mediaItem.mediaMetadata)
            }.build()
        }
    }
    /*
     * End of inner class
     */


    /*
     * Custom MediaSession Callback that handles player commands
     */
    private inner class CustomSessionCallback: MediaSession.SessionCallback {

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            // add custom commands
            val connectionResult: MediaSession.ConnectionResult  = super.onConnect(session, controller)
            val builder: SessionCommands.Builder = connectionResult.availableSessionCommands.buildUpon()
            builder.add(SessionCommand(Keys.CMD_START_SLEEP_TIMER, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_UPDATE_UP_NEXT_EPISODE, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_START_UP_NEXT_EPISODE, Bundle.EMPTY))
            return MediaSession.ConnectionResult.accept(builder.build(), connectionResult.availablePlayerCommands);
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                Keys.CMD_START_SLEEP_TIMER -> {
                    startSleepTimer()
                }
                Keys.CMD_CANCEL_SLEEP_TIMER -> {
                    cancelSleepTimer()
                }
                Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING -> {
                    val resultBundle = Bundle()
                    resultBundle.putLong(Keys.EXTRA_SLEEP_TIMER_REMAINING, sleepTimerTimeRemaining)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
                }
                Keys.CMD_START_UP_NEXT_EPISODE -> {
                    tryToStartUpNextEpisode()
                }
                Keys.CMD_UPDATE_UP_NEXT_EPISODE -> {
                    upNextEpisodeMediaId = args.getString(Keys.EXTRA_UP_NEXT_EPISODE_MEDIA_ID) ?: String()
                    PreferencesHelper.saveUpNextMediaId(upNextEpisodeMediaId)
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
            // playerCommand = one of COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_SEEK_TO_DEFAULT_POSITION, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_MEDIA_ITEM, COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD, COMMAND_SET_SPEED_AND_PITCH, COMMAND_SET_SHUFFLE_MODE, COMMAND_SET_REPEAT_MODE, COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_MEDIA_ITEMS_METADATA, COMMAND_SET_MEDIA_ITEMS_METADATA, COMMAND_CHANGE_MEDIA_ITEMS, COMMAND_GET_AUDIO_ATTRIBUTES, COMMAND_GET_VOLUME, COMMAND_GET_DEVICE_VOLUME, COMMAND_SET_VOLUME, COMMAND_SET_DEVICE_VOLUME, COMMAND_ADJUST_DEVICE_VOLUME, COMMAND_SET_VIDEO_SURFACE, COMMAND_GET_TEXT, COMMAND_SET_TRACK_SELECTION_PARAMETERS or COMMAND_GET_TRACK_INFOS. */
            // emulate headphone buttons
            // start/pause: adb shell input keyevent 85
            // next: adb shell input keyevent 87
            // prev: adb shell input keyevent 88
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT ->  {
                    // override "skip to next" command - just skip forward instead
                    player.seekForward()
                    return SessionResult.RESULT_INFO_SKIPPED
                }
                Player.COMMAND_SEEK_TO_PREVIOUS ->  {
                    // override "skip to previous" command - just skip back instead
                    player.seekBack()
                    return SessionResult.RESULT_INFO_SKIPPED
                }
                else -> {
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
            }
        }
    }
    /*
     * End of inner class
     */


    /*
     * Custom NotificationProvider that sets tailored Notification
     */
    private inner class CustomNotificationProvider: MediaNotification.Provider {

        override fun createNotification(mediaController: MediaController, actionFactory: MediaNotification.ActionFactory, onNotificationChangedCallback: MediaNotification.Provider.Callback): MediaNotification {
            return MediaNotification(Keys.NOW_PLAYING_NOTIFICATION_ID, NotificationHelper(this@PlayerService).getNotification(mediaSession, mediaController, actionFactory))
        }

        override fun handleCustomAction(mediaController: MediaController,  action: String,  extras: Bundle) {
            TODO("Not yet implemented")
        }
    }
    /*
     * End of inner class
     */

    /*
     * Player.Listener: Called when one or more player states changed.
     */
    private var playerListener: Player.Listener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            // store state of playback
            val currentMediaId: String = player.currentMediaItem?.mediaId ?: String()
            PreferencesHelper.saveIsPlaying(isPlaying)
            PreferencesHelper.saveCurrentMediaId(currentMediaId)
            if (currentMediaId == upNextEpisodeMediaId) PreferencesHelper.saveUpNextMediaId(String())
            // save playback position
            val currentPosition: Long = player.currentPosition
            CoroutineScope(IO).launch {
                collectionDatabase.episodeDao().updatePlaybackPosition(currentMediaId, currentPosition, isPlaying)
            }

            if (isPlaying) {
                // playback is active
            } else {
                // cancel sleep timer
                cancelSleepTimer()

                // playback is not active
                // Not playing because playback is paused, ended, suppressed, or the player
                // is buffering, stopped or failed. Check player.getPlayWhenReady,
                // player.getPlaybackState, player.getPlaybackSuppressionReason and
                // player.getPlaybackError for details.
                when (player.playbackState) {
                    // player is able to immediately play from its current position
                    Player.STATE_READY -> {
                        // todo
                    }
                    // buffering - data needs to be loaded
                    Player.STATE_BUFFERING -> {
                        // todo
                    }
                    // player finished playing all media
                    Player.STATE_ENDED -> {
                        // todo
                    }
                    // initial state or player is stopped or playback failed
                    Player.STATE_IDLE -> {
                        // todo
                    }
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (!playWhenReady) {
                if (player.mediaItemCount == 0) {
                    // todo
                }
                when (reason) {
                    Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> {
                        // playback reached end: stop / end playback
                        LogHelper.e(TAG, "Ding: playback reached end.") // todo remove
                        tryToStartUpNextEpisode()
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