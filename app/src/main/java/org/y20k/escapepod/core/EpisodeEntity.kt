package org.y20k.escapepod.core

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "episode",
        foreignKeys = [ForeignKey(entity = PodcastEntity::class, parentColumns = ["pid"], childColumns = ["podcast_id"])])
data class EpisodeEntity (

        @PrimaryKey val eid: Int,
        @ColumnInfo (name = "podcast_id") val podcastId: Int,
        @ColumnInfo (name = "episode_guid") val guid: String,
        @ColumnInfo (name = "episode_title") val title: String,
        @ColumnInfo (name = "episode_description") val description: String,
        @ColumnInfo (name = "episode_audio") val audio: String,
        @ColumnInfo (name = "episode_cover") val cover: String,
        @ColumnInfo (name = "episode_small_cover") val smallCover: String,
        @ColumnInfo (name = "episode_publication_date") val publicationDate: Long,
        @ColumnInfo (name = "episode_playback_state") val playbackState: Int,
        @ColumnInfo (name = "episode_playback_position") val playbackPosition: Long,
        @ColumnInfo (name = "episode_duration") val duration: Long,
        @ColumnInfo (name = "episode_manually_downloaded") val manuallyDownloaded: Boolean,
        @ColumnInfo (name = "episode_manually_deleted") val manuallyDeleted: Boolean,
        @ColumnInfo (name = "episode_remote_cover_file_location") val remoteCoverFileLocation: String,
        @ColumnInfo (name = "episode_remote_audio_file_location") val remoteAudioFileLocation: String

)