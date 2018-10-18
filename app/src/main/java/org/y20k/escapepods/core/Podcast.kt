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
        stringBuilder.append("Name: $name\n")
        stringBuilder.append("CoverUri: $cover\n")
        stringBuilder.append("CoverURL: $remoteImageFileLocation\n")
        stringBuilder.append("FeedURL: $remotePodcastFeedLocation\n")
        stringBuilder.append("Update: ${lastUpdate.toString()}\n")
        stringBuilder.append("Episodes: ${episodes.size}\n")
        stringBuilder.append("$shortDescription ...\n")
        episodes.forEachIndexed { index, episode ->
            // print out three episodes
            if (index > 3) {
                stringBuilder.append("${episode.toString()}\n")
            }
        }
        return stringBuilder.toString()
    }

}