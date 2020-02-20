/*
 * ShowNotesDialog
 * Implements the ShowNotesDialog class
 * A ShowNotesDialog displays the show notes for a podcast episode
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.y20k.escapepod.R
import org.y20k.escapepod.core.Episode
import org.y20k.escapepod.helpers.LogHelper


class ShowNotesDialog () {

    /* Define log tag */
    private val TAG = LogHelper.makeLogTag(ShowNotesDialog::class.java.simpleName)


    /* Construct and show dialog */
    fun show(context: Context, episode: Episode) {
        // prepare dialog builder
        val builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(context)

        // get views
        val inflater: LayoutInflater = LayoutInflater.from(context)
        val view: View = inflater.inflate(R.layout.dialog_show_notes, null)
        val podcastNameView: TextView = view.findViewById(R.id.result_podcast_name)
        val podcastWebsiteView: TextView = view.findViewById(R.id.podcast_website)
        val podcastWebsiteOpenView: ImageView = view.findViewById(R.id.open_website_icon)
        val podcastFeedView: TextView = view.findViewById(R.id.podcast_feed)
        val episodeDateView: TextView = view.findViewById(R.id.episode_date)
        val episodeTitleView: TextView = view.findViewById(R.id.episode_title)
        val episodeShowNotesView: TextView = view.findViewById(R.id.episodes_show_notes_view)

        // set dialog view
        builder.setView(view)

        // set views
        podcastNameView.text = episode.podcastName
        episodeDateView.text = episode.getDateString()
        episodeTitleView.text = episode.title
        episodeTitleView.isSelected = true // triggers the marquee
        episodeShowNotesView.text = Html.fromHtml(episode.description.replace(Regex("<img.+?>"), ""), Html.FROM_HTML_MODE_COMPACT) // regex removes placeholder images
        episodeShowNotesView.movementMethod = LinkMovementMethod.getInstance() // make link tapable

        // podcast website: set up open browser
        if (episode.podcastWebsite.isNotEmpty()) {
            podcastWebsiteView.visibility = View.VISIBLE
            podcastWebsiteOpenView.visibility = View.VISIBLE
            podcastWebsiteView.setOnClickListener {
                startActivity(context, Intent(Intent.ACTION_VIEW, Uri.parse(episode.podcastWebsite)), null)
            }
        }

        // podcast feed: set up clipboard copy
        podcastFeedView.setOnClickListener {
            val clip: ClipData = ClipData.newPlainText("simple text", episode.podcastFeedLocation)
            val cm: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(clip)
            Toast.makeText(context, R.string.toast_message_podcast_feed_address_copied, Toast.LENGTH_LONG).show()
        }

        // add close button
        builder.setPositiveButton(R.string.dialog_episode_shownotes_close) { _, _ ->
            // listen for click on close button
            // do nothing
        }

        // display dialog
        builder.show()
    }

}