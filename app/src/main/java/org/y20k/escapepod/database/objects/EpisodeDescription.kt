/*
 * EpisodeDescription.kt
 * Implements the EpisodeDescription class
 * An EpisodeDescription object holds the description of an episode
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.database.objects

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.y20k.escapepod.xml.RssHelper


/*
 * Episode class
 */
@Entity(tableName = "episode_descriptions", indices = arrayOf(Index(value = ["media_id", "episode_remote_podcast_feed_location"], unique = true)))
data class EpisodeDescription (

        @PrimaryKey
        @ColumnInfo (name = "media_id")
        val mediaId: String,

        @ColumnInfo (name = "episode_remote_podcast_feed_location")
        val episodeRemotePodcastFeedLocation: String,

        @ColumnInfo (name = "description") val description: String

        ) {


    /* Constructor that uses output from RssHelper */
    constructor(rssEpisode: RssHelper.RssEpisode) : this (

            // use remoteAudioFileLocation as unique media id
            mediaId = rssEpisode.remoteAudioFileLocation,
            //mediaId = (rssEpisode.remoteAudioFileLocation + rssEpisode.guid).hashCode().toString()

            episodeRemotePodcastFeedLocation = rssEpisode.episodeRemotePodcastFeedLocation,

            description = rssEpisode.description
    )

}