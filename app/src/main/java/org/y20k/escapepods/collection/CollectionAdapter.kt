/*
 * CollectionAdapter.kt
 * Implements the CollectionAdapter class
 * A CollectionAdapter is a custom adapter for a RecyclerView
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.collection

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Vibrator
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.y20k.escapepods.Keys
import org.y20k.escapepods.R
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.core.Episode
import org.y20k.escapepods.core.Podcast
import org.y20k.escapepods.dialogs.AddPodcastDialog
import org.y20k.escapepods.helpers.CollectionHelper
import org.y20k.escapepods.helpers.DownloadHelper
import org.y20k.escapepods.helpers.LogHelper
import org.y20k.escapepods.helpers.PreferencesHelper
import java.text.DateFormat


/*
 * CollectionAdapter class
 */
class CollectionAdapter(val activity: Activity) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(CollectionAdapter::class.java)


    /* Main class variables */
    private lateinit var collectionViewModel: CollectionViewModel
    private lateinit var collectionAdapterListener: CollectionAdapterListener
    private var collection: Collection = Collection()


    /* Listener Interface */
    interface CollectionAdapterListener {
//        fun onItemSelected (mediaId: String, isLongPress: Boolean)
        fun onPlayButtonTapped(mediaId: String, playbackState: Int)
        fun onDownloadButtonTapped(episode: Episode)
        fun onDeleteButtonTapped(episode: Episode)
//        fun onJumpToPosition (position: Int)
    }


    /* Overrides onAttachedToRecyclerView */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        // get reference to listener
        collectionAdapterListener = activity as CollectionAdapterListener

        // create view model and observe changes in collection view model
        collectionViewModel = ViewModelProviders.of(activity as AppCompatActivity).get(CollectionViewModel::class.java)
        observeCollectionViewModel(activity as LifecycleOwner)

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
                val addNewViewHolder: AddNewViewHolder = holder as AddNewViewHolder
                addNewViewHolder.addNewPodcastView.setOnClickListener {
                    // show the add podcast dialog
                    AddPodcastDialog(activity as AddPodcastDialog.AddPodcastDialogListener).show(activity)
                }
            }

            // CASE PODCAST CARD
            is PodcastViewHolder -> {
                // get station from position
                val podcast: Podcast = collection.podcasts[position]

                // get reference to StationViewHolder
                val podcastViewHolder: PodcastViewHolder = holder as PodcastViewHolder

                // set up views
                setPodcastName(podcastViewHolder, podcast)
                setPodcastImage(podcastViewHolder, podcast)
                setEpisodeTitles(podcastViewHolder, podcast)
                setEpisodeButtons(podcastViewHolder, podcast)

                // set up episode list
                val episodeListAdapter = EpisodeListAdapter(activity, podcast)
                podcastViewHolder.olderEpisodesList.adapter = episodeListAdapter
                podcastViewHolder.olderEpisodesList.setLayoutManager(LinearLayoutManager(activity))
                podcastViewHolder.olderEpisodesList.setItemAnimator(DefaultItemAnimator())
            }
        }
    }


    /* Sets the podcast name view */
    private fun setPodcastName(podcastViewHolder: PodcastViewHolder, podcast: Podcast) {
        podcastViewHolder.pocastNameView.setText(podcast.name)
    }


    /* Sets the episode title views */
    private fun setEpisodeTitles(podcastViewHolder: PodcastViewHolder, podcast: Podcast) {
        val episodeListSize = podcast.episodes.size

        // episode 0
        podcastViewHolder.currentEpisodeDateView.setText(podcast.episodes[0].getDateString(DateFormat.MEDIUM))
        podcastViewHolder.currentEpisodeNameView.setText(podcast.episodes[0].title)

        // episode 1
        if (episodeListSize > 1) {
//            podcastViewHolder.episode1DateView.setText(podcast.episodes[1].getDateString(DateFormat.MEDIUM))
//            podcastViewHolder.episode1NameView.setText(podcast.episodes[1].title)
        } else {
            // todo hide episode 1 views
        }
    }


    /* Sets the podcast image view */
    private fun setPodcastImage(podcastViewHolder: PodcastViewHolder, podcast: Podcast) {
        podcastViewHolder.podcastImageView.setImageURI(Uri.parse(podcast.cover))
        podcastViewHolder.podcastImageView.setOnLongClickListener {
            DownloadHelper.refreshCover(activity, podcast)
            val v = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(50)
            // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
            true
        }
    }


    /* Sets the episode play, download and delete button views */
    private fun setEpisodeButtons(podcastViewHolder: PodcastViewHolder, podcast: Podcast) {
        val episodeListSize = podcast.episodes.size

        // episode 0
        val playbackState: Int = podcast.episodes[0].playbackState
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> podcastViewHolder.currentEpisodePlayButtonView.setImageResource(R.drawable.ic_pause_symbol_24dp)
            else -> podcastViewHolder.currentEpisodePlayButtonView.setImageResource(R.drawable.ic_play_symbol_24dp)
        }
        podcastViewHolder.currentEpisodeDownloadButtonView.setOnClickListener {
            collectionAdapterListener.onDownloadButtonTapped(podcast.episodes[0])
        }
        podcastViewHolder.currentEpisodePlayButtonView.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(podcast.episodes[0].getMediaId(), playbackState)
        }
        podcastViewHolder.currentEpisodeDeleteButtonView.setOnClickListener {
            collectionAdapterListener.onDeleteButtonTapped(podcast.episodes[0])
        }
        if (podcast.episodes[0].audio.isNotEmpty()) {
            podcastViewHolder.currentEpisodeDownloadButtonView.visibility = View.GONE
            podcastViewHolder.currentEpisodePlaybackViews.visibility = View.VISIBLE
        } else {
            podcastViewHolder.currentEpisodeDownloadButtonView.visibility = View.VISIBLE
            podcastViewHolder.currentEpisodePlaybackViews.visibility = View.GONE
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
            val stationViewHolder = holder as PodcastViewHolder

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
        // delete folders and assets
        CollectionHelper.deletePodcastFolders(context, collection.podcasts[position])
        // remove podcast from collection
        collection.podcasts.removeAt(position)
        // save last update
        PreferencesHelper.saveLastUpdateCollection(context)
        // export collection as OPML
        CollectionHelper.exportCollection(context, collection)
        // save collection and broadcast changes
        CollectionHelper.saveCollection(context, collection)
    }


    /* Deletes an episode download from collection */
    fun deleteEpisode(context: Context, mediaID: String) {
        LogHelper.v(TAG, "Deleting episode: $mediaID")
        // todo implement
        // 1. delete reference, 2. delete file, 3. reset manually downloaded state
    }


    /* Determines if position is last */
    private fun isPositionFooter(position: Int): Boolean {
        return position == collection.podcasts.size
    }


    /* Manipulates state of playback button */
    private fun togglePlaybackButtonState(holder: PodcastViewHolder, podcast: Podcast) {
        // todo implement
    }


    /* Observe view model of podcast collection*/
    private fun observeCollectionViewModel(owner: LifecycleOwner) {
        collectionViewModel.collectionLiveData.observe(owner, Observer<Collection> { it ->
            // update collection
            collection = it

//            // calculate differences between new station list and current station list
//            val diffResult = DiffUtil.calculateDiff(CollectionAdapterDiffUtilCallback(mStationList, newStationList), true)
//
//            // inform this adapter about the changes
//            diffResult.dispatchUpdatesTo(this@CollectionAdapter)

            this@CollectionAdapter.notifyDataSetChanged() // todo remove

        })
    }


    /*
     * Inner class: ViewHolder for the Add New Podcast action
     */
    private inner class AddNewViewHolder (val listItemAddNewLayout: View) : RecyclerView.ViewHolder(listItemAddNewLayout) {
        val addNewPodcastView: ConstraintLayout = listItemAddNewLayout.findViewById(R.id.card_add_new_podcast)
    }
    /*
     * End of inner class
     */


    /*
     * Inner class: ViewHolder for a podcast
     */
    private inner class PodcastViewHolder (val podcastCardLayout: View) : RecyclerView.ViewHolder(podcastCardLayout) {
        val podcastImageView: ImageView = podcastCardLayout.findViewById(R.id.player_podcast_cover)
        val pocastNameView: TextView = podcastCardLayout.findViewById(R.id.podcast_name)
        val currentEpisodeDateView: TextView = podcastCardLayout.findViewById(R.id.episode_date)
        val currentEpisodeNameView: TextView = podcastCardLayout.findViewById(R.id.episode_name)
        val currentEpisodeDownloadButtonView: ImageView = podcastCardLayout.findViewById(R.id.episode_download_button)
        val currentEpisodeDeleteButtonView: ImageView = podcastCardLayout.findViewById(R.id.episode_delete_button)
        val currentEpisodePlayButtonView: ImageView = podcastCardLayout.findViewById(R.id.episode_play_button)
        val currentEpisodePlaybackProgressView: ProgressBar = podcastCardLayout.findViewById(R.id.episode_playback_progress)
        val currentEpisodePlaybackViews: Group = podcastCardLayout.findViewById(R.id.episode_playback_views)
        val olderEpisodesList: RecyclerView = podcastCardLayout.findViewById(R.id.episode_list)
    }
    /*
     * End of inner class
     */


    /*
     * Inner class: ViewHolder for an episode
     */
    private inner class EpisodeViewHolder (val podcastCardLayout: View) : RecyclerView.ViewHolder(podcastCardLayout) {
        val episodeDateView: TextView = podcastCardLayout.findViewById(R.id.episode_date)
        val episodeNameView: TextView = podcastCardLayout.findViewById(R.id.episode_name)
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
     * Inner class: ViewHolder for an episode
     */
    private inner class EpisodeListAdapter(val activity: Activity, val podcast: Podcast) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.element_episode, parent, false)
            return EpisodeViewHolder(v)
        }

        override fun getItemCount(): Int {
            val numberOfOlderEpisodes = Keys.DEFAULT_NUMBER_OF_EPISODES_TO_KEEP - 1 // minus the current one
            if (podcast.episodes.size > numberOfOlderEpisodes) {
                return numberOfOlderEpisodes
            } else {
                return podcast.episodes.size
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val episode = podcast.episodes[position + 1] // only older episodes - leave out the current one
            val episodeViewHolder: EpisodeViewHolder = holder as EpisodeViewHolder
            // todo implememt

            // todo remove - just a test
            episodeViewHolder.episodeDateView.text = episode.getDateString(DateFormat.MEDIUM)
            episodeViewHolder.episodeNameView.text = episode.title

        }
    }
    /*
     * End of inner class
     */



}