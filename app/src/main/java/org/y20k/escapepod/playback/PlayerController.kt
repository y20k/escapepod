/*
 * PlayerController.kt
 * Implements the PlayerController class
 * PlayerController is provides playback controls for PlayerService
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.playback

import android.os.ResultReceiver
import android.support.v4.media.session.MediaControllerCompat
import org.y20k.escapepod.Keys
import org.y20k.escapepod.helpers.LogHelper


/*
 * PlayerController class
 */
class PlayerController (private val mediaController: MediaControllerCompat) {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PlayerController::class.java)


    /* Main class variables */
    val transportControls: MediaControllerCompat.TransportControls = mediaController.transportControls


    /* Start playback for given media id */
    fun play(mediaId: String = String()) {
        if (mediaId.isNotEmpty()) {
            transportControls.playFromMediaId(mediaId, null)
        }
    }


    /* Pause playback */
    fun pause() {
        transportControls.pause()
    }

    /* Skip back 10 seconds */
    fun skipBack() {
        var position: Long = mediaController.playbackState.position - Keys.SKIP_BACK_TIME_SPAN
        if (position < 0L) position = 0L
        transportControls.seekTo(position)
    }

    /* Skip forward 30 seconds */
    fun skipForward(episodeDuration: Long) {
        var position: Long = mediaController.playbackState.position + Keys.SKIP_FORWARD_TIME_SPAN
        if (position > episodeDuration && episodeDuration != 0L) position = episodeDuration
        transportControls.seekTo(position)
    }


    /* Seek to given position */
    fun seekTo(position: Long) {
        transportControls.seekTo(position)
    }


    /* Send command to change playback speed */
    fun changePlaybackSpeed(resultReceiver: ResultReceiver) {
        mediaController.sendCommand(Keys.CMD_CHANGE_PLAYBACK_SPEED, null, resultReceiver)
    }


    /* Send command to reset playback speed */
    fun resetPlaybackSpeed(resultReceiver: ResultReceiver) {
        mediaController.sendCommand(Keys.CMD_RESET_PLAYBACK_SPEED, null, resultReceiver)
    }


    /* Send command to start sleep timer */
    fun startSleepTimer() {
        mediaController.sendCommand(Keys.CMD_START_SLEEP_TIMER, null, null)
    }


    /* Send command to cancel sleep timer */
    fun cancelSleepTimer() {
        mediaController.sendCommand(Keys.CMD_CANCEL_SLEEP_TIMER, null, null)
    }


    /* Send command to request updates - used to build the ui */
    fun requestProgressUpdate(resultReceiver: ResultReceiver) {
        mediaController.sendCommand(Keys.CMD_REQUEST_PROGRESS_UPDATE, null, resultReceiver)
    }


    /* Send command to request episode duration - used to update ui (when streaming) */
    fun requestEpisodeDuration(resultReceiver: ResultReceiver) {
        mediaController.sendCommand(Keys.CMD_REQUEST_EPISODE_DURATION, null, resultReceiver)
    }


    /* Register MediaController callback to get notified about player state changes */
    fun registerCallback(callback: MediaControllerCompat.Callback) {
        mediaController.registerCallback(callback)
    }


    /* Unregister MediaController callback */
    fun unregisterCallback(callback: MediaControllerCompat.Callback) {
        mediaController.unregisterCallback(callback)
    }


    /* Get the current playback state */
    fun getPlaybackState(): Int = mediaController.playbackState.state

}
