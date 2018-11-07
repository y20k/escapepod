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
import java.util.*


/**
 * CollectionProvider.class
 */
class CollectionProvider {

    /* Define log tag */
    private val TAG = CollectionProvider::class.java.simpleName


    /* Main class variables */
    private val episodeListById: TreeMap<String, MediaMetadataCompat> = TreeMap()
    private enum class State { NON_INITIALIZED, INITIALIZING, INITIALIZED }
    private var currentState = State.NON_INITIALIZED


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


//    /* Creates MediaMetadata from station */
//    private fun buildMediaMetadata(station:Station):MediaMetadataCompat {
//
//        return MediaMetadataCompat.Builder()
//                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, station.getStationId())
//                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, station.getStreamUri().toString())
//                //                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, station.getStationName())
//                //                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, station.getStationName())
//                .putString(MediaMetadataCompat.METADATA_KEY_GENRE, "Radio")
//                //                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, iconUrl)
//                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, station.getStationName())
//                //                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber)
//                //                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, totalTrackCount)
//                .build()
//    }


}
