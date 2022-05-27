/*
 * ImportHelper.kt
 * Implements the ImportHelper object
 * A ImportHelper provides helper methods for importing data from previous version sof Escapepod
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.legacy

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.EpisodeDescription
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.database.objects.PodcastDescription
import org.y20k.escapepod.helpers.FileHelper


/*
 * ImportHelper object
 */
object ImportHelper {

    /* Import a collection stored in the v0.9.x JSON format into database */
    fun importLegacyCollection(context: Context, collectionDatabase: CollectionDatabase) {

        // read legacy collection (json)
        val legacyCollection: LegacyCollection = FileHelper.readLegacyCollection(context)

        // initialize lists
        val podcastDataList: MutableList<Podcast> = mutableListOf()
        val podcastDescriptionList: MutableList<PodcastDescription> = mutableListOf()
        val episodeList: MutableList<Episode> = mutableListOf()
        val episodeDescriptionList: MutableList<EpisodeDescription> = mutableListOf()

        // iterate through podcasts
        legacyCollection.podcasts.forEach { legacyPodcast ->
            // create and add podcast
            val podcastData: Podcast = Podcast(
                    name = legacyPodcast.name,
                    website = legacyPodcast.website,
                    cover = legacyPodcast.cover,
                    smallCover = legacyPodcast.smallCover,
                    latestEpisodeDate = legacyPodcast.lastUpdate,
                    remoteImageFileLocation = legacyPodcast.remoteImageFileLocation,
                    remotePodcastFeedLocation = legacyPodcast.remotePodcastFeedLocation
            )
            podcastDataList.add(podcastData)
            // create and add podcast description
            val podcastDescription: PodcastDescription = PodcastDescription(
                    description = legacyPodcast.description,
                    remotePodcastFeedLocation = legacyPodcast.remotePodcastFeedLocation
            )
            podcastDescriptionList.add(podcastDescription)

            // iterate through episodes
            legacyPodcast.episodes.forEach { legacyEpisode ->
                // create and add episode
                val episode: Episode = Episode(
                        mediaId = legacyEpisode.remoteAudioFileLocation,
                        guid = legacyEpisode.guid,
                        title = legacyEpisode.title,
                        audio = legacyEpisode.audio,
                        cover = legacyEpisode.cover,
                        smallCover = legacyEpisode.smallCover,
                        publicationDate = legacyEpisode.publicationDate,
                        isPlaying = legacyEpisode.isPlaying,
                        playbackPosition = legacyEpisode.playbackPosition,
                        duration = legacyEpisode.duration,
                        manuallyDeleted = legacyEpisode.manuallyDeleted,
                        manuallyDownloaded = legacyEpisode.manuallyDownloaded,
                        podcastName = legacyEpisode.podcastName,
                        remoteAudioFileLocation = legacyEpisode.remoteAudioFileLocation,
                        remoteAudioFileName = FileHelper.getFileNameFromUrl(legacyEpisode.remoteAudioFileLocation),
                        remoteCoverFileLocation = legacyEpisode.remoteCoverFileLocation,
                        episodeRemotePodcastFeedLocation = podcastData.remotePodcastFeedLocation
                )
                episodeList.add(episode)
                // create and add episode description
                val episodeDescription: EpisodeDescription = EpisodeDescription(
                        mediaId = legacyEpisode.remoteAudioFileLocation,
                        description = legacyEpisode.description,
                        episodeRemotePodcastFeedLocation = podcastData.remotePodcastFeedLocation
                )
                episodeDescriptionList.add(episodeDescription)
            }


        }

        // insert podcasts and episodes
        CoroutineScope(IO).launch {
            collectionDatabase.podcastDao().insertAll(podcastDataList)
            collectionDatabase.podcastDescriptionDao().insertAll(podcastDescriptionList)
            collectionDatabase.episodeDao().insertAll(episodeList)
            collectionDatabase.episodeDescriptionDao().insertAll(episodeDescriptionList)
        }

    }

}