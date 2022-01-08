/*
 * ShowAllEpisodesDialog
 * Implements the ShowAllEpisodesDialog class
 * A ShowAllEpisodesDialog displays all episodes of a given podcast in a dialog
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.startActivity
import androidx.core.net.toUri
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import org.y20k.escapepod.R
import org.y20k.escapepod.collection.PodcastAllEpisodesAdapter
import org.y20k.escapepod.database.objects.Episode
import org.y20k.escapepod.database.objects.Podcast
import org.y20k.escapepod.helpers.LogHelper
import org.y20k.escapepod.helpers.NetworkHelper


/*
 * ShowAllEpisodes class
 */
class ShowAllEpisodesDialog {

    /* Define log tag */
    private val TAG = LogHelper.makeLogTag(ShowAllEpisodesDialog::class.java.simpleName)

    /* Listener Interface */
    interface ShowAllEpisodesDialogListener {
        fun onPlayButtonTapped(mediaId: String, playbackState: Int)
    }


    /* Main class variables */
    private lateinit var allEpisodesDialog: AlertDialog


    /* Construct and show dialog */
    fun show(context: Context, podcast: Podcast, episodes: List<Episode>, podcastAllEpisodesAdapterListener: PodcastAllEpisodesAdapter.PodcastAllEpisodesAdapterListener) {
        // prepare dialog builder
        val builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme)

        // set title
        //builder.setTitle()

        // get views
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_show_all_epidodes, null)
        val podcastNameView: MaterialTextView = view.findViewById(R.id.podcast_name)
        val podcastWebsiteView: TextView = view.findViewById(R.id.podcast_website)
        //val podcastEpisodeListDivider: ImageView = view.findViewById(R.id.divider_centered_dot)
        val podcastFeedView: TextView = view.findViewById(R.id.podcast_feed)
        val podcastEpisodesList: RecyclerView = view.findViewById(R.id.podcast_episodes_list)

        // set dialog view
        builder.setView(view)

        // set views
        podcastNameView.text = podcast.name
        podcastWebsiteView.setOnClickListener {
            startActivity(context, Intent(Intent.ACTION_VIEW, podcast.website.toUri()), null)
        }
        podcastFeedView.setOnClickListener {
            val clip: ClipData = ClipData.newPlainText("simple text", podcast.remotePodcastFeedLocation)
            val cm: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(clip)
            Toast.makeText(context, R.string.toast_message_copied_to_clipboard, Toast.LENGTH_LONG).show()
        }

        // set up list of episodes
        val showAllEpisodesDialogListener: ShowAllEpisodesDialogListener = object : ShowAllEpisodesDialogListener {
            override fun onPlayButtonTapped(mediaId: String, playbackState: Int) {
                if (NetworkHelper.isConnectedToNetwork(context)) {
                    allEpisodesDialog.dismiss()
                    podcastAllEpisodesAdapterListener.onPlayButtonTapped(mediaId, playbackState)
                } else {
                    Toast.makeText(context, R.string.toast_message_error_no_internet_connection, Toast.LENGTH_LONG).show()
                }
            }
        }
        val episodesAdapter: PodcastAllEpisodesAdapter = PodcastAllEpisodesAdapter(context, episodes, showAllEpisodesDialogListener)
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

        // display dialog
        allEpisodesDialog = builder.show()
    }
}