/*
 * CollectionProvider.kt
 * Implements the CollectionProvider class
 * A CollectionProvider provides a list of podcast episodes as MediaMetadata items
 * Credit: https://github.com/googlesamples/android-MediaBrowserService/ (-> MusicProvider)
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.collection

import android.support.v4.media.MediaBrowserCompat
import org.y20k.escapepod.core.Collection
import org.y20k.escapepod.helpers.CollectionHelper


/**
 * CollectionProvider.class
 */
class CollectionProvider {

    /* Define log tag */
    private val TAG = CollectionProvider::class.java.simpleName


    /* Main class variables */
    private enum class State { NON_INITIALIZED, INITIALIZING, INITIALIZED }
    private var currentState = State.NON_INITIALIZED
    val episodeListByDate: MutableList<MediaBrowserCompat.MediaItem> = mutableListOf()


    /* Callback used by PlayerService */
    interface CollectionProviderCallback {
        fun onEpisodeListReady(success: Boolean)
    }


    /* Return current state */
    fun isInitialized(): Boolean {
        return currentState == State.INITIALIZED
    }


    /* Gets list of episodes and caches meta information in list of MediaMetaItems */
    fun retrieveMedia(collection: Collection, episodeListProviderCallback: CollectionProviderCallback) {
        if (currentState == State.INITIALIZED) {
            // already initialized, set callback immediately
            episodeListProviderCallback.onEpisodeListReady(true)
        } else {
            // fill episode list
            CollectionHelper.getAllEpisodesChronological(collection).forEach { episode ->
                // add only episodes with downloaded audio
                if (episode.audio.isNotEmpty()) {
                    episodeListByDate.add(CollectionHelper.buildEpisodeMediaMetaItem(episode))
                }
            }
            // afterwards: update state and set callback
            currentState = State.INITIALIZED
            episodeListProviderCallback.onEpisodeListReady(true)
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
