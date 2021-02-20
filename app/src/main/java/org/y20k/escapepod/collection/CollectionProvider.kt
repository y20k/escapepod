/*
 * CollectionProvider.kt
 * Implements the CollectionProvider class
 * A CollectionProvider provides a list of podcast episodes as MediaMetadata items
 * Credit: https://github.com/googlesamples/android-MediaBrowserService/ (-> MusicProvider)
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.collection

import android.support.v4.media.MediaBrowserCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.y20k.escapepod.Keys
import org.y20k.escapepod.database.CollectionDatabase


/**
 * CollectionProvider.class
 */
class CollectionProvider {

    /* Define log tag */
    private val TAG = CollectionProvider::class.java.simpleName


    /* Main class variables */
    private enum class State { NON_INITIALIZED, INITIALIZING, INITIALIZED }
    private var currentState: State = State.NON_INITIALIZED
    val episodeListByDate: MutableList<MediaBrowserCompat.MediaItem> = mutableListOf()
//    var currentEpisode: Episode? = null
//    var upNextEpisode: Episode? = null


    /* Callback used by PlayerService */
    interface CollectionProviderCallback {
        fun onEpisodeListReady(success: Boolean)
    }


    /* Return current state */
    fun isInitialized(): Boolean {
        return currentState == State.INITIALIZED
    }


    /* Gets list of episodes and caches meta information in list of MediaMetaItems */
    fun retrieveMedia(collectionDatabase: CollectionDatabase, episodeListProviderCallback: CollectionProviderCallback) {
        if (currentState == State.INITIALIZED) {
            // already initialized, set callback immediately
            episodeListProviderCallback.onEpisodeListReady(true)
        } else {
            // fill episode list
            CoroutineScope(IO).launch {
                // fill episode list
                collectionDatabase.episodeDao().getChronological(Keys.DEFAULT_NUMBER_OF_EPISODES_FOR_ANDROID_AUTO).forEach { episode ->
                    // add only episodes with downloaded audio
                    if (episode.audio.isNotEmpty()) {
                        episodeListByDate.add(episode.toMediaMetaItem())
                    }
                }
//                // try to get current and up next episode
//                currentEpisode = collectionDatabase.episodeDao().findByMediaId(playerState.episodeMediaId)
//                upNextEpisode = collectionDatabase.episodeDao().findByMediaId(playerState.upNextEpisodeMediaId)
                // afterwards: update state and set callback
                currentState = State.INITIALIZED
                episodeListProviderCallback.onEpisodeListReady(true)
            }


        }
    }


    /* Get newest episode as media item */
    fun getNewestEpisode(): MediaBrowserCompat.MediaItem? {
        when (isInitialized() && episodeListByDate.isNotEmpty()) {
            true -> return episodeListByDate.first()
            false -> return null
        }
    }


    /* Get oldest episode as media item */
    fun getOldestEpisode(): MediaBrowserCompat.MediaItem? {
        when (isInitialized() && episodeListByDate.isNotEmpty()) {
            true -> return episodeListByDate.last()
            false -> return null
        }
    }


    /* Get next episode as media item */
    fun getNextEpisode(mediaId: String): MediaBrowserCompat.MediaItem? {
        episodeListByDate.forEachIndexed { index, mediaItem ->
            if (mediaItem.description.mediaId == mediaId) {
                if (index + 1 > episodeListByDate.size) {
                    // return next episode
                    return episodeListByDate[index + 1]
                }
            }
        }
        // default: return newest (cycle through from oldest)
        return getNewestEpisode()
    }


    /* Get previous episode as media item */
    fun getPreviousEpisode(mediaId: String): MediaBrowserCompat.MediaItem? {
        episodeListByDate.forEachIndexed { index, mediaItem ->
            if (mediaItem.description.mediaId == mediaId) {
                if (index - 1 >= 0) {
                    // return previous episode
                    return episodeListByDate[index - 1]
                }
            }
        }
        // default: return oldest (cycle through from newest)
        return getOldestEpisode()
    }


}
