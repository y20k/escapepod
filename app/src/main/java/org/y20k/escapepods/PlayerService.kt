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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.util.Util
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.helpers.*
import java.util.*


/**
 * PlayerService class
 */
class PlayerService(): MediaBrowserServiceCompat() {


    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PlayerService::class.java)


    /* Main class variables */
    private var collection: Collection = Collection()
    private var collectionProvider: CollectionProvider = CollectionProvider()
    private lateinit var packageValidator: PackageValidator
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var userAgent: String
    private lateinit var collectionChangedReceiver: BroadcastReceiver


    /* Overrides onCreate */
    override fun onCreate() {
        super.onCreate()
        // set user agent
        userAgent = Util.getUserAgent(this, Keys.APPLICATION_NAME)

        // get the package validator // todo can be local?
        packageValidator = PackageValidator(this, R.xml.allowed_media_browser_callers)

        // create media session
        mediaSession = createMediaSession()

        // initialize notification helper and notification manager
        notificationHelper = NotificationHelper(this)
        notificationManager = NotificationManagerCompat.from(this)

        // create and register collection changed receiver
        collectionChangedReceiver = createCollectionChangedReceiver()
        LocalBroadcastManager.getInstance(application).registerReceiver(collectionChangedReceiver, IntentFilter(Keys.ACTION_COLLECTION_CHANGED))

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


    /* Overrides onGetRoot */
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot {
        // Credit: https://github.com/googlesamples/android-UniversalMusicPlayer (->  MusicService)
        LogHelper.d(TAG, "OnGetRoot: clientPackageName=$clientPackageName; clientUid=$clientUid ; rootHints=$rootHints") // todo change
        // to ensure you are not allowing any arbitrary app to browse your app's contents, you need to check the origin:
        if (!packageValidator.isKnownCaller(clientPackageName, clientUid)) {
            // request comes from an untrusted package
            LogHelper.i(TAG, "OnGetRoot: Browsing NOT ALLOWED for unknown caller. "
                    + "Returning empty browser root so all apps can use MediaController."
                    + clientPackageName)
            return MediaBrowserServiceCompat.BrowserRoot(Keys.MEDIA_ID_EMPTY_ROOT, null)
        } else {
            return MediaBrowserServiceCompat.BrowserRoot(Keys.MEDIA_ID_ROOT, null)
        }
    }


    /* Overrides onLoadChildren */
    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        LogHelper.v(TAG, "OnLoadChildren called.")
        if (!collectionProvider.isInitialized()) {
            // use result.detach to allow calling result.sendResult from another thread:
            result.detach()
            collectionProvider.retrieveMedia(this, object: CollectionProvider.EpisodeListProviderCallback {
                override fun onEpisodeListReady(success: Boolean) {
                    if (success) {
                        loadChildren(parentId, result)
                    }
                }
            })
        } else {
            // if music catalog is already loaded/cached, load them into result immediately
            loadChildren(parentId, result)
        }
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


    /* Loads media items into result - assumes that collectionProvider is initialized */
    private fun loadChildren(parentId: String, result: MediaBrowserServiceCompat.Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()
        when (parentId) {
            Keys.MEDIA_ID_ROOT -> {
                // mediaItems
                for (track in collectionProvider.getAllEpisodes()) {
                    val item = MediaBrowserCompat.MediaItem(track.getDescription(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
                    mediaItems.add(item)
                }
            }
            Keys.MEDIA_ID_EMPTY_ROOT -> {
                // do nothing
            }
            else -> {
                // log error
                LogHelper.w(TAG, "Skipping unmatched parentId: $parentId")
            }
        }
        result.sendResult(mediaItems)
    }


    /* Creates the collectionChangedReceiver - handles Keys.ACTION_COLLECTION_CHANGED */
    private fun createCollectionChangedReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                LogHelper.e(TAG, "Collection changed. Do something") // todo remove
                // todo reload mediaItems
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

        override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
            super.onCommand(command, extras, cb)
            // todo react to command given at PodcastPlayerActivity -> mediaController.sendCommand(...)
        }

    }
    /*
     * End of inner class
     */
}