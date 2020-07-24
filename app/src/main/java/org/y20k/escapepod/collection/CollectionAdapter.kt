/*
 * CollectionAdapter.kt
 * Implements the CollectionAdapter class
 * A CollectionAdapter is a custom adapter providing podcast card views for a RecyclerView
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.collection

import android.content.Context
import android.net.Uri
import android.os.Vibrator
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.Group
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import org.y20k.escapepod.core.Collection
import org.y20k.escapepod.core.Episode
import org.y20k.escapepod.core.Podcast
import org.y20k.escapepod.dialogs.ShowNotesDialog
import org.y20k.escapepod.helpers.*


/*
 * CollectionAdapter class
 */
class CollectionAdapter(private val context: Context, private val collectionAdapterListener: CollectionAdapterListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(CollectionAdapter::class.java)


    /* Main class variables */
    private lateinit var collectionViewModel: CollectionViewModel
    // private lateinit var collectionAdapterListener: CollectionAdapterListener
    private var collection: Collection = Collection()


    /* Listener Interface */
    interface CollectionAdapterListener {
        fun onPlayButtonTapped(mediaId: String, playbackState: Int)
        fun onMarkListenedButtonTapped(mediaId: String)
        fun onDownloadButtonTapped(episode: Episode)
        fun onDeleteButtonTapped(episode: Episode)
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
                addNewViewHolder.addNewPodcastView.setOnClickListener {
                    // show the add podcast dialog
                    collectionAdapterListener.onAddNewButtonTapped()
                }
                addNewViewHolder.settingsButtonView.setOnClickListener {
                    it.findNavController().navigate(R.id.settings_destination)
                }
            }

            // CASE PODCAST CARD
            is PodcastViewHolder -> {
                // get station from position
                val podcast: Podcast = collection.podcasts[position]

                // get reference to StationViewHolder
                val podcastViewHolder: PodcastViewHolder = holder

                // set up podcast
                setPodcastName(podcastViewHolder, podcast)
                setPodcastImage(podcastViewHolder, podcast)

                // set up current episode
                setEpisodeTitle(podcastViewHolder.currentEpisodeViews, podcast.episodes[0])
                setEpisodeButtons(podcastViewHolder.currentEpisodeViews, podcast.episodes[0])
                setEpisodePlaybackProgress(podcastViewHolder.currentEpisodeViews, podcast.episodes[0])

                // set up older episodes list
                setOlderEpisodesList(podcastViewHolder, podcast)
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
        podcastViewHolder.podcastImageView.setOnLongClickListener {
            DownloadHelper.refreshCover(context, podcast)
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(50)
            // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
            return@setOnLongClickListener true
        }
    }


    /* Sets up an episode's title views */
    private fun setEpisodeTitle(episodeViewHolder: EpisodeViewHolder, episode: Episode) {
        episodeViewHolder.episodeDateView.text = episode.getDateString()
        episodeViewHolder.episodeDateView.setOnLongClickListener {
            ShowNotesDialog().show(context, episode)
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(50)
            // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
            return@setOnLongClickListener true
        }
        episodeViewHolder.episodeTitleView.text = episode.title
        episodeViewHolder.episodeTitleView.setOnLongClickListener {
            ShowNotesDialog().show(context, episode)
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
        val playbackState: Int = episode.playbackState
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> episodeViewHolder.episodePlayButtonView.setImageResource(R.drawable.ic_pause_symbol_24dp)
            else -> episodeViewHolder.episodePlayButtonView.setImageResource(R.drawable.ic_play_symbol_24dp)
        }
        episodeViewHolder.episodeDownloadButtonView.setOnClickListener {
            collectionAdapterListener.onDownloadButtonTapped(episode)
        }
        episodeViewHolder.episodePlayButtonView.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(episode.getMediaId(), playbackState)
        }
        episodeViewHolder.episodePlayButtonView.setOnLongClickListener {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(50)
            // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
            collectionAdapterListener.onMarkListenedButtonTapped(episode.getMediaId())
            return@setOnLongClickListener true
        }
        episodeViewHolder.episodeDeleteButtonView.setOnClickListener {
            collectionAdapterListener.onDeleteButtonTapped(episode)
        }
        if (episode.audio.isNotEmpty()) {
            episodeViewHolder.episodeDownloadButtonView.visibility = View.GONE
            episodeViewHolder.episodePlaybackViews.visibility = View.VISIBLE
        } else {
            episodeViewHolder.episodeDownloadButtonView.visibility = View.VISIBLE
            episodeViewHolder.episodePlaybackViews.visibility = View.GONE
        }
    }


    /* Fills the list of older episodes */
    private fun setOlderEpisodesList(podcastViewHolder: PodcastViewHolder, podcast: Podcast) {
        if (podcast.episodes.size > 1) {
            // set up episode list
            val episodeListAdapter = EpisodeListAdapter(podcast)
            podcastViewHolder.olderEpisodesList.adapter = episodeListAdapter
            podcastViewHolder.olderEpisodesList.layoutManager = LinearLayoutManager(context)
            podcastViewHolder.olderEpisodesList.itemAnimator = DefaultItemAnimator()
            // set up Older Episodes button
            setOlderEpisodesButton(podcastViewHolder)
            podcastViewHolder.olderEpisodesButtonView.setOnClickListener {
                toggleEpisodeList(podcastViewHolder)
            }
        }
    }


    /* Shows / hides the episode list */
    private fun toggleEpisodeList(podcastViewHolder: PodcastViewHolder) {
        when (podcastViewHolder.olderEpisodesList.visibility) {
            // CASE: older episode list is hidden -> show episode list
            View.GONE -> {
                podcastViewHolder.olderEpisodesList.visibility = View.VISIBLE
            }
            // CASE: older episode list is visible -> hide episode list
            View.VISIBLE -> {
                podcastViewHolder.olderEpisodesList.visibility = View.GONE
            }
        }
        // update older episodes button text
        setOlderEpisodesButton(podcastViewHolder)
    }


    /* Sets / updates the older episodes button text */
    private fun setOlderEpisodesButton(podcastViewHolder: PodcastViewHolder) {
        when (podcastViewHolder.olderEpisodesList.visibility) {
            // CASE: older episode list is hidden -> set button to "show"
            View.GONE -> {
                podcastViewHolder.olderEpisodesButtonView.text = context.getString(R.string.podcast_list_button_toggle_show_older_episodes)
            }
            // CASE: older episode list is visible -> set button to "hide"
            View.VISIBLE -> {
                podcastViewHolder.olderEpisodesButtonView.text = context.getString(R.string.podcast_list_button_toggle_hide_older_episodes)
            }
        }
    }


    /* Overrides onBindViewHolder */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {

        if (payloads.isEmpty()) {
            // call regular onBindViewHolder method
            onBindViewHolder(holder, position)

        } else if (holder is PodcastViewHolder) {
            // get podcast from position
            val podcast = collection.podcasts[holder.getAdapterPosition()]

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
        return collection.podcasts.size + 1
    }


    /* Removes a podcast from collection */
    fun removePodcast(context: Context, position: Int) {
        val newCollection = collection.deepCopy()
        // delete folders and assets
        CollectionHelper.deletePodcastFolders(context, newCollection.podcasts[position])
        // remove podcast from collection
        newCollection.podcasts.removeAt(position)
        collection = newCollection
        // update list
        notifyItemRemoved(position)
        // export collection as OPML
        CollectionHelper.exportCollection(context, newCollection)
        // save collection and broadcast changes
        CollectionHelper.saveCollection(context, newCollection)
    }


    /* Deletes an episode download from collection */
    fun deleteEpisode(context: Context, mediaID: String) {
        LogHelper.v(TAG, "Deleting episode: $mediaID")
        val newCollection = collection.deepCopy()
        // delete episode and update collection
        CollectionHelper.deleteEpisodeAudioFile(context, newCollection, mediaID, manuallyDeleted = true)
        // update player state if necessary
        PreferencesHelper.updatePlayerState(context, newCollection)
        // save collection and broadcast changes
        CollectionHelper.saveCollection(context, newCollection)
    }


    /* Marks an episode as playedin collection */
    fun markEpisodePlayed(context: Context, mediaID: String) {
        LogHelper.v(TAG, "Marking as played episode: $mediaID")
        val newCollection = collection.deepCopy()
        // mark episode als played and update collection
        CollectionHelper.markEpisodePlayed(newCollection, mediaID)
        // update player state if necessary
        PreferencesHelper.updatePlayerState(context, newCollection)
        // save collection and broadcast changes
        CollectionHelper.saveCollection(context, newCollection)
    }



    /* Determines if position is last */
    private fun isPositionFooter(position: Int): Boolean {
        return position == collection.podcasts.size
    }


    /* Updates the podcast list - redraws the views with changed content */
    private fun updateRecyclerView(oldCollection: Collection, newCollection: Collection) {
        collection = newCollection
        if (oldCollection.podcasts.size == 0 && newCollection.podcasts.size > 0) {
            // data set has been initialized - redraw the whole list
            notifyDataSetChanged()
        } else {
            // calculate differences between current collection and new collection - and inform this adapter about the changes
            val diffResult = DiffUtil.calculateDiff(CollectionDiffCallback(oldCollection, newCollection), true)
            diffResult.dispatchUpdatesTo(this@CollectionAdapter)
        }
    }


    /* Observe view model of podcast collection*/
    private fun observeCollectionViewModel(owner: LifecycleOwner) {
        collectionViewModel.collectionLiveData.observe(owner, Observer<Collection> { newCollection ->
            updateRecyclerView(collection, newCollection)
        })
    }


    /*
     * Inner class: ViewHolder for the Add New Podcast action
     */
    private inner class AddNewViewHolder (listItemAddNewLayout: View) : RecyclerView.ViewHolder(listItemAddNewLayout) {
        val addNewPodcastView: CardView = listItemAddNewLayout.findViewById(R.id.card_add_new_podcast)
        val settingsButtonView: ImageButton = listItemAddNewLayout.findViewById(R.id.settings_button)
    }
    /*
     * End of inner class
     */


    /*
     * Inner class: ViewHolder for a podcast
     */
    private inner class PodcastViewHolder (podcastCardLayout: View): RecyclerView.ViewHolder(podcastCardLayout) {
        val podcastImageView: ImageView = podcastCardLayout.findViewById(R.id.player_podcast_cover)
        val podcastNameView: TextView = podcastCardLayout.findViewById(R.id.podcast_name)
        val currentEpisodeViews: EpisodeViewHolder = EpisodeViewHolder(podcastCardLayout)
        val olderEpisodesButtonView: TextView = podcastCardLayout.findViewById(R.id.older_episodes_toggle)
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
    private inner class CollectionDiffCallback(val oldCollection: Collection, val newCollection: Collection): DiffUtil.Callback() {

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldPodcast: Podcast = oldCollection.podcasts[oldItemPosition]
            val newPodcast: Podcast = newCollection.podcasts[newItemPosition]
            return oldPodcast.getPodcastId() == newPodcast.getPodcastId()
        }

        override fun getOldListSize(): Int {
            return oldCollection.podcasts.size
        }

        override fun getNewListSize(): Int {
            return newCollection.podcasts.size
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldPodcast: Podcast = oldCollection.podcasts[oldItemPosition]
            val newPodcast: Podcast = newCollection.podcasts[newItemPosition]

            // compare relevant contents of podcast
            if (oldPodcast.episodes.size != newPodcast.episodes.size) return false
            if (oldPodcast.name != newPodcast.name) return false
            if (oldPodcast.description != newPodcast.description) return false
            if (oldPodcast.website != newPodcast.website) return false
            if (oldPodcast.remoteImageFileLocation != newPodcast.remoteImageFileLocation) return false
            if (oldPodcast.remotePodcastFeedLocation != newPodcast.remotePodcastFeedLocation) return false
            if (FileHelper.getFileSize(context, Uri.parse(oldPodcast.cover)) != FileHelper.getFileSize(context, Uri.parse(newPodcast.cover))) return false
            if (FileHelper.getFileSize(context, Uri.parse(oldPodcast.smallCover)) != FileHelper.getFileSize(context, Uri.parse(newPodcast.smallCover))) return false

            // compare relevant contents of episodes within podcast
            oldPodcast.episodes.forEachIndexed { index, oldEpisode ->
                val newEpisode: Episode = newPodcast.episodes[index]
                // check most likely changes fist
                if (oldEpisode.playbackState != newEpisode.playbackState) return false
                if (oldEpisode.playbackPosition != newEpisode.playbackPosition) return false
                if (oldEpisode.manuallyDownloaded != newEpisode.manuallyDownloaded) return false
                if (oldEpisode.audio != newEpisode.audio) return false
                // check the rest afterwards
                if (oldEpisode.guid != newEpisode.guid) return false
                if (oldEpisode.title != newEpisode.title) return false
                if (oldEpisode.description != newEpisode.description) return false
                if (oldEpisode.chapters != newEpisode.chapters) return false
                if (oldEpisode.publicationDate != newEpisode.publicationDate) return false
                if (oldEpisode.duration != newEpisode.duration) return false
                if (oldEpisode.remoteCoverFileLocation != newEpisode.remoteCoverFileLocation) return false
                if (oldEpisode.remoteAudioFileLocation != newEpisode.remoteAudioFileLocation) return false
                if (FileHelper.getFileSize(context, Uri.parse(oldEpisode.cover)) != FileHelper.getFileSize(context, Uri.parse(newEpisode.cover))) return false
                if (FileHelper.getFileSize(context, Uri.parse(oldEpisode.smallCover)) != FileHelper.getFileSize(context, Uri.parse(newEpisode.smallCover))) return false
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
    private inner class EpisodeListAdapter(val podcast: Podcast) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.element_episode, parent, false)
            return EpisodeViewHolder(v)
        }

        override fun getItemCount(): Int {
            val numberOfOlderEpisodes = Keys.DEFAULT_NUMBER_OF_EPISODES_TO_KEEP - 1 // minus the current one
            if (podcast.episodes.size > numberOfOlderEpisodes) {
                return numberOfOlderEpisodes
            } else {
                return podcast.episodes.size -1
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val episodeNumber = position + 1 // only older episodes - leave out the current one
            if (podcast.episodes.size > episodeNumber) {
                val episode = podcast.episodes[episodeNumber]
                val episodeViewHolder: EpisodeViewHolder = holder as EpisodeViewHolder
                // set up episode
                setEpisodeTitle(episodeViewHolder, episode)
                setEpisodeButtons(episodeViewHolder, episode)
                setEpisodePlaybackProgress(episodeViewHolder, episode)
            }
        }
    }
    /*
     * End of inner class
     */



}