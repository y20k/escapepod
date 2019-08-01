/*
 * CollectionProvider.kt
 * Implements the CollectionProvider class
 * A CollectionProvider provides a list of podcast episodes as MediaMetadata items
 * Credit: https://github.com/googlesamples/android-MediaBrowserService/ (-> MusicProvider)
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.collection

import android.support.v4.media.MediaBrowserCompat
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.helpers.CollectionHelper


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


    /* Gets list of episodes and caches meta information in list of MediaMetaItems */
    fun retrieveMedia2(collection: Collection, episodeListProviderCallback: CollectionProviderCallback) {
        if (currentState == State.INITIALIZED) {
            // already initialized, set callback immediately
            episodeListProviderCallback.onEpisodeListReady(true)
        } else {
            // fill podcast list
            collection.podcasts.forEach { podcast ->
                episodeListByDate.add(CollectionHelper.buildPodcastMediaMetaItem(podcast))
                podcast.episodes.forEach { episode ->
                    if (episode.audio.isNotEmpty()) {
                        CollectionHelper.buildEpisodeMediaMetaItem(episode)
                    }
                }
            }
            // afterwards: update state and set callback
            currentState = State.INITIALIZED
            episodeListProviderCallback.onEpisodeListReady(true)
        }
    }

}
