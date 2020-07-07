package org.y20k.escapepod.core

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "podcast")
data class PodcastEntity(

    @PrimaryKey val pid: Int,
    @ColumnInfo (name = "podcast_name") val name: String,
    @ColumnInfo (name = "podcast_description") val description: String,
    @ColumnInfo (name = "podcast_website") val website: String,
    @ColumnInfo (name = "podcast_cover") val cover: String,
    @ColumnInfo (name = "podcast_small_cover") val smallCover: String,
    @ColumnInfo (name = "podcast_last_update") val lastUpdate: Long,
    @ColumnInfo (name = "podcast_remote_image_file_location") val remoteImageFileLocation: String,
    @ColumnInfo (name = "podcast_remote_podcast_feed_location") val remotePodcastFeedLocation: String

)
