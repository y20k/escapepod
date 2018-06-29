/*
 * ErrorDialog.kt
 * Implements the DialogError class
 * A DialogError shows an error dialog with details
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
import android.support.v7.app.AlertDialog
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import org.y20k.escapepods.R


/*
 * ErrorDialog class
 */
class ErrorDialog {

    /* Construct and show dialog */
    fun show(context: Context, errorTitle: String, errorMessage: String, errorDetails: String) {
        // prepare dialog builder
        val inflater: LayoutInflater = LayoutInflater.from(context)
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)

        // get views
        val view: View = inflater.inflate(R.layout.dialog_error, null)
        val errorTitleView: TextView = view.findViewById(R.id.dialog_error_title) as TextView
        val errorMessageView: TextView = view.findViewById(R.id.dialog_error_message) as TextView
        val errorDetailsLinkView: TextView = view.findViewById(R.id.dialog_error_details_link) as TextView
        val errorDetailsView: TextView = view.findViewById(R.id.dialog_error_details) as TextView

        if (errorDetails.isNotEmpty()) {
            // show details link
            errorDetailsLinkView.visibility == View.VISIBLE

            // allow scrolling on details view
            errorDetailsView.movementMethod = ScrollingMovementMethod()

            // show and hide details on click
            errorDetailsLinkView.setOnClickListener {
                when (errorDetailsView.visibility) {
                    View.GONE -> errorDetailsView.visibility = View.VISIBLE
                    View.VISIBLE -> errorDetailsView.visibility = View.GONE
                }
            }
            // set details text view
            errorDetailsView.text = errorDetails
        } else {
            // hide details link
            errorDetailsLinkView.visibility == View.GONE
        }

        // set text views
        errorTitleView.text = errorTitle
        errorMessageView.text = errorMessage

        // set dialog view
        builder.setView(view)

        // add rename button
        builder.setPositiveButton(R.string.dialog_generic_button_okay, DialogInterface.OnClickListener { arg0, arg1 ->
            // listen for click on okay button
            // do nothing
        })

        // display error dialog
        builder.show()
    }
}