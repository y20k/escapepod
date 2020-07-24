/*
 * NotificationHelper.kt
 * Implements the NotificationHelper class
 * A NotificationHelper creates and configures a notification
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import org.y20k.escapepod.Keys
import org.y20k.escapepod.PlayerService
import org.y20k.escapepod.R
import org.y20k.escapepod.core.Episode
import org.y20k.escapepod.extensions.isFastForwardEnabled
import org.y20k.escapepod.extensions.isPlayEnabled
import org.y20k.escapepod.extensions.isPlaying
import org.y20k.escapepod.extensions.isRewindEnabled


/*
 * NotificationHelper class
 */
class NotificationHelper(private val playerService: PlayerService) {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(NotificationHelper::class.java)


    /* Main class variables */
    private val notificationManager: NotificationManager = playerService.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager


    /* Creates notification */
    fun buildNotification(sessionToken: MediaSessionCompat.Token, episode: Episode): Notification {
        if (shouldCreateNowPlayingChannel()) {
            createNowPlayingChannel()
        }

        val controller = MediaControllerCompat(playerService, sessionToken)
        val playbackState = controller.playbackState

        val builder = NotificationCompat.Builder(playerService, Keys.NOTIFICATION_NOW_PLAYING_CHANNEL)

        // add actions for rewind, play/pause, fast forward, based on what's enabled
        var playPauseIndex = 0
        if (playbackState.isRewindEnabled) {
            builder.addAction(rewindAction)
            ++playPauseIndex
        }
        if (playbackState.isPlaying) {
            builder.addAction(pauseAction)
        } else if (playbackState.isPlayEnabled) {
            builder.addAction(playAction)
        }
        if (playbackState.isFastForwardEnabled) {
            builder.addAction(fastForwardAction)
        }

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
                .setCancelButtonIntent(stopPendingIntent)
                .setMediaSession(sessionToken)
                .setShowActionsInCompactView(playPauseIndex)
                .setShowCancelButton(true)

        return builder.setContentIntent(controller.sessionActivity) // todo check if sessionActivity is correct
                .setContentTitle(episode.podcastName)
                .setContentText(episode.title)
                .setDeleteIntent(stopPendingIntent)
                .setLargeIcon(ImageHelper.getScaledPodcastCover(playerService, episode.cover, Keys.SIZE_COVER_NOTIFICATION_LARGE_ICON))
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.ic_notification_app_icon_white_24dp)
                .setStyle(mediaStyle)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
    }


    /* Checks if notification channel should be created */
    private fun shouldCreateNowPlayingChannel() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !nowPlayingChannelExists()


    /* Checks if notification channel exists */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun nowPlayingChannelExists() = notificationManager.getNotificationChannel(Keys.NOTIFICATION_NOW_PLAYING_CHANNEL) != null


    /* Create a notification channel */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNowPlayingChannel() {
        val notificationChannel = NotificationChannel(Keys.NOTIFICATION_NOW_PLAYING_CHANNEL,
                playerService.getString(R.string.notification_now_playing_channel_name),
                NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = playerService.getString(R.string.notification_now_playing_channel_description)
                }
        notificationManager.createNotificationChannel(notificationChannel)
    }


    /* Notification actions */
    private val fastForwardAction = NotificationCompat.Action(
            R.drawable.ic_notification_skip_forward_36dp,
            playerService.getString(R.string.notification_skip_forward),
            MediaButtonReceiver.buildMediaButtonPendingIntent(playerService, PlaybackStateCompat.ACTION_FAST_FORWARD))
    private val playAction = NotificationCompat.Action(
            R.drawable.ic_notification_play_36dp,
            playerService.getString(R.string.notification_play),
            MediaButtonReceiver.buildMediaButtonPendingIntent(playerService, PlaybackStateCompat.ACTION_PLAY))
    private val pauseAction = NotificationCompat.Action(
            R.drawable.ic_notification_pause_36dp,
            playerService.getString(R.string.notification_pause),
            MediaButtonReceiver.buildMediaButtonPendingIntent(playerService, PlaybackStateCompat.ACTION_PAUSE))
    private val rewindAction = NotificationCompat.Action(
            R.drawable.ic_notification_skip_back_36dp,
            playerService.getString(R.string.notification_skip_back),
            MediaButtonReceiver.buildMediaButtonPendingIntent(playerService, PlaybackStateCompat.ACTION_REWIND))
    private val stopPendingIntent =
            MediaButtonReceiver.buildMediaButtonPendingIntent(playerService, PlaybackStateCompat.ACTION_STOP)

}