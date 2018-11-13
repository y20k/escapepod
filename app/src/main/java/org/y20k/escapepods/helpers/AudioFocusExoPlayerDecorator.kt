/*
 * AudioFocusExoPlayerDecorator.kt
 * Implements the AudioFocusExoPlayerDecorator class
 * A AudioFocusExoPlayerDecorator is a wrapper around a SimpleExoPlayer that simplifies playback by automatically handling audio
 * Credit: https://github.com/googlesamples/android-UniversalMusicPlayer/blob/master/media/src/main/java/com/example/android/uamp/media/audiofocus/AudioFocusExoPlayerDecorator.kt
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import android.annotation.TargetApi
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.media.AudioAttributesCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.TrackSelectionArray


/*
 * AudioFocusExoPlayerDecorator class
 */

class AudioFocusExoPlayerDecorator(private val audioAttributes: AudioAttributesCompat,
                                   private val audioManager: AudioManager,
                                   private val player: SimpleExoPlayer) : ExoPlayer by player {

    private val eventListeners = mutableListOf<Player.EventListener>()

    private var shouldPlayWhenReady = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (shouldPlayWhenReady || player.playWhenReady) {
                    player.playWhenReady = true
                    player.volume = MEDIA_VOLUME_DEFAULT
                }
                shouldPlayWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (player.playWhenReady) {
                    player.volume = MEDIA_VOLUME_DUCK
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Save the current state of playback so the _intention_ to play can be properly
                // reported to the app.
                shouldPlayWhenReady = player.playWhenReady
                player.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // This will chain through to abandonAudioFocus().
                AudioFocusExoPlayerDecorator@playWhenReady = false
            }
        }
    }

    @get:RequiresApi(Build.VERSION_CODES.O)
    private val audioFocusRequest by lazy { buildFocusRequest() }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) {
            requestAudioFocus()
        } else {
            if (shouldPlayWhenReady) {
                shouldPlayWhenReady = false
                playerEventListener.onPlayerStateChanged(false, player.playbackState)
            }
            player.playWhenReady = false
            abandonAudioFocus()
        }
    }

    override fun getPlayWhenReady(): Boolean = player.playWhenReady || shouldPlayWhenReady

    override fun addListener(listener: Player.EventListener?) {
        if (listener != null && !eventListeners.contains(listener)) {
            eventListeners += listener
        }
    }
    override fun removeListener(listener: Player.EventListener?) {
        if (listener != null && eventListeners.contains(listener)) {
            eventListeners -= listener
        }
    }
    private fun requestAudioFocus() {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestAudioFocusOreo()
        } else {
            @Suppress("deprecation")
            audioManager.requestAudioFocus(audioFocusListener,
                    audioAttributes.legacyStreamType,
                    AudioManager.AUDIOFOCUS_GAIN)
        }
        // Call the listener whenever focus is granted - even the first time!
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            shouldPlayWhenReady = true
            audioFocusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        } else {
            Log.i(TAG, "Playback not started: Audio focus request denied")
        }
    }
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            abandonAudioFocusOreo()
        } else {
            @Suppress("deprecation")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestAudioFocusOreo(): Int = audioManager.requestAudioFocus(audioFocusRequest)
    @RequiresApi(Build.VERSION_CODES.O)
    private fun abandonAudioFocusOreo() = audioManager.abandonAudioFocusRequest(audioFocusRequest)
    @TargetApi(Build.VERSION_CODES.O)
    private fun buildFocusRequest(): AudioFocusRequest =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes.unwrap() as AudioAttributes)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()

    private val playerEventListener = object : Player.EventListener {
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
            eventListeners.forEach { it.onPlaybackParametersChanged(playbackParameters) }
        }
        override fun onSeekProcessed() {
            eventListeners.forEach { it.onSeekProcessed() }
        }
        override fun onTracksChanged(trackGroups: TrackGroupArray?,
                                     trackSelections: TrackSelectionArray?) {
            eventListeners.forEach { it.onTracksChanged(trackGroups, trackSelections) }
        }
        override fun onPlayerError(error: ExoPlaybackException?) {
            eventListeners.forEach { it.onPlayerError(error) }
        }
        override fun onLoadingChanged(isLoading: Boolean) {
            eventListeners.forEach { it.onLoadingChanged(isLoading) }
        }
        override fun onPositionDiscontinuity(reason: Int) {
            eventListeners.forEach { it.onPositionDiscontinuity(reason) }
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            eventListeners.forEach { it.onRepeatModeChanged(repeatMode) }
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            eventListeners.forEach { it.onShuffleModeEnabledChanged(shuffleModeEnabled) }
        }
        override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
            eventListeners.forEach { it.onTimelineChanged(timeline, manifest, reason) }
        }
        /**
         * Handles the case where the intention is to play (so [Player.getPlayWhenReady] should
         * return `true`), but it's actually paused because the app had a temporary loss
         * of audio focus; i.e.: [AudioManager.AUDIOFOCUS_LOSS_TRANSIENT].
         */
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            val reportPlayWhenReady = getPlayWhenReady()
            eventListeners.forEach { it.onPlayerStateChanged(reportPlayWhenReady, playbackState) }
        }
    }
    // Add the Player.EventListener wrapper (above) to the player.
    init {
        player.addListener(playerEventListener)
    }
}
private const val TAG = "AFExoPlayerDecorator"
private const val MEDIA_VOLUME_DEFAULT = 1.0f
private const val MEDIA_VOLUME_DUCK = 0.2f