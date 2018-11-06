/*
 * NotificationHelper.kt
 * Implements the NotificationHelper object
 * A NotificationHelper creates and configures a notification
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import org.y20k.escapepods.PlayerService
import org.y20k.escapepods.PodcastPlayerActivity
import org.y20k.escapepods.R
import org.y20k.escapepods.core.Episode


/*
 * NotificationHelper object
 */
object NotificationHelper {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(NotificationHelper::class.java)


    /* Main class variables */
    private var notification: Notification? = null
    private var mediaSession: MediaSessionCompat? = null


    /* Create and put up notification */
    fun show(playerService: PlayerService, session: MediaSessionCompat, episode: Episode) {
        // save service and mediaSession
        mediaSession = session

        // create notification channel
        createChannel(playerService)

        // build notification
        notification = getNotificationBuilder(playerService, episode).build()

        // display notification
        playerService.startForeground(Keys.PLAYER_SERVICE_NOTIFICATION_ID, notification)
    }


    /* Creates a notification builder */
    private fun getNotificationBuilder(context: Context, episode: Episode): NotificationCompat.Builder {

        // explicit intent for notification tap
        val tapActionIntent = Intent(context, PodcastPlayerActivity::class.java)
        tapActionIntent.action = Keys.ACTION_SHOW_PLAYER
        // tapActionIntent.putExtra(EXTRA_STATION, station)

        // explicit intent for stopping playback
        val stopActionIntent = Intent(context, PlayerService::class.java)
//        stopActionIntent.action = ACTION_STOP  // todo implement

        // explicit intent for starting playback
        val playActionIntent = Intent(context, PlayerService::class.java)
//        playActionIntent.action = ACTION_PLAY  // todo implement

        // explicit intent for swiping notification
        val swipeActionIntent = Intent(context, PlayerService::class.java)
//        swipeActionIntent.action = ACTION_DISMISS  // todo implement

        // artificial back stack for started Activity.
        // -> navigating backward from the Activity leads to Home screen.
        val stackBuilder = TaskStackBuilder.create(context)
        //        // backstack: adds back stack for Intent (but not the Intent itself)
        //        stackBuilder.addParentStack(MainActivity.class);
        // backstack: add explicit intent for notification tap
        stackBuilder.addNextIntent(tapActionIntent)

        // pending intent wrapper for notification tap
        val tapActionPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        //        PendingIntent tapActionPendingIntent = PendingIntent.getService(mService, 0, tapActionIntent, 0);
        // pending intent wrapper for notification stop action
        val stopActionPendingIntent = PendingIntent.getService(context, 10, stopActionIntent, 0)
        // pending intent wrapper for notification start action
        val playActionPendingIntent = PendingIntent.getService(context, 11, playActionIntent, 0)
        // pending intent wrapper for notification swipe action
        val swipeActionPendingIntent = PendingIntent.getService(context, 12, swipeActionIntent, 0)

        // create media style
        val style = androidx.media.app.NotificationCompat.MediaStyle()
//        style.setMediaSession(mediaSession.getSessionToken())  // todo implement
        style.setShowActionsInCompactView(0)
        style.setShowCancelButton(true) // pre-Lollipop workaround
        style.setCancelButtonIntent(swipeActionPendingIntent)

        // construct notification in builder
        val builder: NotificationCompat.Builder
        builder = NotificationCompat.Builder(context, Keys.NOTIFICATION_CHANNEL_ID_PLAYBACK_CHANNEL)
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
//        builder.setSmallIcon(R.drawable.ic_notification_app_icon_white_24dp) // todo implement
//        builder.setLargeIcon(getStationIcon(context, station)) // todo implement
        builder.setContentTitle(episode.title)
        builder.setContentText(episode.description)  // todo implement
        builder.setShowWhen(false)
        builder.setStyle(style)
        builder.setContentIntent(tapActionPendingIntent)
        builder.setDeleteIntent(swipeActionPendingIntent)

        // todo implement
//        if (station.getPlaybackState() !== PLAYBACK_STATE_STOPPED) {
//            builder.addAction(R.drawable.ic_notification_stop_white_36dp, context.getString(R.string.notification_stop), stopActionPendingIntent)
//        } else {
//            builder.addAction(R.drawable.ic_notification_play_arrow_white_36dp, context.getString(R.string.notification_play), playActionPendingIntent)
//        }

        return builder
    }

    /* Get station image for notification's large icon */
    private fun getStationIcon(context: Context, episode: Episode): Bitmap? {
        // create and return station image icon
        val imageHelper = ImageHelper
        return null
//        val color = imageHelper.getStationImageColor()
//        return imageHelper.createCircularFramedImage(512, color)
    }


    /* Create a notification channel */
    private fun createChannel(context: Context): Boolean {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API level 26 ("Android O") supports notification channels.
            val id = Keys.NOTIFICATION_CHANNEL_ID_PLAYBACK_CHANNEL
            val name = context.getString(R.string.notification_channel_playback_name)
            val description = context.getString(R.string.notification_channel_playback_description)
            val importance = NotificationManager.IMPORTANCE_LOW

            // create channel
            val channel = NotificationChannel(id, name, importance)
            channel.description = description

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            return true

        } else {
            return false
        }
    }


}