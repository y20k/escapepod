/*
 * PodcastWithRecentEpisodesWrapper.kt
 * Implements the PodcastWithRecentEpisodesWrapper class
 * A PodcastWithRecentEpisodesWrapper object holds the base data of a podcast and a list of its most recent episodes - a wrapper for Podcast & Episode(s)
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.database.wrappers

import androidx.room.Embedded
import androidx.room.Relation
import org.y20k.escapepod.database.objects.Podcast


/*
 * PodcastWithRecentEpisodesWrapper class
 */
data class PodcastWithRecentEpisodesWrapper(
        @Embedded
        val data: Podcast,

        @Relation(parentColumn = "remote_podcast_feed_location", entityColumn = "episode_remote_podcast_feed_location")
        val episodes: List<EpisodeMostRecentView>
)