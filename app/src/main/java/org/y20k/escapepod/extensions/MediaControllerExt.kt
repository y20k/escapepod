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
import androidx.core.os.bundleOf
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


/* Continue playback */
fun MediaController.continuePlayback() {
    // if episode is finished / almost finished (1/2 second before the end), then continue from start of episode
    if (currentPosition >= duration - 500L) seekTo(0L)
    playWhenReady = true
}


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
    if (isPlaying) pause()
    // set media item, prepare and play
    val position: Long = if (episode.isFinished()) 0L else episode.playbackPosition
    setMediaItem(CollectionHelper.buildMediaItem(episode, streaming), position)
    prepare()
    play()
}


/* Puts current episode into playlist */
fun MediaController.setCurrentEpisode(episode: Episode?, playerState: PlayerState) {
    if (episode != null) {
        setMediaItem(CollectionHelper.buildMediaItem(episode, playerState.streaming), episode.playbackPosition)
        prepare()
    }
}


/* Updates Up Next episode media id */
fun MediaController.updateUpNextEpisode(upNextEpisodeMediaId: String) {
    sendCustomCommand(SessionCommand(Keys.CMD_UPDATE_UP_NEXT_EPISODE, Bundle.EMPTY), bundleOf(Pair(Keys.EXTRA_UP_NEXT_EPISODE_MEDIA_ID, upNextEpisodeMediaId)))
}


/* Starts playback for next episode */
fun MediaController.startUpNextEpisode() {
    sendCustomCommand(SessionCommand(Keys.CMD_START_UP_NEXT_EPISODE, Bundle.EMPTY), Bundle.EMPTY)
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
fun MediaController.getCurrentMediaId(): String {
    if (mediaItemCount > 0) {
        return getMediaItemAt(0).mediaId
    } else {
        return String()
    }
}
