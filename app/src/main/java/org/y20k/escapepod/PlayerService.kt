/*
 * PlayerService.kt
 * Implements the PlayerService class
 * PlayerService is Escapepod's foreground service that plays podcast audio
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package org.y20k.escapepod

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Intent
import android.content.SharedPreferences
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.y20k.escapepod.helpers.LogHelper


/*
 * PlayerService class
 */
class PlayerService: MediaSessionService(), SharedPreferences.OnSharedPreferenceChangeListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PlayerService::class.java)


    /* Main class variables */
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession


    /* Overrides onCreate from Service */
    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        initializeSession()
    }


    /* Overrides onDestroy from Service */
    override fun onDestroy() {
        player.release()
        mediaSession.release()
        super.onDestroy()
    }

    /* Overrides onGetSession from MediaSessionService */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }


    /* Overrides onSharedPreferenceChanged from SharedPreferences.OnSharedPreferenceChangeListener */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID -> {
                CoroutineScope(Dispatchers.IO).launch {
                    // update up next episode
                    val mediaId: String = sharedPreferences?.getString(Keys.PREF_PLAYER_STATE_UP_NEXT_MEDIA_ID, String()) ?: String()
                    // upNextEpisode = collectionDatabase.episodeDao().findByMediaId(mediaId) // todo
                }
            }
        }
    }


    /* Initializes the ExoPlayer */
    private fun initializePlayer() {
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    /* Initializes the MediaSession */
    private fun initializeSession() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            TaskStackBuilder.create(this).run {
                addNextIntent(intent)
                getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setMediaItemFiller(CustomMediaItemFiller())
            .build()
    }


    /* Custom MediaItemFiller needed to prevent a NullPointerException with MediaItems created in PlayerFragment */ // todo check if this is only occurs in the alpha versions of media3
    /* Credit: https://stackoverflow.com/a/70103460 */
    class CustomMediaItemFiller : MediaSession.MediaItemFiller {
        override fun fillInLocalConfiguration(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItem: MediaItem): MediaItem {
            // return the media item that it will be played
            return MediaItem.Builder()
                // use the metadata values to fill our media item
                .setUri(mediaItem.mediaMetadata.mediaUri)
                .setMediaMetadata(mediaItem.mediaMetadata)
                .build()
        }
    }

}