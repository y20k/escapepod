/*
 * Podcast.kt
 * Implements the Podcast class
 * A Podcast object holds the base data of a podcast and a list of its Episodes
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
import java.util.*


/*
 * Podcast class
 */
class Podcast(@Expose var name: String = "",
              @Expose var description: String = "",
              @Expose var cover: String = "android.resource://org.y20k.escapepods/drawable/default_podcast_cover",
              @Expose var episodes: MutableList<Episode> = mutableListOf<Episode>(),
              @Expose var lastUpdate: Date = Calendar.getInstance().time,
              @Expose var remoteImageFileLocation: String = "",
              @Expose var remotePodcastFeedLocation: String = "") {


    /* overrides toString method */
    override fun toString(): String {
        val descriptionLength: Int = 50
        val stringBuilder: StringBuilder = StringBuilder()
        val shortDescriptionLength: Int = if (description.length <= descriptionLength) description.length -1 else descriptionLength
        val shortDescription: String = description.trim().substring(0, shortDescriptionLength)
        stringBuilder
                .append("Name: $name\n")
                .append("CoverUri: $cover\n")
                .append("CoverURL: $remoteImageFileLocation\n")
                .append("FeedURL: $remotePodcastFeedLocation\n")
                .append("Update: ${lastUpdate.toString()}\n")
                .append("$shortDescription ...\n")
        for (episode in episodes) {
            episode.toString()
        }
        return stringBuilder.toString()
    }

}