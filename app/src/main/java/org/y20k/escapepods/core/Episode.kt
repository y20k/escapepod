/*
 * Episode.kt
 * Implements the Episode class
 * A Episode object holds the base data of an episode
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
 * Episode class
 */
class Episode (@Expose var guid: String = "",
               @Expose var title: String = "",
               @Expose var description: String = "",
               @Expose var audio: String = "",
               @Expose var cover: String = "android.resource://org.y20k.escapepods/drawable/default_podcast_cover",
               @Expose var publicationDate: Date = Calendar.getInstance().time,
               @Expose var remoteCoverFileLocation: String = "",
               @Expose var remoteAudioFileLocation: String = "") {


    /* overrides toString method */
    override fun toString(): String {
        val descriptionMaxLength: Int = 50
        val stringBuilder: StringBuilder = StringBuilder()
        val episodeShortDescriptionLength: Int = if (description.length <= descriptionMaxLength) description.length -1 else descriptionMaxLength
        val episodeShortDescription: String = description.trim().substring(0, episodeShortDescriptionLength)
        stringBuilder.append("\nGUID: ${guid}\n")
        stringBuilder.append("${title}\n")
        stringBuilder.append("$episodeShortDescription ...\n")
        stringBuilder.append("${publicationDate}\n")
        stringBuilder.append("Audio: $audio \n")
        stringBuilder.append("Cover: $cover \n")
        stringBuilder.append("${remoteAudioFileLocation} \n")
        stringBuilder.append("${remoteCoverFileLocation}")
        return stringBuilder.toString()
    }

}