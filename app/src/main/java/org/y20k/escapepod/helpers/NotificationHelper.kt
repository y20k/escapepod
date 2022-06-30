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
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.Util
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
    fun getNotification(session: MediaSession, actionFactory: MediaNotification.ActionFactory): Notification {
        ensureNotificationChannel()
        val player: Player = session.player
        val metadata = player.mediaMetadata
        val builder = NotificationCompat.Builder(context, Keys.NOW_PLAYING_NOTIFICATION_CHANNEL_ID)

        // Skip to previous action - duration is set via setSeekBackIncrementMs
        builder.addAction(
            actionFactory.createMediaAction(
                session,
                IconCompat.createWithResource(context, R.drawable.ic_notification_skip_back_36dp),
                context.getString(R.string.notification_skip_back),
                Player.COMMAND_SEEK_BACK
            )
        )
        if (player.playbackState == Player.STATE_ENDED || !player.playWhenReady) {
            // Play action.
            builder.addAction(
                actionFactory.createMediaAction(
                    session,
                    IconCompat.createWithResource(context, R.drawable.ic_notification_play_36dp),
                    context.getString(R.string.notification_play),
                    Player.COMMAND_PLAY_PAUSE
                )
            )
        } else {
            // Pause action.
            builder.addAction(
                actionFactory.createMediaAction(
                    session,
                    IconCompat.createWithResource(context, R.drawable.ic_notification_pause_36dp),
                    context.getString(R.string.notification_pause),
                    Player.COMMAND_PLAY_PAUSE
                )
            )
        }
        // Skip to next action - duration is set via setSeekForwardIncrementMs
        builder.addAction(
            actionFactory.createMediaAction(
                session,
                IconCompat.createWithResource(context,  R.drawable.ic_notification_skip_forward_36dp),
                context.getString(R.string.notification_skip_forward),
                Player.COMMAND_SEEK_FORWARD
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


        // define media style properties for notification
        val mediaStyle: MediaStyleNotificationHelper.MediaStyle = MediaStyleNotificationHelper.MediaStyle(session)
//            .setShowCancelButton(true) // only necessary for pre-Lollipop (SDK < 21)
//            .setCancelButtonIntent(actionFactory.createMediaActionPendingIntent(session, Player.COMMAND_STOP)) // only necessary for pre-Lollipop (SDK < 21)
            .setShowActionsInCompactView(1 /* Show play/pause button only in compact view */)

        // configure notification content
        builder.apply {
            setContentTitle(metadata.title)
            setContentText(metadata.artist)
            setContentIntent(session.sessionActivity)
            setDeleteIntent(actionFactory.createMediaActionPendingIntent(session, Player.COMMAND_STOP.toLong()))
            setOnlyAlertOnce(true)
            setSmallIcon(R.drawable.ic_notification_app_icon_white_24dp)
            setLargeIcon(ImageHelper.getPodcastCover(context, metadata.artworkUri.toString()))
            setStyle(mediaStyle)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setOngoing(false)
        }
//        // add podcast icon if available
//        if (metadata.artworkData != null) {
//            val artworkBitmap = BitmapFactory.decodeByteArray(
//                metadata.artworkData,
//                0,
//                metadata.artworkData!!.size
//            )
//            builder.setLargeIcon(artworkBitmap)
//        }

        return builder.build()
    }


    /* Creates a notification channel if necessary */
    private fun ensureNotificationChannel() {
        if (Util.SDK_INT < 26 || notificationManager.getNotificationChannel(Keys.NOW_PLAYING_NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(Keys.NOW_PLAYING_NOTIFICATION_CHANNEL_ID, context.getString(R.string.notification_now_playing_channel_name), NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
    }

}
