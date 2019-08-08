/*
 * AddPodcastDialog.kt
 * Implements the AddPodcastDialog class
 * A DialogAdd asks the user for the feed URL of a podcast
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.y20k.escapepods.R
import org.y20k.escapepods.helpers.LogHelper


/*
 * AddPodcastDialog class
 */
class AddPodcastDialog (private var addPodcastDialogListener: AddPodcastDialogListener) {

    /* Interface used to communicate back to activity */
    interface AddPodcastDialogListener {
        fun onAddPodcastDialog(textInput: String) {
        }
    }

    /* Define log tag */
    private val TAG = LogHelper.makeLogTag(AddPodcastDialog::class.java.simpleName)


    /* Construct and show dialog */
    fun show(context: Context) {
        // prepare dialog builder
        val builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(context)

        // set title
        builder.setTitle(R.string.dialog_add_podcast_title)

        // get input field
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_add_podcast, null)
        val inputField = view.findViewById<View>(R.id.dialog_add_podcast_input_edit_text) as EditText

        // set dialog view
        builder.setView(view)

        // add "add" button
        builder.setPositiveButton(R.string.dialog_add_podcast_button) { _, _ ->
            // hand text over to initiating activity
            inputField.text?.let {
                addPodcastDialogListener.onAddPodcastDialog(it.toString());
            }
        }

        // add cancel button
        builder.setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
            // listen for click on cancel button
            // do nothing
        }

        // display add dialog
        builder.show()
    }

}