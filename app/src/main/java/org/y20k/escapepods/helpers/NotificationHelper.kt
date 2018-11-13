/*
 * NotificationHelper.kt
 * Implements the NotificationHelper class
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
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import org.y20k.escapepods.PlayerService
import org.y20k.escapepods.R
import org.y20k.escapepods.core.Episode
import org.y20k.escapepods.core.Podcast


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

        // get the controller
        val controller = MediaControllerCompat(playerService, sessionToken)

        // create a builder
        val builder = NotificationCompat.Builder(playerService, Keys.NOTIFICATION_NOW_PLAYING_CHANNEL)

        // add actions
        builder.addAction(skipBackAction)
        when (controller.playbackState.state) {
            PlaybackStateCompat.STATE_PLAYING -> builder.addAction(pauseAction)
            else -> builder.addAction(playAction)
        }
        builder.addAction(skipForwardAction)

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
                .setCancelButtonIntent(stopPendingIntent)
                .setMediaSession(sessionToken)
                .setShowActionsInCompactView(1)
                .setShowCancelButton(true)

        return builder.setContentIntent(controller.sessionActivity) // todo check if sessionActivity is correct
                .setContentTitle(episode.podcastName)
                .setContentText(episode.title)
                .setDeleteIntent(stopPendingIntent)
                .setLargeIcon(ImageHelper.getPodcastCover(playerService, Uri.parse(episode.cover), 256))
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.ic_notification_app_icon_white_24dp)
                .setStyle(mediaStyle)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
    }


    /* Create and put up notification */
    fun show(sessionToken: MediaSessionCompat.Token, episode: Episode) {
        // display notification - first run
        playerService.startForeground(Keys.NOTIFICATION_NOW_PLAYING_ID, buildNotification(sessionToken, episode))
    }


    /* Updates notification */
    fun update(sessionToken: MediaSessionCompat.Token, episode: Episode) {
        // update existing notification
        notificationManager.notify(Keys.NOTIFICATION_NOW_PLAYING_ID, buildNotification(sessionToken, episode))
//        if (playerService.playbackState == Keys.STATE_NOT_PLAYING) {
//            // make notification swipe-able
//            playerService.stopForeground(false)
//        }
    }


    /* Creates a notification builder */
//    private fun getNotificationBuilder(context: Context, podcast: Podcast, episodeId: Int, playbackState: Int): NotificationCompat.Builder {
//
//        // explicit intent for notification tap
//        val tapActionIntent = Intent(context, PodcastPlayerActivity::class.java)
//        tapActionIntent.action = Keys.ACTION_SHOW_PLAYER
//        // tapActionIntent.putExtra(Keys.EXTRA_EPISODE, podcast.episodes[episodeId])
//
//        // explicit intent for pausing playback
//        val pauseActionIntent = Intent(context, PlayerService::class.java)
//        pauseActionIntent.action = Keys.ACTION_PAUSE
//
//        // explicit intent for starting playback
//        val playActionIntent = Intent(context, PlayerService::class.java)
//        playActionIntent.action = Keys.ACTION_PLAY
//
//        // explicit intent for starting playback
//        val forwardActionIntent = Intent(context, PlayerService::class.java)
//        forwardActionIntent.action = Keys.ACTION_FORWARD
//
//        // explicit intent for starting playback
//        val replayActionIntent = Intent(context, PlayerService::class.java)
//        replayActionIntent.action = Keys.ACTION_REPLAY
//
//        // explicit intent for swiping notification
//        val swipeActionIntent = Intent(context, PlayerService::class.java)
//        swipeActionIntent.action = Keys.ACTION_DISMISS
//
//        // artificial back stack for started Activity.
//        // -> navigating backward from the Activity leads to Home screen.
//        val stackBuilder = TaskStackBuilder.create(context)
//        //        // backstack: adds back stack for Intent (but not the Intent itself)
//        //        stackBuilder.addParentStack(MainActivity.class);
//        // backstack: add explicit intent for notification tap
//        stackBuilder.addNextIntent(tapActionIntent)
//
//        // pending intent wrapper for notification tap
//        val tapActionPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
//        //        PendingIntent tapActionPendingIntent = PendingIntent.getService(mService, 0, tapActionIntent, 0);
//        // pending intent wrapper for notification pause action
//        val pauseActionPendingIntent = PendingIntent.getService(context, 10, pauseActionIntent, 0)
//        // pending intent wrapper for notification play action
//        val playActionPendingIntent = PendingIntent.getService(context, 11, playActionIntent, 0)
//        // pending intent wrapper for notification play action
//        val forwardActionPendingIntent = PendingIntent.getService(context, 11, forwardActionIntent, 0)
//        // pending intent wrapper for notification play action
//        val replayActionPendingIntent = PendingIntent.getService(context, 11, replayActionIntent, 0)
//        // pending intent wrapper for notification swipe action
//        val swipeActionPendingIntent = PendingIntent.getService(context, 12, swipeActionIntent, 0)
//
//        // create media style
//        val style = androidx.media.app.NotificationCompat.MediaStyle()
//        style.setMediaSession(mediaSession.sessionToken)
//        style.setShowActionsInCompactView(0)
//        style.setCancelButtonIntent(swipeActionPendingIntent)
//
//        // construct notification in builder
//        val builder: NotificationCompat.Builder = NotificationCompat.Builder(context, Keys.NOTIFICATION_NOW_PLAYING_CHANNEL)
//        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
//        builder.setSmallIcon(R.drawable.ic_notification_app_icon_white_24dp)
//        builder.setLargeIcon(getEpisodeIcon(context, podcast))
//        builder.setContentTitle(podcast.name)
//        builder.setContentText(podcast.episodes[episodeId].title)
//        builder.setShowWhen(false)
//        builder.setStyle(style)
//        builder.setContentIntent(tapActionPendingIntent)
//        builder.setDeleteIntent(swipeActionPendingIntent)
//        builder.addAction(R.drawable.ic_notification_skip_back_36dp, context.getString(R.string.notification_skip_back), replayActionPendingIntent)
//        when (playbackState) {
//            Keys.STATE_PLAYING -> builder.addAction(R.drawable.ic_notification_pause_36dp, context.getString(R.string.notification_pause), pauseActionPendingIntent)
//            Keys.STATE_NOT_PLAYING -> builder.addAction(R.drawable.ic_notification_play_36dp, context.getString(R.string.notification_play), playActionPendingIntent)
//        }
//        builder.addAction(R.drawable.ic_notification_skip_forward_36dp, context.getString(R.string.notification_skip_forward), forwardActionPendingIntent)
//        return builder
//    }

    /* Get episode cover for notification's large icon */
    private fun getEpisodeIcon(context: Context, podcast: Podcast): Bitmap {
        return ImageHelper.getPodcastCover(context, Uri.parse(podcast.cover), 192)
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
    private val skipForwardAction = NotificationCompat.Action(
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
    private val skipBackAction = NotificationCompat.Action(
            R.drawable.ic_notification_skip_back_36dp,
            playerService.getString(R.string.notification_skip_back),
            MediaButtonReceiver.buildMediaButtonPendingIntent(playerService, PlaybackStateCompat.ACTION_REWIND))
    private val stopPendingIntent =
            MediaButtonReceiver.buildMediaButtonPendingIntent(playerService, PlaybackStateCompat.ACTION_STOP)



}