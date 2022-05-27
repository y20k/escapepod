/*
 * CollectionAdapter.kt
 * Implements the CollectionAdapter class
 * A CollectionAdapter is a custom adapter providing podcast card views for a RecyclerView
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.collection

import android.content.Context
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.EpisodeDescription
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.database.wrappers.EpisodeMostRecentView
import org.y20k.escapepod.database.wrappers.PodcastWithRecentEpisodesWrapper
import org.y20k.escapepod.dialogs.ShowAllEpisodesDialog
import org.y20k.escapepod.dialogs.ShowNotesDialog
import org.y20k.escapepod.helpers.*


/*
 * CollectionAdapter class
 */
class CollectionAdapter(private val context: Context, private val collectionDatabase: CollectionDatabase, private val collectionAdapterListener: CollectionAdapterListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(CollectionAdapter::class.java)


    /* Main class variables */
    private lateinit var collectionViewModel: CollectionViewModel
    private var collection: List<PodcastWithRecentEpisodesWrapper> = listOf()
    private var expandedPodcastFeedLocation: String = PreferencesHelper.loadPodcastListExpandedFeedLocation()
    private var expandedPodcastPosition: Int = -1


    /* Listener Interface */
    interface CollectionAdapterListener {
        fun onPlayButtonTapped(selectedEpisode: Episode, streaming: Boolean = false)
        fun onMarkListenedButtonTapped(selectedEpisode: Episode)
        fun onDownloadButtonTapped(selectedEpisode: Episode)
        fun onDeleteButtonTapped(selectedEpisode: Episode)
        fun onAddNewButtonTapped()
    }


    /* Overrides onAttachedToRecyclerView */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        // create view model and observe changes in collection view model
        collectionViewModel = ViewModelProvider(context as AppCompatActivity).get(CollectionViewModel::class.java)
        observeCollectionViewModel(context as LifecycleOwner)

    }


    /* Overrides onCreateViewHolder */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        when (viewType) {
            Keys.VIEW_TYPE_ADD_NEW -> {
                // get view, put view into holder and return
                val v = LayoutInflater.from(parent.context).inflate(R.layout.card_add_new_podcast, parent, false)
                return AddNewViewHolder(v)
            }
            else -> {
                // get view, put view into holder and return
                val v = LayoutInflater.from(parent.context).inflate(R.layout.card_podcast, parent, false)
                return PodcastViewHolder(v)
            }
        }
    }


    /* Overrides onBindViewHolder */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        when (holder) {

            // CASE ADD NEW CARD
            is AddNewViewHolder -> {
                // get reference to StationViewHolder
                val addNewViewHolder: AddNewViewHolder = holder
                addNewViewHolder.addNewPodcastButtonView.setOnClickListener {
                    // show the add podcast dialog
                    collectionAdapterListener.onAddNewButtonTapped()
                }
                addNewViewHolder.settingsButtonView.setOnClickListener {
                    it.findNavController().navigate(R.id.settings_destination)
                }
            }

            // CASE PODCAST CARD
            is PodcastViewHolder -> {
                // get podcast from position
                val podcast: PodcastWithRecentEpisodesWrapper = collection[position]

                // get reference to StationViewHolder
                val podcastViewHolder: PodcastViewHolder = holder

                // set up podcast
                setPodcastName(podcastViewHolder, podcast.data)
                setPodcastImage(podcastViewHolder, podcast.data)

                // set up episodes
                if (podcast.episodes.isNotEmpty()) {
                    // sort episodes by date
                    val podcastWithSortedEpisodes: PodcastWithRecentEpisodesWrapper = PodcastWithRecentEpisodesWrapper(data = podcast.data, episodes = podcast.episodes.sortedByDescending { it.data.publicationDate })
                    // current episode
                    setEpisodeTitle(podcastViewHolder.currentEpisodeViews, podcastWithSortedEpisodes.data, podcastWithSortedEpisodes.episodes[0].data)
                    setEpisodeButtons(podcastViewHolder.currentEpisodeViews, podcastWithSortedEpisodes.episodes[0].data)
                    setEpisodePlaybackProgress(podcastViewHolder.currentEpisodeViews, podcastWithSortedEpisodes.episodes[0].data)
                    // older episodes list
                    setOlderEpisodesList(podcastViewHolder, position, podcastWithSortedEpisodes)
                }
            }
        }
    }


    /* Sets the podcast name view */
    private fun setPodcastName(podcastViewHolder: PodcastViewHolder, podcast: Podcast) {
        podcastViewHolder.podcastNameView.text = podcast.name
    }


    /* Sets the podcast image view */
    private fun setPodcastImage(podcastViewHolder: PodcastViewHolder, podcast: Podcast) {
        podcastViewHolder.podcastImageView.setImageBitmap(ImageHelper.getPodcastCover(context, podcast.smallCover))
        // podcastViewHolder.podcastImageView.clipToOutline = true // apply rounded corner mask to covers
        podcastViewHolder.podcastImageView.setOnLongClickListener {
            DownloadHelper.refreshCover(context, podcast)
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(50)
            // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
            return@setOnLongClickListener true
        }
    }


    /* Sets up an episode's title views */
    private fun setEpisodeTitle(episodeViewHolder: EpisodeViewHolder, podcast: Podcast, episode: Episode) {
        episodeViewHolder.episodeDateView.text = DateTimeHelper.getDateString(episode.publicationDate)
        episodeViewHolder.episodeDateView.setOnLongClickListener {
            CoroutineScope(IO).launch {
                val episodeDescription: EpisodeDescription? = collectionDatabase.episodeDescriptionDao().findByMediaId(episode.mediaId)
                if (episodeDescription != null) {
                    withContext(Main) { ShowNotesDialog().show(context, podcast, episode, episodeDescription) }
                }
            }
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(50)
            // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
            return@setOnLongClickListener true
        }
        episodeViewHolder.episodeTitleView.text = episode.title
        episodeViewHolder.episodeTitleView.setOnLongClickListener {
            CoroutineScope(IO).launch {
                val episodeDescription: EpisodeDescription? = collectionDatabase.episodeDescriptionDao().findByMediaId(episode.mediaId)
                if (episodeDescription != null) {
                    withContext(Main) { ShowNotesDialog().show(context, podcast, episode, episodeDescription) }
                }
            }
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(50)
            // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
            return@setOnLongClickListener true
        }
    }


    /* Sets up the circular progress bar */
    private fun setEpisodePlaybackProgress(episodeViewHolder: EpisodeViewHolder, episode: Episode) {
        // start => 12 => playbackPosition = 0
        // finish => 0 => playbackPosition = duration
        val progress: Double = (episode.duration.toDouble() - episode.playbackPosition.toDouble()) / episode.duration.toDouble() * 12
        episodeViewHolder.episodePlaybackProgressView.progress = progress.toInt()
    }


    /* Sets up an episode's play, download and delete button views */
    private fun setEpisodeButtons(episodeViewHolder: EpisodeViewHolder, episode: Episode) {
        val episodeIsPlaying: Boolean = episode.isPlaying
        LogHelper.e(TAG, "setEpisodeButtons => ${episode.isPlaying }${episode.title}")
        when (episodeIsPlaying) {
            true -> episodeViewHolder.episodePlayButtonView.setImageResource(R.drawable.ic_pause_symbol_24dp)
            false -> episodeViewHolder.episodePlayButtonView.setImageResource(R.drawable.ic_play_symbol_24dp)
        }
        episodeViewHolder.episodeDownloadButtonView.setOnClickListener {
            collectionAdapterListener.onDownloadButtonTapped(episode)
        }
        episodeViewHolder.episodePlayButtonView.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(episode, streaming = false)
        }
        episodeViewHolder.episodePlayButtonView.setOnLongClickListener {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(50)
            // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
            collectionAdapterListener.onMarkListenedButtonTapped(episode)
            return@setOnLongClickListener true
        }
        episodeViewHolder.episodeDeleteButtonView.setOnClickListener {
            collectionAdapterListener.onDeleteButtonTapped(episode)
        }
        if (episode.audio.isNotEmpty()) {
            episodeViewHolder.episodeDownloadButtonView.isGone = true
            episodeViewHolder.episodePlaybackViews.isVisible = true
        } else {
            episodeViewHolder.episodeDownloadButtonView.isVisible = true
            episodeViewHolder.episodePlaybackViews.isGone = true
        }
    }


    /* Fills the list of older episodes */
    private fun setOlderEpisodesList(podcastViewHolder: PodcastViewHolder, position: Int, podcast: PodcastWithRecentEpisodesWrapper) {
        // set up Older Episodes toggle button
        if (podcast.episodes.size > 1) {
            // single tap: toggle Older Episodes
            podcastViewHolder.olderEpisodesButtonView.setOnClickListener {
                toggleEpisodeList(position, podcast.data.remotePodcastFeedLocation)
            }
            // long press: show all Episodes - if expanded
            podcastViewHolder.olderEpisodesButtonView.setOnLongClickListener {
                showAllEpisodes(podcastViewHolder, podcast.data)
                return@setOnLongClickListener true
            }
        }
        // decide whether to show and populate the older episodes list or to hide it
        if (podcast.episodes.size > 1 && expandedPodcastFeedLocation == podcast.data.remotePodcastFeedLocation) {
            podcastViewHolder.olderEpisodesList.isVisible = true
            // store position
            expandedPodcastPosition = position
            // set up episode list
            val episodeListAdapter = EpisodeListAdapter(podcast)
            podcastViewHolder.olderEpisodesList.adapter = episodeListAdapter
            podcastViewHolder.olderEpisodesList.layoutManager = LinearLayoutManager(context)
            podcastViewHolder.olderEpisodesList.itemAnimator = DefaultItemAnimator()
            podcastViewHolder.olderEpisodesButtonView.text = context.getString(R.string.podcast_list_button_toggle_hide_older_episodes)
        } else {
            podcastViewHolder.olderEpisodesList.isGone = true
            podcastViewHolder.olderEpisodesButtonView.text = context.getString(R.string.podcast_list_button_toggle_show_older_episodes)
        }
    }


    /* Shows / hides the episode list */
    private fun toggleEpisodeList(position: Int, remotePodcastFeedLocation: String) {
        when (expandedPodcastFeedLocation) {
            // CASE: this podcast is already expanded
            remotePodcastFeedLocation -> {
                // reset currently expanded info
                savePodcastListExpandedState()
                // update podcast card
                notifyItemChanged(position)
            }
            // CASE: this podcast is not yet expanded
            else -> {
                // remember previously expanded position
                val previousExpandedPodcastPosition: Int = expandedPodcastPosition
                // if podcast was expanded - collapse it
                if (previousExpandedPodcastPosition > -1 && previousExpandedPodcastPosition < collection.size) notifyItemChanged(previousExpandedPodcastPosition)
                // store current podcast as the expanded one
                savePodcastListExpandedState(position, remotePodcastFeedLocation)
                // update podcast card
                notifyItemChanged(expandedPodcastPosition)
            }
        }
    }


    /* Displays a dialog containing ALL episodes of a podcast */
    private fun showAllEpisodes(podcastViewHolder: PodcastViewHolder, podcast: Podcast) {
        if (podcastViewHolder.olderEpisodesList.isVisible) {
            Toast.makeText(context, R.string.toast_message_entering_streaming_view, Toast.LENGTH_LONG).show()
            CoroutineScope(IO).launch {
                // get list of all episodes
                val episodes: List<Episode> = collectionDatabase.episodeDao().findByEpisodeRemotePodcastFeedLocation(podcast.remotePodcastFeedLocation)
                if (episodes.isNotEmpty()) {
                    withContext(Main) {
                        // listener that lets player fragment start streaming playback
                        val podcastAllEpisodesAdapterListener: PodcastAllEpisodesAdapter.PodcastAllEpisodesAdapterListener = object: PodcastAllEpisodesAdapter.PodcastAllEpisodesAdapterListener {
                            override fun onPlayButtonTapped(selectedEpisode: Episode, streaming: Boolean) {
                                collectionAdapterListener.onPlayButtonTapped(selectedEpisode, streaming)
                            }
                        }
                        // display Show All Episodes dialog
                        ShowAllEpisodesDialog().show(context, podcast, episodes, podcastAllEpisodesAdapterListener) }
                }
            }
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(50)
            // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
        }
    }



    /* Overrides onBindViewHolder */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {

        if (payloads.isEmpty()) {
            // call regular onBindViewHolder method
            onBindViewHolder(holder, position)

        } else if (holder is PodcastViewHolder) {
            // get podcast from position
            val podcast = collection[holder.getAdapterPosition()]

            // get reference to StationViewHolder
            val stationViewHolder = holder

            for (data in payloads) {
                when (data as Int) {
                    Keys.HOLDER_UPDATE_COVER -> {
                        // todo implement
                    }
                    Keys.HOLDER_UPDATE_NAME -> {
                        // todo implement
                    }
                    Keys.HOLDER_UPDATE_PLAYBACK_STATE -> {
                        // todo implement
                    }
                    Keys.HOLDER_UPDATE_PLAYBACK_PROGRESS -> {
                        // todo implement
                    }
                    Keys.HOLDER_UPDATE_DOWNLOAD_STATE -> {
                        // todo implement
                    }
                }
            }
        }
    }


    /* Overrides getItemViewType */
    override fun getItemViewType(position: Int): Int {
        when (isPositionFooter(position)) {
            true -> return Keys.VIEW_TYPE_ADD_NEW
            false -> return Keys.VIEW_TYPE_PODCAST
        }
    }


    /* Overrides getItemCount */
    override fun getItemCount(): Int {
        // +1 ==> the add podcast card
        return collection.size + 1
    }


    /* Removes a podcast from collection */
    fun removePodcast(context: Context, position: Int) {
        // delete folders and assets
        CollectionHelper.deletePodcastFolders(context, collection[position].data)
        // remove podcast from collection
        collectionViewModel.removePodcast(collection[position].data.remotePodcastFeedLocation)
    }


    /* Deletes an episode download from collection */
    fun deleteEpisode(context: Context, mediaId: String) {
        LogHelper.v(TAG, "Deleting episode: $mediaId")
        // remove audio reference in database
        collectionViewModel.deleteEpisodeAudio(mediaId)
    }


    /* Marks an episode as played in collection */
    fun markEpisodePlayed(context: Context, mediaId: String) {
        LogHelper.v(TAG, "Marking as played episode: $mediaId")
        // mark episode als played and update collection
        collectionViewModel.markEpisodePlayed(mediaId)
    }


    /* Get podcast for given position */
    fun getPodcast(position: Int): PodcastWithRecentEpisodesWrapper {
        return collection[position]
    }


    /* Determines if position is last */
    private fun isPositionFooter(position: Int): Boolean {
        return position == collection.size
    }


    /* Updates the podcast list - redraws the views with changed content */
    private fun updateRecyclerView(newCollection: List<PodcastWithRecentEpisodesWrapper>) {
        if (collection.isEmpty() && newCollection.isNotEmpty()) {
            // data set has been initialized - redraw the whole list
            collection = newCollection
            notifyDataSetChanged()
        } else {
            LogHelper.e(TAG, "updateRecyclerView") // todo remove
            // store old collection temporarily
            val oldCollection: List<PodcastWithRecentEpisodesWrapper> = collection.map { it.copy() }
            // set new collection
            collection = newCollection
            // calculate differences between current collection and old collection - and inform this adapter about the changes
            val diffResult = DiffUtil.calculateDiff(CollectionDiffCallback(oldCollection), true)
            diffResult.dispatchUpdatesTo(this@CollectionAdapter)
        }
    }


    /* Updates and saves state of expanded podcast card in list */
    private fun savePodcastListExpandedState(position: Int = -1, remotePodcastFeedLocation: String = String()) {
        expandedPodcastFeedLocation = remotePodcastFeedLocation
        expandedPodcastPosition = position
        PreferencesHelper.savePodcastListExpandedFeedLocation(expandedPodcastFeedLocation)
    }


    /* Observe view model of podcast collection*/
    private fun observeCollectionViewModel(owner: LifecycleOwner) {
        collectionViewModel.collectionLiveData.observe(owner, Observer<List<PodcastWithRecentEpisodesWrapper>> { newCollection ->
            //LogHelper.v(TAG, "Time to load episode list from database: ${System.currentTimeMillis() - collectionViewModel.initializedTimestamp} ms.")
            val sortedCollection: List<PodcastWithRecentEpisodesWrapper> = newCollection.sortedByDescending { podcast -> podcast.data.latestEpisodeDate }
            updateRecyclerView(sortedCollection)
        } )
    }


    /*
     * Inner class: ViewHolder for the Add New podcast action
     */
    private inner class AddNewViewHolder (listItemAddNewLayout: View) : RecyclerView.ViewHolder(listItemAddNewLayout) {
        val addNewPodcastButtonView: MaterialButton = listItemAddNewLayout.findViewById(R.id.card_add_new_station)
        val settingsButtonView: MaterialButton = listItemAddNewLayout.findViewById(R.id.card_settings)
    }
    /*
     * End of inner class
     */


    /*
     * Inner class: ViewHolder for a podcast
     */
    private inner class PodcastViewHolder (podcastCardLayout: View): RecyclerView.ViewHolder(podcastCardLayout) {
        val podcastImageView: ImageView = podcastCardLayout.findViewById(R.id.podcast_cover)
        val podcastNameView: TextView = podcastCardLayout.findViewById(R.id.podcast_name)
        val currentEpisodeViews: EpisodeViewHolder = EpisodeViewHolder(podcastCardLayout)
        val olderEpisodesButtonView: MaterialButton = podcastCardLayout.findViewById(R.id.older_episodes_toggle)
        val olderEpisodesList: RecyclerView = podcastCardLayout.findViewById(R.id.older_episode_list)
    }
    /*
     * End of inner class
     */


    /*
     * Inner class: ViewHolder for an episode
     */
    private inner class EpisodeViewHolder (podcastCardLayout: View): RecyclerView.ViewHolder(podcastCardLayout) {
        val episodeDateView: TextView = podcastCardLayout.findViewById(R.id.episode_date)
        val episodeTitleView: TextView = podcastCardLayout.findViewById(R.id.episode_title)
        val episodeDownloadButtonView: ImageView = podcastCardLayout.findViewById(R.id.episode_download_button)
        val episodeDeleteButtonView: ImageView = podcastCardLayout.findViewById(R.id.episode_delete_button)
        val episodePlayButtonView: ImageView = podcastCardLayout.findViewById(R.id.episode_play_button)
        val episodePlaybackProgressView: ProgressBar = podcastCardLayout.findViewById(R.id.episode_playback_progress)
        val episodePlaybackViews: Group = podcastCardLayout.findViewById(R.id.episode_playback_views)
    }
    /*
     * End of inner class
     */

    /*
     * Inner class: DiffUtil.Callback that determines changes in data - improves list performance
     */
    private inner class CollectionDiffCallback(val oldCollection: List<PodcastWithRecentEpisodesWrapper>): DiffUtil.Callback() {

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldPodcast: PodcastWithRecentEpisodesWrapper = oldCollection[oldItemPosition]
            val newPodcast: PodcastWithRecentEpisodesWrapper = collection[newItemPosition]
            return oldPodcast.data.remotePodcastFeedLocation == newPodcast.data.remotePodcastFeedLocation
        }

        override fun getOldListSize(): Int {
            return oldCollection.size
        }

        override fun getNewListSize(): Int {
            return collection.size
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldPodcast: PodcastWithRecentEpisodesWrapper = oldCollection[oldItemPosition]
            val newPodcast: PodcastWithRecentEpisodesWrapper = collection[newItemPosition]

            // compare relevant contents of podcast
            if (oldPodcast.episodes.size != newPodcast.episodes.size) return false
            if (oldPodcast.data.name != newPodcast.data.name) return false
            if (oldPodcast.data.website != newPodcast.data.website) return false
            if (oldPodcast.data.remoteImageFileLocation != newPodcast.data.remoteImageFileLocation) return false
            if (oldPodcast.data.remotePodcastFeedLocation == expandedPodcastFeedLocation) return false // todo test and remove again if necessary
            if (FileHelper.getFileSize(oldPodcast.data.cover.toUri()) != FileHelper.getFileSize(newPodcast.data.cover.toUri())) return false
            if (FileHelper.getFileSize(oldPodcast.data.smallCover.toUri()) != FileHelper.getFileSize(newPodcast.data.smallCover.toUri())) return false

            // compare relevant contents of episodes within podcast
            oldPodcast.episodes.forEachIndexed { index, oldEpisode ->
                val newEpisode: EpisodeMostRecentView = newPodcast.episodes[index]
                // check most likely changes fist
                if (oldEpisode.data.isPlaying != newEpisode.data.isPlaying) return false
                if (oldEpisode.data.playbackPosition != newEpisode.data.playbackPosition) return false
                if (oldEpisode.data.manuallyDownloaded != newEpisode.data.manuallyDownloaded) return false
                if (oldEpisode.data.audio != newEpisode.data.audio) return false
                // check the rest afterwards
                if (oldEpisode.data.guid != newEpisode.data.guid) return false
                if (oldEpisode.data.title != newEpisode.data.title) return false
                //if (oldEpisode.chapters != newEpisode.chapters) return false
                if (oldEpisode.data.publicationDate != newEpisode.data.publicationDate) return false
                if (oldEpisode.data.duration != newEpisode.data.duration) return false
                if (oldEpisode.data.remoteCoverFileLocation != newEpisode.data.remoteCoverFileLocation) return false
                if (oldEpisode.data.remoteAudioFileLocation != newEpisode.data.remoteAudioFileLocation) return false
                if (FileHelper.getFileSize(oldEpisode.data.cover.toUri()) != FileHelper.getFileSize(newEpisode.data.cover.toUri())) return false
                if (FileHelper.getFileSize(oldEpisode.data.smallCover.toUri()) != FileHelper.getFileSize(newEpisode.data.smallCover.toUri())) return false
            }
            // none of the above -> contents are the same
            return true
        }
    }
    /*
     * End of inner class
     */


    /*
     * Inner class: Adapter for an episode list
     */
    private inner class EpisodeListAdapter(val podcastWrapper: PodcastWithRecentEpisodesWrapper) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.element_episode, parent, false)
            return EpisodeViewHolder(v)
        }

        override fun getItemCount(): Int {
            val numberOfOlderEpisodes = Keys.DEFAULT_NUMBER_OF_EPISODES_TO_KEEP - 1 // minus the current one
            if (podcastWrapper.episodes.size > numberOfOlderEpisodes) {
                return numberOfOlderEpisodes
            } else {
                return podcastWrapper.episodes.size -1
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val episodeNumber = position + 1 // only older episodes - leave out the current one
            if (podcastWrapper.episodes.size > episodeNumber) {
                val episode = podcastWrapper.episodes[episodeNumber]
                val episodeViewHolder: EpisodeViewHolder = holder as EpisodeViewHolder
                // set up episode
                setEpisodeTitle(episodeViewHolder, podcastWrapper.data, episode.data)
                setEpisodeButtons(episodeViewHolder, episode.data)
                setEpisodePlaybackProgress(episodeViewHolder, episode.data)
            }
        }
    }
    /*
     * End of inner class
     */

}