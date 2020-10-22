/*
 * PodcastDescription.kt
 * Implements the PodcastData class
 * A PodcastDescription object holds the description of a podcast
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
 * PodcastDescription class
 */
@Entity(tableName = "podcast_descriptions", indices = arrayOf(Index(value = ["remote_podcast_feed_location"], unique = true)))
data class PodcastDescription(

        @PrimaryKey
        @ColumnInfo (name = "remote_podcast_feed_location") val remotePodcastFeedLocation: String,
        @ColumnInfo (name = "description") val description: String,

) {

    /* Constructor that uses output from RssHelper*/
    constructor(rssPodcast: RssHelper.RssPodcast) : this (
            remotePodcastFeedLocation = rssPodcast.remotePodcastFeedLocation,
            description = rssPodcast.description
    )
}