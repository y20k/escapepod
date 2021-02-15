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
import android.support.v4.media.session.PlaybackStateCompat
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


    /*  */
    fun play(mediaId: String = String()) {
        if (mediaId.isNotEmpty()) {
            transportControls.playFromMediaId(mediaId, null)
        }
        transportControls.play()
    }


    /*  */
    fun pause() {
        transportControls.pause()
    }


    /*  */
    fun seekTo(position: Long) {
        transportControls.seekTo(position)
    }


    /*  */
    fun changePlaybackSpeed(resultReceiver: ResultReceiver) {
        mediaController.sendCommand(Keys.CMD_CHANGE_PLAYBACK_SPEED, null, resultReceiver)
    }


    /*  */
    fun resetPlaybackSpeed(resultReceiver: ResultReceiver) {
        mediaController.sendCommand(Keys.CMD_RESET_PLAYBACK_SPEED, null, resultReceiver)
    }


    /*  */
    fun startSleepTimer() {
        mediaController.sendCommand(Keys.CMD_START_SLEEP_TIMER, null, null)
    }


    /*  */
    fun cancelSleepTimer() {
        mediaController.sendCommand(Keys.CMD_CANCEL_SLEEP_TIMER, null, null)
    }


    /*  */
    fun requestProgressUpdate(resultReceiver: ResultReceiver) {
        mediaController.sendCommand(Keys.CMD_REQUEST_PROGRESS_UPDATE, null, resultReceiver)
    }


    /* */
    fun registerCallback(callback: MediaControllerCompat.Callback) {
        mediaController.registerCallback(callback)
    }


    /* */
    fun unregisterCallback(callback: MediaControllerCompat.Callback) {
        mediaController.unregisterCallback(callback)
    }

    fun getPlaybackState(): Int = mediaController.playbackState.state

}
