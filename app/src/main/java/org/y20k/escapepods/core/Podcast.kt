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

import android.support.v4.media.MediaMetadataCompat
import com.google.gson.annotations.Expose
import org.y20k.escapepods.helpers.Keys
import java.util.*


/*
 * Podcast class
 */
class Podcast(@Expose var name: String = "",
              @Expose var description: String = "",
              @Expose var image: String = "android.resource://org.y20k.escapepods/drawable/default_podcast_cover",
              @Expose var episodes: MutableList<MediaMetadataCompat> = mutableListOf<MediaMetadataCompat>(),
              @Expose var lastUpdate: Date = Calendar.getInstance().time,
              @Expose var remoteImageFileLocation: String = "",
              @Expose var remotePodcastFeedLocation: String = "") {


    /* overrides toString method */
    override fun toString(): String {
        val descriptionLength: Int = 50
        val stringBuilder: StringBuilder = StringBuilder()
        val shortDescriptionLength: Int = if (description.length <= descriptionLength) description.length -1 else descriptionLength
        val shortDescription: String = description.substring(0, shortDescriptionLength)
        stringBuilder
                .append("$name\n")
                .append("$shortDescription ...\n")
        for (episode in episodes) {
            val episodeTitle: String = episode.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            val episodeDescription: String = episode.getString(Keys.METADATA_CUSTOM_KEY_DESCRIPTION)
            val episodeShortDescriptionLength: Int = if (episodeDescription.length <= descriptionLength) episodeDescription.length -1 else 25
            val episodeShortDescription: String = episodeDescription.substring(0, episodeShortDescriptionLength)
            val publicationDate: String = episode.getString(Keys.METADATA_CUSTOM_KEY_PUBLICATION_DATE)
            val audioUrl: String = episode.getString(Keys.METADATA_CUSTOM_KEY_AUDIO_LINK_URL)
            val imageUrl: String = episode.getString(Keys.METADATA_CUSTOM_KEY_IMAGE_LINK_URL)
            stringBuilder.append("$episodeTitle\n")
            stringBuilder.append("$episodeShortDescription ...\n")
            stringBuilder.append("$publicationDate\n")
            stringBuilder.append("$audioUrl \n")
            stringBuilder.append("$imageUrl \n")
        }
        return stringBuilder.toString()
    }

}