/*
 * MediaControllerExt.kt
 * Implements the MediaControllerExt extension methods
 * Useful extension methods for MediaController
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.extensions

import androidx.media3.session.MediaController
import org.y20k.escapepod.Keys
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.helpers.CollectionHelper
import org.y20k.escapepod.helpers.LogHelper
import org.y20k.escapepod.helpers.PreferencesHelper


private val TAG: String = "MediaControllerExt"


/* Starts the sleep timer */
fun MediaController.startSleepTimer() {
    // todo implement
}


/* Cancels the sleep timer */
fun MediaController.cancelSleepTimer() {
    // todo implement
}


/* Starts playback with a new media item */
fun MediaController.play(episode: Episode?, streaming: Boolean) {
    if (episode != null) {
        // start playing right away if episode is already prepared
        if (episode.mediaId == currentMediaItem?.mediaId) {
            play()
        } else {
            // set media item, prepare and play
            setMediaItem(CollectionHelper.buildMediaItem(episode, streaming))
            seekTo(episode.playbackPosition)
            prepare()
            play()
        }
    } else {
        LogHelper.e(TAG, "Unable to start playback. Episode is null.")
    }
}


/* Skip back 10 seconds */
fun MediaController.skipBack() {
    var position: Long = currentPosition - Keys.SKIP_BACK_TIME_SPAN
    if (position < 0L) position = 0L
    seekTo(position)
}


/* Skip forward 30 seconds */
fun MediaController.skipForward() {
    var position: Long = currentPosition + Keys.SKIP_FORWARD_TIME_SPAN
    if (position > duration && duration != 0L) position = duration
    seekTo(position)
}


/* Change playback speed */
fun MediaController.changePlaybackSpeed(): Float {
    var newSpeed: Float = 1f
    // circle through the speed presets
    val iterator = Keys.PLAYBACK_SPEEDS.iterator()
    while (iterator.hasNext()) {
        // found current speed in array
        if (iterator.next() == playbackParameters.speed) {
            if (iterator.hasNext()) {
                newSpeed = iterator.next()
            }
            break
        }
    }
    // apply new speed and save playback state
    setPlaybackSpeed(newSpeed)
    PreferencesHelper.savePlayerPlaybackSpeed(newSpeed)
    return newSpeed
}


/* Reset playback speed */
fun MediaController.resetPlaybackSpeed(): Float {
    val newSpeed: Float = 1f
    // reset playback speed and save playback state
    setPlaybackSpeed(newSpeed)
    PreferencesHelper.savePlayerPlaybackSpeed(newSpeed)
    return newSpeed
}


/* Returns mediaId of currently active media item */
fun MediaController.currentMediaId(): String {
    return currentMediaItem?.mediaId ?: String()
}


/* Returns mediaId of next media item */
fun MediaController.nextMediaId(): String {
    return if (mediaItemCount > 1) getMediaItemAt(1).mediaId else String()
}