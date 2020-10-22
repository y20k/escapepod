/*
 * Episode.kt
 * Implements the Episode class
 * A Episode object holds the base data of an episode
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package org.y20k.escapepod.database.objects

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.y20k.escapepod.xml.RssHelper
import java.util.*


/*
 * Episode class
 */
@Entity(tableName = "episodes", indices = arrayOf(Index(value = ["media_id", "episode_remote_podcast_feed_location"], unique = true)))
data class Episode (

        // unique media id - currently just the remoteAudioFileLocation
        @PrimaryKey
        @ColumnInfo (name = "media_id") val mediaId: String,

        @ColumnInfo (name = "guid") val guid: String,
        @ColumnInfo (name = "title") val title: String,
        @ColumnInfo (name = "audio") val audio: String,
        @ColumnInfo (name = "cover") val cover: String,
        @ColumnInfo (name = "small_cover") val smallCover: String,
        @ColumnInfo (name = "publication_date") val publicationDate: Date,
        @ColumnInfo (name = "playback_state") val playbackState: Int,
        @ColumnInfo (name = "playback_position") val playbackPosition: Long,
        @ColumnInfo (name = "duration") val duration: Long,
        @ColumnInfo (name = "manually_deleted") val manuallyDeleted: Boolean,
        @ColumnInfo (name = "manually_downloaded") val manuallyDownloaded: Boolean,
        @ColumnInfo (name = "podcast_name") val podcastName: String,
        @ColumnInfo (name = "remote_audio_file_location") val remoteAudioFileLocation: String,
        @ColumnInfo (name = "remote_audio_file_name") val remoteAudioFileName: String,
        @ColumnInfo (name = "remote_cover_file_location") val remoteCoverFileLocation: String,

        // defines the relation between episode and podcast
        @ColumnInfo (name = "episode_remote_podcast_feed_location") val episodeRemotePodcastFeedLocation: String

        ) {


    /* Super-Constructor - can be used to clone an episode - with/without selected alterations */
    constructor(episode: Episode,
                mediaId: String = episode.mediaId,
                guid: String  = episode.guid,
                title: String  = episode.title,
                audio: String  = episode.audio,
                cover: String  = episode.cover,
                smallCover: String  = episode.smallCover,
                publicationDate: Date = episode.publicationDate,
                playbackState: Int = episode.playbackState,
                playbackPosition: Long = episode.playbackPosition,
                duration: Long = episode.duration,
                manuallyDeleted: Boolean = episode.manuallyDeleted,
                manuallyDownloaded: Boolean = episode.manuallyDownloaded,
                podcastName: String  = episode.podcastName,
                remoteAudioFileLocation: String  = episode.remoteAudioFileLocation,
                remoteAudioFileName: String  = episode.remoteAudioFileName,
                remoteCoverFileLocation: String  = episode.remoteCoverFileLocation,
                episodeRemotePodcastFeedLocation: String  = episode.episodeRemotePodcastFeedLocation) : this (
            mediaId = mediaId,
            guid = guid,
            title = title,
            audio = audio,
            cover = cover,
            smallCover = smallCover,
            publicationDate = publicationDate,
            playbackState = playbackState,
            playbackPosition = playbackPosition,
            duration = duration,
            manuallyDeleted = manuallyDeleted,
            manuallyDownloaded = manuallyDownloaded,
            podcastName = podcastName,
            remoteAudioFileLocation = remoteAudioFileLocation,
            remoteAudioFileName = remoteAudioFileName,
            remoteCoverFileLocation = remoteCoverFileLocation,
            episodeRemotePodcastFeedLocation = episodeRemotePodcastFeedLocation
    )


    /* Constructor that uses output from RssHelper */
    constructor(rssEpisode: RssHelper.RssEpisode) : this (
            // use remoteAudioFileLocation as unique media id
            mediaId = rssEpisode.remoteAudioFileLocation,
            //mediaId = (rssEpisode.remoteAudioFileLocation + rssEpisode.guid).hashCode().toString()

            guid = rssEpisode.guid,
            title = rssEpisode.title,
            audio = rssEpisode.audio,
            cover = rssEpisode.cover,
            smallCover = rssEpisode.smallCover,
            publicationDate = rssEpisode.publicationDate,
            playbackState = rssEpisode.playbackState,
            playbackPosition = rssEpisode.playbackPosition,
            duration = rssEpisode.duration,
            manuallyDownloaded = rssEpisode.manuallyDownloaded,
            manuallyDeleted = rssEpisode.manuallyDeleted,
            podcastName = rssEpisode.podcastName,
            remoteCoverFileLocation = rssEpisode.remoteCoverFileLocation,
            remoteAudioFileLocation = rssEpisode.remoteAudioFileLocation,
            remoteAudioFileName = rssEpisode.remoteAudioFileLocation.substring(rssEpisode.remoteAudioFileLocation.lastIndexOf('/')+1, rssEpisode.remoteAudioFileLocation.length),
            episodeRemotePodcastFeedLocation = rssEpisode.episodeRemotePodcastFeedLocation
    )


    /* Return if an episode has been listened to end */
    fun isFinished(): Boolean = playbackPosition >= duration


    /* Return if an episode has been started listening to */
    fun hasBeenStarted(): Boolean = playbackPosition > 0L


    /* Creates MediaItem for a single episode - used by collection provider */
    fun toMediaMetaItem():  MediaBrowserCompat.MediaItem {
        val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
        mediaDescriptionBuilder.setMediaId(mediaId)
        mediaDescriptionBuilder.setTitle(title)
        mediaDescriptionBuilder.setSubtitle(podcastName)
        mediaDescriptionBuilder.setDescription(podcastName)
        //mediaDescriptionBuilder.setIconUri(Uri.parse(episode.cover))
        return MediaBrowserCompat.MediaItem(mediaDescriptionBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

}