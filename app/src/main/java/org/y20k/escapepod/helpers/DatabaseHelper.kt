package org.y20k.escapepod.helpers

import org.y20k.escapepod.core.Collection
import org.y20k.escapepod.database.EpisodeEntity
import org.y20k.escapepod.database.PodcastEntity

object DatabaseHelper {

    fun convertToPodcastEntityList(collection: Collection): Pair<List<PodcastEntity>, List<EpisodeEntity>> {
        val podcastList: MutableList<PodcastEntity> = mutableListOf()
        val episodeList: MutableList<EpisodeEntity> = mutableListOf()
        collection.podcasts.forEach { podcast ->
            val podcastEntity: PodcastEntity = PodcastEntity(
                    name = podcast.name,
                    description = podcast.description,
                    website = podcast.website,
                    cover = podcast.cover,
                    smallCover = podcast.smallCover,
                    lastUpdate = podcast.lastUpdate.time,
                    remoteImageFileLocation = podcast.remoteImageFileLocation,
                    remotePodcastFeedLocation = podcast.remotePodcastFeedLocation
            )
            podcastList.add(podcastEntity)

            podcast.episodes.forEach { episode ->
                val episodeEntity: EpisodeEntity = EpisodeEntity(
                        podcastId = podcastEntity.pid,
                        guid = episode.guid,
                        title = episode.title,
                        description = episode.description,
                        audio = episode.audio,
                        cover = episode.cover,
                        smallCover = episode.smallCover,
                        publicationDate = episode.publicationDate.time,
                        playbackState = episode.playbackState,
                        playbackPosition = episode.playbackPosition,
                        duration = episode.duration,
                        manuallyDownloaded = episode.manuallyDownloaded,
                        manuallyDeleted = episode.manuallyDeleted,
                        remoteCoverFileLocation = episode.remoteCoverFileLocation,
                        remoteAudioFileLocation = episode.remoteAudioFileLocation
                )
                episodeList.add(episodeEntity)
            }


        }
        return Pair(podcastList, episodeList)
    }

}