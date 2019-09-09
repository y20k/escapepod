/*
 * MeteredNetworkDialog.kt
 * Implements the MeteredNetworkDialog class
 * A MeteredNetworkDialog asks the user if he/she wants to proceed a network operation on a metered network
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.dialogs

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import org.y20k.escapepod.helpers.LogHelper


/*
 * MeteredNetworkDialog class
 */
class MeteredNetworkDialog (private var meteredNetworkDialogListener: MeteredNetworkDialogListener) {

    /* Interface used to communicate back to activity */
    interface MeteredNetworkDialogListener {
        fun onMeteredNetworkDialog(dialogType: Int, payload: String) {
        }
    }

    /* Define log tag */
    private val TAG = LogHelper.makeLogTag(MeteredNetworkDialog::class.java.simpleName)


    /* Construct and show dialog */
    fun show(context: Context, dialogType: Int, meteredNetworkTitle: Int, meteredNetworkMessage: Int, okayButtonString: Int, payload: String = Keys.DIALOG_EMPTY_PAYLOAD_STRING) {
        // prepare dialog builder
        val builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(context)

        // set title and message
        builder.setTitle(context.getString(meteredNetworkTitle))
        builder.setMessage(context.getString(meteredNetworkMessage))

        // add okay button
        builder.setPositiveButton(okayButtonString) { _, _ ->
            // listen for click on okay button
            meteredNetworkDialogListener.onMeteredNetworkDialog(dialogType, payload)
        }

        // add cancel button
        builder.setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
            // listen for click on cancel button
            // do nothing
        }

        // display dialog
        builder.show()
    }
}