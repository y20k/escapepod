/*
 * Podcast.kt
 * Implements the Podcast class
 * A Podcast object holds the base data of a podcast
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.database.objects

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.core.net.toUri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.y20k.escapepod.xml.RssHelper
import java.util.*

/*
 * Podcast class
 */
@Entity(tableName = "podcasts", indices = [Index(value = ["remote_podcast_feed_location"], unique = true), Index(value = ["remote_image_file_location"], unique = false), Index(value = ["latest_episode_date"], unique = false)])
data class Podcast(

        @PrimaryKey
        @ColumnInfo (name = "remote_podcast_feed_location") val remotePodcastFeedLocation: String,

        @ColumnInfo (name = "name") val name: String,
        @ColumnInfo (name = "website") val website: String,
        @ColumnInfo (name = "cover") val cover: String,
        @ColumnInfo (name = "small_cover") val smallCover: String,
        @ColumnInfo (name = "latest_episode_date") val latestEpisodeDate: Date,
        @ColumnInfo (name = "remote_image_file_location") val remoteImageFileLocation: String

) {


    /* Constructor used when cover has been set */
    constructor(podcast: Podcast, cover: String, smallCover: String) : this (
            name = podcast.name,
            website = podcast.website,
            cover = cover,           // <= set cover
            smallCover = smallCover, // <= set small cover
            latestEpisodeDate = podcast.latestEpisodeDate,
            remoteImageFileLocation = podcast.remoteImageFileLocation,
            remotePodcastFeedLocation = podcast.remotePodcastFeedLocation
    )


    /* Constructor that uses output from RssHelper*/
    constructor(rssPodcast: RssHelper.RssPodcast) : this (
            name = rssPodcast.name,
            website = rssPodcast.website,
            cover = rssPodcast.cover,
            smallCover = rssPodcast.smallCover,
            latestEpisodeDate = rssPodcast.latestEpisodeDate,
            remoteImageFileLocation = rssPodcast.remoteImageFileLocation,
            remotePodcastFeedLocation = rssPodcast.remotePodcastFeedLocation
    )


    /* Creates MediaItem for a podcast - used by collection provider */
    fun toMediaMetaItem(): MediaBrowserCompat.MediaItem {
        val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
        mediaDescriptionBuilder.setTitle(name)
        mediaDescriptionBuilder.setIconUri(cover.toUri())
        return MediaBrowserCompat.MediaItem(mediaDescriptionBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

}