/*
 * CollectionProvider.kt
 * Implements the CollectionProvider class
 * A CollectionProvider provides a list of podcast episodes as MediaMetadata items
 * Credit: https://github.com/googlesamples/android-MediaBrowserService/ (-> MusicProvider)
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import android.content.Context
import android.support.v4.media.MediaMetadataCompat
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.y20k.escapepods.core.Collection
import java.util.*


/**
 * CollectionProvider.class
 */
class CollectionProvider {

    /* Define log tag */
    private val TAG = CollectionProvider::class.java.simpleName


    /* Main class variables */
    var collection: Collection = Collection()
    private enum class State { NON_INITIALIZED, INITIALIZING, INITIALIZED }
    private val episodeListById: TreeMap<String, MediaMetadataCompat> = TreeMap()
    private var currentState = State.NON_INITIALIZED


    /* Callback used by PlayerService */
    interface CollectionProviderCallback {
        fun onEpisodeListReady(success: Boolean)
    }


    /* Return list of all available episodes */
    fun getAllEpisodes(): Iterable<MediaMetadataCompat> {
        if (currentState != State.INITIALIZED || episodeListById.isEmpty()) {
            return emptyList()
        } else {
            return episodeListById.values
        }
    }


    /* Return the first station in list, or null if none is available */
    fun getFirstEpisode(): MediaMetadataCompat? {
        val entry = episodeListById.firstEntry()
        if (entry != null) {
            return entry.value
        } else {
            return null
        }
    }


    /* Return the last episode in list, or null if none is available */
    fun getLastLast(): MediaMetadataCompat? {
        val entry = episodeListById.lastEntry()
        if (entry != null) {
            return entry.value
        } else {
            return null
        }
    }


    /* Return the first episode after the given episode, or null if none is available */
    fun getEpisodenAfter(stationId:String):MediaMetadataCompat? {
        val entry = episodeListById.higherEntry(stationId)
        return if (entry != null) {
            entry.value
        } else null
    }


    /* Return the first episode before the given episode, or null if none is available */
    fun getEpisodenBefore(stationId:String):MediaMetadataCompat? {
        val entry = episodeListById.lowerEntry(stationId)
        return if (entry != null) {
            entry.value
        } else null
    }


    /* Return the last played episode */
    fun getLastPlayedEpisode(context:Context):MediaMetadataCompat? {
        // todo implement
        // fallback: first station
        return getFirstEpisode()
    }


    /* Return the MediaMetadata for given ID */
    fun getEpisodeMediaMetadata(episodeId:String): MediaMetadataCompat? {
        return if (episodeListById.containsKey(episodeId)) episodeListById[episodeId] else null
    }


    /* Return current state */
    fun isInitialized(): Boolean {
        return currentState == State.INITIALIZED
    }


    /* Check if empty */
    fun isEmpty(): Boolean {
        return episodeListById.isEmpty()
    }


    /* Gets list of episodes and caches track information in TreeMap */
    fun retrieveMedia(context: Context, episodeListProviderCallback: CollectionProviderCallback) {
        if (currentState == State.INITIALIZED) {
            // already initialized, set callback immediately
            episodeListProviderCallback.onEpisodeListReady(true)
        } else {
            // load collection
            collection = loadCollection(context)
            // fill episode list
            for (podcast in collection.podcasts) {
                for (episodeId: Int in podcast.episodes.indices) {
                    val episode = podcast.episodes[episodeId]
                    // add only episodes with downloaded audio
                    if (episode.audio.isNotEmpty()) {
                        val item: MediaMetadataCompat = CollectionHelper.buildMediaMetadata(podcast, episodeId)
                        val mediaId: String = episode.getMediaId()
                        episodeListById.put(mediaId, item)
                    }
                }
            }
            // afterwards: update state and set callback
            currentState = State.INITIALIZED
            episodeListProviderCallback.onEpisodeListReady(true)

        }
    }


    /* Reads podcast collection from storage using GSON */
    private fun loadCollection(context: Context): Collection = runBlocking<Collection> {
        // get JSON from text file async
        val result = async { FileHelper.readCollection(context) }
        // wait for result and return collection
        return@runBlocking result.await()
    }

}
