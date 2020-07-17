package org.y20k.escapepod.database

import androidx.room.Embedded
import androidx.room.Relation

data class PodcastWithEpisodesEntity(
        @Embedded
        val podcast: PodcastEntity,

        @Relation(parentColumn = "pid", entityColumn = "podcast_id")
        val episodes: List<EpisodeEntity> = emptyList()
)