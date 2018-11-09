/*
 * CollectionAdapter.kt
 * Implements the CollectionAdapter object
 * A CollectionAdapter is a custom adapter for a RecyclerView
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.adapter

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.RecyclerView
import org.y20k.escapepods.R
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.core.Podcast
import org.y20k.escapepods.dialogs.AddPodcastDialog
import org.y20k.escapepods.helpers.*
import java.text.DateFormat


/*
 * CollectionAdapter class
 */
class CollectionAdapter(val activity: Activity) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(CollectionAdapter::class.java)


    /* Main class variables */
    private lateinit var collectionViewModel: CollectionViewModel
    private var collection: Collection = Collection()


    /* Listener Interface */
    interface CollectionAdapterListener {
        fun itemSelected (isLongPress: Boolean)
        fun jumpToPosition (position: Int)
    }


    /* Overrides onAttachedToRecyclerView */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

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
                podcastViewHolder.podcastImageView.setImageBitmap(ImageHelper.getPodcastCover(activity, Uri.parse(podcast.cover), Keys.SIZE_COVER_SMALL))
                // podcastViewHolder.podcastImageView.setClipToOutline(true)
                podcastViewHolder.pocastNameView.setText(podcast.name)
                podcastViewHolder.pocastEpisode0DateView.setText(podcast.episodes[0].getDateString(DateFormat.MEDIUM))
                podcastViewHolder.pocastEpisode0NameView.setText(podcast.episodes[0].title)


                podcastViewHolder.podcastImageView.setOnLongClickListener {
                    DownloadHelper().refreshCover(activity, podcast)
                    Toast.makeText(activity as Context, activity.getText(R.string.toast_message_refreshing_cover), Toast.LENGTH_LONG).show()
                    val v = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    v.vibrate(50)
                    // v.vibrate(VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)); // todo check if there is an androidx vibrator
                    true
                }
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
    fun remove(context: Context, position: Int) {
        // delete folders and assets
        CollectionHelper.deletePodcastFolders(context, collection.podcasts[position])
        // remove podcast from collection
        collection.podcasts.removeAt(position)
        // export collection as OPML
        CollectionHelper.exportCollection(context, collection)
        // save collection and broadcast changes
        CollectionHelper.saveCollection(context, collection)
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


    /**
     * Inner class: ViewHolder for a podcast
     */
    private inner class PodcastViewHolder (val podcastCardLayout: View) : RecyclerView.ViewHolder(podcastCardLayout) {
        val podcastImageView: ImageView = podcastCardLayout.findViewById(R.id.player_podcast_cover)
        val pocastNameView: TextView = podcastCardLayout.findViewById(R.id.podcast_name)
        val pocastEpisode0DateView: TextView = podcastCardLayout.findViewById(R.id.episode_0_date)
        val pocastEpisode0NameView: TextView = podcastCardLayout.findViewById(R.id.episode_0_name)
    }
    /**
     * End of inner class
     */

}