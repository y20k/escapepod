/*
 * CollectionHelper.kt
 * Implements the CollectionHelper class
 * A CollectionHelper provides helper methods for the podcast collection
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.helpers

import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.core.Podcast
import java.text.SimpleDateFormat
import java.util.*


/*
 * CollectionHelper class
 */
class CollectionHelper {


    /* Checks if feed is already in collection */
    fun isNewPodcast(remotePodcastFeedLocation: String, collection: Collection): Boolean {
        for (podcast in collection.podcasts) {
            if (podcast.remotePodcastFeedLocation == remotePodcastFeedLocation) return true
        }
        return false
    }


    /* Checks if enough time passed since last update */
    fun hasEnoughTimePassedSinceLastUpdate(collection: Collection): Boolean {
        val currentDate: Date = Calendar.getInstance().time
        return true // todo remove
//        return currentDate.time - collection.lastUpdate.time  > FIVE_MINUTES_IN_MILLISECONDS
    }


    /* Converts RFC 2822 string representation of a date to DATE */
    fun convertToDate(dateString: String): Date {
        val pattern = "EEE, dd MMM yyyy HH:mm:ss Z"
        val format = SimpleDateFormat(pattern, Locale.ENGLISH)
        return format.parse((dateString))
    }


    /* Checks if podcast has new episodes */
    fun podcastHasNewEpisodes(newPodcast: Podcast, collection: Collection): Boolean {
        val oldPodcast = getPodcastFromCollection(newPodcast, collection)
        val newPodcastLatestEpisode: String = newPodcast.episodes[0].getString(Keys.METADATA_CUSTOM_KEY_PUBLICATION_DATE)
        val oldPodcastLatestEpisode: String = oldPodcast.episodes[0].getString(Keys.METADATA_CUSTOM_KEY_PUBLICATION_DATE)
        return newPodcastLatestEpisode != oldPodcastLatestEpisode
    }


    /* Returns folder name for podcast */
    fun getPodcastSubDirectory(podcast: Podcast): String {
        return podcast.remotePodcastFeedLocation.hashCode().toString()
    }


    /* Get the counterpart from collection for given podcast */
    private fun getPodcastFromCollection(newPodcast: Podcast, collection: Collection): Podcast {
        for (podcast in collection.podcasts) {
            if (newPodcast.remotePodcastFeedLocation == podcast.remotePodcastFeedLocation) {
                return podcast
            }
        }
        return Podcast()
    }

}