/*
 * PodcastCollection.kt
 * Implements the PodcastCollection class
 * A PodcastCollection object holds a list of Podcasts
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */



package org.y20k.escapepods.core

import com.google.gson.annotations.Expose


/*
 * PodcastCollection class
 */
class PodcastCollection(@Expose var podcasts: MutableList<Podcast> = mutableListOf<Podcast>()) {

    fun isInCollection(remotePodcastFeedLocation: String): Boolean {
        for (podcast in podcasts) {
            if (podcast.remotePodcastFeedLocation == remotePodcastFeedLocation) return true
        }
        return false
    }

}


