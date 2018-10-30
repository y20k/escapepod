/*
 * YesNoDialog
 * Implements the YesNoDialog class
 * A YesNoDialog asks the user if he/she wants to do something or not
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
 * YesNoDialog class
 */
class YesNoDialog (private var yesNoDialogListener: YesNoDialogListener) {

    /* Interface used to communicate back to activity */
    interface YesNoDialogListener {
        fun onYesNoDialog(dialogType: Int, dialogResult: Boolean, dialogPayload: Int) {
        }
    }


    /* Define log tag */
    private val TAG = LogHelper.makeLogTag(YesNoDialog::class.java.simpleName)


    /* Construct and show dialog */
    fun show(context: Context, dialogType: Int, dialogPayload: Int, yesNoTitleRessouce: Int, yesNoMessageRessouce: Int, yesButtonRessouce: Int, noButtonRessouce: Int = R.string.dialog_generic_button_cancel) {
        // variant of "show" that only accepts ressource ints
        show(context, dialogPayload, dialogType, yesNoTitleRessouce, context.getString(yesNoMessageRessouce), yesButtonRessouce, noButtonRessouce)
    }


    /* Construct and show dialog */
    fun show(context: Context, dialogType: Int, dialogPayload: Int, yesNoTitleRessouce: Int, yesNoMessageString: String, yesButtonRessouce: Int, noButtonRessouce: Int = R.string.dialog_generic_button_cancel) {
        // prepare dialog builder
        val inflater: LayoutInflater = LayoutInflater.from(context)
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)

        // get views
        val view: View = inflater.inflate(R.layout.dialog_yes_no, null)
        val yesNoTitleView: TextView = view.findViewById(R.id.dialog_yes_no_title) as TextView
        val yesNoMessageView: TextView = view.findViewById(R.id.dialog_yes_no_message) as TextView

        // set text views
        yesNoTitleView.text = context.getString(yesNoTitleRessouce)
        yesNoMessageView.text = yesNoMessageString

        // set dialog view
        builder.setView(view)

        // add yes button
        builder.setPositiveButton(yesButtonRessouce) { _, _ ->
            // listen for click on yes button
            yesNoDialogListener.onYesNoDialog(dialogType, true, dialogPayload)
        }

        // add no button
        builder.setNegativeButton(noButtonRessouce) { _, _ ->
            // listen for click on no button
            yesNoDialogListener.onYesNoDialog(dialogType, false, dialogPayload)
        }

        // display dialog
        builder.show()
    }
}