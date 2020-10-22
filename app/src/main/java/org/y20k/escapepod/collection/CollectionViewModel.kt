/*
 * CollectionViewModel.kt
 * Implements the CollectionViewModel class
 * A CollectionViewModel stores the podcast collection as live data
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.wrappers.PodcastWithAllEpisodesWrapper
import org.y20k.escapepod.database.wrappers.PodcastWithRecentEpisodesWrapper
import org.y20k.escapepod.helpers.CollectionHelper
import org.y20k.escapepod.helpers.LogHelper


/*
 * CollectionViewModel.class
 */
class CollectionViewModel(application: Application) : AndroidViewModel(application) {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(CollectionViewModel::class.java)


    /* Main class variables */
    val collectionLiveData: LiveData<List<PodcastWithRecentEpisodesWrapper>>
    val numberOfPodcastsLiveData: LiveData<Int>

    private val backgroundJob = Job()
    private var collectionDatabase: CollectionDatabase = CollectionDatabase.getInstance(application)


    /* Init constructor */
    init {
        // initialize live data - note: no need for async/background thread execution, because LiveData updating already asynchronous
        collectionLiveData = collectionDatabase.podcastDao().getAllPodcastsWithMostRecentEpisodeLiveData()
        numberOfPodcastsLiveData = collectionDatabase.podcastDao().getSize()
    }


    /* Overrides onCleared */
    override fun onCleared() {
        super.onCleared()
        backgroundJob.cancel()
    }


    /* Delete a podcast and all its episodes from database */
    fun removePodcast(remotePodcastFeedLocation: String) {
        GlobalScope.launch {
            val podcast: PodcastWithAllEpisodesWrapper? = collectionDatabase.podcastDao().getWithRemotePodcastFeedLocation(remotePodcastFeedLocation)
            if (podcast != null) {
                collectionDatabase.episodeDao().deleteAll(podcast.data.remotePodcastFeedLocation)
                collectionDatabase.episodeDescriptionDao().deleteAll(podcast.data.remotePodcastFeedLocation)
                collectionDatabase.podcastDao().delete(podcast.data)
                collectionDatabase.podcastDescriptionDao().delete(podcast.data.remotePodcastFeedLocation)
            }
        }
    }


    /* Marks an episode as played */
    fun markEpisodePlayed(mediaId: String) {
        GlobalScope.launch {
            collectionDatabase.episodeDao().markPlayed(mediaId = mediaId)
        }
    }


    /* Remove local audio reference within an episode */
    fun deleteEpisodeAudio(mediaId: String) {
        GlobalScope.launch {
            val episode: Episode? = collectionDatabase.episodeDao().findByMediaId(mediaId)
            LogHelper.e(TAG, "DING = ${episode?.title}") // todo remove
            if (episode != null) {
                collectionDatabase.episodeDao().update(CollectionHelper.deleteEpisodeAudioFile(episode = episode, manuallyDeleted = true))
            }
        }
    }

}