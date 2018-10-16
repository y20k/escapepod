/*
 * ErrorDialog.kt
 * Implements the ErrorDialog class
 * A ErrorDialog shows an error dialog with details
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.dialogs

import android.content.Context
import android.content.DialogInterface
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.y20k.escapepods.R
import org.y20k.escapepods.helpers.LogHelper


/*
 * ErrorDialog class
 */
class ErrorDialog {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(ErrorDialog::class.java)


    /* Construct and show dialog */
    fun show(context: Context, errorTitle: Int, errorMessage: Int, errorDetails: String = String()) {
        // prepare dialog builder
        val inflater: LayoutInflater = LayoutInflater.from(context)
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)

        // get views
        val view: View = inflater.inflate(R.layout.dialog_error, null)
        val errorTitleView: TextView = view.findViewById(R.id.dialog_error_title) as TextView
        val errorMessageView: TextView = view.findViewById(R.id.dialog_error_message) as TextView
        val errorDetailsLinkView: TextView = view.findViewById(R.id.dialog_error_details_link) as TextView
        val errorDetailsView: TextView = view.findViewById(R.id.dialog_error_details) as TextView

        // set dialog view
        builder.setView(view)

        if (errorDetails.isNotEmpty()) {
            // show details link
            errorDetailsLinkView.setVisibility(View.VISIBLE)

            // allow scrolling on details view
            errorDetailsView.movementMethod = ScrollingMovementMethod()

            // show and hide details on click
            errorDetailsLinkView.setOnClickListener {
                when (errorDetailsView.visibility) {
                    View.GONE -> errorDetailsView.setVisibility(View.VISIBLE)
                    View.VISIBLE -> errorDetailsView.setVisibility(View.GONE)
                }
            }
            // set details text view
            errorDetailsView.text = errorDetails
        } else {
            // hide details link
            errorDetailsLinkView.setVisibility(View.GONE)
        }

        // set text views
        errorTitleView.text = context.getString(errorTitle)
        errorMessageView.text = context.getString(errorMessage)

        // add okay button
        builder.setPositiveButton(R.string.dialog_generic_button_okay, DialogInterface.OnClickListener { _, _ ->
            // listen for click on okay button
            // do nothing
        })

        // display error dialog
        builder.show()
    }
}