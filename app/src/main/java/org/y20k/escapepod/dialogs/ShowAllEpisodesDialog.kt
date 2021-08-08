/*
 * ShowAllEpisodesDialog
 * Implements the ShowAllEpisodesDialog class
 * A ShowAllEpisodesDialog displays all episodes of a given podcast in a dialog
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.dialogs

import android.content.Context
import android.view.LayoutInflater
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.y20k.escapepod.R
import org.y20k.escapepod.collection.PodcastAllEpisodesAdapter
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.helpers.LogHelper


/*
 * ShowAllEpisodes class
 */
class ShowAllEpisodesDialog {

    /* Define log tag */
    private val TAG = LogHelper.makeLogTag(ShowAllEpisodesDialog::class.java.simpleName)


    /* Main class variables */
    private lateinit var episodesAdapter: PodcastAllEpisodesAdapter
    private lateinit var podcastEpisodesList: RecyclerView


    /* Construct and show dialog */
    fun show(context: Context, episodes: List<Episode>, adapterListener: PodcastAllEpisodesAdapter.PodcastAllEpisodesAdapterListener) {
        // prepare dialog builder
        val builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(context)

        // set title
        builder.setTitle(episodes[0].podcastName)

        // get views
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_show_all_epidodes, null)
        podcastEpisodesList = view.findViewById(R.id.podcast_episodes_list)

        // set up list of search results
        episodesAdapter = PodcastAllEpisodesAdapter(context, episodes, adapterListener)
        podcastEpisodesList.adapter = episodesAdapter
        val layoutManager: LinearLayoutManager = object: LinearLayoutManager(context) {
            override fun supportsPredictiveItemAnimations(): Boolean {
                return true
            }
        }
        podcastEpisodesList.layoutManager = layoutManager
        podcastEpisodesList.itemAnimator = DefaultItemAnimator()

        // set dialog view
        builder.setView(view)

        // add cancel button
        builder.setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
            // listen for click on cancel button
            // do nothing
        }

        // display add dialog
        builder.show()
    }

}