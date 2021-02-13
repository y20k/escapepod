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

import android.support.v4.media.session.MediaControllerCompat
import org.y20k.escapepod.helpers.LogHelper


/*
 * PlayerController class
 */
class PlayerController (mediaController: MediaControllerCompat) {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PlayerController::class.java)


    /* Main class variables */
    val transportControls: MediaControllerCompat.TransportControls = mediaController.transportControls


    /*  */
    fun play(mediaId: String = String()) {
        LogHelper.e(TAG, "play!!!") // todo remove
        if (mediaId.isNotEmpty()) {
            transportControls.playFromMediaId(mediaId, null)
        }
        transportControls.play()
    }


    /*  */
    fun pause() {

    }


    /*  */
    fun seekTo() {

    }



    /*  */
    fun changePlaybackSpeed() {

    }


    /*  */
    fun resetPlaybackSpeed() {

    }


    /*  */
    fun startSleepTimer() {

    }


    /*  */
    fun cancelSleepTimer() {

    }


    /*  */
    fun requestProgressUpdate() {

    }


}
