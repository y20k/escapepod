package org.y20k.escapepods.dialogs

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import org.y20k.escapepods.R
import org.y20k.escapepods.core.Episode
import org.y20k.escapepods.helpers.LogHelper

class ShowNotesDialog () {

    /* Define log tag */
    private val TAG = LogHelper.makeLogTag(ShowNotesDialog::class.java.simpleName)


    /* Construct and show dialog */
    fun show(context: Context, episode: Episode) {
        // prepare dialog builder
        val inflater: LayoutInflater = LayoutInflater.from(context)
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)

        // get views
        val view: View = inflater.inflate(R.layout.dialog_show_notes, null)
        val podcastNameView: TextView = view.findViewById(R.id.podcast_name)
        val podcastFeedXmlView: TextView = view.findViewById(R.id.podcast_feed_xml)
        val episodeTitleView: TextView = view.findViewById(R.id.episode_title)
        val episodeShowNotesView: TextView = view.findViewById(R.id.episodes_show_notes_view)

        // set dialog view
        builder.setView(view)

        // set views
        podcastNameView.text = episode.podcastName
        podcastFeedXmlView.text = episode.podcastFeedLocation
        episodeTitleView.text = episode.title
        episodeShowNotesView.text = Html.fromHtml(episode.description, Html.FROM_HTML_MODE_COMPACT)

        // set up clipboard copy
        podcastFeedXmlView.setOnClickListener {
            val clip: ClipData = ClipData.newPlainText("simple text", episode.podcastFeedLocation)
            val cm: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip = clip
            Toast.makeText(context, R.string.toast_message_podcast_feed_address_copied, Toast.LENGTH_LONG).show()
        }

        // add close button
        builder.setPositiveButton(R.string.dialog_show_notes_close) { _, _ ->
            // listen for click on close button
            // do nothing
        }

        // display dialog
        builder.show()
    }

}