/*
 * PlayerService.kt
 * Implements the PlayerService class
 * PodcastPlayerActivity is Escapepod's foreground service that plays podcast audio and handles playback controls
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods

import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import androidx.media.MediaBrowserServiceCompat
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.helpers.Keys
import org.y20k.escapepods.helpers.LogHelper
import org.y20k.escapepods.helpers.NotificationHelper


/**
 * PlayerService class
 */
class PlayerService(): MediaBrowserServiceCompat() {


    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PlayerService::class.java)


    /* Main class variables */
    private var collection: Collection = Collection()
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationHelper: NotificationHelper


    /* Overrides onCreate */
    override fun onCreate() {
        super.onCreate()

        // create media session
        mediaSession = createMediaSession()

        // initialize notification helper and notification manager
        notificationHelper = NotificationHelper(this)
        notificationManager = NotificationManagerCompat.from(this)

        // initialize listener for headphone unplug
        // todo
    }


    /* Overrides onStartCommand */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }


    /* Overrides onTaskRemoved */
    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }


    /* Overrides onDestroy */
    override fun onDestroy() {
        // release media session
        mediaSession.run {
            isActive = false
            release()
        }
    }


    /* Overrides onLoadChildren */
    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        LogHelper.v(TAG, "OnLoadChildren called.")
        // todo fill collection provider
    }


    /* Overrides onGetRoot */
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        // todo implement a package validator
        // if not validated
        return MediaBrowserServiceCompat.BrowserRoot(Keys.MEDIA_ID_EMPTY_ROOT, null)
        // if validated - afterwards go load children
        // return MediaBrowserServiceCompat.BrowserRoot(Keys.MEDIA_ID_ROOT, null)
    }


    /* Updates collection */
    fun updateCollection(newCollection: Collection) {
        collection = newCollection
        LogHelper.w(TAG, "New Collection: ${collection.toString()}")
    }


    /* Starts playback */
    private fun startPlayback() {
        LogHelper.d(TAG, "Starting Playback")
        mediaSession.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_PLAYING))
        // notificationHelper.show(mediaSession.sessionToken, collection.podcasts[0], 0) // todo collection is empty here - change that
    }


    /* Stops / pauses playback */
    private fun stopPlayback(dismissNotification: Boolean) {
        LogHelper.d(TAG, "Pausing Playback")
        mediaSession.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_PAUSED))
        // notificationHelper.update(mediaSession.sessionToken, collection.podcasts[0], 0) // todo collection is empty here - change that
    }


    /* Skips playback forward */
    private fun skipForwardPlayback() {
        LogHelper.d(TAG, "Skipping forward")
    }


    /* Skips playback back */
    private fun skipBackPlayback() {
        LogHelper.d(TAG, "Skipping back")
    }


    /* Removes the now playing notification. */
    private fun removeNowPlayingNotification() {
        stopForeground(true)
    }


    /* Creates media session */
    private fun createMediaSession(): MediaSessionCompat {
        val session = MediaSessionCompat(this, TAG)
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        session.setPlaybackState(createPlaybackState(PlaybackStateCompat.STATE_STOPPED))
        session.setCallback(MediaSessionCallback())
        setSessionToken(session.sessionToken)
        return session
    }


    /* Creates playback state - actions for playback state to be used in media session callback */
    private fun createPlaybackState(state: Int): PlaybackStateCompat {
        val skipActions: Long = PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_REWIND
        when(state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                return PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PLAYING, 0, 0f)
                        .setActions(PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or skipActions)
                        .build()
            }
            else -> {
                return PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_STOPPED, 0, 0f)
                        .setActions(PlaybackStateCompat.ACTION_PLAY or skipActions)
                        .build()
            }
        }
    }



    /*
     * Inner class: Handles callback from active media session
     */
    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            // start playback
            startPlayback()
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            startPlayback()
        }

        override fun onPause() {
            // pause playback - keep notification
            stopPlayback(false)
        }

        override fun onStop() {
            // stop playback - remove notification
            stopPlayback(true)
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            // handle requests to begin playback from a search query (eg. Assistant, Android Auto, etc.)
            LogHelper.i(TAG, "playFromSearch  query=$query extras=$extras")

            if (TextUtils.isEmpty(query)) {
                // user provided generic string e.g. 'Play music'
                // mStation = Station(mStationListProvider.getFirstStation())
            } else {
                // try to match station name and voice query
//                for (stationMetadata in mStationListProvider.getAllStations()) {
//                    val words = query!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
//                    for (word in words) {
//                        if (stationMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE).toLowerCase().contains(word.toLowerCase())) {
//                            mStation = Station(stationMetadata)
//                        }
//                    }
//                }
            }
            // start playback
            startPlayback()
        }

        override fun onFastForward() {
            super.onFastForward()
            skipForwardPlayback()
        }

        override fun onRewind() {
            super.onRewind()
            skipBackPlayback()
        }

    }
    /*
     * End of inner class
     */
}