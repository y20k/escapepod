/*
 * Episode.kt
 * Implements the Episode class
 * A Episode object holds the base data of an episode
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.legacy

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import org.y20k.escapepod.Keys
import java.text.DateFormat
import java.util.*


/*
 * episode class
 */
@Keep
data class LegacyEpisode (@Expose var guid: String = String(),
                          @Expose var title: String = String(),
                          @Expose var description: String = String(),
                          @Expose var audio: String = String(),
                          @Expose var cover: String = Keys.LOCATION_DEFAULT_COVER,
                          @Expose var smallCover: String = Keys.LOCATION_DEFAULT_COVER,
                          @Expose var chapters: MutableList<Pair<Long, String>> = mutableListOf<Pair<Long, String>>(),
                          @Expose var publicationDate: Date = Keys.DEFAULT_DATE,
                          @Expose var playbackState: Int = Keys.EPISODE_IS_STOPPED,
                          @Expose var playbackPosition: Long = 0L,
                          @Expose var duration: Long = 0L,
                          @Expose var manuallyDownloaded: Boolean = false,
                          @Expose var manuallyDeleted: Boolean = false,
                          @Expose var remoteCoverFileLocation: String = String(),
                          @Expose var remoteAudioFileLocation: String = String(),
                          @Expose var podcastName: String = String(),
                          @Expose var podcastFeedLocation: String = String(),
                          @Expose var podcastWebsite: String = String()) {


    /* overrides toString method */
    override fun toString(): String {
        val descriptionMaxLength: Int = 50
        val stringBuilder: StringBuilder = StringBuilder()
        val episodeShortDescriptionLength: Int = if (description.length <= descriptionMaxLength) description.length -1 else descriptionMaxLength
//        val episodeShortDescription: String = description.trim().substring(0, episodeShortDescriptionLength)
        val episodeShortDescription: String = description
        stringBuilder.append("\nGUID: $guid (playback = $playbackState)\n")
        stringBuilder.append("Title: $title\n")
        //stringBuilder.append("$episodeShortDescription ...\n")
        stringBuilder.append("$publicationDate\n")
        stringBuilder.append("Audio: $audio \n")
        stringBuilder.append("Cover: $cover \n")
        stringBuilder.append("Audio URL: $remoteAudioFileLocation \n")
        // stringBuilder.append("Manually downloaded: $manuallyDownloaded \n")
        return stringBuilder.toString()
    }


    /* Creates a readable date string */
    fun getDateString(dateStyle: Int = DateFormat.MEDIUM): String = DateFormat.getDateInstance(dateStyle, Locale.getDefault()).format(publicationDate)


    /* Return a unique media id - currently just the remoteAudioFileLocation */
    fun getMediaId(): String = remoteAudioFileLocation
        // = (remoteAudioFileLocation + guid).hashCode().toString() // hash value of remoteAudioFileLocation and guid


    /* Checks if episode has the minimum required elements / data */
    fun isValid(): Boolean = title.isNotEmpty() && remoteAudioFileLocation.isNotEmpty() && publicationDate != Keys.DEFAULT_DATE


    /* Return if an episode has been listened to end */
    fun isFinished(): Boolean = playbackPosition >= duration


    /* Return if an episode has been started listening to */
    fun hasBeenStarted(): Boolean = playbackPosition > 0L


    /* Creates a deep copy of an episode */
    fun deepCopy(): LegacyEpisode = LegacyEpisode(guid = guid,
                                      title = title,
                                      description = description,
                                      audio = audio,
                                      cover = cover,
                                      smallCover = smallCover,
                                      chapters = chapters,
                                      publicationDate = publicationDate,
                                      playbackState = playbackState,
                                      playbackPosition = playbackPosition,
                                      duration = duration,
                                      manuallyDownloaded = manuallyDownloaded,
                                      manuallyDeleted = manuallyDeleted,
                                      remoteCoverFileLocation = remoteCoverFileLocation,
                                      remoteAudioFileLocation = remoteAudioFileLocation,
                                      podcastName = podcastName,
                                      podcastFeedLocation = podcastFeedLocation,
                                      podcastWebsite = podcastWebsite)

}