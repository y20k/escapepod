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
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import androidx.media.MediaBrowserServiceCompat
import org.y20k.escapepods.helpers.Keys
import org.y20k.escapepods.helpers.LogHelper


/**
 * PlayerService class
 */
class PlayerService(): MediaBrowserServiceCompat() {

    /* Interface used to communicate back to activity */
    interface PlayerServiceListener {
        fun onPlaybackStateChanged(playbackState: Int) {
        }
    }


    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PlayerService::class.java)


    /* Main class variables */
    var playbackState = Keys.STATE_STOPPED
    private val downloadServiceBinder: LocalBinder = LocalBinder()
    private var playerServiceListener: PlayerServiceListener? = null


    /* Overrides onCreate */
    override fun onCreate() {
        super.onCreate()
    }


    /* Overrides onStartCommand */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }


    /* Overrides onBind */
    override fun onBind(intent: Intent): IBinder? {
        return downloadServiceBinder
    }


    /* Overrides onUnbind */
    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }


    /* Overrides onLoadChildren */
    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        // TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    /* Overrides onGetRoot */
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        // TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        return null
    }


    /* Initializes the service -> must ALWAYS be called */
    fun initialize(listener: PlayerServiceListener?, update: Boolean) {
        // set listener
        playerServiceListener = listener
        // load collection
        // ...
    }


    /* Toggles play and pause */
    fun togglePlayback(): Boolean {
        LogHelper.v(TAG, "Toggling playback")
        when (playbackState) {
            Keys.STATE_STOPPED -> playbackState = Keys.STATE_PLAYING
            Keys.STATE_PLAYING -> playbackState = Keys.STATE_STOPPED
        }
        playerServiceListener?.onPlaybackStateChanged(playbackState)
        return true
    }


    /*
     * Inner class: Local Binder that returns this service
     */
    inner class LocalBinder: Binder() {
        fun getService(): PlayerService {
            // return this instance of PlayerService so clients can call public methods
            return this@PlayerService
        }
    }


}