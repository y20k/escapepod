/*
 * MeteredNetworkDialog.kt
 * Implements the MeteredNetworkDialog class
 * A MeteredNetworkDialog asks the user if he/she wants to proceed a network operation on a metered network
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
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.y20k.escapepods.R
import org.y20k.escapepods.helpers.LogHelper


/*
 * MeteredNetworkDialog class
 */
class MeteredNetworkDialog (private var meteredNetworkDialogListener: MeteredNetworkDialogListener) {

    /* Interface used to communicate back to activity */
    interface MeteredNetworkDialogListener {
        fun onMeteredNetworkDialog(dialogType: Int) {
        }
    }

    /* Define log tag */
    private val TAG = LogHelper.makeLogTag(MeteredNetworkDialog::class.java.simpleName)


    /* Construct and show dialog */
    fun show(context: Context, dialogType: Int, meteredNetworkTitle: Int, meteredNetworkMessage: Int, okayButtonString: Int) {
        // prepare dialog builder
        val inflater: LayoutInflater = LayoutInflater.from(context)
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)

        // get views
        val view: View = inflater.inflate(R.layout.dialog_metered_network, null)
        val meteredNetworkTitleView: TextView = view.findViewById(R.id.dialog_metered_network_title) as TextView
        val meteredNetworkMessageView: TextView = view.findViewById(R.id.dialog_metered_network_message) as TextView

        // set text views
        meteredNetworkTitleView.text = context.getString(meteredNetworkTitle)
        meteredNetworkMessageView.text = context.getString(meteredNetworkMessage)

        // set dialog view
        builder.setView(view)

        // add okay button
        builder.setPositiveButton(okayButtonString) { _, _ ->
            // listen for click on okay button
            meteredNetworkDialogListener.onMeteredNetworkDialog(dialogType)
        }

        // add cancel button
        builder.setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
            // listen for click on cancel button
            // do nothing
        }

        // display error dialog
        builder.show()
    }
}