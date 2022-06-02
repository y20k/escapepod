/*
 * NotificationHelper.kt
 * Implements the NotificationHelper class
 * A NotificationHelper creates and configures a notification
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R


/*
 * NotificationHelper class
 * Credit: https://github.com/android/uamp/blob/5bae9316b60ba298b6080de1fcad53f6f74eb0bf/common/src/main/java/com/example/android/uamp/media/UampNotificationManager.kt
 */
class NotificationHelper(private val context: Context) {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(NotificationHelper::class.java)


    /* Main class variables */
    private val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager


    /* Builds a notification for given media session and controller */
    fun getNotification(mediaSession: MediaSession, mediaController: MediaController, actionFactory: MediaNotification.ActionFactory): Notification {
        ensureNotificationChannel()
        val builder = NotificationCompat.Builder(context, Keys.NOW_PLAYING_NOTIFICATION_CHANNEL_ID)

        // Skip to previous action - duration is set via setSeekBackIncrementMs
        builder.addAction(
            actionFactory.createMediaAction(
                IconCompat.createWithResource(context, R.drawable.ic_notification_skip_back_36dp),
                context.getString(R.string.notification_skip_back),
                MediaNotification.ActionFactory.COMMAND_REWIND
            )
        )
        if (mediaController.playbackState == Player.STATE_ENDED || !mediaController.playWhenReady) {
            // Play action.
            builder.addAction(
                actionFactory.createMediaAction(
                    IconCompat.createWithResource(context, R.drawable.ic_notification_play_36dp),
                    context.getString(R.string.notification_play),
                    MediaNotification.ActionFactory.COMMAND_PLAY
                )
            )
        } else {
            // Pause action.
            builder.addAction(
                actionFactory.createMediaAction(
                    IconCompat.createWithResource(context, R.drawable.ic_notification_pause_36dp
                    ),
                    context.getString(R.string.notification_pause),
                    MediaNotification.ActionFactory.COMMAND_PAUSE
                )
            )
        }
        // Skip to next action - duration is set via setSeekForwardIncrementMs
        builder.addAction(
            actionFactory.createMediaAction(
                IconCompat.createWithResource(context,  R.drawable.ic_notification_skip_forward_36dp),
                context.getString(R.string.notification_skip_forward),
                MediaNotification.ActionFactory.COMMAND_FAST_FORWARD
            )
        )
//        // Dismiss action - stops playback (and dismisses notification)
//        builder.addAction(
//            actionFactory.createMediaAction(
//                IconCompat.createWithResource(context,  R.drawable.ic_notification_clear_36dp),
//                context.getString(R.string.notification_dismiss),
//                MediaNotification.ActionFactory.COMMAND_STOP
//            )
//        )

        // Set metadata info in the notification.
        val metadata = mediaController.mediaMetadata
        builder.setContentTitle(metadata.title).setContentText(metadata.artist)
        if (metadata.artworkData != null) {
            val artworkBitmap = BitmapFactory.decodeByteArray(
                metadata.artworkData,
                0,
                metadata.artworkData!!.size
            )
            builder.setLargeIcon(artworkBitmap)
        }

        val mediaStyle: MediaStyleNotificationHelper.MediaStyle = MediaStyleNotificationHelper.MediaStyle(mediaSession)
            .setShowCancelButton(true)
            .setCancelButtonIntent(actionFactory.createMediaActionPendingIntent(MediaNotification.ActionFactory.COMMAND_STOP))
            .setShowActionsInCompactView(1 /* Show play/pause button only in compact view */)

        val notification: Notification = builder.apply {
            setContentIntent(mediaController.sessionActivity)
            setDeleteIntent(actionFactory.createMediaActionPendingIntent(MediaNotification.ActionFactory.COMMAND_STOP))
            setOnlyAlertOnce(true)
            setSmallIcon(R.drawable.ic_notification_app_icon_white_24dp)
            setStyle(mediaStyle)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setOngoing(false)
        }.build()

        return notification

    }


    /* Creates a notification channel if necessary */
    private fun ensureNotificationChannel() {
        if (Util.SDK_INT < 26 || notificationManager.getNotificationChannel(Keys.NOW_PLAYING_NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(Keys.NOW_PLAYING_NOTIFICATION_CHANNEL_ID, context.getString(R.string.notification_now_playing_channel_name), NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
    }

}
