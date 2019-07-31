/*
 * Podcast.kt
 * Implements the Podcast class
 * A Podcast object holds the base data of a podcast and a list of its Episodes
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.core

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import kotlinx.android.parcel.Parcelize
import org.y20k.escapepods.Keys
import java.util.*


/*
 * Podcast class
 */
@Keep
@Parcelize
data class Podcast(@Expose var name: String = "",
                   @Expose var description: String = "",
                   @Expose var cover: String = Keys.LOCATION_DEFAULT_COVER,
                   @Expose var smallCover: String = Keys.LOCATION_DEFAULT_COVER,
                   @Expose var episodes: MutableList<Episode> = mutableListOf<Episode>(),
                   @Expose var lastUpdate: Date = Calendar.getInstance().time,
                   @Expose var remoteImageFileLocation: String = "",
                   @Expose var remotePodcastFeedLocation: String = ""): Parcelable {


    /* overrides toString method */
    override fun toString(): String {
        val descriptionLength: Int = 50
        val stringBuilder: StringBuilder = StringBuilder()
        val shortDescriptionLength: Int = if (description.length <= descriptionLength) description.length -1 else descriptionLength
        val shortDescription: String = description.trim().substring(0, shortDescriptionLength)
        stringBuilder.append("Name: $name\n")
        stringBuilder.append("Cover: $cover\n")
        stringBuilder.append("Cover URL: $remoteImageFileLocation\n")
        stringBuilder.append("Feed URL: $remotePodcastFeedLocation\n")
        stringBuilder.append("Update: ${lastUpdate.toString()}\n")
        stringBuilder.append("Episodes: ${episodes.size}\n")
        stringBuilder.append("$shortDescription ...\n")
        episodes.forEachIndexed { index, episode ->
            // print out three episodes
            if (index < 3) {
                stringBuilder.append("${episode.toString()}")
            }
        }
        return stringBuilder.toString()
    }


    /* Returns a unique podcast id - currently just the remotePodcastFeedLocation */
    fun getPodcastId(): String {
        return remotePodcastFeedLocation
    }

}