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

import android.os.Parcelable
import com.google.gson.annotations.Expose
import kotlinx.android.parcel.Parcelize
import org.y20k.escapepods.helpers.Keys
import java.text.DateFormat
import java.util.*


/*
 * Episode class
 */
@Parcelize
data class Episode (@Expose var guid: String = "",
                    @Expose var title: String = "",
                    @Expose var description: String = "",
                    @Expose var audio: String = "",
                    @Expose var cover: String = Keys.LOCATION_DEFAULT_COVER,
                    @Expose var publicationDate: Date = Calendar.getInstance().time,
                    @Expose var isPlaying: Boolean = false,
                    @Expose var playbackPosition: Long = 0L,
                    @Expose var listened: Boolean = false,
                    @Expose var manuallyDownloaded: Boolean = false,
                    @Expose var remoteCoverFileLocation: String = "",
                    @Expose var remoteAudioFileLocation: String = "",
                    @Expose var podcastName: String = ""): Parcelable {


    /* overrides toString method */
    override fun toString(): String {
        val descriptionMaxLength: Int = 50
        val stringBuilder: StringBuilder = StringBuilder()
        val episodeShortDescriptionLength: Int = if (description.length <= descriptionMaxLength) description.length -1 else descriptionMaxLength
//        val episodeShortDescription: String = description.trim().substring(0, episodeShortDescriptionLength)
        val episodeShortDescription: String = description
        stringBuilder.append("\nGUID: ${guid}\n")
        stringBuilder.append("Title: ${title}\n")
        //stringBuilder.append("$episodeShortDescription ...\n")
        stringBuilder.append("${publicationDate}\n")
        stringBuilder.append("Audio: $audio \n")
        stringBuilder.append("Cover: $cover \n")
        stringBuilder.append("Audio URL: ${remoteAudioFileLocation} \n")
        // stringBuilder.append("Cover URL: ${remoteCoverFileLocation} \n") // is currently not being read by RssHelper
        stringBuilder.append("Manually downloaded: $manuallyDownloaded \n")
        return stringBuilder.toString()
    }


    /* Creates a readable date string */
    fun getDateString(dateStyle: Int): String {
        return DateFormat.getDateInstance(dateStyle, Locale.getDefault()).format(publicationDate)
    }


    /* Return a unique media id - currently just the remoteAudioFileLocation */
    fun getMediaId(): String {
        return remoteAudioFileLocation
        // return (remoteAudioFileLocation + guid).hashCode().toString() // hash value of remoteAudioFileLocation and guid
    }

}