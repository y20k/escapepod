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

import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import org.y20k.escapepod.Keys
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.helpers.CollectionHelper
import org.y20k.escapepod.helpers.PreferencesHelper
import org.y20k.escapepod.ui.PlayerState


private val TAG: String = "MediaControllerExt"


/* Starts the sleep timer */
fun MediaController.startSleepTimer() {
    sendCustomCommand(SessionCommand(Keys.CMD_START_SLEEP_TIMER, Bundle.EMPTY), Bundle.EMPTY)
}


/* Cancels the sleep timer */
fun MediaController.cancelSleepTimer() {
    sendCustomCommand(SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY), Bundle.EMPTY)
}

/* Request sleep timer remaining */
fun MediaController.requestSleepTimerRemaining(): ListenableFuture<SessionResult> {
    return sendCustomCommand(SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY), Bundle.EMPTY)
}


/* Starts playback with a new media item */
fun MediaController.play(episode: Episode, streaming: Boolean) {
    // set media item, prepare and play
    setMediaItem(CollectionHelper.buildMediaItem(episode, streaming), episode.playbackPosition)
    prepare()
    playWhenReady = true
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


/* Puts current episode into playlist */
fun MediaController.setCurrentEpisode(episode: Episode?, playerState: PlayerState) {
    if (episode != null) {
        setMediaItem(CollectionHelper.buildMediaItem(episode, playerState.streaming), episode.playbackPosition)
        prepare()
    }
}


/* Puts next episode into playlist */
fun MediaController.setUpNextEpisode(episode: Episode?) {
    removeUpNextEpisode()
    if (episode != null) {
        addMediaItem(CollectionHelper.buildMediaItem(episode, streaming = false))
        prepare()
    }
}


/* Starts playback for next episode */
fun MediaController.startUpNextEpisode() {
    seekToNextMediaItem()
}


/* Removes all media items except for the first */
fun MediaController.removeUpNextEpisode() {
    if (mediaItemCount > 1) removeMediaItems(/* fromIndex= */ 1, /* toIndex= */ mediaItemCount -1 )
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
    if (mediaItemCount > 0) {
        return getMediaItemAt(0).mediaId
    } else {
        return String()
    }
}


/* Returns mediaId of next media item */
fun MediaController.nextMediaId(): String {
    return if (mediaItemCount > 1) getMediaItemAt(1).mediaId else String()
}