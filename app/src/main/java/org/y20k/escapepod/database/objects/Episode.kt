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

package org.y20k.escapepod.database.objects

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.y20k.escapepod.helpers.FileHelper
import org.y20k.escapepod.xml.RssHelper
import java.util.*


/*
 * Episode class
 */
@Entity(tableName = "episodes", indices = [Index(value = ["media_id"], unique = true), Index(value = ["publication_date"], unique = false), Index(value = ["remote_audio_file_location"], unique = true), Index(value = ["episode_remote_podcast_feed_location"], unique = false)])
data class Episode (

        // unique media id - currently just the remoteAudioFileLocation
        @PrimaryKey
        @ColumnInfo (name = "media_id") val mediaId: String,                                        /* database version 1 | app version 31 => added   */

        @ColumnInfo (name = "guid") val guid: String,                                               /* database version 1 | app version 31 => added   */
        @ColumnInfo (name = "title") val title: String,                                             /* database version 1 | app version 31 => added   */
        @ColumnInfo (name = "audio") val audio: String,                                             /* database version 1 | app version 31 => added   */
        @ColumnInfo (name = "cover") val cover: String,                                             /* database version 1 | app version 31 => added   */
        @ColumnInfo (name = "small_cover") val smallCover: String,                                  /* database version 1 | app version 31 => added   */
        @ColumnInfo (name = "publication_date") val publicationDate: Date,                          /* database version 1 | app version 31 => added   */
        /* @ColumnInfo (name = "playback_state") val playbackState: Int, */                         /* database version 2 | app version 40 => removed */
        @ColumnInfo (name = "is_playing") val isPlaying: Boolean,                                   /* database version 2 | app version 40 => added   */
        @ColumnInfo (name = "playback_position") val playbackPosition: Long,                        /* database version 1 | app version 31 => added   */
        @ColumnInfo (name = "duration") val duration: Long,                                         /* database version 1 | app version 31 => added   */
        @ColumnInfo (name = "manually_deleted") val manuallyDeleted: Boolean,                       /* database version 1 | app version 31 => added   */
        @ColumnInfo (name = "manually_downloaded") val manuallyDownloaded: Boolean,                 /* database version 1 | app version 31 => added   */
        @ColumnInfo (name = "podcast_name") val podcastName: String,                                /* database version 1 | app version 31 => added   */
        @ColumnInfo (name = "remote_audio_file_location") val remoteAudioFileLocation: String,      /* database version 1 | app version 31 => added   */
        @ColumnInfo (name = "remote_audio_file_name") val remoteAudioFileName: String,              /* database version 1 | app version 31 => added   */
        @ColumnInfo (name = "remote_cover_file_location") val remoteCoverFileLocation: String,      /* database version 1 | app version 31 => added   */

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
                isPlaying: Boolean = episode.isPlaying,
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
            isPlaying = isPlaying,
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
            isPlaying = rssEpisode.isPlaying,
            playbackPosition = rssEpisode.playbackPosition,
            duration = rssEpisode.duration,
            manuallyDownloaded = rssEpisode.manuallyDownloaded,
            manuallyDeleted = rssEpisode.manuallyDeleted,
            podcastName = rssEpisode.podcastName,
            remoteCoverFileLocation = rssEpisode.remoteCoverFileLocation,
            remoteAudioFileLocation = rssEpisode.remoteAudioFileLocation,
            remoteAudioFileName = FileHelper.getFileNameFromUrl(rssEpisode.remoteAudioFileLocation),
            episodeRemotePodcastFeedLocation = rssEpisode.episodeRemotePodcastFeedLocation
    )


    /* Return if an episode has been listened to end */
    fun isFinished(): Boolean = playbackPosition >= duration


    /* Return if an episode has been started listening to */
    fun hasBeenStarted(): Boolean = playbackPosition > 0L


//    /* Creates MediaItem for a single episode - used by collection provider */
//    fun toMediaMetaItem():  MediaBrowserCompat.MediaItem {
//        val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
//        mediaDescriptionBuilder.setMediaId(mediaId)
//        mediaDescriptionBuilder.setTitle(title)
//        mediaDescriptionBuilder.setSubtitle(podcastName)
//        mediaDescriptionBuilder.setDescription(podcastName)
//        //mediaDescriptionBuilder.setIconUri(episode.cover.toUri())
//        return MediaBrowserCompat.MediaItem(mediaDescriptionBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
//    }

}