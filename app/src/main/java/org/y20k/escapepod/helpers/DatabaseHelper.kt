package org.y20k.escapepod.helpers

import org.y20k.escapepod.core.Collection
import org.y20k.escapepod.database.PodcastEntity

object DatabaseHelper {

    fun convertToPodcastEntityList(collection: Collection): List<PodcastEntity> {
        val list: MutableList<PodcastEntity> = mutableListOf()
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
            list.add(podcastEntity)
        }
        return list
    }

}