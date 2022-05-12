/*
 * PodcastAllEpisodesAdapter.kt
 * Implements the PodcastAllEpisodesAdapter class
 * A PodcastAllEpisodesAdapter is a custom adapter providing card views for ALL episodes of a podcast for a RecyclerView
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.y20k.escapepod.R
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.dialogs.ShowAllEpisodesDialog
import org.y20k.escapepod.helpers.DateTimeHelper
import org.y20k.escapepod.helpers.LogHelper


/*
 * PodcastAllEpisodesAdapter class
 */
class PodcastAllEpisodesAdapter (private val context: Context, private  val episodes: List<Episode>, private val showAllEpisodesDialogListener: ShowAllEpisodesDialog.ShowAllEpisodesDialogListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(PodcastAllEpisodesAdapter::class.java)


    /* Main class variables */



    /* Listener Interface */
    interface PodcastAllEpisodesAdapterListener {
        fun onPlayButtonTapped(selectedEpisode: Episode, streaming: Boolean = true)
    }


    /* Overrides onCreateViewHolder */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.element_streaming_episode, parent, false)
        return EpisodeViewHolder(v)
    }


    /* Overrides onBindViewHolder */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val episode = episodes[position]
        val episodeViewHolder: EpisodeViewHolder = holder as EpisodeViewHolder
        // set up episode
        setEpisodeTitle(episodeViewHolder, episode)
        setEpisodeButtons(episodeViewHolder, episode)
    }


    /* Overrides getItemCount */
    override fun getItemCount(): Int {
        return episodes.size -1
    }


    /* Sets up an episode's title views */
    private fun setEpisodeTitle (episodeViewHolder: EpisodeViewHolder, episode: Episode) {
        episodeViewHolder.episodeDateView.text = DateTimeHelper.getDateString(episode.publicationDate)
        episodeViewHolder.episodeTitleView.text = episode.title
    }


    /* Sets up an episode's play button view */
    private fun setEpisodeButtons (episodeViewHolder: EpisodeViewHolder, episode: Episode) {
        val episodeIsPlaying: Boolean = episode.isPlaying()
        when (episodeIsPlaying) {
            true -> episodeViewHolder.episodePlayButtonView.setImageResource(R.drawable.ic_cloud_pause_36dp)
            false -> episodeViewHolder.episodePlayButtonView.setImageResource(R.drawable.ic_cloud_play_36dp)
        }
        episodeViewHolder.episodePlayButtonView.setOnClickListener {
            showAllEpisodesDialogListener.onPlayButtonTapped(episode.mediaId, episodeIsPlaying, true)
        }
    }


    /*
     * Inner class: ViewHolder for an episode
     */
    private inner class EpisodeViewHolder (podcastCardLayout: View): RecyclerView.ViewHolder(podcastCardLayout) {
        val episodeDateView: TextView = podcastCardLayout.findViewById(R.id.streaming_episode_date)
        val episodeTitleView: TextView = podcastCardLayout.findViewById(R.id.streaming_episode_title)
        val episodePlayButtonView: ImageButton = podcastCardLayout.findViewById(R.id.streaming_episode_play_button)
    }
    /*
     * End of inner class
     */

}
