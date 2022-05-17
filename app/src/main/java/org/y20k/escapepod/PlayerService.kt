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
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
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
import org.y20k.escapepod.helpers.PreferencesHelper
import org.y20k.escapepod.ui.PlayerState


/*
 * PlayerService class
 */
class PlayerService: MediaSessionService(), SharedPreferences.OnSharedPreferenceChangeListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PlayerService::class.java)


    /* Main class variables */
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var collectionDatabase: CollectionDatabase
    private lateinit var sleepTimer: CountDownTimer
    private var playerState: PlayerState = PlayerState()
    private val handler: Handler = Handler(Looper.getMainLooper())
    var sleepTimerTimeRemaining: Long = 0L


    /* Overrides onCreate from Service */
    override fun onCreate() {
        super.onCreate()
        // load player state
        playerState = PreferencesHelper.loadPlayerState()
        // get instance of database
        collectionDatabase = CollectionDatabase.getInstance(application)
        // initialize player and session
        initializePlayer()
        initializeSession()
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
                    // update up next episode
                    val mediaId: String = sharedPreferences?.getString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, String()) ?: String()
                    // upNextEpisode = collectionDatabase.episodeDao().findByMediaId(mediaId) // todo
                }
            }
        }
    }


    /* Initializes the ExoPlayer */
    private fun initializePlayer() {
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(Keys.SKIP_BACK_TIME_SPAN)
            .setSeekForwardIncrementMs(Keys.SKIP_FORWARD_TIME_SPAN)
            .build()
        player.addListener(playerListener)
//        player.addAnalyticsListener(analyticsListener)
    }


    /* Initializes the MediaSession */
    private fun initializeSession() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            TaskStackBuilder.create(this).run {
                addNextIntent(intent)
                getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setSessionCallback(CustomSessionCallback())
            .setMediaItemFiller(CustomMediaItemFiller())
            .build()
    }


    /* adds episodes to player */
    private fun addEpisodesToPlayer() {
        CoroutineScope(IO).launch {
            val currentEpisode: Episode? = collectionDatabase.episodeDao().findByMediaId(playerState.currentEpisodeMediaId)
            withContext(Main) {
                setCurrentEpisode(currentEpisode, playerState)
            }
            val upNextEpisode: Episode? = collectionDatabase.episodeDao().findByMediaId(playerState.upNextEpisodeMediaId)
            withContext(Main) {
                setUpNextEpisode(upNextEpisode)
            }
        }
    }


    /* Puts current episode into playlist */
    private fun setCurrentEpisode(episode: Episode?, playerState: PlayerState) {
        if (episode != null) {
            player.setMediaItem(CollectionHelper.buildMediaItem(episode, playerState.streaming), episode.playbackPosition)
            player.prepare()
        }
    }


    /* Puts next episode into playlist */
    private fun setUpNextEpisode(episode: Episode?) {
        removeUpNextEpisode()
        if (episode != null) {
            player.addMediaItem(CollectionHelper.buildMediaItem(episode, streaming = false))
            player.prepare()
        }
    }


    /* Removes all media items except for the first */
    private fun removeUpNextEpisode() {
        if (player.mediaItemCount > 1) player.removeMediaItems(/* fromIndex= */ 1, /* toIndex= */ player.mediaItemCount -1 )
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


    /*
     * Custom MediaItemFiller needed to prevent a NullPointerException with MediaItems created in PlayerFragment // todo check if this is only occurs in the alpha versions of media3
     * Credit: https://stackoverflow.com/a/70103460
     */
    private inner class CustomMediaItemFiller: MediaSession.MediaItemFiller {
        override fun fillInLocalConfiguration(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItem: MediaItem): MediaItem {
            // return the media item that it will be played
            return MediaItem.Builder()
                // use the metadata values to fill our media item
                .setMediaId(mediaItem.mediaId)
                .setUri(mediaItem.mediaMetadata.mediaUri)
                .setMediaMetadata(mediaItem.mediaMetadata)
                .build()
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
            // add available custom commands to session
            val connectionResult: MediaSession.ConnectionResult  = super.onConnect(session, controller)
            val builder: SessionCommands.Builder = connectionResult.availableSessionCommands.buildUpon()
            builder.add(SessionCommand(Keys.CMD_START_SLEEP_TIMER, Bundle.EMPTY))
            builder.add(SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
            return MediaSession.ConnectionResult.accept(builder.build(), connectionResult.availablePlayerCommands);
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                Keys.CMD_START_SLEEP_TIMER -> {
                    LogHelper.e(TAG, "CMD START SLEEP TIMER") // todo remove
                    startSleepTimer()
                }
                Keys.CMD_CANCEL_SLEEP_TIMER -> {
                    LogHelper.e(TAG, "CMD START CANCEL TIMER") // todo remove
                    cancelSleepTimer()
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
            // playerCommand = one of COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_SEEK_TO_DEFAULT_POSITION, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_MEDIA_ITEM, COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD, COMMAND_SET_SPEED_AND_PITCH, COMMAND_SET_SHUFFLE_MODE, COMMAND_SET_REPEAT_MODE, COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_MEDIA_ITEMS_METADATA, COMMAND_SET_MEDIA_ITEMS_METADATA, COMMAND_CHANGE_MEDIA_ITEMS, COMMAND_GET_AUDIO_ATTRIBUTES, COMMAND_GET_VOLUME, COMMAND_GET_DEVICE_VOLUME, COMMAND_SET_VOLUME, COMMAND_SET_DEVICE_VOLUME, COMMAND_ADJUST_DEVICE_VOLUME, COMMAND_SET_VIDEO_SURFACE, COMMAND_GET_TEXT, COMMAND_SET_TRACK_SELECTION_PARAMETERS or COMMAND_GET_TRACK_INFOS. */
            when (playerCommand) {
                Player.COMMAND_SEEK_FORWARD -> {
                    // todo implement
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_BACK ->  {
                    // todo implement
                    return SessionResult.RESULT_SUCCESS
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
     * Player.Listener: Called when one or more player states changed.
     */
    private var playerListener: Player.Listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            super.onTimelineChanged(timeline, reason)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            // todo handle transition to up next - remove prev mediaitem !!
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            // store state of playback
            val currentMediaId: String = player.currentMediaItem?.mediaId ?: String()
            playerState.currentEpisodeMediaId = currentMediaId
            playerState.isPlaying = isPlaying
            PreferencesHelper.savePlayerState(playerState)

            if (isPlaying) {
                // playback is active - periodically update playback position in database
                handler.removeCallbacks(periodicPlaybackPositionUpdateRunnable)
                handler.postDelayed(periodicPlaybackPositionUpdateRunnable, 0)
            } else {
                // playback is not active - stop periodically updating database
                handler.removeCallbacks(periodicPlaybackPositionUpdateRunnable)
                // save playback position
                val currentPosition: Long = player.currentPosition
                CoroutineScope(IO).launch {
                    collectionDatabase.episodeDao().updatePlaybackPosition(currentMediaId, currentPosition)
                }

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
     * Runnable: Periodically requests playback position (and sleep timer if running)
     */
    private val periodicPlaybackPositionUpdateRunnable: Runnable = object : Runnable {
        override fun run() {
            if (player.mediaItemCount > 0) {
                val playbackPosition: Long = player.currentPosition
                val mediaId: String = player.getMediaItemAt(0).mediaId
                CoroutineScope(IO).launch {
                    collectionDatabase.episodeDao().updatePlaybackPosition(mediaId = mediaId, playbackPosition = playbackPosition)
                }
            }
            // use the handler to start runnable again after specified delay (every 20 seconds)
            handler.postDelayed(this, 20000)
        }
    }
    /*
     * End of declaration
     */

}